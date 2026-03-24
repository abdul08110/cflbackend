package com.friendsfantasy.fantasybackend.contest.service;

import com.friendsfantasy.fantasybackend.auth.entity.User;
import com.friendsfantasy.fantasybackend.auth.repository.UserRepository;
import com.friendsfantasy.fantasybackend.contest.dto.*;
import com.friendsfantasy.fantasybackend.contest.entity.Contest;
import com.friendsfantasy.fantasybackend.contest.entity.ContestEntry;
import com.friendsfantasy.fantasybackend.contest.entity.ContestPrize;
import com.friendsfantasy.fantasybackend.contest.repository.ContestEntryRepository;
import com.friendsfantasy.fantasybackend.contest.repository.ContestPrizeRepository;
import com.friendsfantasy.fantasybackend.contest.repository.ContestRepository;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.notification.entity.Notification;
import com.friendsfantasy.fantasybackend.notification.service.NotificationService;
import com.friendsfantasy.fantasybackend.room.entity.Room;
import com.friendsfantasy.fantasybackend.room.entity.RoomMember;
import com.friendsfantasy.fantasybackend.room.repository.RoomMemberRepository;
import com.friendsfantasy.fantasybackend.room.repository.RoomRepository;
import com.friendsfantasy.fantasybackend.stats.entity.UserStats;
import com.friendsfantasy.fantasybackend.stats.service.UserStatsService;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeam;
import com.friendsfantasy.fantasybackend.team.dto.TeamResponse;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamRepository;
import com.friendsfantasy.fantasybackend.team.service.TeamService;
import com.friendsfantasy.fantasybackend.wallet.entity.WalletTransaction;
import com.friendsfantasy.fantasybackend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContestEntryService {

    private static final int COMMUNITY_PAYOUT_PERCENT = 75;

    private final ContestRepository contestRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final ContestPrizeRepository contestPrizeRepository;
    private final FixtureRepository fixtureRepository;
    private final UserMatchTeamRepository userMatchTeamRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserStatsService userStatsService;
    private final TeamService teamService;
    private final NotificationService notificationService;
    private final CricketFantasyScoringService cricketFantasyScoringService;

    @Transactional
    public ContestEntryResponse joinContest(Long userId, Long contestId, JoinContestRequest request) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (fixture.getDeadlineTime() == null || !fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Contest join is closed for this fixture");
        }

        UserMatchTeam team = userMatchTeamRepository.findByIdAndUserId(request.getTeamId(), userId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        if (!Objects.equals(team.getFixtureId(), contest.getFixtureId())) {
            throw new RuntimeException("Selected team does not belong to this contest fixture");
        }

        if (contestEntryRepository.existsByContestIdAndUserMatchTeamId(contestId, team.getId())) {
            throw new RuntimeException("This team is already joined in the contest");
        }

        Long roomId = request.getRoomId();
        ContestEntry reservedEntry = null;
        if (contest.getContestType() == Contest.ContestType.COMMUNITY) {
            roomId = validateCommunityContestAccess(userId, contest, fixture.getSportId());
            reservedEntry = contestEntryRepository
                    .findFirstByContestIdAndUserIdAndUserMatchTeamIdIsNullOrderByJoinedAtAsc(contestId, userId)
                    .orElse(null);
            if (reservedEntry == null) {
                validateCommunityJoinAllowed(contest);
            } else if (contest.getStatus() != Contest.Status.OPEN && contest.getStatus() != Contest.Status.FULL) {
                throw new RuntimeException("Contest is not open for team selection");
            }
        } else {
            if (contest.getStatus() != Contest.Status.OPEN) {
                throw new RuntimeException("Contest is not open for joining");
            }

            if (contest.getSpotsFilled() >= contest.getMaxSpots()) {
                contest.setStatus(Contest.Status.FULL);
                contestRepository.save(contest);
                throw new RuntimeException("Contest is full");
            }

            if (Boolean.TRUE.equals(contest.getJoinConfirmRequired())
                    && !Boolean.TRUE.equals(request.getConfirmJoin())) {
                throw new RuntimeException("Join confirmation is required for this contest");
            }
            validateOptionalRoom(userId, roomId, fixture.getSportId());
        }

        if (reservedEntry != null) {
            reservedEntry.setRoomId(roomId);
            reservedEntry.setUserMatchTeamId(team.getId());
            reservedEntry = contestEntryRepository.save(reservedEntry);
            return mapContestEntry(reservedEntry);
        }

        WalletTransaction walletTxn = walletService.debitForContestJoin(
                userId,
                contest.getEntryFeePoints(),
                contestId,
                "Contest join - " + contest.getContestName()
        );

        ContestEntry entry = ContestEntry.builder()
                .contestId(contestId)
                .userId(userId)
                .roomId(roomId)
                .userMatchTeamId(team.getId())
                .walletTransactionId(walletTxn.getId())
                .entryFeePoints(contest.getEntryFeePoints())
                .build();

        entry = contestEntryRepository.save(entry);

        if (contest.getContestType() == Contest.ContestType.COMMUNITY) {
            updateCommunityPrizeState(contest);
        } else {
            contest.setSpotsFilled(contest.getSpotsFilled() + 1);
            if (contest.getSpotsFilled() >= contest.getMaxSpots()) {
                contest.setStatus(Contest.Status.FULL);
            }
            contestRepository.save(contest);
        }

        userStatsService.recordContestJoin(userId, contest.getEntryFeePoints());

        return mapContestEntry(entry);
    }

    @Transactional
    public ContestEntryResponse reserveCommunityContestSpot(Long userId, Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        if (contest.getContestType() != Contest.ContestType.COMMUNITY) {
            throw new RuntimeException("Only community contests support reserved creator spots");
        }

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        Long roomId = validateCommunityContestMembership(userId, contest, fixture.getSportId());
        if (contestEntryRepository.existsByContestIdAndUserId(contestId, userId)) {
            throw new RuntimeException("User has already joined this contest");
        }

        WalletTransaction walletTxn = walletService.debitForContestJoin(
                userId,
                contest.getEntryFeePoints(),
                contestId,
                "Community contest creator reserve - " + contest.getContestName()
        );

        ContestEntry entry = ContestEntry.builder()
                .contestId(contestId)
                .userId(userId)
                .roomId(roomId)
                .userMatchTeamId(null)
                .walletTransactionId(walletTxn.getId())
                .entryFeePoints(contest.getEntryFeePoints())
                .build();

        entry = contestEntryRepository.save(entry);
        updateCommunityPrizeState(contest);
        userStatsService.recordContestJoin(userId, contest.getEntryFeePoints());

        return mapContestEntry(entry);
    }

    @Transactional
    public ContestEntryResponse createCommunityEntry(Long userId, Long roomId) {
        throw new RuntimeException("Join a specific community contest instead of joining the community itself");
    }

    @Transactional
    public ContestEntryResponse attachTeamToCommunityEntry(Long userId, Long roomId, Long teamId) {
        throw new RuntimeException("Join a specific community contest with the team you want to use");
    }

    @Transactional
    public void syncCommunityContestState(Long roomId) {
        List<Contest> contests = contestRepository.findByRoomIdOrderByCreatedAtDescIdDesc(roomId);
        for (Contest contest : contests) {
            if (contest.getContestType() == Contest.ContestType.COMMUNITY) {
                try {
                    syncCommunityContestState(contest);
                } catch (Exception ex) {
                    log.warn("Community contest sync failed for contest {}", contest.getId(), ex);
                }
            }
        }
    }

    @Transactional
    public Map<String, Object> syncDueCommunityContests() {
        List<Contest> contests = contestRepository.findByContestTypeAndStatusInOrderByCreatedAtDescIdDesc(
                Contest.ContestType.COMMUNITY,
                List.of(Contest.Status.OPEN, Contest.Status.FULL, Contest.Status.LIVE)
        );

        int changed = 0;
        int cancelled = 0;
        int live = 0;

        for (Contest contest : contests) {
            Contest.Status beforeStatus = contest.getStatus();
            try {
                syncCommunityContestState(contest);
            } catch (Exception ex) {
                log.warn("Scheduled community contest sync failed for contest {}", contest.getId(), ex);
                continue;
            }

            Contest.Status afterStatus = contestRepository.findById(contest.getId())
                    .map(Contest::getStatus)
                    .orElse(beforeStatus);

            if (afterStatus != beforeStatus) {
                changed++;
            }
            if (afterStatus == Contest.Status.CANCELLED) {
                cancelled++;
            }
            if (afterStatus == Contest.Status.LIVE) {
                live++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("scanned", contests.size());
        result.put("statusChanged", changed);
        result.put("cancelled", cancelled);
        result.put("live", live);
        return result;
    }

    @Transactional
    public void syncCancelledFixtureCommunityContests(Long fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null || !isFixtureCancelledStatus(fixture.getStatus())) {
            return;
        }

        List<Contest> contests = contestRepository.findByFixtureIdAndContestTypeAndStatusIn(
                fixtureId,
                Contest.ContestType.COMMUNITY,
                List.of(Contest.Status.OPEN, Contest.Status.FULL, Contest.Status.LIVE)
        );

        for (Contest contest : contests) {
            cancelCommunityContestForCancelledFixture(contest, fixture);
        }
    }

    @Transactional
    public void cancelCommunityContestsForRoomClosure(Long roomId, String roomName) {
        List<Contest> contests = contestRepository.findByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
                roomId,
                List.of(Contest.Status.OPEN, Contest.Status.FULL, Contest.Status.LIVE)
        );

        for (Contest contest : contests) {
            cancelCommunityContestForRoomClosure(contest, roomName);
        }
    }

    public ContestEntryResponse getCommunityEntry(Long userId, Long roomId) {
        throw new RuntimeException("Open a specific community contest to view your entries");
    }

    @Transactional
    public Map<String, Object> syncFixtureFantasyPoints(Long fixtureId, boolean force) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (!fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            lockFixtureContestTeamsIfNeeded(fixtureId);
            markPublicContestsLiveIfStarted(fixtureId);
        }

        Map<String, Object> result = cricketFantasyScoringService.syncFixtureFantasyPoints(fixtureId, force);
        finalizeFixtureContestsIfNeeded(fixtureId);
        return result;
    }

    @Transactional
    public ContestEntryResponse updateEntryPoints(Long contestId, Long entryId, UpdateEntryPointsRequest request) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        if (contest.getStatus() == Contest.Status.COMPLETED) {
            throw new RuntimeException("Contest already completed");
        }

        ContestEntry entry = contestEntryRepository.findByIdAndContestId(entryId, contestId)
                .orElseThrow(() -> new RuntimeException("Contest entry not found"));

        entry.setFantasyPoints(request.getFantasyPoints());
        contestEntryRepository.save(entry);

        return mapContestEntry(entry);
    }

    @Transactional
    public Map<String, Object> settleContest(Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        if (contest.getStatus() == Contest.Status.COMPLETED) {
            throw new RuntimeException("Contest already settled");
        }

        if (contest.getContestType() == Contest.ContestType.COMMUNITY) {
            return settleCommunityContest(contest, true);
        }

        return settlePublicContest(contest, true);
    }

    private Map<String, Object> settlePublicContest(Contest contest, boolean syncBeforeSettle) {
        if (contest.getStatus() == Contest.Status.COMPLETED) {
            return buildSettlementResult(contest.getId(), 0, 0, 0, contest.getStatus().name());
        }

        if (syncBeforeSettle) {
            syncFixtureFantasyPointsIfNeeded(contest, true);
            contest = contestRepository.findById(contest.getId())
                    .orElseThrow(() -> new RuntimeException("Contest not found"));
        }

        List<ContestEntry> entries = new ArrayList<>(contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contest.getId()));
        if (entries.isEmpty()) {
            throw new RuntimeException("No contest entries found");
        }

        entries.sort(Comparator
                .comparing(ContestEntry::getFantasyPoints, Comparator.nullsLast(BigDecimal::compareTo)).reversed()
                .thenComparing(ContestEntry::getJoinedAt, Comparator.nullsLast(LocalDateTime::compareTo)));

        List<ContestPrize> prizes = contestPrizeRepository.findByContestIdOrderByRankFromNoAsc(contest.getId());

        int rank = 1;
        int winnersCredited = 0;
        int totalPrizeCredited = 0;

        for (ContestEntry entry : entries) {
            entry.setRankNo(rank);

            ContestPrize matchedPrize = findPrizeForRank(prizes, rank);

            if (matchedPrize != null) {
                int prizePoints = matchedPrize.getPrizePoints();
                entry.setPrizePointsAwarded(prizePoints);
                entry.setStatus(ContestEntry.Status.SETTLED);
                entry.setSettledAt(LocalDateTime.now());

                walletService.creditContestWin(
                        entry.getUserId(),
                        prizePoints,
                        contest.getId(),
                        "Contest win - rank " + rank
                );

                userStatsService.recordContestWin(entry.getUserId(), prizePoints, rank);

                winnersCredited++;
                totalPrizeCredited += prizePoints;
            } else {
                entry.setPrizePointsAwarded(0);
                entry.setStatus(ContestEntry.Status.SETTLED);
                entry.setSettledAt(LocalDateTime.now());
            }

            contestEntryRepository.save(entry);
            rank++;
        }

        contest.setStatus(Contest.Status.COMPLETED);
        contestRepository.save(contest);

        Map<String, Object> result = new HashMap<>();
        result.put("contestId", contest.getId());
        result.put("totalEntries", entries.size());
        result.put("winnersCredited", winnersCredited);
        result.put("totalPrizeCredited", totalPrizeCredited);
        result.put("contestStatus", contest.getStatus().name());

        return result;
    }

    public List<ContestEntryResponse> getMyEntries(Long userId, Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));
        try {
            syncFixtureFantasyPointsIfNeeded(contest, false);
        } catch (RuntimeException ex) {
            log.warn(
                    "Skipping contest entry sync for contest {} due to sync issue: {}",
                    contestId,
                    ex.getMessage()
            );
        }

        List<ContestEntry> entries = contestEntryRepository.findByContestIdAndUserIdOrderByJoinedAtAsc(contestId, userId);
        List<ContestEntryResponse> response = new ArrayList<>();

        for (ContestEntry entry : entries) {
            response.add(mapContestEntry(entry));
        }

        return response;
    }

    public List<ContestEntryResponse> getMyContestHistory(Long userId) {
        List<ContestEntry> entries = contestEntryRepository.findByUserIdOrderByJoinedAtDesc(userId);
        List<ContestEntryResponse> response = new ArrayList<>();
        Set<Long> syncedFixtureIds = new HashSet<>();

        for (ContestEntry entry : entries) {
            contestRepository.findById(entry.getContestId()).ifPresent(contest -> {
                if (syncedFixtureIds.add(contest.getFixtureId())) {
                    try {
                        syncFixtureFantasyPointsIfNeeded(contest, false);
                    } catch (RuntimeException ex) {
                        log.warn(
                                "Skipping contest history sync for contest {} due to sync issue: {}",
                                contest.getId(),
                                ex.getMessage()
                        );
                    }
                }
            });
            response.add(mapContestEntry(entry));
        }

        return response;
    }

    public UserStatsResponse getMyStats(Long userId) {
        UserStats stats = userStatsService.getStats(userId);

        return UserStatsResponse.builder()
                .userId(userId)
                .totalContestsJoined(stats.getTotalContestsJoined())
                .totalContestsWon(stats.getTotalContestsWon())
                .totalPointsWon(stats.getTotalPointsWon())
                .totalPointsSpent(stats.getTotalPointsSpent())
                .totalCommunitiesCreated(stats.getTotalRoomsCreated())
                .winRate(stats.getWinRate())
                .bestRank(stats.getBestRank())
                .build();
    }

    public List<LeaderboardEntryResponse> getLeaderboard(Long contestId, Long viewerUserId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));
        ensureContestViewerAllowed(contest, viewerUserId);
        try {
            syncFixtureFantasyPointsIfNeeded(contest, false);
        } catch (RuntimeException ex) {
            log.warn(
                    "Skipping live leaderboard sync for contest {} due to sync issue: {}",
                    contestId,
                    ex.getMessage()
            );
        }

        List<ContestEntry> entries = new ArrayList<>(contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contestId));

        boolean hasFinalRanks = entries.stream().anyMatch(e -> e.getRankNo() != null);

        if (hasFinalRanks) {
            entries.sort(Comparator.comparing(
                    ContestEntry::getRankNo,
                    Comparator.nullsLast(Integer::compareTo)
            ));
        } else {
            entries.sort(Comparator
                    .comparing(ContestEntry::getFantasyPoints, Comparator.nullsLast(BigDecimal::compareTo)).reversed()
                    .thenComparing(ContestEntry::getJoinedAt, Comparator.nullsLast(LocalDateTime::compareTo)));
        }

        List<LeaderboardEntryResponse> response = new ArrayList<>();

        int position = 1;
        for (ContestEntry entry : entries) {
            User user = userRepository.findById(entry.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            UserMatchTeam team = null;
            if (entry.getUserMatchTeamId() != null) {
                team = userMatchTeamRepository.findById(entry.getUserMatchTeamId())
                        .orElseThrow(() -> new RuntimeException("Team not found"));
            }

            response.add(LeaderboardEntryResponse.builder()
                    .position(position++)
                    .entryId(entry.getId())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .teamId(team != null ? team.getId() : null)
                    .teamName(team != null ? team.getTeamName() : null)
                    .fantasyPoints(entry.getFantasyPoints())
                    .rankNo(entry.getRankNo())
                    .prizePointsAwarded(entry.getPrizePointsAwarded())
                    .status(entry.getStatus().name())
                    .build());
        }

        return response;
    }

    public TeamResponse getContestTeamView(Long viewerUserId, Long contestId, Long teamId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));
        ensureContestViewerAllowed(contest, viewerUserId);

        if (!contestEntryRepository.existsByContestIdAndUserId(contestId, viewerUserId)) {
            throw new RuntimeException("Join this contest to view participant teams");
        }

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (!hasFixtureStarted(fixture)) {
            throw new RuntimeException("Participant teams become visible only after the match starts");
        }

        contestEntryRepository.findByContestIdAndUserMatchTeamId(contestId, teamId)
                .orElseThrow(() -> new RuntimeException("This team is not part of the contest"));

        TeamResponse team = teamService.getTeamById(teamId);
        try {
            return cricketFantasyScoringService.populateFantasyPointsForTeamPreview(fixture.getId(), team);
        } catch (RuntimeException ex) {
            log.warn("Unable to enrich contest team preview with fantasy points for team {}", teamId, ex);
            return team;
        }
    }

    private ContestPrize findPrizeForRank(List<ContestPrize> prizes, int rank) {
        for (ContestPrize prize : prizes) {
            if (rank >= prize.getRankFromNo() && rank <= prize.getRankToNo()) {
                return prize;
            }
        }
        return null;
    }

    private boolean hasFixtureStarted(Fixture fixture) {
        LocalDateTime now = LocalDateTime.now();
        if (fixture.getDeadlineTime() != null) {
            return !fixture.getDeadlineTime().isAfter(now);
        }
        if (fixture.getStartTime() != null) {
            return !fixture.getStartTime().isAfter(now);
        }
        return false;
    }

    private void validateOptionalRoom(Long userId, Long roomId, Long fixtureSportId) {
        if (roomId == null) {
            return;
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Community not found"));

        if (!Objects.equals(room.getSportId(), fixtureSportId)) {
            throw new RuntimeException("Community sport does not match fixture sport");
        }

        roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .filter(m -> m.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException("You are not a member of the selected community"));
    }

    private ContestEntryResponse mapContestEntry(ContestEntry entry) {
        UserMatchTeam team = null;
        if (entry.getUserMatchTeamId() != null) {
            team = userMatchTeamRepository.findById(entry.getUserMatchTeamId())
                    .orElseThrow(() -> new RuntimeException("Team not found"));
        }

        return ContestEntryResponse.builder()
                .entryId(entry.getId())
                .contestId(entry.getContestId())
                .teamId(team != null ? team.getId() : null)
                .teamName(team != null ? team.getTeamName() : null)
                .communityId(entry.getRoomId())
                .entryFeePoints(entry.getEntryFeePoints())
                .fantasyPoints(entry.getFantasyPoints())
                .rankNo(entry.getRankNo())
                .prizePointsAwarded(entry.getPrizePointsAwarded())
                .status(entry.getStatus().name())
                .joinedAt(entry.getJoinedAt())
                .build();
    }

    private Long validateCommunityContestMembership(Long userId, Contest contest, Long fixtureSportId) {
        Long roomId = validateCommunityContestAccess(userId, contest, fixtureSportId);
        validateCommunityJoinAllowed(contest);
        return roomId;
    }

    private Long validateCommunityContestAccess(Long userId, Contest contest, Long fixtureSportId) {
        Long roomId = contest.getRoomId();
        if (roomId == null) {
            throw new RuntimeException("Community contest is missing its community");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Community not found"));

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new RuntimeException("Community is not active");
        }

        if (!Objects.equals(room.getSportId(), fixtureSportId)) {
            throw new RuntimeException("Community sport does not match fixture sport");
        }

        roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .filter(member -> member.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException("You are not a member of this community"));
        return roomId;
    }

    private void validateCommunityJoinAllowed(Contest contest) {
        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (!fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Community join is closed for this fixture");
        }

        if (contest.getStatus() == Contest.Status.CANCELLED || contest.getStatus() == Contest.Status.COMPLETED) {
            throw new RuntimeException("This community contest is no longer active");
        }

        if (contest.getSpotsFilled() >= contest.getMaxSpots()) {
            contest.setStatus(Contest.Status.FULL);
            contestRepository.save(contest);
            throw new RuntimeException("Community is full");
        }
    }

    private void updateCommunityPrizeState(Contest contest) {
        int activeEntries = contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contest.getId()).size();
        int prizePoolPoints = activeEntries * contest.getEntryFeePoints();
        List<ContestPrize> prizes = buildCommunityPrizes(contest.getId(), prizePoolPoints, contest.getWinnerCount());
        int firstPrizePoints = prizes.isEmpty() ? 0 : prizes.getFirst().getPrizePoints();

        contest.setSpotsFilled(activeEntries);
        contest.setPrizePoolPoints(prizePoolPoints);
        contest.setFirstPrizePoints(firstPrizePoints);
        contest.setJoinConfirmRequired(false);
        if (contest.getStatus() != Contest.Status.CANCELLED && contest.getStatus() != Contest.Status.COMPLETED) {
            contest.setStatus(activeEntries >= contest.getMaxSpots() ? Contest.Status.FULL : Contest.Status.OPEN);
        }
        contestRepository.save(contest);

        contestPrizeRepository.deleteByContestId(contest.getId());
        if (!prizes.isEmpty()) {
            contestPrizeRepository.saveAll(prizes);
        }
    }

    private void syncCommunityContestState(Contest contest) {
        if (contest.getContestType() != Contest.ContestType.COMMUNITY) {
            return;
        }

        if (contest.getStatus() == Contest.Status.COMPLETED || contest.getStatus() == Contest.Status.CANCELLED) {
            return;
        }

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (isFixtureCancelledStatus(fixture.getStatus())) {
            cancelCommunityContestForCancelledFixture(contest, fixture);
            return;
        }

        if (fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            return;
        }

        syncFixtureFantasyPointsIfNeeded(contest, false);

        List<ContestEntry> entries = new ArrayList<>(contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contest.getId()));

        if (entries.size() <= 1) {
            LocalDateTime now = LocalDateTime.now();
            List<Notification> refundNotifications = new ArrayList<>();
            Set<Long> notifiedUserIds = new HashSet<>();
            for (ContestEntry entry : entries) {
                if (entry.getStatus() == ContestEntry.Status.JOINED) {
                    entry.setStatus(ContestEntry.Status.REFUNDED);
                    entry.setPrizePointsAwarded(0);
                    entry.setSettledAt(now);
                    contestEntryRepository.save(entry);

                    walletService.creditRefund(
                            entry.getUserId(),
                            entry.getEntryFeePoints(),
                            contest.getId(),
                            "Community contest refund - not enough members joined"
                    );

                    if (notifiedUserIds.add(entry.getUserId())) {
                        refundNotifications.add(Notification.builder()
                                .userId(entry.getUserId())
                                .type("COMMUNITY_CONTEST_CANCELLED")
                                .title("Contest Cancelled")
                                .body(contest.getContestName() + " was cancelled and your points were refunded.")
                                .payloadJson("{\"communityId\":" + contest.getRoomId()
                                        + ",\"contestId\":" + contest.getId()
                                        + ",\"reason\":\"NOT_ENOUGH_MEMBERS\"}")
                                .isRead(false)
                                .build());
                    }
                }
            }

            contest.setPrizePoolPoints(0);
            contest.setFirstPrizePoints(0);
            contest.setStatus(Contest.Status.CANCELLED);
            contestRepository.save(contest);
            notificationService.createNotifications(refundNotifications);
            return;
        }

        if (contest.getStatus() == Contest.Status.OPEN || contest.getStatus() == Contest.Status.FULL) {
            contest.setStatus(Contest.Status.LIVE);
            contestRepository.save(contest);
        }
    }

    private void cancelCommunityContestForCancelledFixture(Contest contest, Fixture fixture) {
        if (contest.getStatus() == Contest.Status.CANCELLED || contest.getStatus() == Contest.Status.COMPLETED) {
            return;
        }

        List<ContestEntry> entries = new ArrayList<>(
                contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contest.getId())
        );
        LocalDateTime now = LocalDateTime.now();

        for (ContestEntry entry : entries) {
            entry.setRankNo(null);
            entry.setPrizePointsAwarded(0);

            if (entry.getStatus() == ContestEntry.Status.JOINED) {
                entry.setStatus(ContestEntry.Status.REFUNDED);
                entry.setSettledAt(now);
                walletService.creditRefund(
                        entry.getUserId(),
                        entry.getEntryFeePoints(),
                        contest.getId(),
                        "Community refund - " + fixture.getTitle() + " was cancelled"
                );
            } else if (entry.getSettledAt() == null) {
                entry.setSettledAt(now);
            }

            contestEntryRepository.save(entry);
        }

        contest.setPrizePoolPoints(0);
        contest.setFirstPrizePoints(0);
        contest.setStatus(Contest.Status.CANCELLED);
        contestRepository.save(contest);

        sendCommunityCancelledNotifications(contest, fixture, entries);
    }

    private void cancelCommunityContestForRoomClosure(Contest contest, String roomName) {
        if (contest.getStatus() == Contest.Status.CANCELLED || contest.getStatus() == Contest.Status.COMPLETED) {
            return;
        }

        List<ContestEntry> entries = new ArrayList<>(contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contest.getId()));
        LocalDateTime now = LocalDateTime.now();

        for (ContestEntry entry : entries) {
            entry.setRankNo(null);
            entry.setPrizePointsAwarded(0);

            if (entry.getStatus() == ContestEntry.Status.JOINED) {
                entry.setStatus(ContestEntry.Status.REFUNDED);
                entry.setSettledAt(now);
                walletService.creditRefund(
                        entry.getUserId(),
                        entry.getEntryFeePoints(),
                        contest.getId(),
                        "Community contest refund - " + roomName + " was deleted"
                );
            } else if (entry.getSettledAt() == null) {
                entry.setSettledAt(now);
            }

            contestEntryRepository.save(entry);
        }

        contest.setPrizePoolPoints(0);
        contest.setFirstPrizePoints(0);
        contest.setStatus(Contest.Status.CANCELLED);
        contestRepository.save(contest);

        if (!entries.isEmpty()) {
            List<Notification> notifications = new ArrayList<>();
            Set<Long> notifiedUserIds = new HashSet<>();
            String payload = "{\"communityId\":" + contest.getRoomId()
                    + ",\"contestId\":" + contest.getId()
                    + ",\"reason\":\"COMMUNITY_DELETED\"}";

            for (ContestEntry entry : entries) {
                if (!notifiedUserIds.add(entry.getUserId())) {
                    continue;
                }

                notifications.add(Notification.builder()
                        .userId(entry.getUserId())
                        .type("COMMUNITY_CONTEST_CANCELLED")
                        .title("Community Deleted")
                        .body("A contest in " + roomName + " was cancelled and your points were refunded.")
                        .payloadJson(payload)
                        .isRead(false)
                        .build());
            }

            notificationService.createNotifications(notifications);
        }
    }

    private void sendCommunityCancelledNotifications(
            Contest contest,
            Fixture fixture,
            List<ContestEntry> entries
    ) {
        if (contest.getRoomId() == null || entries.isEmpty()) {
            return;
        }

        String payload = "{\"communityId\":" + contest.getRoomId()
                + ",\"contestId\":" + contest.getId()
                + ",\"fixtureId\":" + contest.getFixtureId()
                + ",\"reason\":\"MATCH_CANCELLED\"}";
        List<Notification> notifications = new ArrayList<>();
        Set<Long> notifiedUserIds = new HashSet<>();

        for (ContestEntry entry : entries) {
            if (!notifiedUserIds.add(entry.getUserId())) {
                continue;
            }

            notifications.add(Notification.builder()
                    .userId(entry.getUserId())
                    .type("COMMUNITY_CANCELLED")
                    .title("Match Cancelled")
                    .body(fixture.getTitle() + " was cancelled. Your community points were refunded.")
                    .payloadJson(payload)
                    .isRead(false)
                    .build());
        }

        notificationService.createNotifications(notifications);
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

    private void ensureContestViewerAllowed(Contest contest, Long viewerUserId) {
        if (contest.getContestType() != Contest.ContestType.COMMUNITY) {
            return;
        }

        Long roomId = contest.getRoomId();
        if (roomId == null) {
            throw new RuntimeException("Community contest is missing its community");
        }

        roomMemberRepository.findByRoomIdAndUserId(roomId, viewerUserId)
                .filter(member -> member.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException("You are not a member of this community"));
    }

    private Map<String, Object> settleCommunityContest(Contest contest) {
        return settleCommunityContest(contest, true);
    }

    private Map<String, Object> settleCommunityContest(Contest contest, boolean syncBeforeSettle) {
        syncCommunityContestState(contest);
        contest = contestRepository.findById(contest.getId())
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        if (contest.getStatus() == Contest.Status.CANCELLED) {
            return buildSettlementResult(contest.getId(), 1, 0, 0, contest.getStatus().name());
        }

        if (contest.getStatus() == Contest.Status.COMPLETED) {
            return buildSettlementResult(contest.getId(), 0, 0, 0, contest.getStatus().name());
        }

        if (syncBeforeSettle) {
            syncFixtureFantasyPointsIfNeeded(contest, true);
            contest = contestRepository.findById(contest.getId())
                    .orElseThrow(() -> new RuntimeException("Contest not found"));

            if (contest.getStatus() == Contest.Status.CANCELLED
                    || contest.getStatus() == Contest.Status.COMPLETED) {
                return buildSettlementResult(contest.getId(), 0, 0, 0, contest.getStatus().name());
            }
        }

        List<ContestEntry> entries = new ArrayList<>(contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contest.getId()));
        if (entries.isEmpty()) {
            throw new RuntimeException("No contest entries found");
        }

        int totalCollected = entries.size() * contest.getEntryFeePoints();
        List<ContestPrize> prizes = contestPrizeRepository.findByContestIdOrderByRankFromNoAsc(contest.getId());
        if (prizes.isEmpty()) {
            prizes = buildCommunityPrizes(contest.getId(), totalCollected, contest.getWinnerCount());
        }
        int firstPrizePoints = prizes.isEmpty() ? 0 : prizes.getFirst().getPrizePoints();

        List<ContestEntry> eligibleEntries = entries.stream()
                .filter(entry -> entry.getUserMatchTeamId() != null)
                .toList();

        if (eligibleEntries.isEmpty()) {
            for (ContestEntry entry : entries) {
                entry.setRankNo(null);
                entry.setPrizePointsAwarded(0);
                entry.setStatus(ContestEntry.Status.SETTLED);
                entry.setSettledAt(LocalDateTime.now());
                contestEntryRepository.save(entry);
            }

            contest.setPrizePoolPoints(totalCollected);
            contest.setFirstPrizePoints(0);
            contest.setStatus(Contest.Status.COMPLETED);
            contestRepository.save(contest);
            return buildSettlementResult(contest.getId(), entries.size(), 0, 0, contest.getStatus().name());
        }

        List<ContestEntry> rankedEligibleEntries = new ArrayList<>(eligibleEntries);
        rankedEligibleEntries.sort(Comparator
                .comparing(ContestEntry::getFantasyPoints, Comparator.nullsLast(BigDecimal::compareTo)).reversed()
                .thenComparing(ContestEntry::getJoinedAt, Comparator.nullsLast(LocalDateTime::compareTo)));

        int rank = 1;
        int winnersCredited = 0;
        int totalPrizeCredited = 0;
        Set<Long> rankedEntryIds = new HashSet<>();

        for (ContestEntry entry : rankedEligibleEntries) {
            rankedEntryIds.add(entry.getId());
            entry.setRankNo(rank);

            ContestPrize matchedPrize = findPrizeForRank(prizes, rank);
            if (matchedPrize != null && matchedPrize.getPrizePoints() > 0) {
                int prizePoints = matchedPrize.getPrizePoints();
                entry.setPrizePointsAwarded(prizePoints);
                walletService.creditContestWin(
                        entry.getUserId(),
                        prizePoints,
                        contest.getId(),
                        "Community win - rank " + rank
                );
                userStatsService.recordContestWin(entry.getUserId(), prizePoints, rank);
                winnersCredited++;
                totalPrizeCredited += prizePoints;
            } else {
                entry.setPrizePointsAwarded(0);
            }

            entry.setStatus(ContestEntry.Status.SETTLED);
            entry.setSettledAt(LocalDateTime.now());
            contestEntryRepository.save(entry);
            rank++;
        }

        for (ContestEntry entry : entries) {
            if (rankedEntryIds.contains(entry.getId())) {
                continue;
            }

            entry.setRankNo(null);
            entry.setPrizePointsAwarded(0);
            entry.setStatus(ContestEntry.Status.SETTLED);
            entry.setSettledAt(LocalDateTime.now());
            contestEntryRepository.save(entry);
        }

        contest.setPrizePoolPoints(totalCollected);
        contest.setFirstPrizePoints(firstPrizePoints);
        contest.setStatus(Contest.Status.COMPLETED);
        contestRepository.save(contest);

        return buildSettlementResult(
                contest.getId(),
                entries.size(),
                winnersCredited,
                totalPrizeCredited,
                contest.getStatus().name()
        );
    }

    private List<ContestPrize> buildCommunityPrizes(Long contestId, int totalCollected, Integer winnerCount) {
        int normalizedWinnerCount = winnerCount == null || winnerCount < 1 ? 1 : Math.min(winnerCount, 3);
        int payoutPoints = calculateCommunityWinnerPayout(totalCollected);
        List<Integer> percentages = switch (normalizedWinnerCount) {
            case 2 -> List.of(60, 40);
            case 3 -> List.of(50, 30, 20);
            default -> List.of(100);
        };
        List<Integer> prizePoints = splitPointsByPercentages(payoutPoints, percentages);

        List<ContestPrize> prizes = new ArrayList<>();
        for (int index = 0; index < prizePoints.size(); index++) {
            prizes.add(ContestPrize.builder()
                    .contestId(contestId)
                    .rankFromNo(index + 1)
                    .rankToNo(index + 1)
                    .prizePoints(prizePoints.get(index))
                    .build());
        }
        return prizes;
    }

    private List<Integer> splitPointsByPercentages(int totalPoints, List<Integer> percentages) {
        List<Integer> split = new ArrayList<>();
        int allocated = 0;

        for (Integer percentage : percentages) {
            int points = totalPoints * percentage / 100;
            split.add(points);
            allocated += points;
        }

        int remainder = totalPoints - allocated;
        int index = 0;
        while (remainder > 0 && !split.isEmpty()) {
            split.set(index, split.get(index) + 1);
            remainder--;
            index = (index + 1) % split.size();
        }

        return split;
    }

    private int calculateCommunityWinnerPayout(int prizePoolPoints) {
        return prizePoolPoints * COMMUNITY_PAYOUT_PERCENT / 100;
    }

    private void lockContestTeamsIfNeeded(Contest contest) {
        Fixture fixture = fixtureRepository.findById(contest.getFixtureId()).orElse(null);
        if (fixture == null || fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            return;
        }

        for (ContestEntry entry : contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contest.getId())) {
            if (entry.getUserMatchTeamId() != null) {
                teamService.lockAndApplyAutoSubstitutesIfNeeded(entry.getUserMatchTeamId());
            }
        }
    }

    private void lockFixtureContestTeamsIfNeeded(Long fixtureId) {
        for (Contest contest : contestRepository.findByFixtureIdOrderByEntryFeePointsAscIdAsc(fixtureId)) {
            lockContestTeamsIfNeeded(contest);
        }
    }

    private void markPublicContestsLiveIfStarted(Long fixtureId) {
        List<Contest> contests = contestRepository.findByFixtureIdOrderByEntryFeePointsAscIdAsc(fixtureId);
        List<Contest> updatable = new ArrayList<>();

        for (Contest contest : contests) {
            if (contest.getContestType() != Contest.ContestType.PUBLIC) {
                continue;
            }

            if (contest.getStatus() == Contest.Status.OPEN || contest.getStatus() == Contest.Status.FULL) {
                contest.setStatus(Contest.Status.LIVE);
                updatable.add(contest);
            }
        }

        if (!updatable.isEmpty()) {
            contestRepository.saveAll(updatable);
        }
    }

    private void finalizeFixtureContestsIfNeeded(Long fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) {
            return;
        }

        if (isFixtureCancelledStatus(fixture.getStatus())) {
            syncCancelledFixtureCommunityContests(fixtureId);
            return;
        }

        if (!isFixtureFinishedStatus(fixture.getStatus())) {
            return;
        }

        for (Contest contest : contestRepository.findByFixtureIdOrderByEntryFeePointsAscIdAsc(fixtureId)) {
            if (contest.getStatus() == Contest.Status.CANCELLED || contest.getStatus() == Contest.Status.COMPLETED) {
                continue;
            }

            if (contest.getContestType() == Contest.ContestType.COMMUNITY) {
                settleCommunityContest(contest, false);
            } else {
                settlePublicContest(contest, false);
            }
        }
    }

    private void syncFixtureFantasyPointsIfNeeded(Contest contest, boolean force) {
        if (contest.getStatus() == Contest.Status.CANCELLED || contest.getStatus() == Contest.Status.COMPLETED) {
            return;
        }

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId()).orElse(null);
        if (fixture == null || fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            return;
        }

        if (isFixtureCancelledStatus(fixture.getStatus())) {
            return;
        }

        syncFixtureFantasyPoints(contest.getFixtureId(), force);

        Contest refreshedContest = contestRepository.findById(contest.getId()).orElse(contest);
        if (refreshedContest.getStatus() == Contest.Status.CANCELLED
                || refreshedContest.getStatus() == Contest.Status.COMPLETED) {
            return;
        }

        Fixture refreshedFixture = fixtureRepository.findById(refreshedContest.getFixtureId()).orElse(fixture);
        if (refreshedFixture != null && isFixtureFinishedStatus(refreshedFixture.getStatus())) {
            if (refreshedContest.getContestType() == Contest.ContestType.COMMUNITY) {
                settleCommunityContest(refreshedContest, false);
            } else {
                settlePublicContest(refreshedContest, false);
            }
        }
    }

    private boolean isFixtureFinishedStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        String compact = normalized.replaceAll("[^A-Z]", "");

        return compact.contains("FINISH")
                || compact.contains("COMPLET")
                || compact.equals("RESULT")
                || compact.equals("DONE");
    }

    private Map<String, Object> buildSettlementResult(
            Long contestId,
            int totalEntries,
            int winnersCredited,
            int totalPrizeCredited,
            String contestStatus
    ) {
        Map<String, Object> result = new HashMap<>();
        result.put("contestId", contestId);
        result.put("totalEntries", totalEntries);
        result.put("winnersCredited", winnersCredited);
        result.put("totalPrizeCredited", totalPrizeCredited);
        result.put("contestStatus", contestStatus);
        return result;
    }
}
