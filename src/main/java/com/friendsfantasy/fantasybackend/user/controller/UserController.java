package com.friendsfantasy.fantasybackend.user.controller;

import com.friendsfantasy.fantasybackend.auth.entity.User;
import com.friendsfantasy.fantasybackend.auth.service.AuthService;
import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.security.UserPrincipal;
import com.friendsfantasy.fantasybackend.wallet.entity.WalletAccount;
import com.friendsfantasy.fantasybackend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final WalletService walletService;

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        User user = authService.getById(principal.getId());

        return ApiResponse.ok("User fetched successfully", Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "mobile", user.getMobile(),
                "status", user.getStatus(),
                "mobileVerified", user.getMobileVerified(),
                "biometricEnabled", user.getBiometricEnabled()
        ));
    }

    @GetMapping("/me/wallet")
    public ApiResponse<WalletAccount> getMyWallet(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok("Wallet fetched successfully", walletService.getWallet(principal.getId()));
    }
}