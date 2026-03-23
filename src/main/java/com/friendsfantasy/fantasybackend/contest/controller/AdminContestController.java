package com.friendsfantasy.fantasybackend.contest.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.contest.dto.ContestResponse;
import com.friendsfantasy.fantasybackend.contest.dto.CreateContestRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminContestController {

    @PostMapping("/fixtures/{fixtureId}/contests")
    public ApiResponse<ContestResponse> createContest(
            @PathVariable Long fixtureId,
            @Valid @RequestBody CreateContestRequest request
    ) {
        throw new RuntimeException("Admin contest creation is disabled. Users must create communities.");
    }

    @PutMapping("/contests/{contestId}")
    public ApiResponse<ContestResponse> updateContest(
            @PathVariable Long contestId,
            @Valid @RequestBody CreateContestRequest request
    ) {
        throw new RuntimeException("Admin contest updates are disabled for community-based contests.");
    }
}
