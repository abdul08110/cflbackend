package com.friendsfantasy.fantasybackend.admin.auth.controller;

import com.friendsfantasy.fantasybackend.admin.auth.dto.AdminAuthResponse;
import com.friendsfantasy.fantasybackend.admin.auth.dto.AdminLoginRequest;
import com.friendsfantasy.fantasybackend.admin.auth.service.AdminAuthService;
import com.friendsfantasy.fantasybackend.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ApiResponse<AdminAuthResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminAuthResponse response = adminAuthService.login(request);
        return ApiResponse.ok("Admin login successful", response);
    }
}