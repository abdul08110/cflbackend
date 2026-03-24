package com.friendsfantasy.fantasybackend.team.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixturePlayerPoolResponse;
import com.friendsfantasy.fantasybackend.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PlayerPoolController {

    private final TeamService teamService;

    @PostMapping("/admin/fixtures/{fixtureId}/player-pool/sync")
    public ApiResponse<Map<String, Object>> syncPlayerPool(@PathVariable Long fixtureId) {
        int synced = teamService.syncFixturePlayerPool(fixtureId);
        return ApiResponse.ok("Fixture player pool synced successfully", Map.of("syncedCount", synced));
    }

    @GetMapping("/fixtures/{fixtureId}/player-pool")
    public ApiResponse<List<FixturePlayerPoolResponse>> getPlayerPool(
            @PathVariable Long fixtureId,
            @RequestParam(name = "forceSync", defaultValue = "false") boolean forceSync
    ) {
        return ApiResponse.ok(
                "Fixture player pool fetched successfully",
                teamService.getFixturePlayerPool(fixtureId, forceSync)
        );
    }
}
