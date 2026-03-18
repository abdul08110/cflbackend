package com.friendsfantasy.fantasybackend.team.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.security.UserPrincipal;
import com.friendsfantasy.fantasybackend.team.dto.CreateTeamRequest;
import com.friendsfantasy.fantasybackend.team.dto.TeamResponse;
import com.friendsfantasy.fantasybackend.team.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping("/fixtures/{fixtureId}/teams")
    public ApiResponse<TeamResponse> createTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long fixtureId,
            @Valid @RequestBody CreateTeamRequest request
    ) {
        return ApiResponse.ok("Team created successfully",
                teamService.createTeam(principal.getId(), fixtureId, request));
    }

    @GetMapping("/fixtures/{fixtureId}/teams/my")
    public ApiResponse<List<TeamResponse>> getMyTeams(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long fixtureId
    ) {
        return ApiResponse.ok("My teams fetched successfully",
                teamService.getMyTeams(principal.getId(), fixtureId));
    }

    @GetMapping("/teams/{teamId}")
    public ApiResponse<TeamResponse> getTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long teamId
    ) {
        return ApiResponse.ok("Team fetched successfully",
                teamService.getTeamForOwner(principal.getId(), teamId));
    }

    @PutMapping("/teams/{teamId}")
    public ApiResponse<TeamResponse> updateTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long teamId,
            @Valid @RequestBody CreateTeamRequest request
    ) {
        return ApiResponse.ok("Team updated successfully",
                teamService.updateTeam(principal.getId(), teamId, request));
    }

    @DeleteMapping("/teams/{teamId}")
    public ApiResponse<Map<String, Object>> deleteTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long teamId
    ) {
        teamService.deleteTeam(principal.getId(), teamId);
        return ApiResponse.ok("Team deleted successfully", Map.of("teamId", teamId));
    }
}