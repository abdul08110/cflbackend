package com.friendsfantasy.fantasybackend.notification.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.notification.dto.NotificationDeviceTokenRequest;
import com.friendsfantasy.fantasybackend.notification.dto.NotificationResponse;
import com.friendsfantasy.fantasybackend.notification.dto.NotificationUnreadCountResponse;
import com.friendsfantasy.fantasybackend.notification.service.NotificationService;
import jakarta.validation.Valid;
import com.friendsfantasy.fantasybackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(
                "Notifications fetched successfully",
                notificationService.getMyNotifications(principal.getId())
        );
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(
                "Unread notification count fetched successfully",
                notificationService.getUnreadCount(principal.getId())
        );
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markAsRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long notificationId
    ) {
        return ApiResponse.ok(
                "Notification marked as read",
                notificationService.markAsRead(principal.getId(), notificationId)
        );
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Object>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.ok(
                "Notifications marked as read",
                notificationService.markAllAsRead(principal.getId())
        );
    }

    @PostMapping("/device-token")
    public ApiResponse<Map<String, Object>> registerDeviceToken(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody NotificationDeviceTokenRequest request
    ) {
        return ApiResponse.ok(
                "Device token updated successfully",
                notificationService.registerDeviceToken(principal.getId(), request)
        );
    }
}
