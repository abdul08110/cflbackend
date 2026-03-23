package com.friendsfantasy.fantasybackend.fixture.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fantasy.fixtures.background-sync-enabled", havingValue = "true")
public class FixtureSyncScheduler {

    private final FixtureSyncService fixtureSyncService;

    @Scheduled(
            initialDelayString = "${fantasy.fixtures.background-sync-initial-delay-ms:30000}",
            fixedDelayString = "${fantasy.fixtures.background-sync-fixed-delay-ms:900000}"
    )
    public void syncUpcomingFixtures() {
        try {
            int syncedCount = fixtureSyncService.syncUpcomingFixtures();
            log.info("Background upcoming fixture sync completed with {} fixtures", syncedCount);
        } catch (Exception ex) {
            log.error("Background upcoming fixture sync failed", ex);
        }
    }
}
