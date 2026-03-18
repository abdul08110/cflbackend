package com.friendsfantasy.fantasybackend.fixture.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fixture_participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixtureParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fixture_id", nullable = false)
    private Long fixtureId;

    @Column(name = "external_team_id", nullable = false)
    private Long externalTeamId;

    @Column(name = "team_name", nullable = false, length = 100)
    private String teamName;

    @Column(name = "short_name", length = 30)
    private String shortName;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(name = "is_home", nullable = false)
    private Boolean isHome = false;

    @Column(name = "raw_json", columnDefinition = "json")
    private String rawJson;
}