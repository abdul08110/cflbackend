package com.friendsfantasy.fantasybackend.contest.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "contests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contest {

    public enum ContestType {
        PUBLIC,
        COMMUNITY
    }

    public enum Status {
        DRAFT, OPEN, FULL, CLOSED, LIVE, COMPLETED, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fixture_id", nullable = false)
    private Long fixtureId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "scoring_template_id", nullable = false)
    private Long scoringTemplateId;

    @Column(name = "contest_name", nullable = false, length = 100)
    private String contestName;

    @Enumerated(EnumType.STRING)
    @Column(name = "contest_type", nullable = false, length = 20)
    private ContestType contestType = ContestType.PUBLIC;

    @Column(name = "entry_fee_points", nullable = false)
    private Integer entryFeePoints;

    @Column(name = "prize_pool_points", nullable = false)
    private Integer prizePoolPoints;

    @Column(name = "winner_count", nullable = false)
    private Integer winnerCount;

    @Column(name = "max_spots", nullable = false)
    private Integer maxSpots;

    @Column(name = "spots_filled", nullable = false)
    private Integer spotsFilled = 0;

    @Column(name = "join_confirm_required", nullable = false)
    private Boolean joinConfirmRequired = false;

    @Column(name = "first_prize_points", nullable = false)
    private Integer firstPrizePoints = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
