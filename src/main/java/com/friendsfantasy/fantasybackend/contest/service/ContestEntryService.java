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
import com.friendsfantasy.fantasybackend.room.entity.Room;
import com.friendsfantasy.fantasybackend.room.entity.RoomMember;
import com.friendsfantasy.fantasybackend.room.repository.RoomMemberRepository;
import com.friendsfantasy.fantasybackend.room.repository.RoomRepository;
import com.friendsfantasy.fantasybackend.stats.entity.UserStats;
import com.friendsfantasy.fantasybackend.stats.service.UserStatsService;
import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeam;
import com.friendsfantasy.fantasybackend.team.repository.UserMatchTeamRepository;
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

    @Transactional
    public ContestEntryResponse joinContest(Long userId, Long contestId, JoinContestRequest request) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

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

        List<ContestEntry> entries = new ArrayList<>(contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contestId));
        if (entries.isEmpty()) {
            throw new RuntimeException("No contest entries found");
        }

        entries.sort(Comparator
                .comparing(ContestEntry::getFantasyPoints, Comparator.nullsLast(BigDecimal::compareTo)).reversed()
                .thenComparing(ContestEntry::getJoinedAt, Comparator.nullsLast(LocalDateTime::compareTo)));

        List<ContestPrize> prizes = contestPrizeRepository.findByContestIdOrderByRankFromNoAsc(contestId);

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
                        contestId,
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
        result.put("contestId", contestId);
        result.put("totalEntries", entries.size());
        result.put("winnersCredited", winnersCredited);
        result.put("totalPrizeCredited", totalPrizeCredited);
        result.put("contestStatus", contest.getStatus().name());

        return result;
    }

    public List<ContestEntryResponse> getMyEntries(Long userId, Long contestId) {
        contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

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

        for (ContestEntry entry : entries) {
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
                .totalRoomsCreated(stats.getTotalRoomsCreated())
                .winRate(stats.getWinRate())
                .bestRank(stats.getBestRank())
                .build();
    }

    public List<LeaderboardEntryResponse> getLeaderboard(Long contestId) {
        contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

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

            UserMatchTeam team = userMatchTeamRepository.findById(entry.getUserMatchTeamId())
                    .orElseThrow(() -> new RuntimeException("Team not found"));

            response.add(LeaderboardEntryResponse.builder()
                    .position(position++)
                    .entryId(entry.getId())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .teamId(team.getId())
                    .teamName(team.getTeamName())
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
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!Objects.equals(room.getSportId(), fixtureSportId)) {
            throw new RuntimeException("Room sport does not match fixture sport");
        }

        roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .filter(m -> m.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException("You are not a member of the selected room"));
    }

    private ContestEntryResponse mapContestEntry(ContestEntry entry) {
        UserMatchTeam team = userMatchTeamRepository.findById(entry.getUserMatchTeamId())
                .orElseThrow(() -> new RuntimeException("Team not found"));

        return ContestEntryResponse.builder()
                .entryId(entry.getId())
                .contestId(entry.getContestId())
                .teamId(team.getId())
                .teamName(team.getTeamName())
                .roomId(entry.getRoomId())
                .entryFeePoints(entry.getEntryFeePoints())
                .fantasyPoints(entry.getFantasyPoints())
                .rankNo(entry.getRankNo())
                .prizePointsAwarded(entry.getPrizePointsAwarded())
                .status(entry.getStatus().name())
                .joinedAt(entry.getJoinedAt())
                .build();
    }
}