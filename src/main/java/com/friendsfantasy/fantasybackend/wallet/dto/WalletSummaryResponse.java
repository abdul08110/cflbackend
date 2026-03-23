package com.friendsfantasy.fantasybackend.wallet.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WalletSummaryResponse {
    private Long walletId;
    private Integer balance;
    private String currency;
    private Integer totalContestsJoined;
    private Integer totalWinnings;
    private List<WalletTransactionResponse> history;
}
