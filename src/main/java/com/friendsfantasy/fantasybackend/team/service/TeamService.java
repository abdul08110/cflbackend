package com.friendsfantasy.fantasybackend.team.service;

import com.friendsfantasy.fantasybackend.fixture.dto.FixturePlayerPoolResponse;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import com.friendsfantasy.fantasybackend.fixture.entity.FixturePlayerPool;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureParticipantRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixturePlayerPoolRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.integration.sportmonks.SportMonksCricketClient;
import com.friendsfantasy.fantasybackend.player.entity.Player;
import com.friendsfantasy.fantasybackend.player.repository.PlayerRepository;
import com.friendsfantasy.fantasybackend.team.dto.*;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeam;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeamPlayer;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamPlayerRepository;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class TeamService {

    private final FixtureRepository fixtureRepository;
    private final FixtureParticipantRepository fixtureParticipantRepository;
    private final FixturePlayerPoolRepository fixturePlayerPoolRepository;
    private final PlayerRepository playerRepository;
    private final UserMatchTeamRepository userMatchTeamRepository;
    private final UserMatchTeamPlayerRepository userMatchTeamPlayerRepository;
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

    @Transactional
    public int syncFixturePlayerPool(Long fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        JsonNode root = sportMonksCricketClient.getFixtureWithLineup(fixture.getExternalFixtureId());
        JsonNode data = root.path("data");
        JsonNode lineup = data.path("lineup");

        if (!lineup.isArray()) {
            throw new RuntimeException("Lineup not available yet for this fixture");
        }

        Map<Long, String> teamSideMap = new HashMap<>();
        List<FixtureParticipant> participants = fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixtureId);
        for (FixtureParticipant p : participants) {
            teamSideMap.put(p.getExternalTeamId(), p.getIsHome() ? "HOME" : "AWAY");
        }

        fixturePlayerPoolRepository.deleteByFixtureId(fixtureId);

        int count = 0;

        for (JsonNode playerNode : lineup) {
            Long externalPlayerId = longValue(playerNode, "id");
            if (externalPlayerId == null) {
                continue;
            }

            Player player = playerRepository.findBySportIdAndExternalPlayerId(cricketSportId, externalPlayerId)
                    .orElseGet(Player::new);

            player.setSportId(cricketSportId);
            player.setExternalPlayerId(externalPlayerId);
            player.setPlayerName(textValue(playerNode, "fullname", textValue(playerNode, "name", "Unknown Player")));
            player.setShortName(textValue(playerNode, "lastname", null));
            player.setCountryName(null);
            player.setImageUrl(textValue(playerNode, "image_path", null));
            player.setRawJson(writeJson(playerNode));
            player = playerRepository.save(player);

            Long externalTeamId = longValue(playerNode, "lineup.team_id");
            if (externalTeamId == null) {
                externalTeamId = longValue(playerNode, "team_id");
            }
            if (externalTeamId == null) {
                externalTeamId = 0L;
            }

            String roleCode = resolveRoleCode(playerNode);

            FixturePlayerPool pool = FixturePlayerPool.builder()
                    .fixtureId(fixtureId)
                    .playerId(player.getId())
                    .externalTeamId(externalTeamId)
                    .roleCode(roleCode)
                    .creditValue(defaultPlayerCredit.setScale(1, RoundingMode.HALF_UP))
                    .isAnnounced(true)
                    .isPlaying(true)
                    .isActive(true)
                    .selectionPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .build();

            fixturePlayerPoolRepository.save(pool);
            count++;
        }

        return count;
    }

    public List<FixturePlayerPoolResponse> getFixturePlayerPool(Long fixtureId) {
        fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        List<FixturePlayerPool> pool = fixturePlayerPoolRepository
                .findByFixtureIdAndIsActiveTrueOrderByExternalTeamIdAscRoleCodeAscIdAsc(fixtureId);

        List<FixturePlayerPoolResponse> response = new ArrayList<>();

        for (FixturePlayerPool p : pool) {
            Player player = playerRepository.findById(p.getPlayerId())
                    .orElseThrow(() -> new RuntimeException("Player not found"));

            response.add(FixturePlayerPoolResponse.builder()
                    .fixturePlayerPoolId(p.getId())
                    .playerId(player.getId())
                    .externalTeamId(p.getExternalTeamId())
                    .playerName(player.getPlayerName())
                    .shortName(player.getShortName())
                    .roleCode(p.getRoleCode())
                    .creditValue(p.getCreditValue())
                    .isAnnounced(p.getIsAnnounced())
                    .isPlaying(p.getIsPlaying())
                    .imageUrl(player.getImageUrl())
                    .build());
        }

        return response;
    }

    @Transactional
    public TeamResponse createTeam(Long userId, Long fixtureId, CreateTeamRequest request) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (fixture.getDeadlineTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Team creation is closed for this fixture");
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

        return getTeamForOwner(userId, team.getId());
    }

    public List<TeamResponse> getMyTeams(Long userId, Long fixtureId) {
        List<UserMatchTeam> teams = userMatchTeamRepository.findByFixtureIdAndUserIdOrderByCreatedAtDesc(fixtureId, userId);
        List<TeamResponse> response = new ArrayList<>();
        for (UserMatchTeam t : teams) {
            response.add(mapTeamResponse(t));
        }
        return response;
    }

    public TeamResponse getTeamForOwner(Long userId, Long teamId) {
        UserMatchTeam team = userMatchTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> new RuntimeException("Team not found"));
        return mapTeamResponse(team);
    }

    @Transactional
    public TeamResponse updateTeam(Long userId, Long teamId, CreateTeamRequest request) {
        UserMatchTeam team = userMatchTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        Fixture fixture = fixtureRepository.findById(team.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (Boolean.TRUE.equals(team.getIsLocked()) || fixture.getDeadlineTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Team can no longer be edited");
        }

        ValidationBundle bundle = validateAndBuildTeam(team.getFixtureId(), request);

        team.setTeamName(request.getTeamName().trim());
        team.setCaptainPlayerId(request.getCaptainPlayerId());
        team.setViceCaptainPlayerId(request.getViceCaptainPlayerId());
        team.setTotalCredits(bundle.totalCredits);
        userMatchTeamRepository.save(team);

        userMatchTeamPlayerRepository.deleteByUserMatchTeamId(team.getId());
        for (UserMatchTeamPlayer p : bundle.teamPlayers) {
            p.setUserMatchTeamId(team.getId());
            userMatchTeamPlayerRepository.save(p);
        }

        return getTeamForOwner(userId, team.getId());
    }

    @Transactional
    public void deleteTeam(Long userId, Long teamId) {
        UserMatchTeam team = userMatchTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        Fixture fixture = fixtureRepository.findById(team.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (Boolean.TRUE.equals(team.getIsLocked()) || fixture.getDeadlineTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Team can no longer be deleted");
        }

        userMatchTeamPlayerRepository.deleteByUserMatchTeamId(team.getId());
        userMatchTeamRepository.delete(team);
    }

    private ValidationBundle validateAndBuildTeam(Long fixtureId, CreateTeamRequest request) {
        if (request.getPlayers() == null || request.getPlayers().size() != maxPlayers) {
            throw new RuntimeException("Exactly " + maxPlayers + " players must be selected");
        }

        Set<Long> uniquePoolIds = new HashSet<>();
        for (SelectedTeamPlayerRequest p : request.getPlayers()) {
            if (!uniquePoolIds.add(p.getFixturePlayerPoolId())) {
                throw new RuntimeException("Duplicate players selected");
            }
        }

        List<FixturePlayerPool> selectedPool = new ArrayList<>();
        Map<Long, FixturePlayerPool> byPlayerId = new HashMap<>();
        Map<Long, Integer> teamCounts = new HashMap<>();
        BigDecimal totalCredits = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);

        for (SelectedTeamPlayerRequest selected : request.getPlayers()) {
            FixturePlayerPool pool = fixturePlayerPoolRepository.findById(selected.getFixturePlayerPoolId())
                    .orElseThrow(() -> new RuntimeException("Invalid fixture player pool id: " + selected.getFixturePlayerPoolId()));

            if (!Objects.equals(pool.getFixtureId(), fixtureId)) {
                throw new RuntimeException("Selected players do not belong to this fixture");
            }

            if (!Boolean.TRUE.equals(pool.getIsActive())) {
                throw new RuntimeException("Inactive player selected");
            }

            selectedPool.add(pool);
            byPlayerId.put(pool.getPlayerId(), pool);
            totalCredits = totalCredits.add(pool.getCreditValue());

            teamCounts.put(pool.getExternalTeamId(), teamCounts.getOrDefault(pool.getExternalTeamId(), 0) + 1);
        }

        if (!byPlayerId.containsKey(request.getCaptainPlayerId())) {
            throw new RuntimeException("Captain must be selected from the team");
        }

        if (!byPlayerId.containsKey(request.getViceCaptainPlayerId())) {
            throw new RuntimeException("Vice captain must be selected from the team");
        }

        if (Objects.equals(request.getCaptainPlayerId(), request.getViceCaptainPlayerId())) {
            throw new RuntimeException("Captain and vice captain must be different");
        }

        for (Integer count : teamCounts.values()) {
            if (count > maxFromOneSide) {
                throw new RuntimeException("Maximum " + maxFromOneSide + " players allowed from one real team");
            }
        }

        if (totalCredits.compareTo(creditLimit) > 0) {
            throw new RuntimeException("Selected players exceed credit limit");
        }

        List<UserMatchTeamPlayer> teamPlayers = new ArrayList<>();

        for (FixturePlayerPool pool : selectedPool) {
            UserMatchTeamPlayer teamPlayer = UserMatchTeamPlayer.builder()
                    .fixturePlayerPoolId(pool.getId())
                    .playerId(pool.getPlayerId())
                    .roleCode(pool.getRoleCode())
                    .teamSide(resolveTeamSide(fixtureId, pool.getExternalTeamId()))
                    .creditValue(pool.getCreditValue())
                    .isCaptain(Objects.equals(pool.getPlayerId(), request.getCaptainPlayerId()))
                    .isViceCaptain(Objects.equals(pool.getPlayerId(), request.getViceCaptainPlayerId()))
                    .build();

            teamPlayers.add(teamPlayer);
        }

        ValidationBundle bundle = new ValidationBundle();
        bundle.totalCredits = totalCredits;
        bundle.teamPlayers = teamPlayers;
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
        List<UserMatchTeamPlayer> players = userMatchTeamPlayerRepository.findByUserMatchTeamIdOrderByIdAsc(team.getId());

        List<TeamPlayerResponse> playerResponses = new ArrayList<>();
        for (UserMatchTeamPlayer p : players) {
            Player player = playerRepository.findById(p.getPlayerId())
                    .orElseThrow(() -> new RuntimeException("Player not found"));

            playerResponses.add(TeamPlayerResponse.builder()
                    .fixturePlayerPoolId(p.getFixturePlayerPoolId())
                    .playerId(p.getPlayerId())
                    .playerName(player.getPlayerName())
                    .roleCode(p.getRoleCode())
                    .teamSide(p.getTeamSide())
                    .creditValue(p.getCreditValue())
                    .isCaptain(p.getIsCaptain())
                    .isViceCaptain(p.getIsViceCaptain())
                    .build());
        }

        return TeamResponse.builder()
                .teamId(team.getId())
                .fixtureId(team.getFixtureId())
                .teamName(team.getTeamName())
                .captainPlayerId(team.getCaptainPlayerId())
                .viceCaptainPlayerId(team.getViceCaptainPlayerId())
                .totalCredits(team.getTotalCredits())
                .isLocked(team.getIsLocked())
                .players(playerResponses)
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
    }
}