package com.friendsfantasy.fantasybackend.contest.service;

import com.friendsfantasy.fantasybackend.contest.dto.*;
import com.friendsfantasy.fantasybackend.contest.entity.Contest;
import com.friendsfantasy.fantasybackend.contest.entity.ContestPrize;
import com.friendsfantasy.fantasybackend.contest.repository.ContestPrizeRepository;
import com.friendsfantasy.fantasybackend.contest.repository.ContestRepository;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.room.entity.RoomMember;
import com.friendsfantasy.fantasybackend.room.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestPrizeRepository contestPrizeRepository;
    private final FixtureRepository fixtureRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Transactional
    public ContestResponse createContest(Long fixtureId, CreateContestRequest request) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (fixture.getDeadlineTime() == null || !fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Contest creation is closed for this fixture");
        }

        validateContestRequest(request);

        int firstPrize = request.getPrizes().stream()
                .filter(p -> p.getRankFrom() == 1 && p.getRankTo() == 1)
                .map(ContestPrizeRequest::getPrizePoints)
                .findFirst()
                .orElse(0);

        Contest contest = Contest.builder()
                .fixtureId(fixture.getId())
                .roomId(null)
                .scoringTemplateId(request.getScoringTemplateId())
                .contestName(request.getContestName().trim())
                .contestType(Contest.ContestType.PUBLIC)
                .entryFeePoints(request.getEntryFeePoints())
                .prizePoolPoints(request.getPrizePoolPoints())
                .winnerCount(request.getWinnerCount())
                .maxSpots(request.getMaxSpots())
                .spotsFilled(0)
                .joinConfirmRequired(Boolean.TRUE.equals(request.getJoinConfirmRequired()))
                .firstPrizePoints(firstPrize)
                .status(Contest.Status.OPEN)
                .createdByUserId(null)
                .build();

        contest = contestRepository.save(contest);

        savePrizes(contest.getId(), request.getPrizes());

        return getContest(contest.getId());
    }

    @Transactional
    public ContestResponse updateContest(Long contestId, CreateContestRequest request) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        if (contest.getContestType() == Contest.ContestType.COMMUNITY) {
            throw new RuntimeException("Community contests cannot be updated from admin");
        }

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));
        if (fixture.getDeadlineTime() == null || !fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Contest update is closed for this fixture");
        }

        validateContestRequest(request);

        int firstPrize = request.getPrizes().stream()
                .filter(p -> p.getRankFrom() == 1 && p.getRankTo() == 1)
                .map(ContestPrizeRequest::getPrizePoints)
                .findFirst()
                .orElse(0);

        contest.setScoringTemplateId(request.getScoringTemplateId());
        contest.setContestName(request.getContestName().trim());
        contest.setEntryFeePoints(request.getEntryFeePoints());
        contest.setPrizePoolPoints(request.getPrizePoolPoints());
        contest.setWinnerCount(request.getWinnerCount());
        contest.setMaxSpots(request.getMaxSpots());
        contest.setJoinConfirmRequired(Boolean.TRUE.equals(request.getJoinConfirmRequired()));
        contest.setFirstPrizePoints(firstPrize);

        if (contest.getSpotsFilled() >= contest.getMaxSpots()) {
            contest.setStatus(Contest.Status.FULL);
        } else if (contest.getStatus() != Contest.Status.CANCELLED
                && contest.getStatus() != Contest.Status.COMPLETED
                && contest.getStatus() != Contest.Status.LIVE) {
            contest.setStatus(Contest.Status.OPEN);
        }

        contestRepository.save(contest);

        contestPrizeRepository.deleteByContestId(contestId);
        savePrizes(contestId, request.getPrizes());

        return getContest(contestId);
    }

    public List<ContestResponse> getContestsByFixture(Long fixtureId) {
        fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        List<Contest> contests = contestRepository.findByFixtureIdAndContestTypeOrderByEntryFeePointsAscIdAsc(
                fixtureId,
                Contest.ContestType.PUBLIC
        );
        List<ContestResponse> response = new ArrayList<>();

        for (Contest contest : contests) {
            response.add(mapContest(contest));
        }

        return response;
    }

    public ContestResponse getContest(Long contestId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        return mapContest(contest);
    }

    public ContestResponse getContest(Long contestId, Long viewerUserId) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        ensureViewerAllowed(contest, viewerUserId);
        return mapContest(contest);
    }

    private ContestResponse mapContest(Contest contest) {
        List<ContestPrizeResponse> prizes = contestPrizeRepository.findByContestIdOrderByRankFromNoAsc(contest.getId())
                .stream()
                .map(p -> ContestPrizeResponse.builder()
                        .rankFrom(p.getRankFromNo())
                        .rankTo(p.getRankToNo())
                        .prizePoints(p.getPrizePoints())
                        .build())
                .toList();

        if (prizes.isEmpty() && contest.getFirstPrizePoints() != null) {
            prizes = List.of(ContestPrizeResponse.builder()
                    .rankFrom(1)
                    .rankTo(1)
                    .prizePoints(contest.getFirstPrizePoints())
                    .build());
        }

        int spotsLeft = Math.max(0, contest.getMaxSpots() - contest.getSpotsFilled());

        return ContestResponse.builder()
                .contestId(contest.getId())
                .communityId(contest.getRoomId())
                .fixtureId(contest.getFixtureId())
                .contestName(contest.getContestName())
                .contestType(contest.getContestType().name())
                .maxSpots(contest.getMaxSpots())
                .spotsFilled(contest.getSpotsFilled())
                .spotsLeft(spotsLeft)
                .entryFeePoints(contest.getEntryFeePoints())
                .prizePoolPoints(contest.getPrizePoolPoints())
                .winnerCount(contest.getWinnerCount())
                .joinConfirmRequired(contest.getJoinConfirmRequired())
                .firstPrizePoints(contest.getFirstPrizePoints())
                .status(contest.getStatus().name())
                .prizes(prizes)
                .build();
    }

    private void ensureViewerAllowed(Contest contest, Long viewerUserId) {
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

    private void savePrizes(Long contestId, List<ContestPrizeRequest> prizes) {
        for (ContestPrizeRequest prize : prizes) {
            ContestPrize entity = ContestPrize.builder()
                    .contestId(contestId)
                    .rankFromNo(prize.getRankFrom())
                    .rankToNo(prize.getRankTo())
                    .prizePoints(prize.getPrizePoints())
                    .build();

            contestPrizeRepository.save(entity);
        }
    }

    private void validateContestRequest(CreateContestRequest request) {
        if (request.getPrizes() == null || request.getPrizes().isEmpty()) {
            throw new RuntimeException("Prize breakup is required");
        }

        validateWinnerStructure(request.getWinnerCount(), request.getMaxSpots());

        List<ContestPrizeRequest> prizes = new ArrayList<>(request.getPrizes());
        prizes.sort(Comparator.comparingInt(ContestPrizeRequest::getRankFrom));

        int totalCoveredWinners = 0;
        int totalPrizePoints = 0;
        int previousTo = 0;

        for (ContestPrizeRequest prize : prizes) {
            if (prize.getRankFrom() > prize.getRankTo()) {
                throw new RuntimeException("Invalid rank range in prize breakup");
            }

            if (prize.getRankFrom() <= previousTo) {
                throw new RuntimeException("Prize rank ranges overlap");
            }

            int winnersInThisRange = prize.getRankTo() - prize.getRankFrom() + 1;
            totalCoveredWinners += winnersInThisRange;
            totalPrizePoints += prize.getPrizePoints() * winnersInThisRange;
            previousTo = prize.getRankTo();
        }

        if (totalCoveredWinners != request.getWinnerCount()) {
            throw new RuntimeException("Prize breakup winner count does not match winnerCount");
        }

        if (totalPrizePoints > request.getPrizePoolPoints()) {
            throw new RuntimeException("Prize breakup total exceeds prize pool");
        }

        if (request.getMaxSpots() < request.getWinnerCount()) {
            throw new RuntimeException("maxSpots cannot be less than winnerCount");
        }
    }

    private void validateWinnerStructure(Integer winnerCount, Integer maxSpots) {
        if (winnerCount == null || winnerCount < 1 || winnerCount > 3) {
            throw new RuntimeException("winnerCount must be 1, 2, or 3");
        }

        if (maxSpots == null) {
            throw new RuntimeException("maxSpots is required");
        }

        int minSpots = switch (winnerCount) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 10;
            default -> 2;
        };
        int maxAllowedSpots = switch (winnerCount) {
            case 1 -> 10;
            case 2 -> 20;
            case 3 -> 30;
            default -> 10;
        };

        if (maxSpots < minSpots || maxSpots > maxAllowedSpots) {
            throw new RuntimeException(
                    "maxSpots must be between " + minSpots + " and " + maxAllowedSpots
                            + " when winnerCount is " + winnerCount
            );
        }
    }
}
