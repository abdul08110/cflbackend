package com.friendsfantasy.fantasybackend.team.service;

import com.friendsfantasy.fantasybackend.contest.repository.ContestEntryRepository;
import com.friendsfantasy.fantasybackend.fixture.dto.FixturePlayerPoolResponse;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import com.friendsfantasy.fantasybackend.fixture.entity.FixturePlayerPool;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureParticipantRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixturePlayerPoolRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.fixture.service.FixtureSyncService;
import com.friendsfantasy.fantasybackend.integration.sportmonks.SportMonksCricketClient;
import com.friendsfantasy.fantasybackend.common.ApiException;
import com.friendsfantasy.fantasybackend.player.entity.Player;
import com.friendsfantasy.fantasybackend.player.repository.PlayerRepository;
import com.friendsfantasy.fantasybackend.notification.entity.Notification;
import com.friendsfantasy.fantasybackend.notification.repository.NotificationRepository;
import com.friendsfantasy.fantasybackend.notification.service.NotificationService;
import com.friendsfantasy.fantasybackend.team.dto.*;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeam;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeamPlayer;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamPlayerRepository;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TeamService {

    private static final String LINEUP_ANNOUNCED_NOTIFICATION_TYPE = "LINEUP_ANNOUNCED";

    private final FixtureRepository fixtureRepository;
    private final FixtureParticipantRepository fixtureParticipantRepository;
    private final FixturePlayerPoolRepository fixturePlayerPoolRepository;
    private final PlayerRepository playerRepository;
    private final UserMatchTeamRepository userMatchTeamRepository;
    private final UserMatchTeamPlayerRepository userMatchTeamPlayerRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final FixtureSyncService fixtureSyncService;
    private final SportMonksCricketClient sportMonksCricketClient;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    @Value("${app.cricket-sport-id:1}")
    private Long cricketSportId;

    @Value("${app.team.max-players:11}")
    private int maxPlayers;

    @Value("${app.team.max-from-one-side:7}")
    private int maxFromOneSide;

    @Value("${app.team.credit-limit:100.0}")
    private BigDecimal creditLimit;

    @Value("${app.team.default-player-credit:8.0}")
    private BigDecimal defaultPlayerCredit;

    @Value("${app.team.max-substitutes:4}")
    private int maxSubstitutes;

    @Value("${app.player-pool.auto-refresh-window-minutes:180}")
    private long playerPoolAutoRefreshWindowMinutes;

    @Value("${app.player-pool.stale-refresh-min-seconds:120}")
    private long playerPoolStaleRefreshMinSeconds;

    @Value("${app.player-pool.announced-stale-refresh-min-seconds:45}")
    private long playerPoolAnnouncedStaleRefreshMinSeconds;

    @Transactional
    public int syncFixturePlayerPool(Long fixtureId) {
        Fixture fixture = ensureFixtureReadyForPlayerSync(fixtureId);
        List<FixtureParticipant> participants = fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixtureId);

        List<PlayerPoolSeed> seeds = loadPlayerPoolSeeds(fixture, participants);
        Map<Long, FixturePlayerPool> existingByPlayerId = new HashMap<>();
        for (FixturePlayerPool existing : fixturePlayerPoolRepository.findByFixtureIdOrderByIdAsc(fixtureId)) {
            existingByPlayerId.put(existing.getPlayerId(), existing);
        }
        boolean lineupWasAnnounced = existingByPlayerId.values().stream()
                .anyMatch(existing -> Boolean.TRUE.equals(existing.getIsAnnounced()));
        boolean lineupAnnouncedNow = seeds.stream()
                .anyMatch(seed -> Boolean.TRUE.equals(seed.isAnnounced));

        int count = 0;
        Set<Long> syncedPlayerIds = new HashSet<>();

        for (PlayerPoolSeed seed : seeds) {
            Player player = playerRepository.findBySportIdAndExternalPlayerId(cricketSportId, seed.externalPlayerId)
                    .orElseGet(Player::new);

            player.setSportId(cricketSportId);
            player.setExternalPlayerId(seed.externalPlayerId);
            player.setPlayerName(seed.playerName);
            player.setShortName(seed.shortName);
            player.setCountryName(null);
            player.setImageUrl(seed.imageUrl);
            player.setRawJson(seed.rawJson);
            player = playerRepository.save(player);

            FixturePlayerPool pool = existingByPlayerId.getOrDefault(
                    player.getId(),
                    FixturePlayerPool.builder()
                            .fixtureId(fixtureId)
                            .playerId(player.getId())
                            .selectionPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .build()
            );

            pool.setFixtureId(fixtureId);
            pool.setPlayerId(player.getId());
            pool.setExternalTeamId(seed.externalTeamId);
            pool.setRoleCode(seed.roleCode);
            pool.setCreditValue(defaultPlayerCredit.setScale(1, RoundingMode.HALF_UP));
            pool.setIsAnnounced(seed.isAnnounced);
            pool.setIsPlaying(seed.isPlaying);
            pool.setIsActive(true);

            fixturePlayerPoolRepository.save(pool);
            syncedPlayerIds.add(player.getId());
            count++;
        }

        for (FixturePlayerPool existing : existingByPlayerId.values()) {
            if (syncedPlayerIds.contains(existing.getPlayerId())) {
                continue;
            }

            existing.setIsActive(false);
            existing.setIsAnnounced(false);
            existing.setIsPlaying(false);
            fixturePlayerPoolRepository.save(existing);
        }

        if (!lineupWasAnnounced && lineupAnnouncedNow) {
            notifyFixtureUsersAboutLineupAnnouncement(fixture, participants);
        }

        return count;
    }

    public List<FixturePlayerPoolResponse> getFixturePlayerPool(Long fixtureId) {
        return getFixturePlayerPool(fixtureId, false);
    }

    public List<FixturePlayerPoolResponse> getFixturePlayerPool(Long fixtureId, boolean forceSync) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> ApiException.notFound("Fixture not found"));

        List<FixturePlayerPool> pool = findActivePool(fixtureId);
        if (shouldSyncPlayerPoolOnRead(fixture, pool, forceSync)) {
            try {
                syncFixturePlayerPool(fixtureId);
            } catch (RuntimeException ex) {
                log.warn(
                        "Player-pool sync failed for fixture {}. Falling back to the saved pool if present.",
                        fixtureId,
                        ex
                );
            }
            pool = findActivePool(fixtureId);
        }

        Map<Long, String> teamNameMap = new HashMap<>();
        for (FixtureParticipant participant : fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixtureId)) {
            teamNameMap.put(participant.getExternalTeamId(), participant.getTeamName());
        }

        Map<Long, Player> playersById = new HashMap<>();
        for (Player player : playerRepository.findAllById(
                pool.stream().map(FixturePlayerPool::getPlayerId).toList()
        )) {
            playersById.put(player.getId(), player);
        }

        List<FixturePlayerPoolResponse> response = new ArrayList<>();

        for (FixturePlayerPool p : pool) {
            Player player = playersById.get(p.getPlayerId());
            if (player == null) {
                throw ApiException.notFound("Player not found");
            }

            response.add(FixturePlayerPoolResponse.builder()
                    .fixturePlayerPoolId(p.getId())
                    .playerId(player.getId())
                    .externalTeamId(p.getExternalTeamId())
                    .teamName(teamNameMap.getOrDefault(p.getExternalTeamId(), "Team"))
                    .playerName(player.getPlayerName())
                    .shortName(player.getShortName())
                    .roleCode(p.getRoleCode())
                    .creditValue(p.getCreditValue())
                    .selectionPercent(p.getSelectionPercent())
                    .isAnnounced(p.getIsAnnounced())
                    .isPlaying(p.getIsPlaying())
                    .imageUrl(player.getImageUrl())
                    .build());
        }

        return response;
    }

    private List<FixturePlayerPool> findActivePool(Long fixtureId) {
        return fixturePlayerPoolRepository
                .findByFixtureIdAndIsActiveTrueOrderByExternalTeamIdAscRoleCodeAscIdAsc(fixtureId);
    }

    private boolean shouldSyncPlayerPoolOnRead(Fixture fixture, List<FixturePlayerPool> pool, boolean forceSync) {
        LocalDateTime now = LocalDateTime.now();
        if (fixture.getDeadlineTime() == null || !fixture.getDeadlineTime().isAfter(now)) {
            return false;
        }

        if (pool.isEmpty()) {
            return true;
        }

        LocalDateTime lastPoolSyncAt = resolveLastPlayerPoolSyncAt(pool);
        if (forceSync) {
            return true;
        }

        boolean lineupAnnounced = pool.stream().anyMatch(player -> Boolean.TRUE.equals(player.getIsAnnounced()));
        if (!lineupAnnounced && playerPoolAutoRefreshWindowMinutes > 0) {
            LocalDateTime autoRefreshWindowStart = fixture.getDeadlineTime().minusMinutes(playerPoolAutoRefreshWindowMinutes);
            if (now.isBefore(autoRefreshWindowStart)) {
                return false;
            }
        }

        long staleSeconds = lineupAnnounced
                ? playerPoolAnnouncedStaleRefreshMinSeconds
                : playerPoolStaleRefreshMinSeconds;
        if (staleSeconds <= 0) {
            return true;
        }

        if (lastPoolSyncAt == null) {
            return true;
        }

        return lastPoolSyncAt.isBefore(now.minusSeconds(staleSeconds));
    }

    private LocalDateTime resolveLastPlayerPoolSyncAt(List<FixturePlayerPool> pool) {
        LocalDateTime latest = null;
        for (FixturePlayerPool playerPool : pool) {
            LocalDateTime candidate = playerPool.getUpdatedAt();
            if (candidate == null) {
                candidate = playerPool.getCreatedAt();
            }

            if (candidate != null && (latest == null || candidate.isAfter(latest))) {
                latest = candidate;
            }
        }
        return latest;
    }

    private List<PlayerPoolSeed> loadPlayerPoolSeeds(Fixture fixture, List<FixtureParticipant> participants) {
        JsonNode root = sportMonksCricketClient.getFixtureWithLineup(fixture.getExternalFixtureId());
        JsonNode lineup = root.path("data").path("lineup");

        Map<Long, PlayerPoolSeed> seedsByExternalPlayerId = new LinkedHashMap<>();
        Map<Long, PlayerPoolSeed> lineupByExternalPlayerId = new HashMap<>();

        if (lineup.isArray() && !lineup.isEmpty()) {
            for (JsonNode playerNode : lineup) {
                PlayerPoolSeed seed = toSeedFromLineup(playerNode);
                if (seed != null) {
                    lineupByExternalPlayerId.put(seed.externalPlayerId, seed);
                }
            }
        }

        boolean lineupAnnounced = !lineupByExternalPlayerId.isEmpty();

        if (fixture.getExternalSeasonId() != null) {
            for (FixtureParticipant participant : participants) {
                JsonNode squadRoot = sportMonksCricketClient.getTeamSquad(participant.getExternalTeamId(), fixture.getExternalSeasonId());
                JsonNode squad = extractSquadNodes(squadRoot);
                if (!squad.isArray()) {
                    continue;
                }

                for (JsonNode playerNode : squad) {
                    PlayerPoolSeed seed = toSeedFromSquad(playerNode, participant.getExternalTeamId());
                    if (seed == null) {
                        continue;
                    }

                    PlayerPoolSeed lineupSeed = lineupByExternalPlayerId.get(seed.externalPlayerId);
                    if (lineupSeed != null) {
                        seed = seed.toBuilder()
                                .isAnnounced(true)
                                .isPlaying(Boolean.TRUE.equals(lineupSeed.isPlaying))
                                .rawJson(lineupSeed.rawJson)
                                .build();
                    } else if (lineupAnnounced) {
                        seed = seed.toBuilder()
                                .isAnnounced(true)
                                .isPlaying(false)
                                .build();
                    }

                    seedsByExternalPlayerId.put(seed.externalPlayerId, seed);
                }
            }
        }

        for (PlayerPoolSeed lineupSeed : lineupByExternalPlayerId.values()) {
            seedsByExternalPlayerId.putIfAbsent(lineupSeed.externalPlayerId, lineupSeed);
        }

        if (seedsByExternalPlayerId.isEmpty()) {
            throw ApiException.conflict("No lineup or squad available yet for this fixture");
        }

        return new ArrayList<>(seedsByExternalPlayerId.values());
    }

    private JsonNode extractSquadNodes(JsonNode squadRoot) {
        if (squadRoot.isArray()) {
            return squadRoot;
        }

        JsonNode data = squadRoot.path("data");
        if (data.isArray()) {
            return data;
        }

        JsonNode squad = data.path("squad");
        if (squad.isArray()) {
            return squad;
        }

        JsonNode squadData = squad.path("data");
        if (squadData.isArray()) {
            return squadData;
        }

        JsonNode squadPlayers = squad.path("players");
        if (squadPlayers.isArray()) {
            return squadPlayers;
        }

        JsonNode squadPlayersData = squadPlayers.path("data");
        if (squadPlayersData.isArray()) {
            return squadPlayersData;
        }

        JsonNode players = data.path("players");
        if (players.isArray()) {
            return players;
        }

        JsonNode playersData = players.path("data");
        if (playersData.isArray()) {
            return playersData;
        }

        return objectMapper.createArrayNode();
    }

    @Transactional
    public void lockAndApplyAutoSubstitutesIfNeeded(Long teamId) {
        UserMatchTeam team = userMatchTeamRepository.findById(teamId).orElse(null);
        if (team == null) {
            return;
        }

        Fixture fixture = fixtureRepository.findById(team.getFixtureId()).orElse(null);
        if (fixture == null || fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            return;
        }

        try {
            syncFixturePlayerPool(team.getFixtureId());
        } catch (RuntimeException ignored) {
            // Continue with the last saved player-pool state when live sync is unavailable.
        }

        applyAutoSubstitutes(team);

        if (!Boolean.TRUE.equals(team.getIsLocked())) {
            team.setIsLocked(true);
        }
        if (team.getLockedAt() == null) {
            team.setLockedAt(LocalDateTime.now());
        }
        userMatchTeamRepository.save(team);
    }

    private void applyAutoSubstitutes(UserMatchTeam team) {
        List<UserMatchTeamPlayer> players = new ArrayList<>(
                userMatchTeamPlayerRepository.findByUserMatchTeamIdOrderByIsSubstituteAscSubstitutePriorityAscIdAsc(team.getId())
        );

        if (players.isEmpty()) {
            return;
        }

        Map<Long, FixturePlayerPool> poolById = new HashMap<>();
        for (UserMatchTeamPlayer player : players) {
            fixturePlayerPoolRepository.findById(player.getFixturePlayerPoolId())
                    .ifPresent(pool -> poolById.put(player.getFixturePlayerPoolId(), pool));
        }

        List<UserMatchTeamPlayer> starters = new ArrayList<>();
        List<UserMatchTeamPlayer> substitutes = new ArrayList<>();

        for (UserMatchTeamPlayer player : players) {
            if (Boolean.TRUE.equals(player.getIsSubstitute())) {
                substitutes.add(player);
            } else {
                starters.add(player);
            }
        }

        List<UserMatchTeamPlayer> nonPlayingStarters = new ArrayList<>();
        for (UserMatchTeamPlayer starter : starters) {
            if (!isPlaying(poolById.get(starter.getFixturePlayerPoolId()))) {
                nonPlayingStarters.add(starter);
            }
        }

        if (nonPlayingStarters.isEmpty() || substitutes.isEmpty()) {
            return;
        }

        boolean changed = false;

        for (UserMatchTeamPlayer substitute : substitutes) {
            if (!Boolean.TRUE.equals(substitute.getIsSubstitute())) {
                continue;
            }
            if (!isPlaying(poolById.get(substitute.getFixturePlayerPoolId()))) {
                continue;
            }

            UserMatchTeamPlayer outgoing = findAutoSwapTarget(
                    substitute,
                    nonPlayingStarters,
                    starters
            );
            if (outgoing == null) {
                continue;
            }

            Integer outgoingPriority = substitute.getSubstitutePriority();
            boolean outgoingCaptain = Boolean.TRUE.equals(outgoing.getIsCaptain());
            boolean outgoingViceCaptain = Boolean.TRUE.equals(outgoing.getIsViceCaptain());

            outgoing.setIsSubstitute(true);
            outgoing.setSubstitutePriority(outgoingPriority);
            outgoing.setIsCaptain(false);
            outgoing.setIsViceCaptain(false);

            substitute.setIsSubstitute(false);
            substitute.setSubstitutePriority(null);
            substitute.setIsCaptain(outgoingCaptain);
            substitute.setIsViceCaptain(outgoingViceCaptain);

            if (outgoingCaptain) {
                team.setCaptainPlayerId(substitute.getPlayerId());
            }
            if (outgoingViceCaptain) {
                team.setViceCaptainPlayerId(substitute.getPlayerId());
            }

            starters.remove(outgoing);
            starters.add(substitute);
            nonPlayingStarters.remove(outgoing);
            changed = true;
        }

        if (changed) {
            userMatchTeamPlayerRepository.saveAll(players);
        }
    }

    private UserMatchTeamPlayer findAutoSwapTarget(
            UserMatchTeamPlayer substitute,
            List<UserMatchTeamPlayer> nonPlayingStarters,
            List<UserMatchTeamPlayer> currentStarters
    ) {
        for (UserMatchTeamPlayer starter : nonPlayingStarters) {
            if (Objects.equals(starter.getRoleCode(), substitute.getRoleCode())
                    && replacementKeepsTeamValid(currentStarters, starter, substitute)) {
                return starter;
            }
        }

        for (UserMatchTeamPlayer starter : nonPlayingStarters) {
            if (replacementKeepsTeamValid(currentStarters, starter, substitute)) {
                return starter;
            }
        }

        return null;
    }

    private boolean replacementKeepsTeamValid(
            List<UserMatchTeamPlayer> currentStarters,
            UserMatchTeamPlayer outgoing,
            UserMatchTeamPlayer incoming
    ) {
        Map<String, Integer> roleCounts = new HashMap<>();
        Map<String, Integer> teamCounts = new HashMap<>();

        for (UserMatchTeamPlayer starter : currentStarters) {
            UserMatchTeamPlayer effectivePlayer = starter.getId().equals(outgoing.getId()) ? incoming : starter;
            roleCounts.put(
                    effectivePlayer.getRoleCode(),
                    roleCounts.getOrDefault(effectivePlayer.getRoleCode(), 0) + 1
            );

            String teamSide = effectivePlayer.getTeamSide();
            if (teamSide != null) {
                teamCounts.put(teamSide, teamCounts.getOrDefault(teamSide, 0) + 1);
            }
        }

        for (String requiredRole : List.of("WK", "BAT", "AR", "BOWL")) {
            if (roleCounts.getOrDefault(requiredRole, 0) < 1) {
                return false;
            }
        }

        for (Integer count : teamCounts.values()) {
            if (count > maxFromOneSide) {
                return false;
            }
        }

        return true;
    }

    private boolean isPlaying(FixturePlayerPool pool) {
        return pool != null && Boolean.TRUE.equals(pool.getIsPlaying());
    }

    private Fixture ensureFixtureReadyForPlayerSync(Long fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> ApiException.notFound("Fixture not found"));

        List<FixtureParticipant> participants = fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixtureId);
        boolean missingSeason = fixture.getExternalSeasonId() == null;
        boolean missingParticipants = participants.isEmpty()
                || participants.stream().anyMatch(participant -> participant.getExternalTeamId() == null);

        if (missingSeason || missingParticipants) {
            fixtureSyncService.refreshFixtureMetadataByExternalId(fixture.getExternalFixtureId());
            fixture = fixtureRepository.findById(fixtureId)
                    .orElseThrow(() -> ApiException.notFound("Fixture not found after refresh"));
        }

        return fixture;
    }

    private PlayerPoolSeed toSeedFromLineup(JsonNode playerNode) {
        Long externalPlayerId = longValue(playerNode, "id");
        if (externalPlayerId == null) {
            return null;
        }

        Long externalTeamId = firstAvailableLong(
                playerNode,
                "lineup.team_id",
                "lineup.team.id",
                "team_id",
                "team.id"
        );
        if (externalTeamId == null) {
            externalTeamId = 0L;
        }

        return PlayerPoolSeed.builder()
                .externalPlayerId(externalPlayerId)
                .externalTeamId(externalTeamId)
                .playerName(textValue(playerNode, "fullname", textValue(playerNode, "name", "Unknown Player")))
                .shortName(textValue(playerNode, "lastname", null))
                .imageUrl(textValue(playerNode, "image_path", null))
                .roleCode(resolveRoleCode(playerNode))
                .isAnnounced(true)
                .isPlaying(true)
                .rawJson(writeJson(playerNode))
                .build();
    }

    private PlayerPoolSeed toSeedFromSquad(JsonNode playerNode, Long externalTeamId) {
        Long externalPlayerId = longValue(playerNode, "id");
        if (externalPlayerId == null) {
            return null;
        }

        return PlayerPoolSeed.builder()
                .externalPlayerId(externalPlayerId)
                .externalTeamId(externalTeamId)
                .playerName(textValue(playerNode, "fullname", textValue(playerNode, "name", "Unknown Player")))
                .shortName(textValue(playerNode, "lastname", null))
                .imageUrl(textValue(playerNode, "image_path", null))
                .roleCode(resolveRoleCode(playerNode))
                .isAnnounced(false)
                .isPlaying(false)
                .rawJson(writeJson(playerNode))
                .build();
    }

    @Transactional
    public TeamResponse createTeam(Long userId, Long fixtureId, CreateTeamRequest request) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> ApiException.notFound("Fixture not found"));

        if (fixture.getDeadlineTime() == null || !fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw ApiException.conflict("Team creation is closed for this fixture");
        }

        ValidationBundle bundle = validateAndBuildTeam(fixtureId, request);

        UserMatchTeam team = UserMatchTeam.builder()
                .fixtureId(fixtureId)
                .userId(userId)
                .teamName(request.getTeamName().trim())
                .captainPlayerId(request.getCaptainPlayerId())
                .viceCaptainPlayerId(request.getViceCaptainPlayerId())
                .totalCredits(bundle.totalCredits)
                .isLocked(false)
                .build();

        team = userMatchTeamRepository.save(team);

        for (UserMatchTeamPlayer p : bundle.teamPlayers) {
            p.setUserMatchTeamId(team.getId());
            userMatchTeamPlayerRepository.save(p);
        }
        for (UserMatchTeamPlayer p : bundle.substitutePlayers) {
            p.setUserMatchTeamId(team.getId());
            userMatchTeamPlayerRepository.save(p);
        }

        return getTeamForOwner(userId, team.getId());
    }

    public List<TeamResponse> getMyTeams(Long userId, Long fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> ApiException.notFound("Fixture not found"));
        cleanupUnusedTeamsAfterStart(userId, fixture);

        List<UserMatchTeam> teams = userMatchTeamRepository
                .findByFixtureIdAndUserIdOrderByCreatedAtDesc(fixtureId, userId);
        List<TeamResponse> response = new ArrayList<>();
        for (UserMatchTeam t : teams) {
            lockAndApplyAutoSubstitutesIfNeeded(t.getId());
            UserMatchTeam refreshedTeam = userMatchTeamRepository.findById(t.getId())
                    .orElseThrow(() -> ApiException.notFound("Team not found"));
            response.add(mapTeamResponse(refreshedTeam));
        }
        return response;
    }

    public TeamResponse getTeamForOwner(Long userId, Long teamId) {
        UserMatchTeam team = userMatchTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));
        lockAndApplyAutoSubstitutesIfNeeded(teamId);
        team = userMatchTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));
        return mapTeamResponse(team);
    }

    private void cleanupUnusedTeamsAfterStart(Long userId, Fixture fixture) {
        if (fixture.getDeadlineTime() == null || fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            return;
        }

        List<UserMatchTeam> teams = userMatchTeamRepository
                .findByFixtureIdAndUserIdOrderByCreatedAtDesc(fixture.getId(), userId);

        for (UserMatchTeam team : teams) {
            if (contestEntryRepository.existsByUserMatchTeamId(team.getId())) {
                continue;
            }

            userMatchTeamPlayerRepository.deleteByUserMatchTeamId(team.getId());
            userMatchTeamPlayerRepository.flush();
            userMatchTeamRepository.delete(team);
        }
    }

    private void notifyFixtureUsersAboutLineupAnnouncement(
            Fixture fixture,
            List<FixtureParticipant> participants
    ) {
        List<UserMatchTeam> teams = userMatchTeamRepository.findByFixtureIdOrderByCreatedAtDesc(fixture.getId());
        if (teams.isEmpty()) {
            return;
        }

        String payloadJson = "{\"fixtureId\":" + fixture.getId() + ",\"type\":\"LINEUP_ANNOUNCED\"}";
        String matchLabel = buildFixtureShortLabel(fixture, participants);
        List<Notification> notifications = new ArrayList<>();
        Set<Long> notifiedUserIds = new LinkedHashSet<>();

        for (UserMatchTeam team : teams) {
            if (!notifiedUserIds.add(team.getUserId())) {
                continue;
            }
            if (notificationRepository.existsByUserIdAndTypeAndPayloadJson(
                    team.getUserId(),
                    LINEUP_ANNOUNCED_NOTIFICATION_TYPE,
                    payloadJson
            )) {
                continue;
            }

            notifications.add(Notification.builder()
                    .userId(team.getUserId())
                    .type(LINEUP_ANNOUNCED_NOTIFICATION_TYPE)
                    .title("Playing XI Announced")
                    .body(matchLabel + " lineup is out. Refresh Live Team and review your team.")
                    .payloadJson(payloadJson)
                    .isRead(false)
                    .build());
        }

        if (!notifications.isEmpty()) {
            notificationService.createNotifications(notifications);
        }
    }

    private String buildFixtureShortLabel(Fixture fixture, List<FixtureParticipant> participants) {
        if (participants.size() >= 2) {
            FixtureParticipant first = participants.get(0);
            FixtureParticipant second = participants.get(1);
            String firstShort = first.getShortName() != null && !first.getShortName().isBlank()
                    ? first.getShortName()
                    : first.getTeamName();
            String secondShort = second.getShortName() != null && !second.getShortName().isBlank()
                    ? second.getShortName()
                    : second.getTeamName();
            return firstShort + " vs " + secondShort;
        }

        return fixture.getTitle() == null || fixture.getTitle().isBlank()
                ? "This match"
                : fixture.getTitle();
    }

    public TeamResponse getTeamById(Long teamId) {
        UserMatchTeam team = userMatchTeamRepository.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));
        lockAndApplyAutoSubstitutesIfNeeded(teamId);
        team = userMatchTeamRepository.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));
        return mapTeamResponse(team);
    }

    @Transactional
    public TeamResponse updateTeam(Long userId, Long teamId, CreateTeamRequest request) {
        UserMatchTeam team = userMatchTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));

        Fixture fixture = fixtureRepository.findById(team.getFixtureId())
                .orElseThrow(() -> ApiException.notFound("Fixture not found"));

        if (Boolean.TRUE.equals(team.getIsLocked())
                || fixture.getDeadlineTime() == null
                || !fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw ApiException.conflict("Team can no longer be edited");
        }

        ValidationBundle bundle = validateAndBuildTeam(team.getFixtureId(), request);

        team.setTeamName(request.getTeamName().trim());
        team.setCaptainPlayerId(request.getCaptainPlayerId());
        team.setViceCaptainPlayerId(request.getViceCaptainPlayerId());
        team.setTotalCredits(bundle.totalCredits);
        userMatchTeamRepository.save(team);

        userMatchTeamPlayerRepository.deleteByUserMatchTeamId(team.getId());
        // Force the old roster rows out before inserting the replacement set.
        userMatchTeamPlayerRepository.flush();
        for (UserMatchTeamPlayer p : bundle.teamPlayers) {
            p.setUserMatchTeamId(team.getId());
            userMatchTeamPlayerRepository.save(p);
        }
        for (UserMatchTeamPlayer p : bundle.substitutePlayers) {
            p.setUserMatchTeamId(team.getId());
            userMatchTeamPlayerRepository.save(p);
        }

        return getTeamForOwner(userId, team.getId());
    }

    @Transactional
    public void deleteTeam(Long userId, Long teamId) {
        UserMatchTeam team = userMatchTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> ApiException.notFound("Team not found"));

        if (!canDeleteTeam(team)) {
            throw ApiException.conflict("Joined teams cannot be deleted");
        }

        userMatchTeamPlayerRepository.deleteByUserMatchTeamId(team.getId());
        userMatchTeamPlayerRepository.flush();
        userMatchTeamRepository.delete(team);
    }

    private ValidationBundle validateAndBuildTeam(Long fixtureId, CreateTeamRequest request) {
        if (request.getPlayers() == null || request.getPlayers().size() != maxPlayers) {
            throw ApiException.badRequest("Exactly " + maxPlayers + " players must be selected");
        }

        Set<Long> uniquePoolIds = new HashSet<>();
        for (SelectedTeamPlayerRequest p : request.getPlayers()) {
            if (!uniquePoolIds.add(p.getFixturePlayerPoolId())) {
                throw ApiException.badRequest("Duplicate players selected");
            }
        }

        List<FixturePlayerPool> selectedPool = new ArrayList<>();
        Map<Long, FixturePlayerPool> byPlayerId = new HashMap<>();
        Map<Long, Integer> teamCounts = new HashMap<>();
        BigDecimal totalCredits = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);

        for (SelectedTeamPlayerRequest selected : request.getPlayers()) {
            FixturePlayerPool pool = fixturePlayerPoolRepository.findById(selected.getFixturePlayerPoolId())
                    .orElseThrow(() -> ApiException.badRequest("Invalid fixture player pool id: " + selected.getFixturePlayerPoolId()));

            if (!Objects.equals(pool.getFixtureId(), fixtureId)) {
                throw ApiException.badRequest("Selected players do not belong to this fixture");
            }

            if (!Boolean.TRUE.equals(pool.getIsActive())) {
                throw ApiException.conflict("Inactive player selected");
            }

            selectedPool.add(pool);
            byPlayerId.put(pool.getPlayerId(), pool);
            totalCredits = totalCredits.add(pool.getCreditValue());

            teamCounts.put(pool.getExternalTeamId(), teamCounts.getOrDefault(pool.getExternalTeamId(), 0) + 1);
        }

        if (!byPlayerId.containsKey(request.getCaptainPlayerId())) {
            throw ApiException.badRequest("Captain must be selected from the team");
        }

        if (!byPlayerId.containsKey(request.getViceCaptainPlayerId())) {
            throw ApiException.badRequest("Vice captain must be selected from the team");
        }

        if (Objects.equals(request.getCaptainPlayerId(), request.getViceCaptainPlayerId())) {
            throw ApiException.badRequest("Captain and vice captain must be different");
        }

        for (Integer count : teamCounts.values()) {
            if (count > maxFromOneSide) {
                throw ApiException.badRequest("Maximum " + maxFromOneSide + " players allowed from one real team");
            }
        }

        List<UserMatchTeamPlayer> teamPlayers = new ArrayList<>();
        Set<Long> selectedPlayerIds = new HashSet<>();

        for (FixturePlayerPool pool : selectedPool) {
            selectedPlayerIds.add(pool.getPlayerId());
            UserMatchTeamPlayer teamPlayer = UserMatchTeamPlayer.builder()
                    .fixturePlayerPoolId(pool.getId())
                    .playerId(pool.getPlayerId())
                    .roleCode(pool.getRoleCode())
                    .teamSide(resolveTeamSide(fixtureId, pool.getExternalTeamId()))
                    .creditValue(pool.getCreditValue())
                    .isCaptain(Objects.equals(pool.getPlayerId(), request.getCaptainPlayerId()))
                    .isViceCaptain(Objects.equals(pool.getPlayerId(), request.getViceCaptainPlayerId()))
                    .isSubstitute(false)
                    .build();

            teamPlayers.add(teamPlayer);
        }

        List<UserMatchTeamPlayer> substitutePlayers = new ArrayList<>();
        List<SelectedTeamPlayerRequest> requestedSubstitutes = request.getSubstitutes() == null
                ? List.of()
                : request.getSubstitutes();

        if (requestedSubstitutes.size() > maxSubstitutes) {
            throw ApiException.badRequest("Maximum " + maxSubstitutes + " substitutes can be selected");
        }

        Set<Long> uniqueSubstitutePoolIds = new HashSet<>();
        int substitutePriority = 1;
        for (SelectedTeamPlayerRequest substituteRequest : requestedSubstitutes) {
            if (!uniqueSubstitutePoolIds.add(substituteRequest.getFixturePlayerPoolId())) {
                throw ApiException.badRequest("Duplicate substitutes selected");
            }
            if (uniquePoolIds.contains(substituteRequest.getFixturePlayerPoolId())) {
                throw ApiException.badRequest("A player cannot be selected in both playing XI and substitutes");
            }

            FixturePlayerPool pool = fixturePlayerPoolRepository.findById(substituteRequest.getFixturePlayerPoolId())
                    .orElseThrow(() -> ApiException.badRequest("Invalid substitute fixture player pool id: " + substituteRequest.getFixturePlayerPoolId()));

            if (!Objects.equals(pool.getFixtureId(), fixtureId)) {
                throw ApiException.badRequest("Selected substitutes do not belong to this fixture");
            }

            if (!Boolean.TRUE.equals(pool.getIsActive())) {
                throw ApiException.conflict("Inactive substitute player selected");
            }

            if (!selectedPlayerIds.add(pool.getPlayerId())) {
                throw ApiException.badRequest("A player cannot be selected in both playing XI and substitutes");
            }

            UserMatchTeamPlayer substitutePlayer = UserMatchTeamPlayer.builder()
                    .fixturePlayerPoolId(pool.getId())
                    .playerId(pool.getPlayerId())
                    .roleCode(pool.getRoleCode())
                    .teamSide(resolveTeamSide(fixtureId, pool.getExternalTeamId()))
                    .creditValue(pool.getCreditValue())
                    .isCaptain(false)
                    .isViceCaptain(false)
                    .isSubstitute(true)
                    .substitutePriority(substitutePriority++)
                    .build();

            substitutePlayers.add(substitutePlayer);
        }

        ValidationBundle bundle = new ValidationBundle();
        bundle.totalCredits = totalCredits;
        bundle.teamPlayers = teamPlayers;
        bundle.substitutePlayers = substitutePlayers;
        return bundle;
    }

    private String resolveTeamSide(Long fixtureId, Long externalTeamId) {
        List<FixtureParticipant> participants = fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixtureId);
        for (FixtureParticipant p : participants) {
            if (Objects.equals(p.getExternalTeamId(), externalTeamId)) {
                return Boolean.TRUE.equals(p.getIsHome()) ? "HOME" : "AWAY";
            }
        }
        return "UNKNOWN";
    }

    private TeamResponse mapTeamResponse(UserMatchTeam team) {
        List<UserMatchTeamPlayer> players = userMatchTeamPlayerRepository
                .findByUserMatchTeamIdOrderByIsSubstituteAscSubstitutePriorityAscIdAsc(team.getId());
        boolean canDelete = canDeleteTeam(team);

        Map<Long, FixturePlayerPool> poolsById = new HashMap<>();
        Set<Long> fixturePlayerPoolIds = new LinkedHashSet<>();
        for (UserMatchTeamPlayer player : players) {
            if (player.getFixturePlayerPoolId() != null) {
                fixturePlayerPoolIds.add(player.getFixturePlayerPoolId());
            }
        }
        for (FixturePlayerPool pool : fixturePlayerPoolRepository.findAllById(fixturePlayerPoolIds)) {
            poolsById.put(pool.getId(), pool);
        }

        Map<Long, String> teamNameByExternalId = new HashMap<>();
        for (FixtureParticipant participant : fixtureParticipantRepository
                .findByFixtureIdOrderByIsHomeDescTeamNameAsc(team.getFixtureId())) {
            teamNameByExternalId.put(participant.getExternalTeamId(), participant.getTeamName());
        }

        List<TeamPlayerResponse> playerResponses = new ArrayList<>();
        List<TeamPlayerResponse> substituteResponses = new ArrayList<>();
        for (UserMatchTeamPlayer p : players) {
            Player player = playerRepository.findById(p.getPlayerId())
                    .orElseThrow(() -> ApiException.notFound("Player not found"));
            FixturePlayerPool pool = poolsById.get(p.getFixturePlayerPoolId());
            String teamName = "Team";
            if (pool != null && pool.getExternalTeamId() != null) {
                teamName = teamNameByExternalId.getOrDefault(pool.getExternalTeamId(), "Team");
            }

            TeamPlayerResponse response = TeamPlayerResponse.builder()
                    .fixturePlayerPoolId(p.getFixturePlayerPoolId())
                    .playerId(p.getPlayerId())
                    .playerName(player.getPlayerName())
                    .roleCode(p.getRoleCode())
                    .teamSide(p.getTeamSide())
                    .teamName(teamName)
                    .creditValue(p.getCreditValue())
                    .imageUrl(player.getImageUrl())
                    .isCaptain(p.getIsCaptain())
                    .isViceCaptain(p.getIsViceCaptain())
                    .isSubstitute(p.getIsSubstitute())
                    .substitutePriority(p.getSubstitutePriority())
                    .build();

            if (Boolean.TRUE.equals(p.getIsSubstitute())) {
                substituteResponses.add(response);
            } else {
                playerResponses.add(response);
            }
        }

        return TeamResponse.builder()
                .teamId(team.getId())
                .fixtureId(team.getFixtureId())
                .teamName(team.getTeamName())
                .captainPlayerId(team.getCaptainPlayerId())
                .viceCaptainPlayerId(team.getViceCaptainPlayerId())
                .isLocked(team.getIsLocked())
                .canDelete(canDelete)
                .players(playerResponses)
                .substitutes(substituteResponses)
                .build();
    }

    private boolean canDeleteTeam(UserMatchTeam team) {
        return !contestEntryRepository.existsByUserMatchTeamId(team.getId());
    }

    private String resolveRoleCode(JsonNode playerNode) {
        Integer positionId = firstAvailableInt(playerNode, "position.id", "lineup.position.id");
        if (positionId != null) {
            return switch (positionId) {
                case 1 -> "BAT";
                case 2 -> "BOWL";
                case 3 -> "WK";
                case 4 -> "AR";
                default -> resolveRoleCodeFromName(
                        firstAvailableText(playerNode, "position.name", "lineup.position.name")
                );
            };
        }

        boolean wicketKeeper = booleanValue(playerNode, "lineup.wicketkeeper");
        if (wicketKeeper) {
            return "WK";
        }

        String namedRole = resolveRoleCodeFromName(
                firstAvailableText(playerNode, "position.name", "lineup.position.name", "position")
        );
        if (namedRole != null) {
            return namedRole;
        }

        String battingStyle = firstAvailableText(playerNode, "battingstyle", "batting_style");
        String bowlingStyle = firstAvailableText(playerNode, "bowlingstyle", "bowling_style");
        boolean hasBattingStyle = battingStyle != null && !battingStyle.isBlank();
        boolean hasBowlingStyle = bowlingStyle != null && !bowlingStyle.isBlank();

        if (hasBattingStyle && hasBowlingStyle) {
            return "AR";
        }
        if (hasBowlingStyle) {
            return "BOWL";
        }
        if (hasBattingStyle) {
            return "BAT";
        }

        return "BAT";
    }

    private String resolveRoleCodeFromName(String positionName) {
        if (positionName == null || positionName.isBlank()) {
            return null;
        }

        String normalized = positionName.trim().toUpperCase(Locale.ROOT).replace('-', ' ');
        if (normalized.contains("WICKET")) {
            return "WK";
        }
        if (normalized.contains("ALL")) {
            return "AR";
        }
        if (normalized.contains("BOWL")) {
            return "BOWL";
        }
        if (normalized.contains("BAT")) {
            return "BAT";
        }
        return null;
    }

    private String textValue(JsonNode node, String path, String fallback) {
        JsonNode current = node;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            current = current.path(part);
        }
        return current.isMissingNode() || current.isNull() ? fallback : current.asText();
    }

    private Long longValue(JsonNode node, String path) {
        JsonNode current = node;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            current = current.path(part);
        }
        return current.isMissingNode() || current.isNull() ? null : current.asLong();
    }

    private Integer intValue(JsonNode node, String path) {
        JsonNode current = node;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            current = current.path(part);
        }
        if (current.isMissingNode() || current.isNull()) {
            return null;
        }
        if (current.canConvertToInt()) {
            return current.asInt();
        }
        try {
            return Integer.parseInt(current.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long firstAvailableLong(JsonNode node, String... paths) {
        for (String path : paths) {
            Long value = longValue(node, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer firstAvailableInt(JsonNode node, String... paths) {
        for (String path : paths) {
            Integer value = intValue(node, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstAvailableText(JsonNode node, String... paths) {
        for (String path : paths) {
            String value = textValue(node, path, null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean booleanValue(JsonNode node, String path) {
        JsonNode current = node;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            current = current.path(part);
        }
        return !current.isMissingNode() && !current.isNull() && current.asBoolean(false);
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private static class ValidationBundle {
        private BigDecimal totalCredits;
        private List<UserMatchTeamPlayer> teamPlayers;
        private List<UserMatchTeamPlayer> substitutePlayers;
    }

    @lombok.Builder(toBuilder = true)
    private static class PlayerPoolSeed {
        private Long externalPlayerId;
        private Long externalTeamId;
        private String playerName;
        private String shortName;
        private String imageUrl;
        private String roleCode;
        private Boolean isAnnounced;
        private Boolean isPlaying;
        private String rawJson;
    }
}
