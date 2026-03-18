package com.friendsfantasy.fantasybackend.player.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sport_id", nullable = false)
    private Long sportId;

    @Column(name = "external_player_id", nullable = false)
    private Long externalPlayerId;

    @Column(name = "player_name", nullable = false, length = 120)
    private String playerName;

    @Column(name = "short_name", length = 60)
    private String shortName;

    @Column(name = "country_name", length = 60)
    private String countryName;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "raw_json", columnDefinition = "json")
    private String rawJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}