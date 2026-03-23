package com.friendsfantasy.fantasybackend.wallet.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WalletTransactionResponse {
    private Long transactionId;
    private String txnType;
    private String direction;
    private Integer points;
    private Integer signedPoints;
    private Integer balanceAfter;
    private String refType;
    private Long refId;
    private String remarks;
    private LocalDateTime createdAt;
}
