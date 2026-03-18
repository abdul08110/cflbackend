package com.friendsfantasy.fantasybackend.contest.service;

import com.friendsfantasy.fantasybackend.contest.dto.*;
import com.friendsfantasy.fantasybackend.contest.entity.Contest;
import com.friendsfantasy.fantasybackend.contest.entity.ContestPrize;
import com.friendsfantasy.fantasybackend.contest.repository.ContestPrizeRepository;
import com.friendsfantasy.fantasybackend.contest.repository.ContestRepository;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContestService {

    private final ContestRepository contestRepository;
    private final ContestPrizeRepository contestPrizeRepository;
    private final FixtureRepository fixtureRepository;

    @Transactional
    public ContestResponse createContest(Long fixtureId, CreateContestRequest request, Long adminId) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        validateContestRequest(request);

        int firstPrize = request.getPrizes().stream()
                .filter(p -> p.getRankFrom() == 1 && p.getRankTo() == 1)
                .map(ContestPrizeRequest::getPrizePoints)
                .findFirst()
                .orElse(0);

        Contest contest = Contest.builder()
                .fixtureId(fixture.getId())
                .scoringTemplateId(request.getScoringTemplateId())
                .contestName(request.getContestName().trim())
                .entryFeePoints(request.getEntryFeePoints())
                .prizePoolPoints(request.getPrizePoolPoints())
                .winnerCount(request.getWinnerCount())
                .maxSpots(request.getMaxSpots())
                .spotsFilled(0)
                .joinConfirmRequired(Boolean.TRUE.equals(request.getJoinConfirmRequired()))
                .firstPrizePoints(firstPrize)
                .status(Contest.Status.OPEN)
                .createdByAdminId(adminId)
                .build();

        contest = contestRepository.save(contest);

        savePrizes(contest.getId(), request.getPrizes());

        return getContest(contest.getId());
    }

    @Transactional
    public ContestResponse updateContest(Long contestId, CreateContestRequest request) {
        Contest contest = contestRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Contest not found"));

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

        List<Contest> contests = contestRepository.findByFixtureIdOrderByEntryFeePointsAscIdAsc(fixtureId);
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

    private ContestResponse mapContest(Contest contest) {
        List<ContestPrizeResponse> prizes = contestPrizeRepository.findByContestIdOrderByRankFromNoAsc(contest.getId())
                .stream()
                .map(p -> ContestPrizeResponse.builder()
                        .rankFrom(p.getRankFromNo())
                        .rankTo(p.getRankToNo())
                        .prizePoints(p.getPrizePoints())
                        .build())
                .toList();

        int spotsLeft = Math.max(0, contest.getMaxSpots() - contest.getSpotsFilled());

        return ContestResponse.builder()
                .contestId(contest.getId())
                .fixtureId(contest.getFixtureId())
                .contestName(contest.getContestName())
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
}