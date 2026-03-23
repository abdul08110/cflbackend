package com.friendsfantasy.fantasybackend.room.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.room.dto.RoomInvitationResponse;
import com.friendsfantasy.fantasybackend.room.dto.RoomSummaryResponse;
import com.friendsfantasy.fantasybackend.room.service.RoomService;
import com.friendsfantasy.fantasybackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/community-invitations", "/api/v1/room-invitations"})
@RequiredArgsConstructor
public class RoomInvitationController {

    private final RoomService roomService;

    @PostMapping("/{invitationId}/accept")
    public ApiResponse<RoomSummaryResponse> acceptInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long invitationId
    ) {
        return ApiResponse.ok("Community invitation accepted successfully",
                roomService.acceptInvitation(principal.getId(), invitationId));
    }

    @PostMapping("/{invitationId}/decline")
    public ApiResponse<Map<String, Object>> declineInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long invitationId
    ) {
        return ApiResponse.ok("Community invitation declined successfully",
                roomService.declineInvitation(principal.getId(), invitationId));
    }

    @GetMapping("/incoming")
    public ApiResponse<List<RoomInvitationResponse>> getIncomingInvitations(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok("Incoming community invitations fetched successfully",
                roomService.getIncomingInvitations(principal.getId()));
    }
}
