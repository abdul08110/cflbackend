package com.friendsfantasy.fantasybackend.fixture.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureDetailResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureSummaryResponse;
import com.friendsfantasy.fantasybackend.fixture.service.FixtureSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FixtureController {

    private final FixtureSyncService fixtureSyncService;

    @GetMapping("/cricket/fixtures/upcoming")
    public ApiResponse<List<FixtureSummaryResponse>> getUpcomingFixtures() {
        return ApiResponse.ok(
                "Upcoming IPL fixtures fetched successfully",
                fixtureSyncService.getUpcomingFixtures()
        );
    }

    @GetMapping("/fixtures/{fixtureId}")
    public ApiResponse<FixtureDetailResponse> getFixtureDetail(@PathVariable Long fixtureId) {
        return ApiResponse.ok(
                "Fixture detail fetched successfully",
                fixtureSyncService.getFixtureDetail(fixtureId)
        );
    }
}