package com.friendsfantasy.fantasybackend.contest.service;

import com.friendsfantasy.fantasybackend.contest.dto.ContestEntryResponse;
import com.friendsfantasy.fantasybackend.contest.dto.JoinContestRequest;
import com.friendsfantasy.fantasybackend.contest.entity.Contest;
import com.friendsfantasy.fantasybackend.contest.entity.ContestEntry;
import com.friendsfantasy.fantasybackend.contest.entity.ContestPrize;
import com.friendsfantasy.fantasybackend.contest.repository.ContestEntryRepository;
import com.friendsfantasy.fantasybackend.contest.repository.ContestPrizeRepository;
import com.friendsfantasy.fantasybackend.contest.repository.ContestRepository;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.notification.service.NotificationService;
import com.friendsfantasy.fantasybackend.room.entity.Room;
import com.friendsfantasy.fantasybackend.room.entity.RoomMember;
import com.friendsfantasy.fantasybackend.room.repository.RoomMemberRepository;
import com.friendsfantasy.fantasybackend.room.repository.RoomRepository;
import com.friendsfantasy.fantasybackend.stats.service.UserStatsService;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeam;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamRepository;
import com.friendsfantasy.fantasybackend.team.service.TeamService;
import com.friendsfantasy.fantasybackend.wallet.entity.WalletTransaction;
import com.friendsfantasy.fantasybackend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContestEntryServiceTest {

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private ContestEntryRepository contestEntryRepository;

    @Mock
    private ContestPrizeRepository contestPrizeRepository;

    @Mock
    private FixtureRepository fixtureRepository;

    @Mock
    private UserMatchTeamRepository userMatchTeamRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private com.friendsfantasy.fantasybackend.auth.repository.UserRepository userRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private UserStatsService userStatsService;

    @Mock
    private TeamService teamService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CricketFantasyScoringService cricketFantasyScoringService;

    private ContestEntryService contestEntryService;

    @BeforeEach
    void setUp() {
        contestEntryService = new ContestEntryService(
                contestRepository,
                contestEntryRepository,
                contestPrizeRepository,
                fixtureRepository,
                userMatchTeamRepository,
                walletService,
                userRepository,
                roomRepository,
                roomMemberRepository,
                userStatsService,
                teamService,
                notificationService,
                cricketFantasyScoringService
        );
    }

    @Test
    void reserveCommunityContestSpotDebitsCreatorAndCreatesPlaceholderEntry() {
        Contest contest = communityContest(700L, 55L, 88L, Contest.Status.OPEN, 500, 0, 12);
        Fixture fixture = upcomingFixture(55L, 1L);
        Room room = activeRoom(88L, 1L);
        RoomMember membership = joinedMember(88L, 9L);
        WalletTransaction walletTxn = WalletTransaction.builder().id(901L).build();
        ContestEntry[] savedEntry = new ContestEntry[1];

        when(contestRepository.findById(700L)).thenReturn(Optional.of(contest));
        when(fixtureRepository.findById(55L)).thenReturn(Optional.of(fixture));
        when(roomRepository.findById(88L)).thenReturn(Optional.of(room));
        when(roomMemberRepository.findByRoomIdAndUserId(88L, 9L)).thenReturn(Optional.of(membership));
        when(contestEntryRepository.existsByContestIdAndUserId(700L, 9L)).thenReturn(false);
        when(walletService.debitForContestJoin(9L, 500, 700L, "Community contest creator reserve - Friends contest"))
                .thenReturn(walletTxn);
        when(contestEntryRepository.save(any(ContestEntry.class))).thenAnswer(invocation -> {
            ContestEntry entry = invocation.getArgument(0);
            entry.setId(3001L);
            savedEntry[0] = entry;
            return entry;
        });
        when(contestEntryRepository.findByContestIdOrderByJoinedAtAsc(700L))
                .thenAnswer(invocation -> savedEntry[0] == null ? List.of() : List.of(savedEntry[0]));
        when(contestRepository.save(any(Contest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContestEntryResponse response = contestEntryService.reserveCommunityContestSpot(9L, 700L);

        assertThat(response.getEntryId()).isEqualTo(3001L);
        assertThat(response.getContestId()).isEqualTo(700L);
        assertThat(response.getTeamId()).isNull();
        assertThat(response.getEntryFeePoints()).isEqualTo(500);

        verify(walletService).debitForContestJoin(9L, 500, 700L, "Community contest creator reserve - Friends contest");
        verify(userStatsService).recordContestJoin(9L, 500);
        verify(contestEntryRepository).save(argThat(entry ->
                entry != null
                        && entry.getUserMatchTeamId() == null
                        && entry.getWalletTransactionId().equals(901L)
                        && entry.getEntryFeePoints().equals(500)
        ));
        verify(contestRepository).save(argThat(savedContest ->
                savedContest != null
                        && savedContest.getSpotsFilled() == 1
                        && savedContest.getPrizePoolPoints() == 500
                        && savedContest.getFirstPrizePoints() == 375
                        && savedContest.getStatus() == Contest.Status.OPEN
        ));
    }

    @Test
    void joinContestUsesReservedCommunityEntryWithoutSecondDebit() {
        Contest contest = communityContest(700L, 55L, 88L, Contest.Status.FULL, 500, 12, 12);
        Fixture fixture = upcomingFixture(55L, 1L);
        Room room = activeRoom(88L, 1L);
        RoomMember membership = joinedMember(88L, 9L);
        UserMatchTeam team = UserMatchTeam.builder()
                .id(301L)
                .fixtureId(55L)
                .userId(9L)
                .teamName("Creator XI")
                .captainPlayerId(1L)
                .viceCaptainPlayerId(2L)
                .totalCredits(BigDecimal.ZERO)
                .build();
        ContestEntry reservedEntry = ContestEntry.builder()
                .id(4001L)
                .contestId(700L)
                .userId(9L)
                .roomId(88L)
                .walletTransactionId(901L)
                .entryFeePoints(500)
                .build();
        JoinContestRequest request = new JoinContestRequest();
        request.setTeamId(301L);

        when(contestRepository.findById(700L)).thenReturn(Optional.of(contest));
        when(fixtureRepository.findById(55L)).thenReturn(Optional.of(fixture));
        when(userMatchTeamRepository.findByIdAndUserId(301L, 9L)).thenReturn(Optional.of(team));
        when(userMatchTeamRepository.findById(301L)).thenReturn(Optional.of(team));
        when(contestEntryRepository.existsByContestIdAndUserMatchTeamId(700L, 301L)).thenReturn(false);
        when(roomRepository.findById(88L)).thenReturn(Optional.of(room));
        when(roomMemberRepository.findByRoomIdAndUserId(88L, 9L)).thenReturn(Optional.of(membership));
        when(contestEntryRepository.findFirstByContestIdAndUserIdAndUserMatchTeamIdIsNullOrderByJoinedAtAsc(700L, 9L))
                .thenReturn(Optional.of(reservedEntry));
        when(contestEntryRepository.save(any(ContestEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContestEntryResponse response = contestEntryService.joinContest(9L, 700L, request);

        assertThat(response.getEntryId()).isEqualTo(4001L);
        assertThat(response.getContestId()).isEqualTo(700L);
        assertThat(response.getTeamId()).isEqualTo(301L);
        assertThat(response.getTeamName()).isEqualTo("Creator XI");

        verify(contestEntryRepository).save(argThat(entry ->
                entry != null
                        && entry.getId().equals(4001L)
                        && entry.getUserMatchTeamId().equals(301L)
        ));
        verify(walletService, never()).debitForContestJoin(any(), any(), any(), any());
        verify(userStatsService, never()).recordContestJoin(any(), any());
    }

    @Test
    void getContestTeamViewReturnsTeamOnlyAfterFixtureStartForJoinedUser() {
        Contest contest = communityContest(700L, 55L, 88L, Contest.Status.LIVE, 500, 2, 12);
        Fixture fixture = startedFixture(55L, 1L);
        RoomMember membership = joinedMember(88L, 9L);
        ContestEntry targetEntry = ContestEntry.builder()
                .id(4002L)
                .contestId(700L)
                .userId(10L)
                .roomId(88L)
                .userMatchTeamId(301L)
                .walletTransactionId(902L)
                .entryFeePoints(500)
                .build();
        var teamResponse = com.friendsfantasy.fantasybackend.team.dto.TeamResponse.builder()
                .teamId(301L)
                .teamName("Opponent XI")
                .build();
        var enrichedTeamResponse = com.friendsfantasy.fantasybackend.team.dto.TeamResponse.builder()
                .teamId(301L)
                .teamName("Opponent XI")
                .build();

        when(contestRepository.findById(700L)).thenReturn(Optional.of(contest));
        when(fixtureRepository.findById(55L)).thenReturn(Optional.of(fixture));
        when(roomMemberRepository.findByRoomIdAndUserId(88L, 9L)).thenReturn(Optional.of(membership));
        when(contestEntryRepository.existsByContestIdAndUserId(700L, 9L)).thenReturn(true);
        when(contestEntryRepository.findByContestIdAndUserMatchTeamId(700L, 301L))
                .thenReturn(Optional.of(targetEntry));
        when(teamService.getTeamById(301L)).thenReturn(teamResponse);
        when(cricketFantasyScoringService.populateFantasyPointsForTeamPreview(55L, teamResponse))
                .thenReturn(enrichedTeamResponse);

        var response = contestEntryService.getContestTeamView(9L, 700L, 301L);

        assertThat(response.getTeamId()).isEqualTo(301L);
        assertThat(response.getTeamName()).isEqualTo("Opponent XI");
    }

    @Test
    void getContestTeamViewRejectsRequestsBeforeFixtureStart() {
        Contest contest = communityContest(700L, 55L, 88L, Contest.Status.OPEN, 500, 2, 12);
        Fixture fixture = upcomingFixture(55L, 1L);
        RoomMember membership = joinedMember(88L, 9L);

        when(contestRepository.findById(700L)).thenReturn(Optional.of(contest));
        when(fixtureRepository.findById(55L)).thenReturn(Optional.of(fixture));
        when(roomMemberRepository.findByRoomIdAndUserId(88L, 9L)).thenReturn(Optional.of(membership));
        when(contestEntryRepository.existsByContestIdAndUserId(700L, 9L)).thenReturn(true);

        assertThatThrownBy(() -> contestEntryService.getContestTeamView(9L, 700L, 301L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Participant teams become visible only after the match starts");
    }

    @Test
    void syncFixtureFantasyPointsSettlesFinishedPublicContestsInBackground() {
        Contest contest = publicContest(701L, 55L, Contest.Status.LIVE, 500, 2, 10);
        Fixture fixture = finishedFixture(55L, 1L);
        ContestEntry winner = ContestEntry.builder()
                .id(5001L)
                .contestId(701L)
                .userId(11L)
                .userMatchTeamId(301L)
                .entryFeePoints(500)
                .fantasyPoints(BigDecimal.valueOf(120))
                .status(ContestEntry.Status.JOINED)
                .joinedAt(LocalDateTime.now().minusMinutes(8))
                .build();
        ContestEntry runner = ContestEntry.builder()
                .id(5002L)
                .contestId(701L)
                .userId(12L)
                .userMatchTeamId(302L)
                .entryFeePoints(500)
                .fantasyPoints(BigDecimal.valueOf(92))
                .status(ContestEntry.Status.JOINED)
                .joinedAt(LocalDateTime.now().minusMinutes(4))
                .build();
        ContestPrize firstPrize = ContestPrize.builder()
                .contestId(701L)
                .rankFromNo(1)
                .rankToNo(1)
                .prizePoints(750)
                .build();

        when(fixtureRepository.findById(55L)).thenReturn(Optional.of(fixture));
        when(contestRepository.findByFixtureIdOrderByEntryFeePointsAscIdAsc(55L)).thenReturn(List.of(contest));
        when(contestEntryRepository.findByContestIdOrderByJoinedAtAsc(701L)).thenReturn(List.of(winner, runner));
        when(contestPrizeRepository.findByContestIdOrderByRankFromNoAsc(701L)).thenReturn(List.of(firstPrize));
        when(cricketFantasyScoringService.syncFixtureFantasyPoints(55L, false))
                .thenReturn(java.util.Map.of("fixtureId", 55L, "status", "SYNCED"));
        when(contestEntryRepository.save(any(ContestEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contestRepository.save(any(Contest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        contestEntryService.syncFixtureFantasyPoints(55L, false);

        assertThat(contest.getStatus()).isEqualTo(Contest.Status.COMPLETED);
        assertThat(winner.getStatus()).isEqualTo(ContestEntry.Status.SETTLED);
        assertThat(winner.getPrizePointsAwarded()).isEqualTo(750);
        assertThat(winner.getRankNo()).isEqualTo(1);
        assertThat(runner.getStatus()).isEqualTo(ContestEntry.Status.SETTLED);
        assertThat(runner.getPrizePointsAwarded()).isEqualTo(0);
        assertThat(runner.getRankNo()).isEqualTo(2);

        verify(walletService).creditContestWin(11L, 750, 701L, "Contest win - rank 1");
        verify(userStatsService).recordContestWin(11L, 750, 1);
    }

    private Contest communityContest(
            Long contestId,
            Long fixtureId,
            Long roomId,
            Contest.Status status,
            Integer entryFeePoints,
            Integer spotsFilled,
            Integer maxSpots
    ) {
        return Contest.builder()
                .id(contestId)
                .fixtureId(fixtureId)
                .roomId(roomId)
                .contestName("Friends contest")
                .contestType(Contest.ContestType.COMMUNITY)
                .entryFeePoints(entryFeePoints)
                .prizePoolPoints(0)
                .winnerCount(1)
                .maxSpots(maxSpots)
                .spotsFilled(spotsFilled)
                .joinConfirmRequired(false)
                .firstPrizePoints(0)
                .status(status)
                .createdByUserId(9L)
                .build();
    }

    private Contest publicContest(
            Long contestId,
            Long fixtureId,
            Contest.Status status,
            Integer entryFeePoints,
            Integer spotsFilled,
            Integer maxSpots
    ) {
        return Contest.builder()
                .id(contestId)
                .fixtureId(fixtureId)
                .contestName("Public contest")
                .contestType(Contest.ContestType.PUBLIC)
                .entryFeePoints(entryFeePoints)
                .prizePoolPoints(entryFeePoints * spotsFilled)
                .winnerCount(1)
                .maxSpots(maxSpots)
                .spotsFilled(spotsFilled)
                .joinConfirmRequired(false)
                .firstPrizePoints(750)
                .status(status)
                .createdByUserId(1L)
                .build();
    }

    private Fixture upcomingFixture(Long fixtureId, Long sportId) {
        return Fixture.builder()
                .id(fixtureId)
                .sportId(sportId)
                .title("Fixture")
                .status("NS")
                .startTime(LocalDateTime.now().plusHours(2))
                .deadlineTime(LocalDateTime.now().plusHours(2))
                .build();
    }

    private Fixture startedFixture(Long fixtureId, Long sportId) {
        return Fixture.builder()
                .id(fixtureId)
                .sportId(sportId)
                .title("Fixture")
                .status("LIVE")
                .startTime(LocalDateTime.now().minusMinutes(20))
                .deadlineTime(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    private Fixture finishedFixture(Long fixtureId, Long sportId) {
        return Fixture.builder()
                .id(fixtureId)
                .sportId(sportId)
                .title("Fixture")
                .status("Finished")
                .startTime(LocalDateTime.now().minusHours(4))
                .deadlineTime(LocalDateTime.now().minusHours(4))
                .build();
    }

    private Room activeRoom(Long roomId, Long sportId) {
        return Room.builder()
                .id(roomId)
                .sportId(sportId)
                .createdByUserId(9L)
                .roomName("Friends")
                .roomCode("ABC123")
                .isPrivate(true)
                .maxMembers(12)
                .status(Room.Status.ACTIVE)
                .build();
    }

    private RoomMember joinedMember(Long roomId, Long userId) {
        return RoomMember.builder()
                .roomId(roomId)
                .userId(userId)
                .role(RoomMember.Role.MEMBER)
                .status(RoomMember.Status.JOINED)
                .build();
    }
}
