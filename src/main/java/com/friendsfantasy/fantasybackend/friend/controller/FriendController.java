package com.friendsfantasy.fantasybackend.friend.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.friend.dto.*;
import com.friendsfantasy.fantasybackend.friend.service.FriendService;
import com.friendsfantasy.fantasybackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/friends/request")
    public ApiResponse<FriendRequestResponse> sendFriendRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SendFriendRequestRequest request
    ) {
        return ApiResponse.ok(
                "Friend request sent successfully",
                friendService.sendFriendRequest(principal.getId(), request)
        );
    }

    @GetMapping("/friends/requests/incoming")
    public ApiResponse<List<FriendRequestResponse>> getIncomingRequests(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(
                "Incoming friend requests fetched successfully",
                friendService.getIncomingRequests(principal.getId())
        );
    }

    @GetMapping("/friends/requests/outgoing")
    public ApiResponse<List<FriendRequestResponse>> getOutgoingRequests(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(
                "Outgoing friend requests fetched successfully",
                friendService.getOutgoingRequests(principal.getId())
        );
    }

    @PostMapping("/friends/requests/{requestId}/accept")
    public ApiResponse<FriendRequestResponse> acceptRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long requestId
    ) {
        return ApiResponse.ok(
                "Friend request accepted successfully",
                friendService.acceptRequest(principal.getId(), requestId)
        );
    }

    @PostMapping("/friends/requests/{requestId}/reject")
    public ApiResponse<FriendRequestResponse> rejectRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long requestId
    ) {
        return ApiResponse.ok(
                "Friend request rejected successfully",
                friendService.rejectRequest(principal.getId(), requestId)
        );
    }

    @GetMapping("/friends")
    public ApiResponse<List<FriendResponse>> getFriends(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(
                "Friends fetched successfully",
                friendService.getFriends(principal.getId())
        );
    }

    @DeleteMapping("/friends/{friendUserId}")
    public ApiResponse<Map<String, Object>> unfriend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long friendUserId
    ) {
        return ApiResponse.ok(
                "Friend removed successfully",
                friendService.unfriend(principal.getId(), friendUserId)
        );
    }

    @GetMapping("/friends/{friendUserId}/stats")
    public ApiResponse<FriendStatsResponse> getFriendStats(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long friendUserId
    ) {
        return ApiResponse.ok(
                "Friend stats fetched successfully",
                friendService.getFriendStats(principal.getId(), friendUserId)
        );
    }

    @GetMapping("/users/search")
    public ApiResponse<List<UserSearchResponse>> searchUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String query
    ) {
        return ApiResponse.ok(
                "Users fetched successfully",
                friendService.searchUsers(principal.getId(), query)
        );
    }
}