package com.friendsfantasy.fantasybackend.admin.user.controller;

import com.friendsfantasy.fantasybackend.admin.user.dto.AdminUserActivityHistoryResponse;
import com.friendsfantasy.fantasybackend.admin.user.dto.AdminUserStatusActionRequest;
import com.friendsfantasy.fantasybackend.admin.user.dto.AdminUserSummaryResponse;
import com.friendsfantasy.fantasybackend.admin.user.service.AdminUserManagementService;
import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.security.AdminPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserManagementController {

    private final AdminUserManagementService adminUserManagementService;

    @GetMapping
    public ApiResponse<List<AdminUserSummaryResponse>> getUsers(
            @RequestParam(defaultValue = "") String query
    ) {
        return ApiResponse.ok(
                "Users fetched successfully",
                adminUserManagementService.getUsers(query)
        );
    }

    @GetMapping("/{userId}/activity-history")
    public ApiResponse<AdminUserActivityHistoryResponse> getUserActivityHistory(
            @PathVariable Long userId
    ) {
        return ApiResponse.ok(
                "Admin activity history fetched successfully",
                adminUserManagementService.getUserActivityHistory(userId)
        );
    }

    @PatchMapping("/{userId}/block")
    public ApiResponse<AdminUserSummaryResponse> blockUser(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserStatusActionRequest request
    ) {
        return ApiResponse.ok(
                "User blocked successfully",
                adminUserManagementService.blockUser(
                        userId,
                        adminPrincipal.getId(),
                        request.getRemarks()
                )
        );
    }

    @PatchMapping("/{userId}/unblock")
    public ApiResponse<AdminUserSummaryResponse> unblockUser(
            @AuthenticationPrincipal AdminPrincipal adminPrincipal,
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserStatusActionRequest request
    ) {
        return ApiResponse.ok(
                "User activated successfully",
                adminUserManagementService.unblockUser(
                        userId,
                        adminPrincipal.getId(),
                        request.getRemarks()
                )
        );
    }
}
