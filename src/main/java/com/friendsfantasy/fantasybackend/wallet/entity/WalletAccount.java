package com.friendsfantasy.fantasybackend.wallet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletAccount {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "balance_points", nullable = false)
    private Integer balancePoints = 0;

    @Column(name = "lifetime_earned_points", nullable = false)
    private Integer lifetimeEarnedPoints = 0;

    @Column(name = "lifetime_spent_points", nullable = false)
    private Integer lifetimeSpentPoints = 0;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}