package com.friendsfantasy.fantasybackend.room.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.room.dto.*;
import com.friendsfantasy.fantasybackend.room.service.RoomService;
import com.friendsfantasy.fantasybackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ApiResponse<RoomSummaryResponse> createRoom(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        return ApiResponse.ok("Room created successfully", roomService.createRoom(principal.getId(), request));
    }

    @GetMapping("/my")
    public ApiResponse<List<RoomSummaryResponse>> getMyRooms(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok("Rooms fetched successfully", roomService.getMyRooms(principal.getId()));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomDetailResponse> getRoomDetails(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId
    ) {
        return ApiResponse.ok("Room fetched successfully", roomService.getRoomDetails(roomId, principal.getId()));
    }

    @GetMapping("/{roomId}/members")
    public ApiResponse<List<RoomMemberResponse>> getRoomMembers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId
    ) {
        return ApiResponse.ok("Room members fetched successfully", roomService.getRoomMembers(roomId, principal.getId()));
    }

    @PostMapping("/join-by-code")
    public ApiResponse<RoomSummaryResponse> joinByCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody JoinRoomByCodeRequest request
    ) {
        return ApiResponse.ok("Joined room successfully", roomService.joinByCode(principal.getId(), request));
    }

    @PostMapping("/{roomId}/invite")
    public ApiResponse<Map<String, Object>> inviteToRoom(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long roomId,
            @RequestBody InviteToRoomRequest request
    ) {
        return ApiResponse.ok("Invitation sent successfully", roomService.inviteToRoom(principal.getId(), roomId, request));
    }
}