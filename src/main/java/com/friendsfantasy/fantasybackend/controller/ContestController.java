package com.friendsfantasy.fantasybackend.controller;

import com.friendsfantasy.fantasybackend.dto.ApiResponse;
import com.friendsfantasy.fantasybackend.dto.CreateTeamRequest;
import com.friendsfantasy.fantasybackend.dto.JoinContestRequest;
import com.friendsfantasy.fantasybackend.entity.ContestEntry;
import com.friendsfantasy.fantasybackend.entity.Team;
import com.friendsfantasy.fantasybackend.service.ContestService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contest")
public class ContestController {

    private final ContestService contestService;

    public ContestController(ContestService contestService) {
        this.contestService = contestService;
    }

    @PostMapping("/create-team")
    public ApiResponse createTeam(@RequestBody CreateTeamRequest request) {
        Team team = contestService.createTeam(request);
        return ApiResponse.success("Team created successfully", team);
    }

    @PostMapping("/join")
    public ApiResponse joinContest(@RequestBody JoinContestRequest request) {
        ContestEntry entry = contestService.joinContest(request);
        return ApiResponse.success("Joined contest successfully", entry);
    }

    @GetMapping("/leaderboard/{contestId}")
    public ApiResponse getLeaderboard(@PathVariable Integer contestId) {
        return ApiResponse.success("Leaderboard fetched", contestService.getLeaderboard(contestId));
    }
}