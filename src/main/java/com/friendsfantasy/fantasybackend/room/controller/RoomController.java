package com.friendsfantasy.fantasybackend.room.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.contest.dto.ContestEntryResponse;
import com.friendsfantasy.fantasybackend.room.dto.*;
import com.friendsfantasy.fantasybackend.room.service.RoomService;
import com.friendsfantasy.fantasybackend.security.UserPrincipal;
import com.friendsfantasy.fantasybackend.team.dto.CreateTeamRequest;
import com.friendsfantasy.fantasybackend.team.dto.TeamResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/communities", "/api/v1/rooms"})
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ApiResponse<RoomSummaryResponse> createRoom(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        return ApiResponse.ok("Community created successfully", roomService.createRoom(principal.getId(), request));
    }

    @GetMapping("/my")
    public ApiResponse<List<RoomSummaryResponse>> getMyRooms(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok("Communities fetched successfully", roomService.getMyRooms(principal.getId()));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomDetailResponse> getRoomDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId
    ) {
        return ApiResponse.ok("Community fetched successfully", roomService.getRoomDetails(roomId, principal.getId()));
    }

    @GetMapping("/{roomId}/members")
    public ApiResponse<List<RoomMemberResponse>> getRoomMembers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId
    ) {
        return ApiResponse.ok("Community members fetched successfully", roomService.getRoomMembers(roomId, principal.getId()));
    }

    @GetMapping("/{roomId}/teams/{teamId}")
    public ApiResponse<TeamResponse> getCommunityTeamView(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId,
            @PathVariable Long teamId
    ) {
        return ApiResponse.ok(
                "Community team fetched successfully",
                roomService.getCommunityTeamView(roomId, teamId, principal.getId())
        );
    }

    @PostMapping("/join-by-code")
    public ApiResponse<RoomSummaryResponse> joinByCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody JoinRoomByCodeRequest request
    ) {
        return ApiResponse.ok("Joined community successfully", roomService.joinByCode(principal.getId(), request));
    }

    @PostMapping("/{roomId}/invite")
    public ApiResponse<Map<String, Object>> inviteToRoom(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId,
            @RequestBody InviteToRoomRequest request
    ) {
        return ApiResponse.ok("Community invitation sent successfully",
                roomService.inviteToRoom(principal.getId(), roomId, request));
    }

    @PostMapping("/{roomId}/team")
    public ApiResponse<TeamResponse> createCommunityTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId,
            @Valid @RequestBody CreateTeamRequest request
    ) {
        return ApiResponse.ok("Community team created successfully",
                roomService.createCommunityTeam(principal.getId(), roomId, request));
    }

    @PutMapping("/{roomId}/team")
    public ApiResponse<ContestEntryResponse> selectCommunityTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId,
            @Valid @RequestBody SelectCommunityTeamRequest request
    ) {
        return ApiResponse.ok("Community team selected successfully",
                roomService.selectCommunityTeam(principal.getId(), roomId, request));
    }
}
