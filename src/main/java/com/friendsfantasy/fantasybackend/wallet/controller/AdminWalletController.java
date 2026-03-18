package com.friendsfantasy.fantasybackend.wallet.controller;

import com.friendsfantasy.fantasybackend.common.ApiResponse;
import com.friendsfantasy.fantasybackend.wallet.dto.AdminCreditRequest;
import com.friendsfantasy.fantasybackend.wallet.entity.WalletAccount;
import com.friendsfantasy.fantasybackend.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/wallet")
@RequiredArgsConstructor
public class AdminWalletController {

    private final WalletService walletService;

    @PostMapping("/credit/{userId}")
    public ApiResponse<WalletAccount> creditWallet(
            @PathVariable Long userId,
            @Valid @RequestBody AdminCreditRequest request
    ) {
        WalletAccount wallet = walletService.creditPoints(
                userId,
                request.getPoints(),
                request.getRemarks(),
                1L
        );

        return ApiResponse.ok("Wallet credited successfully", wallet);
    }
}