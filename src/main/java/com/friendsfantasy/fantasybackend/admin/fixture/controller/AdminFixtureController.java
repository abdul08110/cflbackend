package com.friendsfantasy.fantasybackend.admin.fixture.controller;

import com.friendsfantasy.fantasybackend.admin.fixture.dto.AdminUpcomingFixtureResponse;
import com.friendsfantasy.fantasybackend.admin.fixture.service.AdminFixtureAdminService;
import com.friendsfantasy.fantasybackend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/fixtures")
@RequiredArgsConstructor
public class AdminFixtureController {

    private final AdminFixtureAdminService adminFixtureAdminService;

    @GetMapping("/upcoming")
    public ApiResponse<List<AdminUpcomingFixtureResponse>> getUpcomingFixtures() {
        return ApiResponse.ok(
                "Upcoming fixtures fetched successfully",
                adminFixtureAdminService.getUpcomingFixtures()
        );
    }
}
