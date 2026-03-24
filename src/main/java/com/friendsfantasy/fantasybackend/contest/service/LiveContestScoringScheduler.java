package com.friendsfantasy.fantasybackend.contest.service;

import com.friendsfantasy.fantasybackend.contest.entity.Contest;
import com.friendsfantasy.fantasybackend.contest.repository.ContestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveContestScoringScheduler {

    private final ContestRepository contestRepository;
    private final ContestEntryService contestEntryService;

    @Value("${fantasy.live-scoring.background-sync-enabled:true}")
    private boolean backgroundSyncEnabled;

    @Scheduled(
            initialDelayString = "${fantasy.live-scoring.background-sync-initial-delay-ms:45000}",
            fixedDelayString = "${fantasy.live-scoring.background-sync-fixed-delay-ms:30000}"
    )
    public void syncLiveContestScores() {
        if (!backgroundSyncEnabled) {
            return;
        }

        List<Contest> contests = contestRepository.findByStatusInOrderByCreatedAtDescIdDesc(
                List.of(Contest.Status.OPEN, Contest.Status.FULL, Contest.Status.LIVE)
        );
        if (contests.isEmpty()) {
            return;
        }

        Set<Long> fixtureIds = new LinkedHashSet<>();
        for (Contest contest : contests) {
            if (contest.getFixtureId() != null) {
                fixtureIds.add(contest.getFixtureId());
            }
        }

        for (Long fixtureId : fixtureIds) {
            try {
                contestEntryService.syncFixtureFantasyPoints(fixtureId, false);
            } catch (Exception ex) {
                log.warn("Background live score sync failed for fixture {}", fixtureId, ex);
            }
        }
    }
}
