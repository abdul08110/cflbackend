package com.friendsfantasy.fantasybackend.fixture.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.contest.service.ContestEntryService;
import com.friendsfantasy.fantasybackend.fixture.service.FixtureSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/fixtures")
@RequiredArgsConstructor
public class AdminFixtureSyncController {

    private final FixtureSyncService fixtureSyncService;
    private final ContestEntryService contestEntryService;

    @PostMapping("/sync-upcoming")
    public ApiResponse<Map<String, Object>> syncUpcomingFixtures() {
        int synced = fixtureSyncService.syncUpcomingFixtures();

        return ApiResponse.ok("Upcoming cricket fixtures synced successfully", Map.of(
                "syncedCount", synced
        ));
    }

    @PostMapping("/sync/{externalFixtureId}")
    public ApiResponse<Map<String, Object>> syncSingleFixture(@PathVariable Long externalFixtureId) {
        fixtureSyncService.syncFixtureByExternalId(externalFixtureId);

        return ApiResponse.ok("Fixture synced successfully", Map.of(
                "externalFixtureId", externalFixtureId
        ));
    }

    @PostMapping("/{fixtureId}/scoring/sync")
    public ApiResponse<Map<String, Object>> syncFixtureScoring(@PathVariable Long fixtureId) {
        return ApiResponse.ok(
                "Fixture scoring synced successfully",
                contestEntryService.syncFixtureFantasyPoints(fixtureId, true)
        );
    }
}
