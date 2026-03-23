package com.friendsfantasy.fantasybackend.team.service;

import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import com.friendsfantasy.fantasybackend.fixture.entity.FixturePlayerPool;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureParticipantRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixturePlayerPoolRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.fixture.service.FixtureSyncService;
import com.friendsfantasy.fantasybackend.integration.sportmonks.SportMonksCricketClient;
import com.friendsfantasy.fantasybackend.player.entity.Player;
import com.friendsfantasy.fantasybackend.player.repository.PlayerRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private FixtureSyncService fixtureSyncService;

    @Mock
    private SportMonksCricketClient sportMonksCricketClient;

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
                fixtureSyncService,
                sportMonksCricketClient,
                new ObjectMapper()
        );

        ReflectionTestUtils.setField(teamService, "cricketSportId", 1L);
        ReflectionTestUtils.setField(teamService, "defaultPlayerCredit", BigDecimal.valueOf(8.0));
        ReflectionTestUtils.setField(teamService, "maxPlayers", 11);
        ReflectionTestUtils.setField(teamService, "maxFromOneSide", 7);
        ReflectionTestUtils.setField(teamService, "creditLimit", BigDecimal.valueOf(100.0));
        ReflectionTestUtils.setField(teamService, "maxSubstitutes", 4);
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
        when(fixtureRepository.save(any(Fixture.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(fixtureRepository.save(any(Fixture.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int syncedCount = teamService.syncFixturePlayerPool(1L);

        assertThat(syncedCount).isEqualTo(1);
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
}
