package com.friendsfantasy.fantasybackend.team.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_match_teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMatchTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fixture_id", nullable = false)
    private Long fixtureId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "team_name", nullable = false, length = 100)
    private String teamName;

    @Column(name = "captain_player_id", nullable = false)
    private Long captainPlayerId;

    @Column(name = "vice_captain_player_id", nullable = false)
    private Long viceCaptainPlayerId;

    @Column(name = "total_credits", nullable = false, precision = 5, scale = 1)
    private BigDecimal totalCredits;

    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked = false;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}