package com.friendsfantasy.fantasybackend.fixture.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fixtures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fixture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sport_id", nullable = false)
    private Long sportId;

    @Column(name = "external_fixture_id", nullable = false)
    private Long externalFixtureId;

    @Column(name = "external_league_id")
    private Long externalLeagueId;

    @Column(name = "external_season_id")
    private Long externalSeasonId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "deadline_time", nullable = false)
    private LocalDateTime deadlineTime;

    @Column(name = "raw_json", columnDefinition = "json")
    private String rawJson;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_score_synced_at")
    private LocalDateTime lastScoreSyncedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
