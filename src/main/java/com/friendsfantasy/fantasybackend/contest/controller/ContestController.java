package com.friendsfantasy.fantasybackend.contest.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.contest.dto.ContestResponse;
import com.friendsfantasy.fantasybackend.contest.service.ContestService;
import com.friendsfantasy.fantasybackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;

    @GetMapping("/fixtures/{fixtureId}/contests")
    public ApiResponse<List<ContestResponse>> getContestsByFixture(@PathVariable Long fixtureId) {
        return ApiResponse.ok(
                "Contests fetched successfully",
                contestService.getContestsByFixture(fixtureId)
        );
    }

    @GetMapping("/contests/{contestId}")
    public ApiResponse<ContestResponse> getContest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long contestId
    ) {
        return ApiResponse.ok(
                "Contest fetched successfully",
                contestService.getContest(contestId, principal.getId())
        );
    }
}
