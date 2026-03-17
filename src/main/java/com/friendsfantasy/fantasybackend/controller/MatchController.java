package com.friendsfantasy.fantasybackend.controller;

import com.friendsfantasy.fantasybackend.dto.ApiResponse;
import com.friendsfantasy.fantasybackend.service.MatchService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public ApiResponse getAllMatches() {
        return ApiResponse.success("Matches fetched", matchService.getAllMatches());
    }

    @GetMapping("/{matchId}/players")
    public ApiResponse getPlayers(@PathVariable Integer matchId) {
        return ApiResponse.success("Players fetched", matchService.getPlayersForMatch(matchId));
    }

    @GetMapping("/{matchId}/contests")
    public ApiResponse getContests(@PathVariable Integer matchId) {
        return ApiResponse.success("Contests fetched", matchService.getContestsByMatch(matchId));
    }
}