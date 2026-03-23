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
import com.friendsfantasy.fantasybackend.notification.repository.NotificationRepository;
import com.friendsfantasy.fantasybackend.room.entity.Room;
import com.friendsfantasy.fantasybackend.room.entity.RoomMember;
import com.friendsfantasy.fantasybackend.room.repository.RoomMemberRepository;
import com.friendsfantasy.fantasybackend.room.repository.RoomRepository;
import com.friendsfantasy.fantasybackend.stats.entity.UserStats;
import com.friendsfantasy.fantasybackend.stats.service.UserStatsService;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeam;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamRepository;
import com.friendsfantasy.fantasybackend.team.service.TeamService;
import com.friendsfantasy.fantasybackend.wallet.entity.WalletTransaction;
import com.friendsfantasy.fantasybackend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
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
    private final NotificationRepository notificationRepository;
    private final CricketFantasyScoringService cricketFantasyScoringService;

    @Transactional
    public ContestEntryResponse joinContest(Long userId, Long contestId, JoinContestRequest request) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        if (contest.getContestType() == Contest.ContestType.COMMUNITY) {
            throw new RuntimeException("Join the community instead of joining this contest directly");
        }

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

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (fixture.getDeadlineTime().isBefore(LocalDateTime.now())) {
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

        validateOptionalRoom(userId, request.getRoomId(), fixture.getSportId());

        WalletTransaction walletTxn = walletService.debitForContestJoin(
                userId,
                contest.getEntryFeePoints(),
                contestId,
                "Contest join - " + contest.getContestName()
        );

        ContestEntry entry = ContestEntry.builder()
                .contestId(contestId)
                .userId(userId)
                .roomId(request.getRoomId())
                .userMatchTeamId(team.getId())
                .walletTransactionId(walletTxn.getId())
                .entryFeePoints(contest.getEntryFeePoints())
                .build();

        entry = contestEntryRepository.save(entry);

        contest.setSpotsFilled(contest.getSpotsFilled() + 1);
        if (contest.getSpotsFilled() >= contest.getMaxSpots()) {
            contest.setStatus(Contest.Status.FULL);
        }
        contestRepository.save(contest);

        userStatsService.recordContestJoin(userId, contest.getEntryFeePoints());

        return mapContestEntry(entry);
    }

    @Transactional
    public ContestEntryResponse createCommunityEntry(Long userId, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Community not found"));

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new RuntimeException("Community is not active");
        }

        roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .filter(member -> member.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException("You are not a member of this community"));

        Contest contest = findCommunityContest(roomId);
        syncCommunityContestState(roomId);
        contest = contestRepository.findById(contest.getId())
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        validateCommunityJoinAllowed(contest);

        if (contestEntryRepository.existsByContestIdAndUserId(contest.getId(), userId)) {
            throw new RuntimeException("You have already joined this community");
        }

        WalletTransaction walletTxn = walletService.debitForContestJoin(
                userId,
                contest.getEntryFeePoints(),
                contest.getId(),
                "Community join - " + contest.getContestName()
        );

        ContestEntry entry = ContestEntry.builder()
                .contestId(contest.getId())
                .userId(userId)
                .roomId(roomId)
                .walletTransactionId(walletTxn.getId())
                .entryFeePoints(contest.getEntryFeePoints())
                .build();

        entry = contestEntryRepository.save(entry);

        updateCommunityPrizeState(contest);
        userStatsService.recordContestJoin(userId, contest.getEntryFeePoints());

        return mapContestEntry(entry);
    }

    @Transactional
    public ContestEntryResponse attachTeamToCommunityEntry(Long userId, Long roomId, Long teamId) {
        Contest contest = findCommunityContest(roomId);
        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (!fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Team creation is closed for this community");
        }

        ContestEntry entry = contestEntryRepository.findByContestIdAndUserId(contest.getId(), userId)
                .orElseThrow(() -> new RuntimeException("You have not joined this community"));

        if (entry.getStatus() != ContestEntry.Status.JOINED) {
            throw new RuntimeException("This community entry can no longer be updated");
        }

        UserMatchTeam team = userMatchTeamRepository.findByIdAndUserId(teamId, userId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        if (!Objects.equals(team.getFixtureId(), contest.getFixtureId())) {
            throw new RuntimeException("Selected team does not belong to this community fixture");
        }

        if (!Objects.equals(entry.getUserMatchTeamId(), teamId)
                && contestEntryRepository.existsByContestIdAndUserMatchTeamId(contest.getId(), teamId)) {
            throw new RuntimeException("This team is already attached to the community");
        }

        entry.setUserMatchTeamId(teamId);
        contestEntryRepository.save(entry);
        return mapContestEntry(entry);
    }

    @Transactional
    public void syncCommunityContestState(Long roomId) {
        Contest contest = contestRepository.findByRoomId(roomId).orElse(null);
        if (contest == null || contest.getContestType() != Contest.ContestType.COMMUNITY) {
            return;
        }

        syncCommunityContestState(contest);
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

    public ContestEntryResponse getCommunityEntry(Long userId, Long roomId) {
        Contest contest = contestRepository.findByRoomId(roomId).orElse(null);
        if (contest == null) {
            return null;
        }

        syncFixtureFantasyPointsIfNeeded(contest, false);

        return contestEntryRepository.findByContestIdAndUserId(contest.getId(), userId)
                .map(this::mapContestEntry)
                .orElse(null);
    }

    @Transactional
    public Map<String, Object> syncFixtureFantasyPoints(Long fixtureId, boolean force) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (!fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            lockFixtureContestTeamsIfNeeded(fixtureId);
        }

        return cricketFantasyScoringService.syncFixtureFantasyPoints(fixtureId, force);
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
        syncFixtureFantasyPointsIfNeeded(contest, false);

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
                    syncFixtureFantasyPointsIfNeeded(contest, false);
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

    public List<LeaderboardEntryResponse> getLeaderboard(Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));
        syncFixtureFantasyPointsIfNeeded(contest, false);

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

    private ContestPrize findPrizeForRank(List<ContestPrize> prizes, int rank) {
        for (ContestPrize prize : prizes) {
            if (rank >= prize.getRankFromNo() && rank <= prize.getRankToNo()) {
                return prize;
            }
        }
        return null;
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

    private Contest findCommunityContest(Long roomId) {
        Contest contest = contestRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Community contest not found"));

        if (contest.getContestType() != Contest.ContestType.COMMUNITY) {
            throw new RuntimeException("This community is not configured as a community contest");
        }

        return contest;
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
        int payoutPoints = calculateCommunityWinnerPayout(prizePoolPoints);

        contest.setSpotsFilled(activeEntries);
        contest.setPrizePoolPoints(prizePoolPoints);
        contest.setFirstPrizePoints(payoutPoints);
        contest.setWinnerCount(1);
        contest.setJoinConfirmRequired(false);
        contest.setStatus(activeEntries >= contest.getMaxSpots() ? Contest.Status.FULL : Contest.Status.OPEN);
        contestRepository.save(contest);
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

        if (entries.size() == 1 && Objects.equals(entries.getFirst().getUserId(), contest.getCreatedByUserId())) {
            ContestEntry creatorEntry = entries.getFirst();

            if (creatorEntry.getStatus() == ContestEntry.Status.JOINED) {
                creatorEntry.setStatus(ContestEntry.Status.REFUNDED);
                creatorEntry.setPrizePointsAwarded(0);
                creatorEntry.setSettledAt(LocalDateTime.now());
                contestEntryRepository.save(creatorEntry);

                walletService.creditRefund(
                        creatorEntry.getUserId(),
                        creatorEntry.getEntryFeePoints(),
                        contest.getId(),
                        "Community refund - no other members joined"
                );
            }

            contest.setPrizePoolPoints(0);
            contest.setFirstPrizePoints(0);
            contest.setStatus(Contest.Status.CANCELLED);
            contestRepository.save(contest);
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

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
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
        int winnerPayout = calculateCommunityWinnerPayout(totalCollected);

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

            if (rank == 1) {
                entry.setPrizePointsAwarded(winnerPayout);
                walletService.creditContestWin(
                        entry.getUserId(),
                        winnerPayout,
                        contest.getId(),
                        "Community win - rank 1"
                );
                userStatsService.recordContestWin(entry.getUserId(), winnerPayout, 1);
                winnersCredited = 1;
                totalPrizeCredited = winnerPayout;
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
        contest.setFirstPrizePoints(winnerPayout);
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
