package com.friendsfantasy.fantasybackend.contest.service;

import com.friendsfantasy.fantasybackend.contest.entity.Contest;
import com.friendsfantasy.fantasybackend.contest.entity.ContestEntry;
import com.friendsfantasy.fantasybackend.contest.repository.ContestEntryRepository;
import com.friendsfantasy.fantasybackend.contest.repository.ContestRepository;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.integration.sportmonks.SportMonksCricketClient;
import com.friendsfantasy.fantasybackend.player.entity.Player;
import com.friendsfantasy.fantasybackend.player.repository.PlayerRepository;
import com.friendsfantasy.fantasybackend.team.dto.TeamPlayerResponse;
import com.friendsfantasy.fantasybackend.team.dto.TeamResponse;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeam;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeamPlayer;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamPlayerRepository;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CricketFantasyScoringService {

    private static final BigDecimal ZERO_POINTS = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Set<String> REGULAR_LIMITED_OVERS_SCOREBOARDS = Set.of("S1", "S2");
    private static final Set<String> REGULAR_TEST_SCOREBOARDS = Set.of("S1", "S2", "S3", "S4");

    private final FixtureRepository fixtureRepository;
    private final ContestRepository contestRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final UserMatchTeamRepository userMatchTeamRepository;
    private final UserMatchTeamPlayerRepository userMatchTeamPlayerRepository;
    private final PlayerRepository playerRepository;
    private final SportMonksCricketClient sportMonksCricketClient;

    @Value("${app.scoring.live-sync-min-seconds:30}")
    private long liveSyncMinSeconds;

    @Transactional
    public Map<String, Object> syncFixtureFantasyPoints(Long fixtureId, boolean force) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            return buildSkippedResult(fixtureId, "FIXTURE_NOT_STARTED");
        }

        if (isFixtureCancelledStatus(fixture.getStatus())) {
            return buildSkippedResult(fixtureId, "FIXTURE_CANCELLED");
        }

        if (!force && isWithinSyncThrottleWindow(fixture)) {
            return buildSkippedResult(fixtureId, "THROTTLED");
        }

        List<Contest> contests = contestRepository.findByFixtureIdOrderByEntryFeePointsAscIdAsc(fixtureId).stream()
                .filter(this::supportsAutomaticScoring)
                .toList();

        if (contests.isEmpty()) {
            fixture.setLastScoreSyncedAt(LocalDateTime.now());
            fixtureRepository.save(fixture);
            return buildCompletedResult(fixtureId, 0, 0, 0);
        }

        JsonNode root = sportMonksCricketClient.getFixtureWithFantasyScoringData(fixture.getExternalFixtureId());
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new RuntimeException("Fixture scoring data not found in SportMonks");
        }

        updateFixtureSnapshotFromLiveData(fixture, data);

        ScoringFormat scoringFormat = resolveScoringFormat(textValue(data, "type"));
        FantasyScorecard scorecard = buildFantasyScorecard(data, scoringFormat);
        List<Long> contestIds = contests.stream().map(Contest::getId).toList();
        List<ContestEntry> entries = contestEntryRepository.findByContestIdInOrderByContestIdAscJoinedAtAsc(contestIds);

        Set<Long> teamIds = new LinkedHashSet<>();
        for (ContestEntry entry : entries) {
            if (entry.getUserMatchTeamId() != null) {
                teamIds.add(entry.getUserMatchTeamId());
            }
        }

        Map<Long, BigDecimal> teamPointsByTeamId = calculateTeamPoints(new ArrayList<>(teamIds), scorecard, scoringFormat);

        int updatedEntries = 0;
        int teamsScored = 0;

        for (ContestEntry entry : entries) {
            BigDecimal nextPoints = ZERO_POINTS;
            if (entry.getStatus() == ContestEntry.Status.JOINED && entry.getUserMatchTeamId() != null) {
                nextPoints = teamPointsByTeamId.getOrDefault(entry.getUserMatchTeamId(), ZERO_POINTS);
            }

            if (entry.getFantasyPoints() == null || entry.getFantasyPoints().compareTo(nextPoints) != 0) {
                entry.setFantasyPoints(nextPoints);
                updatedEntries++;
            }
        }

        if (!entries.isEmpty()) {
            contestEntryRepository.saveAll(entries);
        }

        teamsScored = teamPointsByTeamId.size();

        fixture.setLastScoreSyncedAt(LocalDateTime.now());
        fixtureRepository.save(fixture);

        return buildCompletedResult(fixtureId, contests.size(), teamsScored, updatedEntries);
    }

    @Transactional(readOnly = true)
    public TeamResponse populateFantasyPointsForTeamPreview(Long fixtureId, TeamResponse team) {
        if (team == null) {
            return null;
        }

        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (fixture.getDeadlineTime() == null || fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            return team;
        }

        if (fixture.getExternalFixtureId() == null || isFixtureCancelledStatus(fixture.getStatus())) {
            return team;
        }

        JsonNode root = sportMonksCricketClient.getFixtureWithFantasyScoringData(fixture.getExternalFixtureId());
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return team;
        }

        ScoringFormat scoringFormat = resolveScoringFormat(textValue(data, "type"));
        FantasyScorecard scorecard = buildFantasyScorecard(data, scoringFormat);
        Map<Long, BigDecimal> fantasyPointsByPlayerId = calculatePreviewPlayerPoints(team, scorecard, scoringFormat);

        return TeamResponse.builder()
                .teamId(team.getTeamId())
                .fixtureId(team.getFixtureId())
                .teamName(team.getTeamName())
                .captainPlayerId(team.getCaptainPlayerId())
                .viceCaptainPlayerId(team.getViceCaptainPlayerId())
                .isLocked(team.getIsLocked())
                .canDelete(team.getCanDelete())
                .players(applyFantasyPoints(team.getPlayers(), fantasyPointsByPlayerId))
                .substitutes(applyFantasyPoints(team.getSubstitutes(), fantasyPointsByPlayerId))
                .build();
    }

    private Map<Long, BigDecimal> calculateTeamPoints(
            List<Long> teamIds,
            FantasyScorecard scorecard,
            ScoringFormat scoringFormat
    ) {
        if (teamIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, UserMatchTeam> teamsById = new HashMap<>();
        for (UserMatchTeam team : userMatchTeamRepository.findAllById(teamIds)) {
            teamsById.put(team.getId(), team);
        }

        Map<Long, List<UserMatchTeamPlayer>> teamPlayersByTeamId = new LinkedHashMap<>();
        for (UserMatchTeamPlayer player : userMatchTeamPlayerRepository
                .findByUserMatchTeamIdInOrderByUserMatchTeamIdAscIsSubstituteAscSubstitutePriorityAscIdAsc(teamIds)) {
            teamPlayersByTeamId.computeIfAbsent(player.getUserMatchTeamId(), ignored -> new ArrayList<>()).add(player);
        }

        Set<Long> localPlayerIds = new LinkedHashSet<>();
        for (List<UserMatchTeamPlayer> players : teamPlayersByTeamId.values()) {
            for (UserMatchTeamPlayer player : players) {
                if (!Boolean.TRUE.equals(player.getIsSubstitute())) {
                    localPlayerIds.add(player.getPlayerId());
                }
            }
        }

        Map<Long, Player> playerById = new HashMap<>();
        for (Player player : playerRepository.findAllById(localPlayerIds)) {
            playerById.put(player.getId(), player);
        }

        Map<Long, BigDecimal> teamPointsById = new HashMap<>();

        for (Long teamId : teamIds) {
            UserMatchTeam team = teamsById.get(teamId);
            if (team == null) {
                continue;
            }

            BigDecimal total = ZERO_POINTS;
            for (UserMatchTeamPlayer teamPlayer : teamPlayersByTeamId.getOrDefault(teamId, List.of())) {
                if (Boolean.TRUE.equals(teamPlayer.getIsSubstitute())) {
                    continue;
                }

                Player player = playerById.get(teamPlayer.getPlayerId());
                if (player == null || player.getExternalPlayerId() == null) {
                    continue;
                }

                BigDecimal playerPoints = calculatePlayerPoints(
                        teamPlayer,
                        player.getExternalPlayerId(),
                        scorecard,
                        scoringFormat
                );
                total = total.add(playerPoints);
            }

            teamPointsById.put(teamId, total.setScale(2, RoundingMode.HALF_UP));
        }

        return teamPointsById;
    }

    private BigDecimal calculatePlayerPoints(
            UserMatchTeamPlayer teamPlayer,
            Long externalPlayerId,
            FantasyScorecard scorecard,
            ScoringFormat scoringFormat
    ) {
        return calculatePlayerPoints(
                teamPlayer.getRoleCode(),
                Boolean.TRUE.equals(teamPlayer.getIsCaptain()),
                Boolean.TRUE.equals(teamPlayer.getIsViceCaptain()),
                externalPlayerId,
                scorecard,
                scoringFormat
        );
    }

    private BigDecimal calculatePlayerPoints(
            String roleCode,
            boolean captain,
            boolean viceCaptain,
            Long externalPlayerId,
            FantasyScorecard scorecard,
            ScoringFormat scoringFormat
    ) {
        LineupInfo lineupInfo = scorecard.lineupByExternalPlayerId.getOrDefault(externalPlayerId, LineupInfo.EMPTY);
        BattingStats batting = scorecard.battingByExternalPlayerId.getOrDefault(externalPlayerId, BattingStats.EMPTY);
        BowlingStats bowling = scorecard.bowlingByExternalPlayerId.getOrDefault(externalPlayerId, BowlingStats.EMPTY);
        FieldingStats fielding = scorecard.fieldingByExternalPlayerId.getOrDefault(externalPlayerId, FieldingStats.EMPTY);

        int points = 0;

        if (lineupInfo.announced) {
            points += 4;
        }
        if (lineupInfo.substitute && scorecard.playersWithOnFieldInvolvement.contains(externalPlayerId)) {
            points += 4;
        }

        points += batting.runs;
        points += batting.fours * boundaryBonusPoints(scoringFormat);
        points += batting.sixes * sixBonusPoints(scoringFormat);
        points += battingMilestoneBonus(scoringFormat, batting.runs);

        if (batting.runs == 0
                && scorecard.dismissedBatters.contains(externalPlayerId)
                && !Objects.equals(roleCode, "BOWL")) {
            points += duckPenalty(scoringFormat);
        }

        points += strikeRateBonus(scoringFormat, roleCode, batting.runs, batting.ballsFaced);

        points += dotBallPoints(scoringFormat, bowling.dotBalls);
        points += bowling.wickets * wicketPoints(scoringFormat);
        points += bowling.lbwOrBowledWickets * 8;
        points += wicketMilestoneBonus(scoringFormat, bowling.wickets);
        points += bowling.maidens * maidenOverPoints(scoringFormat);
        points += economyRateBonus(scoringFormat, bowling.ballsBowled, bowling.runsConceded);

        points += fielding.catches * 8;
        points += catchBonus(scoringFormat, fielding.catches);
        points += fielding.stumpings * 12;
        points += fielding.runOutDirectHits * 12;
        points += fielding.runOutAssists * 6;

        BigDecimal total = BigDecimal.valueOf(points).setScale(2, RoundingMode.HALF_UP);
        if (captain) {
            return total.multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.HALF_UP);
        }
        if (viceCaptain) {
            return total.multiply(BigDecimal.valueOf(1.5)).setScale(2, RoundingMode.HALF_UP);
        }
        return total;
    }

    private Map<Long, BigDecimal> calculatePreviewPlayerPoints(
            TeamResponse team,
            FantasyScorecard scorecard,
            ScoringFormat scoringFormat
    ) {
        Set<Long> localPlayerIds = new LinkedHashSet<>();
        for (TeamPlayerResponse player : allPreviewPlayers(team)) {
            if (player.getPlayerId() != null) {
                localPlayerIds.add(player.getPlayerId());
            }
        }

        Map<Long, Player> playerById = new HashMap<>();
        for (Player player : playerRepository.findAllById(localPlayerIds)) {
            playerById.put(player.getId(), player);
        }

        Map<Long, BigDecimal> fantasyPointsByPlayerId = new HashMap<>();
        for (TeamPlayerResponse player : allPreviewPlayers(team)) {
            if (player.getPlayerId() == null) {
                continue;
            }

            Player storedPlayer = playerById.get(player.getPlayerId());
            if (storedPlayer == null || storedPlayer.getExternalPlayerId() == null) {
                fantasyPointsByPlayerId.put(player.getPlayerId(), ZERO_POINTS);
                continue;
            }

            BigDecimal fantasyPoints = calculatePlayerPoints(
                    player.getRoleCode(),
                    Boolean.TRUE.equals(player.getIsCaptain()),
                    Boolean.TRUE.equals(player.getIsViceCaptain()),
                    storedPlayer.getExternalPlayerId(),
                    scorecard,
                    scoringFormat
            );
            fantasyPointsByPlayerId.put(player.getPlayerId(), fantasyPoints);
        }

        return fantasyPointsByPlayerId;
    }

    private List<TeamPlayerResponse> allPreviewPlayers(TeamResponse team) {
        List<TeamPlayerResponse> players = new ArrayList<>();
        if (team.getPlayers() != null) {
            players.addAll(team.getPlayers());
        }
        if (team.getSubstitutes() != null) {
            players.addAll(team.getSubstitutes());
        }
        return players;
    }

    private List<TeamPlayerResponse> applyFantasyPoints(
            List<TeamPlayerResponse> players,
            Map<Long, BigDecimal> fantasyPointsByPlayerId
    ) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }

        List<TeamPlayerResponse> response = new ArrayList<>();
        for (TeamPlayerResponse player : players) {
            response.add(TeamPlayerResponse.builder()
                    .fixturePlayerPoolId(player.getFixturePlayerPoolId())
                    .playerId(player.getPlayerId())
                    .playerName(player.getPlayerName())
                    .roleCode(player.getRoleCode())
                    .teamSide(player.getTeamSide())
                    .teamName(player.getTeamName())
                    .creditValue(player.getCreditValue())
                    .imageUrl(player.getImageUrl())
                    .fantasyPoints(fantasyPointsByPlayerId.getOrDefault(player.getPlayerId(), ZERO_POINTS))
                    .isCaptain(player.getIsCaptain())
                    .isViceCaptain(player.getIsViceCaptain())
                    .isSubstitute(player.getIsSubstitute())
                    .substitutePriority(player.getSubstitutePriority())
                    .build());
        }
        return response;
    }

    private FantasyScorecard buildFantasyScorecard(JsonNode data, ScoringFormat scoringFormat) {
        FantasyScorecard scorecard = new FantasyScorecard();

        for (JsonNode lineupNode : data.path("lineup")) {
            Long playerId = longValue(lineupNode, "id");
            if (playerId == null) {
                continue;
            }

            LineupInfo info = scorecard.lineupByExternalPlayerId.computeIfAbsent(playerId, ignored -> new LineupInfo());
            info.announced = true;
            info.substitute = booleanValue(lineupNode, "lineup.substitution");
        }

        for (JsonNode battingNode : data.path("batting")) {
            if (!isRegularScoreboard(battingNode, scoringFormat)) {
                continue;
            }

            Long playerId = longValue(battingNode, "player_id");
            if (playerId == null) {
                continue;
            }

            BattingStats stats = scorecard.battingByExternalPlayerId.computeIfAbsent(playerId, ignored -> new BattingStats());
            stats.runs += firstAvailableInt(battingNode, "score", "runs");
            stats.ballsFaced += firstAvailableInt(battingNode, "ball", "balls");
            stats.fours += firstAvailableInt(battingNode, "four_x", "fours");
            stats.sixes += firstAvailableInt(battingNode, "six_x", "sixes");
            scorecard.playersWithOnFieldInvolvement.add(playerId);
        }

        for (JsonNode bowlingNode : data.path("bowling")) {
            if (!isRegularScoreboard(bowlingNode, scoringFormat)) {
                continue;
            }

            Long playerId = longValue(bowlingNode, "player_id");
            if (playerId == null) {
                continue;
            }

            BowlingStats stats = scorecard.bowlingByExternalPlayerId.computeIfAbsent(playerId, ignored -> new BowlingStats());
            stats.ballsBowled += oversToBalls(decimalValue(bowlingNode, "overs"));
            stats.runsConceded += firstAvailableInt(bowlingNode, "runs", "runs_conceded");
            stats.maidens += firstAvailableInt(bowlingNode, "medians", "maidens");
            Integer wicketsFromScoreboard = firstAvailableNullableInt(bowlingNode, "wickets", "wicket");
            if (wicketsFromScoreboard != null) {
                stats.wickets += wicketsFromScoreboard;
                scorecard.bowlersWithScoreboardWicketTotals.add(playerId);
            }
            scorecard.playersWithOnFieldInvolvement.add(playerId);
        }

        for (JsonNode ballNode : data.path("balls")) {
            if (!isRegularScoreboard(ballNode, scoringFormat)) {
                continue;
            }

            Long batsmanId = longValue(ballNode, "batsman_id");
            Long bowlerId = longValue(ballNode, "bowler_id");
            Long catchStumpId = longValue(ballNode, "catchstump_id");
            Long runOutById = longValue(ballNode, "runout_by_id");

            if (batsmanId != null) {
                scorecard.playersWithOnFieldInvolvement.add(batsmanId);
            }
            if (bowlerId != null) {
                scorecard.playersWithOnFieldInvolvement.add(bowlerId);
            }
            if (catchStumpId != null) {
                scorecard.playersWithOnFieldInvolvement.add(catchStumpId);
            }
            if (runOutById != null) {
                scorecard.playersWithOnFieldInvolvement.add(runOutById);
            }

            JsonNode scoreNode = ballNode.path("score");
            String scoreName = normalizedScoreName(scoreNode);

            if (bowlerId != null && isDotBall(scoreNode)) {
                BowlingStats bowlingStats = scorecard.bowlingByExternalPlayerId
                        .computeIfAbsent(bowlerId, ignored -> new BowlingStats());
                bowlingStats.dotBalls++;
            }

            if (!booleanValue(scoreNode, "is_wicket")) {
                continue;
            }

            Long dismissedBatterId = longValue(ballNode, "batsmanout_id");
            if (dismissedBatterId == null) {
                dismissedBatterId = batsmanId;
            }
            if (dismissedBatterId != null) {
                scorecard.dismissedBatters.add(dismissedBatterId);
            }

            if (isRunOut(scoreName)) {
                registerRunOut(scorecard, catchStumpId, runOutById);
                continue;
            }

            if (bowlerId != null) {
                BowlingStats bowlingStats = scorecard.bowlingByExternalPlayerId
                        .computeIfAbsent(bowlerId, ignored -> new BowlingStats());
                if (!scorecard.bowlersWithScoreboardWicketTotals.contains(bowlerId)) {
                    bowlingStats.wickets++;
                }
                if (isLbwOrBowled(scoreName)) {
                    bowlingStats.lbwOrBowledWickets++;
                }
            }

            if (isStumping(scoreName) && catchStumpId != null) {
                FieldingStats fieldingStats = scorecard.fieldingByExternalPlayerId
                        .computeIfAbsent(catchStumpId, ignored -> new FieldingStats());
                fieldingStats.stumpings++;
            } else if (isCatch(scoreName) && catchStumpId != null) {
                FieldingStats fieldingStats = scorecard.fieldingByExternalPlayerId
                        .computeIfAbsent(catchStumpId, ignored -> new FieldingStats());
                fieldingStats.catches++;
            }
        }

        return scorecard;
    }

    private void registerRunOut(FantasyScorecard scorecard, Long catchStumpId, Long runOutById) {
        if (catchStumpId != null && runOutById != null && !Objects.equals(catchStumpId, runOutById)) {
            scorecard.fieldingByExternalPlayerId
                    .computeIfAbsent(catchStumpId, ignored -> new FieldingStats())
                    .runOutAssists++;
            scorecard.fieldingByExternalPlayerId
                    .computeIfAbsent(runOutById, ignored -> new FieldingStats())
                    .runOutAssists++;
            return;
        }

        Long directHitPlayerId = catchStumpId != null ? catchStumpId : runOutById;
        if (directHitPlayerId == null) {
            return;
        }

        scorecard.fieldingByExternalPlayerId
                .computeIfAbsent(directHitPlayerId, ignored -> new FieldingStats())
                .runOutDirectHits++;
    }

    private int boundaryBonusPoints(ScoringFormat format) {
        return switch (format) {
            case HUNDRED, OTHER_T20 -> 1;
            default -> 4;
        };
    }

    private int sixBonusPoints(ScoringFormat format) {
        return switch (format) {
            case HUNDRED, OTHER_T20 -> 2;
            default -> 6;
        };
    }

    private int duckPenalty(ScoringFormat format) {
        return switch (format) {
            case ODI -> -3;
            case TEST -> -4;
            default -> -2;
        };
    }

    private int dotBallPoints(ScoringFormat format, int dotBalls) {
        return switch (format) {
            case ODI -> dotBalls / 3;
            case T20, T10 -> dotBalls;
            default -> 0;
        };
    }

    private int wicketPoints(ScoringFormat format) {
        return switch (format) {
            case TEST -> 20;
            case HUNDRED -> 25;
            default -> 30;
        };
    }

    private int maidenOverPoints(ScoringFormat format) {
        return switch (format) {
            case T20, OTHER_T20 -> 12;
            case ODI -> 4;
            case T10 -> 16;
            default -> 0;
        };
    }

    private int catchBonus(ScoringFormat format, int catches) {
        if (format == ScoringFormat.TEST) {
            return 0;
        }
        return catches >= 3 ? 4 : 0;
    }

    private int battingMilestoneBonus(ScoringFormat format, int runs) {
        return switch (format) {
            case T20 -> {
                if (runs >= 100) yield 16;
                if (runs >= 75) yield 12;
                if (runs >= 50) yield 8;
                if (runs >= 25) yield 4;
                yield 0;
            }
            case ODI, TEST -> {
                if (runs >= 150) yield 24;
                if (runs >= 125) yield 20;
                if (runs >= 100) yield 16;
                if (runs >= 75) yield 12;
                if (runs >= 50) yield 8;
                if (runs >= 25) yield 4;
                yield 0;
            }
            case T10 -> {
                if (runs >= 75) yield 16;
                if (runs >= 50) yield 12;
                if (runs >= 25) yield 8;
                yield 0;
            }
            case HUNDRED -> {
                if (runs >= 100) yield 20;
                if (runs >= 50) yield 10;
                if (runs >= 30) yield 5;
                yield 0;
            }
            case OTHER_T20 -> {
                if (runs >= 100) yield 16;
                if (runs >= 50) yield 8;
                if (runs >= 30) yield 4;
                yield 0;
            }
        };
    }

    private int wicketMilestoneBonus(ScoringFormat format, int wickets) {
        return switch (format) {
            case T20 -> {
                if (wickets >= 5) yield 12;
                if (wickets == 4) yield 8;
                if (wickets == 3) yield 4;
                yield 0;
            }
            case ODI, TEST -> {
                if (wickets >= 6) yield 12;
                if (wickets == 5) yield 8;
                if (wickets == 4) yield 4;
                yield 0;
            }
            case T10 -> {
                if (wickets >= 5) yield 16;
                if (wickets == 4) yield 12;
                if (wickets == 3) yield 8;
                if (wickets == 2) yield 4;
                yield 0;
            }
            case HUNDRED -> {
                if (wickets >= 5) yield 20;
                if (wickets == 4) yield 10;
                if (wickets == 3) yield 5;
                if (wickets == 2) yield 3;
                yield 0;
            }
            case OTHER_T20 -> {
                if (wickets >= 5) yield 16;
                if (wickets == 4) yield 8;
                if (wickets == 3) yield 4;
                yield 0;
            }
        };
    }

    private int economyRateBonus(ScoringFormat format, int ballsBowled, int runsConceded) {
        return switch (format) {
            case T20 -> limitedOversEconomyBonus(
                    ballsBowled,
                    runsConceded,
                    12,
                    BigDecimal.valueOf(5),
                    BigDecimal.valueOf(6),
                    BigDecimal.valueOf(7),
                    BigDecimal.valueOf(10),
                    BigDecimal.valueOf(11),
                    BigDecimal.valueOf(11),
                    BigDecimal.valueOf(12)
            );
            case ODI -> limitedOversEconomyBonus(
                    ballsBowled,
                    runsConceded,
                    30,
                    BigDecimal.valueOf(2.5),
                    BigDecimal.valueOf(3.5),
                    BigDecimal.valueOf(4.5),
                    BigDecimal.valueOf(7),
                    BigDecimal.valueOf(8),
                    BigDecimal.valueOf(8),
                    BigDecimal.valueOf(9)
            );
            case T10 -> limitedOversEconomyBonus(
                    ballsBowled,
                    runsConceded,
                    6,
                    BigDecimal.valueOf(7),
                    BigDecimal.valueOf(8),
                    BigDecimal.valueOf(9),
                    BigDecimal.valueOf(14),
                    BigDecimal.valueOf(15),
                    BigDecimal.valueOf(15),
                    BigDecimal.valueOf(16)
            );
            case OTHER_T20 -> limitedOversEconomyBonus(
                    ballsBowled,
                    runsConceded,
                    12,
                    BigDecimal.valueOf(2.5),
                    BigDecimal.valueOf(3.5),
                    BigDecimal.valueOf(4.5),
                    BigDecimal.valueOf(7),
                    BigDecimal.valueOf(8),
                    BigDecimal.valueOf(8.01),
                    BigDecimal.valueOf(9)
            );
            case TEST, HUNDRED -> 0;
        };
    }

    private int limitedOversEconomyBonus(
            int ballsBowled,
            int runsConceded,
            int minimumBalls,
            BigDecimal excellentUpperBound,
            BigDecimal goodUpperBound,
            BigDecimal positiveUpperBound,
            BigDecimal negativeLowerBound,
            BigDecimal negativeUpperBound,
            BigDecimal harsherLowerBound,
            BigDecimal harsherUpperBound
    ) {
        if (ballsBowled < minimumBalls) {
            return 0;
        }

        BigDecimal economyRate = BigDecimal.valueOf(runsConceded)
                .multiply(BigDecimal.valueOf(6))
                .divide(BigDecimal.valueOf(ballsBowled), 4, RoundingMode.HALF_UP);

        if (economyRate.compareTo(excellentUpperBound) < 0) {
            return 6;
        }
        if (economyRate.compareTo(goodUpperBound) < 0) {
            return 4;
        }
        if (economyRate.compareTo(positiveUpperBound) <= 0) {
            return 2;
        }
        if (economyRate.compareTo(negativeLowerBound) >= 0
                && economyRate.compareTo(negativeUpperBound) <= 0) {
            return -2;
        }
        if (economyRate.compareTo(harsherLowerBound) > 0
                && economyRate.compareTo(harsherUpperBound) <= 0) {
            return -4;
        }
        if (economyRate.compareTo(harsherUpperBound) > 0) {
            return -6;
        }
        return 0;
    }

    private int strikeRateBonus(ScoringFormat format, String roleCode, int runs, int ballsFaced) {
        if (Objects.equals(roleCode, "BOWL")) {
            return 0;
        }

        return switch (format) {
            case T20 -> battingStrikeRateBonus(
                    runs,
                    ballsFaced,
                    10,
                    BigDecimal.valueOf(170),
                    BigDecimal.valueOf(150),
                    BigDecimal.valueOf(130),
                    BigDecimal.valueOf(60),
                    BigDecimal.valueOf(70),
                    BigDecimal.valueOf(50),
                    BigDecimal.valueOf(60)
            );
            case ODI, OTHER_T20 -> battingStrikeRateBonus(
                    runs,
                    ballsFaced,
                    20,
                    BigDecimal.valueOf(140),
                    BigDecimal.valueOf(120),
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(40),
                    BigDecimal.valueOf(50),
                    BigDecimal.valueOf(30),
                    BigDecimal.valueOf(40)
            );
            case T10 -> battingStrikeRateBonus(
                    runs,
                    ballsFaced,
                    5,
                    BigDecimal.valueOf(190),
                    BigDecimal.valueOf(170),
                    BigDecimal.valueOf(150),
                    BigDecimal.valueOf(70),
                    BigDecimal.valueOf(80),
                    BigDecimal.valueOf(60),
                    BigDecimal.valueOf(70)
            );
            case TEST, HUNDRED -> 0;
        };
    }

    private int battingStrikeRateBonus(
            int runs,
            int ballsFaced,
            int minimumBalls,
            BigDecimal topTierLowerBound,
            BigDecimal upperPositiveLowerBound,
            BigDecimal positiveLowerBound,
            BigDecimal negativeLowerBound,
            BigDecimal negativeUpperBound,
            BigDecimal harsherNegativeLowerBound,
            BigDecimal harsherNegativeUpperBound
    ) {
        if (ballsFaced < minimumBalls) {
            return 0;
        }

        BigDecimal strikeRate = BigDecimal.valueOf(runs)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(ballsFaced), 4, RoundingMode.HALF_UP);

        if (strikeRate.compareTo(topTierLowerBound) > 0) {
            return 6;
        }
        if (strikeRate.compareTo(upperPositiveLowerBound) > 0) {
            return 4;
        }
        if (strikeRate.compareTo(positiveLowerBound) >= 0) {
            return 2;
        }
        if (strikeRate.compareTo(negativeLowerBound) >= 0
                && strikeRate.compareTo(negativeUpperBound) <= 0) {
            return -2;
        }
        if (strikeRate.compareTo(harsherNegativeLowerBound) >= 0
                && strikeRate.compareTo(harsherNegativeUpperBound) < 0) {
            return -4;
        }
        if (strikeRate.compareTo(harsherNegativeLowerBound) < 0) {
            return -6;
        }
        return 0;
    }

    private boolean supportsAutomaticScoring(Contest contest) {
        return contest.getStatus() != Contest.Status.CANCELLED
                && contest.getStatus() != Contest.Status.COMPLETED;
    }

    private void updateFixtureSnapshotFromLiveData(Fixture fixture, JsonNode data) {
        String liveStatus = textValue(data, "status");
        if (!liveStatus.isBlank()) {
            fixture.setStatus(liveStatus);
        }
        fixture.setRawJson(data.toString());
        fixture.setLastSyncedAt(LocalDateTime.now());
    }

    private boolean isWithinSyncThrottleWindow(Fixture fixture) {
        if (fixture.getLastScoreSyncedAt() == null || liveSyncMinSeconds <= 0) {
            return false;
        }

        LocalDateTime threshold = LocalDateTime.now().minusSeconds(liveSyncMinSeconds);
        return fixture.getLastScoreSyncedAt().isAfter(threshold);
    }

    private boolean isRegularScoreboard(JsonNode node, ScoringFormat scoringFormat) {
        String scoreboard = firstNonBlank(
                textValue(node, "scoreboard"),
                textValue(node, "inning")
        );
        return switch (scoringFormat) {
            case TEST -> REGULAR_TEST_SCOREBOARDS.contains(scoreboard);
            default -> REGULAR_LIMITED_OVERS_SCOREBOARDS.contains(scoreboard);
        };
    }

    private ScoringFormat resolveScoringFormat(String rawFormat) {
        String normalized = compact(rawFormat);
        if (normalized.contains("HUNDRED")) {
            return ScoringFormat.HUNDRED;
        }
        if (normalized.contains("T10")) {
            return ScoringFormat.T10;
        }
        if (normalized.contains("TEST")
                || normalized.contains("FIRSTCLASS")
                || normalized.equals("FC")
                || normalized.contains("MULTIDAY")
                || normalized.contains("4DAY")
                || normalized.contains("5DAY")) {
            return ScoringFormat.TEST;
        }
        if (normalized.contains("ODI")
                || normalized.equals("OD")
                || normalized.contains("ONEDAY")
                || normalized.contains("LISTA")) {
            return ScoringFormat.ODI;
        }
        if (normalized.contains("T20I")
                || normalized.contains("IT20")
                || normalized.contains("TWENTY20INTERNATIONAL")) {
            return ScoringFormat.T20;
        }
        if (normalized.contains("T20")) {
            return ScoringFormat.OTHER_T20;
        }
        return ScoringFormat.T20;
    }

    private boolean isDotBall(JsonNode scoreNode) {
        return booleanValue(scoreNode, "ball")
                && intValue(scoreNode, "runs") == 0
                && intValue(scoreNode, "bye") == 0
                && intValue(scoreNode, "leg_bye") == 0
                && intValue(scoreNode, "noball") == 0
                && intValue(scoreNode, "noball_runs") == 0;
    }

    private boolean isCatch(String scoreName) {
        return scoreName.contains("CATCH");
    }

    private boolean isStumping(String scoreName) {
        return scoreName.contains("STUMP");
    }

    private boolean isRunOut(String scoreName) {
        return scoreName.contains("RUN OUT");
    }

    private boolean isLbwOrBowled(String scoreName) {
        return scoreName.contains("LBW") || scoreName.contains("BOWLED");
    }

    private boolean isFixtureCancelledStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        String compact = normalized.replaceAll("[^A-Z]", "");

        return compact.contains("CANCEL")
                || compact.contains("CANCL")
                || compact.contains("ABANDON")
                || compact.equals("NORESULT")
                || compact.equals("NR");
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String normalizedScoreName(JsonNode scoreNode) {
        return textValue(scoreNode, "name").trim().toUpperCase(Locale.ROOT);
    }

    private String textValue(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return current.isMissingNode() || current.isNull() ? "" : current.asText("");
    }

    private int intValue(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return current.isMissingNode() || current.isNull() ? 0 : current.asInt(0);
    }

    private Integer nullableIntValue(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        if (current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.asInt(0);
    }

    private int firstAvailableInt(JsonNode node, String... paths) {
        for (String path : paths) {
            Integer value = nullableIntValue(node, path);
            if (value != null) {
                return value;
            }
        }
        return 0;
    }

    private Integer firstAvailableNullableInt(JsonNode node, String... paths) {
        for (String path : paths) {
            Integer value = nullableIntValue(node, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Long longValue(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return current.isMissingNode() || current.isNull() ? null : current.asLong();
    }

    private boolean booleanValue(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return !current.isMissingNode() && !current.isNull() && current.asBoolean(false);
    }

    private BigDecimal decimalValue(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        if (current.isMissingNode() || current.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return current.decimalValue();
        } catch (Exception ignored) {
            return BigDecimal.valueOf(current.asDouble(0));
        }
    }

    private int oversToBalls(BigDecimal overs) {
        if (overs == null) {
            return 0;
        }

        int completedOvers = overs.intValue();
        BigDecimal partial = overs.subtract(BigDecimal.valueOf(completedOvers)).abs();
        int extraBalls = partial.movePointRight(1).intValue();
        return (completedOvers * 6) + extraBalls;
    }

    private Map<String, Object> buildSkippedResult(Long fixtureId, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fixtureId", fixtureId);
        result.put("updatedEntries", 0);
        result.put("teamsScored", 0);
        result.put("contestsScored", 0);
        result.put("status", "SKIPPED");
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> buildCompletedResult(
            Long fixtureId,
            int contestsScored,
            int teamsScored,
            int updatedEntries
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fixtureId", fixtureId);
        result.put("updatedEntries", updatedEntries);
        result.put("teamsScored", teamsScored);
        result.put("contestsScored", contestsScored);
        result.put("status", "COMPLETED");
        return result;
    }

    private static class FantasyScorecard {
        private final Map<Long, LineupInfo> lineupByExternalPlayerId = new HashMap<>();
        private final Map<Long, BattingStats> battingByExternalPlayerId = new HashMap<>();
        private final Map<Long, BowlingStats> bowlingByExternalPlayerId = new HashMap<>();
        private final Map<Long, FieldingStats> fieldingByExternalPlayerId = new HashMap<>();
        private final Set<Long> dismissedBatters = new HashSet<>();
        private final Set<Long> playersWithOnFieldInvolvement = new HashSet<>();
        private final Set<Long> bowlersWithScoreboardWicketTotals = new HashSet<>();
    }

    private static class LineupInfo {
        private static final LineupInfo EMPTY = new LineupInfo();

        private boolean announced;
        private boolean substitute;
    }

    private static class BattingStats {
        private static final BattingStats EMPTY = new BattingStats();

        private int runs;
        private int ballsFaced;
        private int fours;
        private int sixes;
    }

    private static class BowlingStats {
        private static final BowlingStats EMPTY = new BowlingStats();

        private int ballsBowled;
        private int runsConceded;
        private int dotBalls;
        private int wickets;
        private int lbwOrBowledWickets;
        private int maidens;
    }

    private static class FieldingStats {
        private static final FieldingStats EMPTY = new FieldingStats();

        private int catches;
        private int stumpings;
        private int runOutDirectHits;
        private int runOutAssists;
    }

    private enum ScoringFormat {
        T20,
        ODI,
        TEST,
        T10,
        HUNDRED,
        OTHER_T20
    }
}
