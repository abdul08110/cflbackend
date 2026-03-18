package com.friendsfantasy.fantasybackend.contest.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.contest.dto.ContestResponse;
import com.friendsfantasy.fantasybackend.contest.dto.CreateContestRequest;
import com.friendsfantasy.fantasybackend.contest.service.ContestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminContestController {

    private final ContestService contestService;

    @PostMapping("/fixtures/{fixtureId}/contests")
    public ApiResponse<ContestResponse> createContest(
            @PathVariable Long fixtureId,
            @Valid @RequestBody CreateContestRequest request
    ) {
        return ApiResponse.ok(
                "Contest created successfully",
                contestService.createContest(fixtureId, request, 1L)
        );
    }

    @PutMapping("/contests/{contestId}")
    public ApiResponse<ContestResponse> updateContest(
            @PathVariable Long contestId,
            @Valid @RequestBody CreateContestRequest request
    ) {
        return ApiResponse.ok(
                "Contest updated successfully",
                contestService.updateContest(contestId, request)
        );
    }
}