package com.friendsfantasy.fantasybackend.auth.controller;

import com.friendsfantasy.fantasybackend.auth.dto.*;
import com.friendsfantasy.fantasybackend.auth.entity.User;
import com.friendsfantasy.fantasybackend.auth.service.AuthService;
import com.friendsfantasy.fantasybackend.auth.service.OtpService;
import com.friendsfantasy.fantasybackend.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OtpService otpService;
    private final AuthService authService;

    @PostMapping("/send-otp")
    public ApiResponse<Map<String, Object>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        String otp = otpService.sendOtp(request.getMobile(), request.getPurpose());

        return ApiResponse.ok("OTP sent successfully", Map.of(
                "mobile", request.getMobile(),
                "purpose", request.getPurpose(),
                "devOtp", otp
        ));
    }

    @PostMapping("/verify-otp")
    public ApiResponse<Map<String, Object>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        otpService.verifyOtp(request.getMobile(), request.getPurpose(), request.getOtp());

        return ApiResponse.ok("OTP verified successfully", Map.of(
                "mobile", request.getMobile(),
                "verified", true
        ));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);

        return ApiResponse.ok("User registered successfully", Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "mobile", user.getMobile()
        ));
    }

    @PostMapping("/login/password")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Login successful", authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok("Token refreshed successfully", authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Object> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.ok("Logout successful", null);
    }
}