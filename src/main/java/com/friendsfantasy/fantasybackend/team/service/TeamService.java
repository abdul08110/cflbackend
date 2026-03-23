package com.friendsfantasy.fantasybackend.team.service;

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

    private final FixtureRepository fixtureRepository;
    private final FixtureParticipantRepository fixtureParticipantRepository;
    private final FixturePlayerPoolRepository fixturePlayerPoolRepository;
    private final PlayerRepository playerRepository;
    private final UserMatchTeamRepository userMatchTeamRepository;
    private final UserMatchTeamPlayerRepository userMatchTeamPlayerRepository;
    private final FixtureSyncService fixtureSyncService;
    private final SportMonksCricketClient sportMonksCricketClient;
    private final ObjectMapper objectMapper;

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

    @Transactional
    public int syncFixturePlayerPool(Long fixtureId) {
        Fixture fixture = ensureFixtureReadyForPlayerSync(fixtureId);
        List<FixtureParticipant> participants = fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixtureId);

        List<PlayerPoolSeed> seeds = loadPlayerPoolSeeds(fixture, participants);
        Map<Long, FixturePlayerPool> existingByPlayerId = new HashMap<>();
        for (FixturePlayerPool existing : fixturePlayerPoolRepository.findByFixtureIdOrderByIdAsc(fixtureId)) {
            existingByPlayerId.put(existing.getPlayerId(), existing);
        }

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

        fixture.setLastSyncedAt(LocalDateTime.now());
        fixtureRepository.save(fixture);

        return count;
    }

    public List<FixturePlayerPoolResponse> getFixturePlayerPool(Long fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> ApiException.notFound("Fixture not found"));

        List<FixturePlayerPool> pool = fixturePlayerPoolRepository
                .findByFixtureIdAndIsActiveTrueOrderByExternalTeamIdAscRoleCodeAscIdAsc(fixtureId);

        if (pool.isEmpty() && fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            try {
                syncFixturePlayerPool(fixtureId);
            } catch (RuntimeException ex) {
                log.warn(
                        "Live player-pool sync failed for fixture {}. Falling back to the saved pool if present.",
                        fixtureId,
                        ex
                );
                // Fall back to the last saved pool if live sync is not available yet.
            }

            pool = fixturePlayerPoolRepository
                    .findByFixtureIdAndIsActiveTrueOrderByExternalTeamIdAscRoleCodeAscIdAsc(fixtureId);
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
                    .isAnnounced(p.getIsAnnounced())
                    .isPlaying(p.getIsPlaying())
                    .imageUrl(player.getImageUrl())
                    .build());
        }

        return response;
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

        JsonNode players = data.path("players");
        if (players.isArray()) {
            return players;
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

        Long externalTeamId = longValue(playerNode, "lineup.team_id");
        if (externalTeamId == null) {
            externalTeamId = longValue(playerNode, "team_id");
        }
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

        if (fixture.getDeadlineTime().isBefore(LocalDateTime.now())) {
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
        List<UserMatchTeam> teams = userMatchTeamRepository.findByFixtureIdAndUserIdOrderByCreatedAtDesc(fixtureId, userId);
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

        if (Boolean.TRUE.equals(team.getIsLocked()) || fixture.getDeadlineTime().isBefore(LocalDateTime.now())) {
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

        Fixture fixture = fixtureRepository.findById(team.getFixtureId())
                .orElseThrow(() -> ApiException.notFound("Fixture not found"));

        if (Boolean.TRUE.equals(team.getIsLocked()) || fixture.getDeadlineTime().isBefore(LocalDateTime.now())) {
            throw ApiException.conflict("Team can no longer be deleted");
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

        List<TeamPlayerResponse> playerResponses = new ArrayList<>();
        List<TeamPlayerResponse> substituteResponses = new ArrayList<>();
        for (UserMatchTeamPlayer p : players) {
            Player player = playerRepository.findById(p.getPlayerId())
                    .orElseThrow(() -> ApiException.notFound("Player not found"));

            TeamPlayerResponse response = TeamPlayerResponse.builder()
                    .fixturePlayerPoolId(p.getFixturePlayerPoolId())
                    .playerId(p.getPlayerId())
                    .playerName(player.getPlayerName())
                    .roleCode(p.getRoleCode())
                    .teamSide(p.getTeamSide())
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
                .players(playerResponses)
                .substitutes(substituteResponses)
                .build();
    }

    private String resolveRoleCode(JsonNode playerNode) {
        String position = textValue(playerNode, "position.name", null);
        if (position != null) {
            String p = position.toUpperCase();
            if (p.contains("WICKET")) return "WK";
            if (p.contains("BAT")) return "BAT";
            if (p.contains("BOWL")) return "BOWL";
            if (p.contains("ALL")) return "AR";
        }

        boolean wicketKeeper = booleanValue(playerNode, "lineup.wicketkeeper");
        boolean captain = booleanValue(playerNode, "lineup.captain");

        if (wicketKeeper) return "WK";
        if (captain) return "BAT";

        return "AR";
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
