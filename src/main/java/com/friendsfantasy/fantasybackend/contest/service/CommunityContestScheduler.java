package com.friendsfantasy.fantasybackend.contest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityContestScheduler {

    private final ContestEntryService contestEntryService;

    @Value("${fantasy.community-contests.background-sync-enabled:true}")
    private boolean backgroundSyncEnabled;

    @Scheduled(
            initialDelayString = "${fantasy.community-contests.background-sync-initial-delay-ms:45000}",
            fixedDelayString = "${fantasy.community-contests.background-sync-fixed-delay-ms:60000}"
    )
    public void syncDueCommunityContests() {
        if (!backgroundSyncEnabled) {
            return;
        }

        Map<String, Object> result = contestEntryService.syncDueCommunityContests();
        Number changed = (Number) result.getOrDefault("statusChanged", 0);
        if (changed.intValue() > 0) {
            log.info("Community contest scheduler updated statuses: {}", result);
        }
    }
}
