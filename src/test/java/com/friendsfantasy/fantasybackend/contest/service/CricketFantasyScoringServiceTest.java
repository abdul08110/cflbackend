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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CricketFantasyScoringServiceTest {

    @Mock
    private FixtureRepository fixtureRepository;

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestEntryRepository contestEntryRepository;

    @Mock
    private UserMatchTeamRepository userMatchTeamRepository;

    @Mock
    private UserMatchTeamPlayerRepository userMatchTeamPlayerRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private SportMonksCricketClient sportMonksCricketClient;

    private CricketFantasyScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new CricketFantasyScoringService(
                fixtureRepository,
                contestRepository,
                contestEntryRepository,
                userMatchTeamRepository,
                userMatchTeamPlayerRepository,
                playerRepository,
                sportMonksCricketClient
        );

        ReflectionTestUtils.setField(scoringService, "liveSyncMinSeconds", 0L);
    }

    @Test
    void syncFixtureFantasyPointsUsesBowlingScoreboardWicketsWhenBallDataIsSparse() throws Exception {
        Fixture fixture = Fixture.builder()
                .id(55L)
                .externalFixtureId(90001L)
                .status("1st Innings")
                .startTime(LocalDateTime.now().minusHours(2))
                .deadlineTime(LocalDateTime.now().minusHours(2))
                .build();
        Contest contest = Contest.builder()
                .id(700L)
                .fixtureId(55L)
                .contestType(Contest.ContestType.PUBLIC)
                .status(Contest.Status.LIVE)
                .entryFeePoints(100)
                .prizePoolPoints(100)
                .winnerCount(1)
                .maxSpots(10)
                .spotsFilled(1)
                .build();
        ContestEntry entry = ContestEntry.builder()
                .id(900L)
                .contestId(700L)
                .userId(15L)
                .userMatchTeamId(301L)
                .entryFeePoints(100)
                .fantasyPoints(BigDecimal.ZERO)
                .status(ContestEntry.Status.JOINED)
                .joinedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        UserMatchTeam team = UserMatchTeam.builder()
                .id(301L)
                .fixtureId(55L)
                .userId(15L)
                .teamName("Bowling XI")
                .captainPlayerId(101L)
                .viceCaptainPlayerId(102L)
                .totalCredits(BigDecimal.valueOf(100))
                .build();
        UserMatchTeamPlayer teamPlayer = UserMatchTeamPlayer.builder()
                .id(501L)
                .userMatchTeamId(301L)
                .fixturePlayerPoolId(801L)
                .playerId(101L)
                .roleCode("BOWL")
                .teamSide("HOME")
                .creditValue(BigDecimal.valueOf(8))
                .isCaptain(false)
                .isViceCaptain(false)
                .isSubstitute(false)
                .build();
        Player player = Player.builder()
                .id(101L)
                .externalPlayerId(7001L)
                .playerName("Harshal Patel")
                .build();

        when(fixtureRepository.findById(55L)).thenReturn(Optional.of(fixture));
        when(contestRepository.findByFixtureIdOrderByEntryFeePointsAscIdAsc(55L))
                .thenReturn(List.of(contest));
        when(sportMonksCricketClient.getFixtureWithFantasyScoringData(90001L)).thenReturn(
                new ObjectMapper().readTree("""
                        {
                          "data": {
                            "status": "1st Innings",
                            "type": "T20",
                            "lineup": [
                              {
                                "id": 7001,
                                "lineup": {
                                  "substitution": false
                                }
                              }
                            ],
                            "batting": [],
                            "bowling": [
                              {
                                "player_id": 7001,
                                "scoreboard": "S1",
                                "overs": "4.0",
                                "runs": 20,
                                "wickets": 2,
                                "maidens": 0
                              }
                            ],
                            "balls": [
                              {
                                "id": 1
                              }
                            ]
                          }
                        }
                        """)
        );
        when(contestEntryRepository.findByContestIdInOrderByContestIdAscJoinedAtAsc(List.of(700L)))
                .thenReturn(List.of(entry));
        when(userMatchTeamRepository.findAllById(List.of(301L))).thenReturn(List.of(team));
        when(userMatchTeamPlayerRepository
                .findByUserMatchTeamIdInOrderByUserMatchTeamIdAscIsSubstituteAscSubstitutePriorityAscIdAsc(List.of(301L)))
                .thenReturn(List.of(teamPlayer));
        when(playerRepository.findAllById(any())).thenReturn(List.of(player));
        when(contestEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixtureRepository.save(any(Fixture.class))).thenAnswer(invocation -> invocation.getArgument(0));

        scoringService.syncFixtureFantasyPoints(55L, true);

        assertThat(entry.getFantasyPoints()).isGreaterThan(BigDecimal.valueOf(50));
    }

    @Test
    void populateFantasyPointsForTeamPreviewReturnsPlayerWisePointsAfterStart() throws Exception {
        Fixture fixture = Fixture.builder()
                .id(55L)
                .externalFixtureId(90001L)
                .status("1st Innings")
                .startTime(LocalDateTime.now().minusHours(2))
                .deadlineTime(LocalDateTime.now().minusHours(2))
                .build();
        TeamResponse team = TeamResponse.builder()
                .teamId(301L)
                .fixtureId(55L)
                .teamName("Preview XI")
                .players(List.of(
                        TeamPlayerResponse.builder()
                                .playerId(101L)
                                .playerName("Virat Kohli")
                                .roleCode("BAT")
                                .teamSide("HOME")
                                .teamName("Royal Challengers Bengaluru")
                                .creditValue(BigDecimal.valueOf(9))
                                .isCaptain(true)
                                .isViceCaptain(false)
                                .isSubstitute(false)
                                .build()
                ))
                .substitutes(List.of())
                .build();
        Player player = Player.builder()
                .id(101L)
                .externalPlayerId(7001L)
                .playerName("Virat Kohli")
                .build();

        when(fixtureRepository.findById(55L)).thenReturn(Optional.of(fixture));
        when(sportMonksCricketClient.getFixtureWithFantasyScoringData(90001L)).thenReturn(
                new ObjectMapper().readTree("""
                        {
                          "data": {
                            "status": "1st Innings",
                            "type": "T20",
                            "lineup": [
                              {
                                "id": 7001,
                                "lineup": {
                                  "substitution": false
                                }
                              }
                            ],
                            "batting": [
                              {
                                "player_id": 7001,
                                "scoreboard": "S1",
                                "score": 30,
                                "ball": 20,
                                "four_x": 3,
                                "six_x": 1
                              }
                            ],
                            "bowling": [],
                            "balls": []
                          }
                        }
                        """)
        );
        when(playerRepository.findAllById(any())).thenReturn(List.of(player));

        TeamResponse response = scoringService.populateFantasyPointsForTeamPreview(55L, team);

        assertThat(response.getPlayers()).hasSize(1);
        assertThat(response.getPlayers().get(0).getFantasyPoints()).isNotNull();
        assertThat(response.getPlayers().get(0).getFantasyPoints()).isGreaterThan(BigDecimal.valueOf(90));
    }
}
