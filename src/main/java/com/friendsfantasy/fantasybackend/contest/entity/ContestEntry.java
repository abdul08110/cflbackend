package com.friendsfantasy.fantasybackend.contest.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "contest_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestEntry {

    public enum Status {
        JOINED, REFUNDED, SETTLED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "user_match_team_id", nullable = true)
    private Long userMatchTeamId;

    @Column(name = "wallet_transaction_id", nullable = false)
    private Long walletTransactionId;

    @Column(name = "entry_fee_points", nullable = false)
    private Integer entryFeePoints;

    @Builder.Default
    @Column(name = "fantasy_points", nullable = false, precision = 10, scale = 2)
    private BigDecimal fantasyPoints = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "rank_no")
    private Integer rankNo;

    @Builder.Default
    @Column(name = "prize_points_awarded", nullable = false)
    private Integer prizePointsAwarded = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.JOINED;

    @Column(name = "joined_at", insertable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;
}
