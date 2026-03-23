package com.friendsfantasy.fantasybackend.team.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_match_team_players")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMatchTeamPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_match_team_id", nullable = false)
    private Long userMatchTeamId;

    @Column(name = "fixture_player_pool_id", nullable = false)
    private Long fixturePlayerPoolId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "role_code", nullable = false, length = 20)
    private String roleCode;

    @Column(name = "team_side", length = 20)
    private String teamSide;

    @Column(name = "credit_value", nullable = false, precision = 5, scale = 1)
    private BigDecimal creditValue;

    @Column(name = "is_captain", nullable = false)
    private Boolean isCaptain = false;

    @Column(name = "is_vice_captain", nullable = false)
    private Boolean isViceCaptain = false;

    @Column(name = "is_substitute", nullable = false)
    private Boolean isSubstitute = false;

    @Column(name = "substitute_priority")
    private Integer substitutePriority;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
