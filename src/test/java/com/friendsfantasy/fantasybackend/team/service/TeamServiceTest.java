package com.friendsfantasy.fantasybackend.team.service;

import com.friendsfantasy.fantasybackend.contest.repository.ContestEntryRepository;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import com.friendsfantasy.fantasybackend.fixture.entity.FixturePlayerPool;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureParticipantRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixturePlayerPoolRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.fixture.service.FixtureSyncService;
import com.friendsfantasy.fantasybackend.integration.sportmonks.SportMonksCricketClient;
import com.friendsfantasy.fantasybackend.notification.repository.NotificationRepository;
import com.friendsfantasy.fantasybackend.notification.service.NotificationService;
import com.friendsfantasy.fantasybackend.player.entity.Player;
import com.friendsfantasy.fantasybackend.player.repository.PlayerRepository;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeam;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamPlayerRepository;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private FixtureRepository fixtureRepository;

    @Mock
    private FixtureParticipantRepository fixtureParticipantRepository;

    @Mock
    private FixturePlayerPoolRepository fixturePlayerPoolRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private UserMatchTeamRepository userMatchTeamRepository;

    @Mock
    private UserMatchTeamPlayerRepository userMatchTeamPlayerRepository;

    @Mock
    private ContestEntryRepository contestEntryRepository;

    @Mock
    private FixtureSyncService fixtureSyncService;

    @Mock
    private SportMonksCricketClient sportMonksCricketClient;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(
                fixtureRepository,
                fixtureParticipantRepository,
                fixturePlayerPoolRepository,
                playerRepository,
                userMatchTeamRepository,
                userMatchTeamPlayerRepository,
                contestEntryRepository,
                fixtureSyncService,
                sportMonksCricketClient,
                new ObjectMapper(),
                notificationService,
                notificationRepository
        );

        ReflectionTestUtils.setField(teamService, "cricketSportId", 1L);
        ReflectionTestUtils.setField(teamService, "defaultPlayerCredit", BigDecimal.valueOf(8.0));
        ReflectionTestUtils.setField(teamService, "maxPlayers", 11);
        ReflectionTestUtils.setField(teamService, "maxFromOneSide", 7);
        ReflectionTestUtils.setField(teamService, "creditLimit", BigDecimal.valueOf(100.0));
        ReflectionTestUtils.setField(teamService, "maxSubstitutes", 4);
        ReflectionTestUtils.setField(teamService, "playerPoolAutoRefreshWindowMinutes", 180L);
        ReflectionTestUtils.setField(teamService, "playerPoolStaleRefreshMinSeconds", 120L);
        ReflectionTestUtils.setField(teamService, "playerPoolAnnouncedStaleRefreshMinSeconds", 45L);
    }

    @Test
    void syncFixturePlayerPoolAcceptsArrayShapedSquadPayloads() throws Exception {
        Fixture fixture = Fixture.builder()
                .id(1L)
                .sportId(1L)
                .externalFixtureId(69518L)
                .externalSeasonId(2026L)
                .title("Royal Challengers Bengaluru vs Sunrisers Hyderabad")
                .status("NS")
                .startTime(LocalDateTime.now().plusDays(2))
                .deadlineTime(LocalDateTime.now().plusDays(2))
                .build();
        List<FixtureParticipant> participants = List.of(
                FixtureParticipant.builder()
                        .fixtureId(1L)
                        .externalTeamId(8L)
                        .teamName("Royal Challengers Bengaluru")
                        .shortName("RCB")
                        .isHome(true)
                        .build(),
                FixtureParticipant.builder()
                        .fixtureId(1L)
                        .externalTeamId(9L)
                        .teamName("Sunrisers Hyderabad")
                        .shortName("SRH")
                        .isHome(false)
                        .build()
        );

        when(fixtureRepository.findById(1L)).thenReturn(Optional.of(fixture));
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(1L)).thenReturn(participants);
        when(fixturePlayerPoolRepository.findByFixtureIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(sportMonksCricketClient.getFixtureWithLineup(69518L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": {
                    "lineup": []
                  }
                }
                """));
        when(sportMonksCricketClient.getTeamSquad(8L, 2026L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": [
                    {
                      "id": 1001,
                      "fullname": "Virat Kohli",
                      "lastname": "Kohli",
                      "image_path": "virat.png",
                      "position": {
                        "name": "Batsman"
                      }
                    }
                  ]
                }
                """));
        when(sportMonksCricketClient.getTeamSquad(9L, 2026L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": []
                }
                """));
        when(playerRepository.findBySportIdAndExternalPlayerId(1L, 1001L)).thenReturn(Optional.empty());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            if (player.getId() == null) {
                player.setId(501L);
            }
            return player;
        });
        when(fixturePlayerPoolRepository.save(any(FixturePlayerPool.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int syncedCount = teamService.syncFixturePlayerPool(1L);

        assertThat(syncedCount).isEqualTo(1);
    }

    @Test
    void syncFixturePlayerPoolAcceptsNestedSquadPayloads() throws Exception {
        Fixture fixture = Fixture.builder()
                .id(1L)
                .sportId(1L)
                .externalFixtureId(69518L)
                .externalSeasonId(2026L)
                .title("Royal Challengers Bengaluru vs Sunrisers Hyderabad")
                .status("NS")
                .startTime(LocalDateTime.now().plusDays(2))
                .deadlineTime(LocalDateTime.now().plusDays(2))
                .build();
        List<FixtureParticipant> participants = List.of(
                FixtureParticipant.builder()
                        .fixtureId(1L)
                        .externalTeamId(8L)
                        .teamName("Royal Challengers Bengaluru")
                        .shortName("RCB")
                        .isHome(true)
                        .build(),
                FixtureParticipant.builder()
                        .fixtureId(1L)
                        .externalTeamId(9L)
                        .teamName("Sunrisers Hyderabad")
                        .shortName("SRH")
                        .isHome(false)
                        .build()
        );

        when(fixtureRepository.findById(1L)).thenReturn(Optional.of(fixture));
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(1L)).thenReturn(participants);
        when(fixturePlayerPoolRepository.findByFixtureIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(sportMonksCricketClient.getFixtureWithLineup(69518L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": {
                    "lineup": []
                  }
                }
                """));
        when(sportMonksCricketClient.getTeamSquad(8L, 2026L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": {
                    "id": 8,
                    "name": "Royal Challengers Bengaluru",
                    "squad": [
                      {
                        "id": 1001,
                        "fullname": "Virat Kohli",
                        "lastname": "Kohli",
                        "image_path": "virat.png",
                        "position": {
                          "name": "Batsman"
                        }
                      }
                    ]
                  }
                }
                """));
        when(sportMonksCricketClient.getTeamSquad(9L, 2026L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": {
                    "id": 9,
                    "name": "Sunrisers Hyderabad",
                    "squad": []
                  }
                }
                """));
        when(playerRepository.findBySportIdAndExternalPlayerId(1L, 1001L)).thenReturn(Optional.empty());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            if (player.getId() == null) {
                player.setId(501L);
            }
            return player;
        });
        when(fixturePlayerPoolRepository.save(any(FixturePlayerPool.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int syncedCount = teamService.syncFixturePlayerPool(1L);

        assertThat(syncedCount).isEqualTo(1);
    }

    @Test
    void syncFixturePlayerPoolMapsRolesFromLineupAndSquadPayloads() throws Exception {
        Fixture fixture = Fixture.builder()
                .id(1L)
                .sportId(1L)
                .externalFixtureId(69518L)
                .externalSeasonId(2026L)
                .title("Royal Challengers Bengaluru vs Sunrisers Hyderabad")
                .status("NS")
                .startTime(LocalDateTime.now().plusDays(2))
                .deadlineTime(LocalDateTime.now().plusDays(2))
                .build();
        List<FixtureParticipant> participants = List.of(
                FixtureParticipant.builder()
                        .fixtureId(1L)
                        .externalTeamId(8L)
                        .teamName("Royal Challengers Bengaluru")
                        .shortName("RCB")
                        .isHome(true)
                        .build(),
                FixtureParticipant.builder()
                        .fixtureId(1L)
                        .externalTeamId(9L)
                        .teamName("Sunrisers Hyderabad")
                        .shortName("SRH")
                        .isHome(false)
                        .build()
        );
        List<FixturePlayerPool> savedPools = new ArrayList<>();
        Map<Long, Long> internalPlayerIdsByExternalId = Map.of(
                1001L, 501L,
                1002L, 502L,
                1003L, 503L
        );

        when(fixtureRepository.findById(1L)).thenReturn(Optional.of(fixture));
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(1L)).thenReturn(participants);
        when(fixturePlayerPoolRepository.findByFixtureIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(sportMonksCricketClient.getFixtureWithLineup(69518L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": {
                    "lineup": [
                      {
                        "id": 1001,
                        "fullname": "Phil Salt",
                        "lastname": "Salt",
                        "image_path": "salt.png",
                        "position": {
                          "id": 3,
                          "name": "Wicketkeeper"
                        },
                        "lineup": {
                          "team_id": 8
                        }
                      },
                      {
                        "id": 1002,
                        "fullname": "Krunal Pandya",
                        "lastname": "Pandya",
                        "image_path": "krunal.png",
                        "position": {
                          "name": "All Rounder"
                        },
                        "lineup": {
                          "team_id": 8
                        }
                      }
                    ]
                  }
                }
                """));
        when(sportMonksCricketClient.getTeamSquad(8L, 2026L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": []
                }
                """));
        when(sportMonksCricketClient.getTeamSquad(9L, 2026L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": [
                    {
                      "id": 1003,
                      "fullname": "Pat Cummins",
                      "lastname": "Cummins",
                      "image_path": "pat.png",
                      "bowlingstyle": "Right-arm fast"
                    }
                  ]
                }
                """));
        when(playerRepository.findBySportIdAndExternalPlayerId(1L, 1001L)).thenReturn(Optional.empty());
        when(playerRepository.findBySportIdAndExternalPlayerId(1L, 1002L)).thenReturn(Optional.empty());
        when(playerRepository.findBySportIdAndExternalPlayerId(1L, 1003L)).thenReturn(Optional.empty());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            if (player.getId() == null) {
                player.setId(internalPlayerIdsByExternalId.get(player.getExternalPlayerId()));
            }
            return player;
        });
        when(fixturePlayerPoolRepository.save(any(FixturePlayerPool.class))).thenAnswer(invocation -> {
            FixturePlayerPool pool = invocation.getArgument(0);
            if (pool.getId() == null) {
                pool.setId(700L + savedPools.size() + 1);
            }
            savedPools.add(pool);
            return pool;
        });

        int syncedCount = teamService.syncFixturePlayerPool(1L);

        assertThat(syncedCount).isEqualTo(3);
        assertThat(savedPools)
                .anySatisfy(pool -> {
                    assertThat(pool.getPlayerId()).isEqualTo(501L);
                    assertThat(pool.getRoleCode()).isEqualTo("WK");
                })
                .anySatisfy(pool -> {
                    assertThat(pool.getPlayerId()).isEqualTo(502L);
                    assertThat(pool.getRoleCode()).isEqualTo("AR");
                })
                .anySatisfy(pool -> {
                    assertThat(pool.getPlayerId()).isEqualTo(503L);
                    assertThat(pool.getRoleCode()).isEqualTo("BOWL");
                });
    }

    @Test
    void getFixturePlayerPoolReturnsSavedPoolWithoutLiveResync() {
        Fixture fixture = Fixture.builder()
                .id(1L)
                .sportId(1L)
                .externalFixtureId(69518L)
                .externalSeasonId(2026L)
                .title("Royal Challengers Bengaluru vs Sunrisers Hyderabad")
                .status("NS")
                .startTime(LocalDateTime.now().plusDays(2))
                .deadlineTime(LocalDateTime.now().plusDays(2))
                .build();
        FixtureParticipant participant = FixtureParticipant.builder()
                .fixtureId(1L)
                .externalTeamId(8L)
                .teamName("Royal Challengers Bengaluru")
                .shortName("RCB")
                .isHome(true)
                .build();
        FixturePlayerPool pool = FixturePlayerPool.builder()
                .id(11L)
                .fixtureId(1L)
                .playerId(101L)
                .externalTeamId(8L)
                .roleCode("BAT")
                .creditValue(BigDecimal.valueOf(8.0))
                .isActive(true)
                .isAnnounced(false)
                .isPlaying(false)
                .selectionPercent(BigDecimal.ZERO)
                .build();
        Player player = Player.builder()
                .id(101L)
                .sportId(1L)
                .externalPlayerId(9999L)
                .playerName("Virat Kohli")
                .shortName("Kohli")
                .imageUrl("virat.png")
                .build();

        when(fixtureRepository.findById(1L)).thenReturn(Optional.of(fixture));
        when(fixturePlayerPoolRepository.findByFixtureIdAndIsActiveTrueOrderByExternalTeamIdAscRoleCodeAscIdAsc(1L))
                .thenReturn(List.of(pool));
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(1L))
                .thenReturn(List.of(participant));
        when(playerRepository.findAllById(List.of(101L))).thenReturn(List.of(player));

        List<?> response = teamService.getFixturePlayerPool(1L);

        assertThat(response).hasSize(1);
        verifyNoInteractions(sportMonksCricketClient);
    }

    @Test
    void getFixturePlayerPoolAutoRefreshesStalePoolNearDeadline() {
        TeamService spyTeamService = org.mockito.Mockito.spy(teamService);
        Fixture fixture = Fixture.builder()
                .id(1L)
                .sportId(1L)
                .externalFixtureId(69518L)
                .externalSeasonId(2026L)
                .title("Royal Challengers Bengaluru vs Sunrisers Hyderabad")
                .status("NS")
                .startTime(LocalDateTime.now().plusMinutes(45))
                .deadlineTime(LocalDateTime.now().plusMinutes(45))
                .build();
        FixtureParticipant participant = FixtureParticipant.builder()
                .fixtureId(1L)
                .externalTeamId(8L)
                .teamName("Royal Challengers Bengaluru")
                .shortName("RCB")
                .isHome(true)
                .build();
        FixturePlayerPool pool = FixturePlayerPool.builder()
                .id(11L)
                .fixtureId(1L)
                .playerId(101L)
                .externalTeamId(8L)
                .roleCode("BAT")
                .creditValue(BigDecimal.valueOf(8.0))
                .isActive(true)
                .isAnnounced(false)
                .isPlaying(false)
                .selectionPercent(BigDecimal.ZERO)
                .updatedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        Player player = Player.builder()
                .id(101L)
                .sportId(1L)
                .externalPlayerId(9999L)
                .playerName("Virat Kohli")
                .shortName("Kohli")
                .imageUrl("virat.png")
                .build();

        doReturn(1).when(spyTeamService).syncFixturePlayerPool(1L);
        when(fixtureRepository.findById(1L)).thenReturn(Optional.of(fixture));
        when(fixturePlayerPoolRepository.findByFixtureIdAndIsActiveTrueOrderByExternalTeamIdAscRoleCodeAscIdAsc(1L))
                .thenReturn(List.of(pool), List.of(pool));
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(1L))
                .thenReturn(List.of(participant));
        when(playerRepository.findAllById(List.of(101L))).thenReturn(List.of(player));

        List<?> response = spyTeamService.getFixturePlayerPool(1L);

        assertThat(response).hasSize(1);
        verify(spyTeamService).syncFixturePlayerPool(1L);
    }

    @Test
    void getFixturePlayerPoolForceSyncsWhenRequested() {
        TeamService spyTeamService = org.mockito.Mockito.spy(teamService);
        Fixture fixture = Fixture.builder()
                .id(1L)
                .sportId(1L)
                .externalFixtureId(69518L)
                .externalSeasonId(2026L)
                .title("Royal Challengers Bengaluru vs Sunrisers Hyderabad")
                .status("NS")
                .startTime(LocalDateTime.now().plusDays(2))
                .deadlineTime(LocalDateTime.now().plusDays(2))
                .build();
        FixtureParticipant participant = FixtureParticipant.builder()
                .fixtureId(1L)
                .externalTeamId(8L)
                .teamName("Royal Challengers Bengaluru")
                .shortName("RCB")
                .isHome(true)
                .build();
        FixturePlayerPool pool = FixturePlayerPool.builder()
                .id(11L)
                .fixtureId(1L)
                .playerId(101L)
                .externalTeamId(8L)
                .roleCode("BAT")
                .creditValue(BigDecimal.valueOf(8.0))
                .isActive(true)
                .isAnnounced(false)
                .isPlaying(false)
                .selectionPercent(BigDecimal.ZERO)
                .updatedAt(LocalDateTime.now())
                .build();
        Player player = Player.builder()
                .id(101L)
                .sportId(1L)
                .externalPlayerId(9999L)
                .playerName("Virat Kohli")
                .shortName("Kohli")
                .imageUrl("virat.png")
                .build();

        doReturn(1).when(spyTeamService).syncFixturePlayerPool(1L);
        when(fixtureRepository.findById(1L)).thenReturn(Optional.of(fixture));
        when(fixturePlayerPoolRepository.findByFixtureIdAndIsActiveTrueOrderByExternalTeamIdAscRoleCodeAscIdAsc(1L))
                .thenReturn(List.of(pool), List.of(pool));
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(1L))
                .thenReturn(List.of(participant));
        when(playerRepository.findAllById(List.of(101L))).thenReturn(List.of(player));

        List<?> response = spyTeamService.getFixturePlayerPool(1L, true);

        assertThat(response).hasSize(1);
        verify(spyTeamService).syncFixturePlayerPool(1L);
    }

    @Test
    void getMyTeamsDeletesUnusedTeamsAfterFixtureStart() {
        Fixture fixture = Fixture.builder()
                .id(1L)
                .sportId(1L)
                .externalFixtureId(69518L)
                .title("Royal Challengers Bengaluru vs Sunrisers Hyderabad")
                .status("LIVE")
                .startTime(LocalDateTime.now().minusMinutes(10))
                .deadlineTime(LocalDateTime.now().minusMinutes(10))
                .build();
        UserMatchTeam joinedTeam = UserMatchTeam.builder()
                .id(1001L)
                .fixtureId(1L)
                .userId(9L)
                .teamName("Joined XI")
                .captainPlayerId(11L)
                .viceCaptainPlayerId(12L)
                .totalCredits(BigDecimal.valueOf(100))
                .build();
        UserMatchTeam unusedTeam = UserMatchTeam.builder()
                .id(1002L)
                .fixtureId(1L)
                .userId(9L)
                .teamName("Unused XI")
                .captainPlayerId(21L)
                .viceCaptainPlayerId(22L)
                .totalCredits(BigDecimal.valueOf(100))
                .build();

        when(fixtureRepository.findById(1L)).thenReturn(Optional.of(fixture));
        when(userMatchTeamRepository.findByFixtureIdAndUserIdOrderByCreatedAtDesc(1L, 9L))
                .thenReturn(List.of(joinedTeam, unusedTeam), List.of(joinedTeam));
        when(contestEntryRepository.existsByUserMatchTeamId(1001L)).thenReturn(true);
        when(contestEntryRepository.existsByUserMatchTeamId(1002L)).thenReturn(false);
        when(userMatchTeamRepository.findById(1001L)).thenReturn(Optional.of(joinedTeam));
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(1L))
                .thenReturn(List.of());
        when(userMatchTeamPlayerRepository.findByUserMatchTeamIdOrderByIsSubstituteAscSubstitutePriorityAscIdAsc(1001L))
                .thenReturn(List.of());

        List<?> teams = teamService.getMyTeams(9L, 1L);

        assertThat(teams).hasSize(1);
        verify(userMatchTeamPlayerRepository).deleteByUserMatchTeamId(1002L);
        verify(userMatchTeamRepository).delete(unusedTeam);
    }

    @Test
    void syncFixturePlayerPoolSendsOneTimeLineupNotificationsWhenLineupFirstAppears() throws Exception {
        Fixture fixture = Fixture.builder()
                .id(1L)
                .sportId(1L)
                .externalFixtureId(69518L)
                .externalSeasonId(2026L)
                .title("Royal Challengers Bengaluru vs Sunrisers Hyderabad")
                .status("NS")
                .startTime(LocalDateTime.now().plusHours(2))
                .deadlineTime(LocalDateTime.now().plusHours(2))
                .build();
        List<FixtureParticipant> participants = List.of(
                FixtureParticipant.builder()
                        .fixtureId(1L)
                        .externalTeamId(8L)
                        .teamName("Royal Challengers Bengaluru")
                        .shortName("RCB")
                        .isHome(true)
                        .build(),
                FixtureParticipant.builder()
                        .fixtureId(1L)
                        .externalTeamId(9L)
                        .teamName("Sunrisers Hyderabad")
                        .shortName("SRH")
                        .isHome(false)
                        .build()
        );
        UserMatchTeam trackedTeam = UserMatchTeam.builder()
                .id(900L)
                .fixtureId(1L)
                .userId(77L)
                .teamName("My XI")
                .captainPlayerId(1001L)
                .viceCaptainPlayerId(1002L)
                .totalCredits(BigDecimal.valueOf(100))
                .build();

        when(fixtureRepository.findById(1L)).thenReturn(Optional.of(fixture));
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(1L)).thenReturn(participants);
        when(fixturePlayerPoolRepository.findByFixtureIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(fixturePlayerPoolRepository.save(any(FixturePlayerPool.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sportMonksCricketClient.getFixtureWithLineup(69518L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": {
                    "lineup": [
                      {
                        "id": 1001,
                        "fullname": "Virat Kohli",
                        "lastname": "Kohli",
                        "image_path": "virat.png",
                        "position": {
                          "id": 1,
                          "name": "Batsman"
                        },
                        "lineup": {
                          "team_id": 8
                        }
                      }
                    ]
                  }
                }
                """));
        when(sportMonksCricketClient.getTeamSquad(8L, 2026L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": []
                }
                """));
        when(sportMonksCricketClient.getTeamSquad(9L, 2026L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": []
                }
                """));
        when(playerRepository.findBySportIdAndExternalPlayerId(1L, 1001L)).thenReturn(Optional.empty());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            if (player.getId() == null) {
                player.setId(501L);
            }
            return player;
        });
        when(userMatchTeamRepository.findByFixtureIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(trackedTeam));
        when(notificationRepository.existsByUserIdAndTypeAndPayloadJson(
                77L,
                "LINEUP_ANNOUNCED",
                "{\"fixtureId\":1,\"type\":\"LINEUP_ANNOUNCED\"}"
        )).thenReturn(false);

        teamService.syncFixturePlayerPool(1L);

        verify(notificationService).createNotifications(argThat(notifications ->
                notifications != null
                        && notifications.size() == 1
                        && notifications.get(0).getUserId().equals(77L)
                        && notifications.get(0).getType().equals("LINEUP_ANNOUNCED")
        ));
    }
}
