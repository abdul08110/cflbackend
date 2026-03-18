package com.friendsfantasy.fantasybackend.contest.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "contest_prizes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestPrize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "rank_from_no", nullable = false)
    private Integer rankFromNo;

    @Column(name = "rank_to_no", nullable = false)
    private Integer rankToNo;

    @Column(name = "prize_points", nullable = false)
    private Integer prizePoints;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}