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
    // Dream11-style behavior: keep fantasy scoring tied to regular innings only.
    private static final Set<String> REGULAR_LIMITED_OVERS_SCOREBOARDS = Set.of("S1", "S2");

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

        FantasyScorecard scorecard = buildFantasyScorecard(data);
        List<Long> contestIds = contests.stream().map(Contest::getId).toList();
        List<ContestEntry> entries = contestEntryRepository.findByContestIdInOrderByContestIdAscJoinedAtAsc(contestIds);

        Set<Long> teamIds = new LinkedHashSet<>();
        for (ContestEntry entry : entries) {
            if (entry.getUserMatchTeamId() != null) {
                teamIds.add(entry.getUserMatchTeamId());
            }
        }

        Map<Long, BigDecimal> teamPointsByTeamId = calculateTeamPoints(new ArrayList<>(teamIds), scorecard);

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

    private Map<Long, BigDecimal> calculateTeamPoints(List<Long> teamIds, FantasyScorecard scorecard) {
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
                        scorecard
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
            FantasyScorecard scorecard
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
        points += batting.fours * 4;
        points += batting.sixes * 6;
        points += battingMilestoneBonus(batting.runs);

        if (batting.runs == 0
                && scorecard.dismissedBatters.contains(externalPlayerId)
                && !Objects.equals(teamPlayer.getRoleCode(), "BOWL")) {
            points -= 2;
        }

        points += strikeRateBonus(teamPlayer.getRoleCode(), batting.runs, batting.ballsFaced);

        points += bowling.dotBalls;
        points += bowling.wickets * 30;
        points += bowling.lbwOrBowledWickets * 8;
        points += wicketMilestoneBonus(bowling.wickets);
        points += bowling.maidens * 12;
        points += economyRateBonus(bowling.ballsBowled, bowling.runsConceded);

        points += fielding.catches * 8;
        if (fielding.catches >= 3) {
            points += 4;
        }
        points += fielding.stumpings * 12;
        points += fielding.runOutDirectHits * 12;
        points += fielding.runOutAssists * 6;

        BigDecimal total = BigDecimal.valueOf(points).setScale(2, RoundingMode.HALF_UP);
        if (Boolean.TRUE.equals(teamPlayer.getIsCaptain())) {
            return total.multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.HALF_UP);
        }
        if (Boolean.TRUE.equals(teamPlayer.getIsViceCaptain())) {
            return total.multiply(BigDecimal.valueOf(1.5)).setScale(2, RoundingMode.HALF_UP);
        }
        return total;
    }

    private FantasyScorecard buildFantasyScorecard(JsonNode data) {
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
            if (!isRegularScoreboard(battingNode)) {
                continue;
            }

            Long playerId = longValue(battingNode, "player_id");
            if (playerId == null) {
                continue;
            }

            BattingStats stats = scorecard.battingByExternalPlayerId.computeIfAbsent(playerId, ignored -> new BattingStats());
            stats.runs += intValue(battingNode, "score");
            stats.ballsFaced += intValue(battingNode, "ball");
            stats.fours += intValue(battingNode, "four_x");
            stats.sixes += intValue(battingNode, "six_x");
            scorecard.playersWithOnFieldInvolvement.add(playerId);
        }

        for (JsonNode bowlingNode : data.path("bowling")) {
            if (!isRegularScoreboard(bowlingNode)) {
                continue;
            }

            Long playerId = longValue(bowlingNode, "player_id");
            if (playerId == null) {
                continue;
            }

            BowlingStats stats = scorecard.bowlingByExternalPlayerId.computeIfAbsent(playerId, ignored -> new BowlingStats());
            stats.ballsBowled += oversToBalls(decimalValue(bowlingNode, "overs"));
            stats.runsConceded += intValue(bowlingNode, "runs");
            stats.maidens += intValue(bowlingNode, "medians");
            scorecard.playersWithOnFieldInvolvement.add(playerId);
        }

        for (JsonNode ballNode : data.path("balls")) {
            if (!isRegularScoreboard(ballNode)) {
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
                bowlingStats.wickets++;
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

    private int battingMilestoneBonus(int runs) {
        if (runs >= 100) {
            return 16;
        }
        if (runs >= 75) {
            return 12;
        }
        if (runs >= 50) {
            return 8;
        }
        if (runs >= 25) {
            return 4;
        }
        return 0;
    }

    private int wicketMilestoneBonus(int wickets) {
        if (wickets >= 5) {
            return 12;
        }
        if (wickets == 4) {
            return 8;
        }
        if (wickets == 3) {
            return 4;
        }
        return 0;
    }

    private int economyRateBonus(int ballsBowled, int runsConceded) {
        if (ballsBowled < 12) {
            return 0;
        }

        BigDecimal economyRate = BigDecimal.valueOf(runsConceded)
                .multiply(BigDecimal.valueOf(6))
                .divide(BigDecimal.valueOf(ballsBowled), 4, RoundingMode.HALF_UP);

        if (economyRate.compareTo(BigDecimal.valueOf(5)) < 0) {
            return 6;
        }
        if (economyRate.compareTo(BigDecimal.valueOf(6)) < 0) {
            return 4;
        }
        if (economyRate.compareTo(BigDecimal.valueOf(7)) <= 0) {
            return 2;
        }
        if (economyRate.compareTo(BigDecimal.valueOf(10)) >= 0
                && economyRate.compareTo(BigDecimal.valueOf(11)) <= 0) {
            return -2;
        }
        if (economyRate.compareTo(BigDecimal.valueOf(11)) > 0
                && economyRate.compareTo(BigDecimal.valueOf(12)) <= 0) {
            return -4;
        }
        if (economyRate.compareTo(BigDecimal.valueOf(12)) > 0) {
            return -6;
        }
        return 0;
    }

    private int strikeRateBonus(String roleCode, int runs, int ballsFaced) {
        if (ballsFaced < 10 || Objects.equals(roleCode, "BOWL")) {
            return 0;
        }

        BigDecimal strikeRate = BigDecimal.valueOf(runs)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(ballsFaced), 4, RoundingMode.HALF_UP);

        if (strikeRate.compareTo(BigDecimal.valueOf(170)) > 0) {
            return 6;
        }
        if (strikeRate.compareTo(BigDecimal.valueOf(150)) > 0) {
            return 4;
        }
        if (strikeRate.compareTo(BigDecimal.valueOf(130)) >= 0) {
            return 2;
        }
        if (strikeRate.compareTo(BigDecimal.valueOf(60)) >= 0
                && strikeRate.compareTo(BigDecimal.valueOf(70)) <= 0) {
            return -2;
        }
        if (strikeRate.compareTo(BigDecimal.valueOf(50)) >= 0
                && strikeRate.compareTo(BigDecimal.valueOf(60)) < 0) {
            return -4;
        }
        if (strikeRate.compareTo(BigDecimal.valueOf(50)) < 0) {
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

    private boolean isRegularScoreboard(JsonNode node) {
        String scoreboard = textValue(node, "scoreboard");
        return REGULAR_LIMITED_OVERS_SCOREBOARDS.contains(scoreboard);
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
}
