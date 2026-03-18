package com.friendsfantasy.fantasybackend.wallet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    public enum TxnType {
        ADMIN_CREDIT,
        ADMIN_DEBIT,
        CONTEST_JOIN_DEBIT,
        CONTEST_WIN_CREDIT,
        REFUND,
        BONUS,
        ADJUSTMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 30)
    private TxnType txnType;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "ref_type", length = 50)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(length = 255)
    private String remarks;

    @Column(name = "created_by_admin_id")
    private Long createdByAdminId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}