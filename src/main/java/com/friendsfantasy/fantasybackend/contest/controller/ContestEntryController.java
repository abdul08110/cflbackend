package com.friendsfantasy.fantasybackend.contest.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.contest.dto.*;
import com.friendsfantasy.fantasybackend.contest.service.ContestEntryService;
import com.friendsfantasy.fantasybackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ContestEntryController {

    private final ContestEntryService contestEntryService;

    @PostMapping("/contests/{contestId}/join")
    public ApiResponse<ContestEntryResponse> joinContest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long contestId,
            @Valid @RequestBody JoinContestRequest request
    ) {
        return ApiResponse.ok(
                "Contest joined successfully",
                contestEntryService.joinContest(principal.getId(), contestId, request)
        );
    }

    @GetMapping("/contests/{contestId}/my-entry")
    public ApiResponse<List<ContestEntryResponse>> getMyEntries(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long contestId
    ) {
        return ApiResponse.ok(
                "My contest entries fetched successfully",
                contestEntryService.getMyEntries(principal.getId(), contestId)
        );
    }

    @GetMapping("/contests/{contestId}/leaderboard")
    public ApiResponse<List<LeaderboardEntryResponse>> getLeaderboard(@PathVariable Long contestId) {
        return ApiResponse.ok(
                "Contest leaderboard fetched successfully",
                contestEntryService.getLeaderboard(contestId)
        );
    }

    @GetMapping("/contests/history/my")
    public ApiResponse<List<ContestEntryResponse>> getMyContestHistory(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(
                "My contest history fetched successfully",
                contestEntryService.getMyContestHistory(principal.getId())
        );
    }

    @GetMapping("/me/stats")
    public ApiResponse<UserStatsResponse> getMyStats(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(
                "User stats fetched successfully",
                contestEntryService.getMyStats(principal.getId())
        );
    }

    @PostMapping("/admin/contests/{contestId}/entries/{entryId}/points")
    public ApiResponse<ContestEntryResponse> updateEntryPoints(
            @PathVariable Long contestId,
            @PathVariable Long entryId,
            @Valid @RequestBody UpdateEntryPointsRequest request
    ) {
        return ApiResponse.ok(
                "Contest entry points updated successfully",
                contestEntryService.updateEntryPoints(contestId, entryId, request)
        );
    }

    @PostMapping("/admin/contests/{contestId}/settle")
    public ApiResponse<Map<String, Object>> settleContest(@PathVariable Long contestId) {
        return ApiResponse.ok(
                "Contest settled successfully",
                contestEntryService.settleContest(contestId)
        );
    }
}