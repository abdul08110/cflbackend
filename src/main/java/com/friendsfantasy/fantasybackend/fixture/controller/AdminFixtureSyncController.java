package com.friendsfantasy.fantasybackend.fixture.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.fixture.service.FixtureSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/fixtures")
@RequiredArgsConstructor
public class AdminFixtureSyncController {

    private final FixtureSyncService fixtureSyncService;

    @PostMapping("/sync-upcoming")
    public ApiResponse<Map<String, Object>> syncUpcomingFixtures() {
        int synced = fixtureSyncService.syncUpcomingIplFixtures();

        return ApiResponse.ok("Upcoming IPL fixtures synced successfully", Map.of(
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
}