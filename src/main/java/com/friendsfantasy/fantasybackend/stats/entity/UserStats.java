package com.friendsfantasy.fantasybackend.stats.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStats {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Builder.Default
    @Column(name = "total_contests_joined", nullable = false)
    private Integer totalContestsJoined = 0;

    @Builder.Default
    @Column(name = "total_contests_won", nullable = false)
    private Integer totalContestsWon = 0;

    @Builder.Default
    @Column(name = "total_points_won", nullable = false)
    private Integer totalPointsWon = 0;

    @Builder.Default
    @Column(name = "total_points_spent", nullable = false)
    private Integer totalPointsSpent = 0;

    @Builder.Default
    @Column(name = "total_rooms_created", nullable = false)
    private Integer totalRoomsCreated = 0;

    @Builder.Default
    @Column(name = "win_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal winRate = BigDecimal.ZERO;

    @Column(name = "best_rank")
    private Integer bestRank;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}