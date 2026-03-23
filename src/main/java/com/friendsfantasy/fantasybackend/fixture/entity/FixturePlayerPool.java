package com.friendsfantasy.fantasybackend.fixture.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fixture_player_pool")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixturePlayerPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fixture_id", nullable = false)
    private Long fixtureId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "external_team_id", nullable = false)
    private Long externalTeamId;

    @Column(name = "role_code", nullable = false, length = 20)
    private String roleCode;

    @Column(name = "credit_value", nullable = false, precision = 5, scale = 1)
    private BigDecimal creditValue;

    @Column(name = "is_announced", nullable = false)
    @Builder.Default
    private Boolean isAnnounced = false;

    @Column(name = "is_playing", nullable = false)
    @Builder.Default
    private Boolean isPlaying = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "selection_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal selectionPercent = BigDecimal.ZERO;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
