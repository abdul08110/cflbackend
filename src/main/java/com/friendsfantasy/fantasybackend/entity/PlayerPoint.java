package com.friendsfantasy.fantasybackend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "player_points")
public class PlayerPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "match_id", nullable = false)
    private Integer matchId;

    @Column(name = "player_id", nullable = false)
    private Integer playerId;

    @Column(nullable = false)
    private Integer points;

    public PlayerPoint() {
    }

    public PlayerPoint(Integer id, Integer matchId, Integer playerId, Integer points) {
        this.id = id;
        this.matchId = matchId;
        this.playerId = playerId;
        this.points = points;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMatchId() {
        return matchId;
    }

    public void setMatchId(Integer matchId) {
        this.matchId = matchId;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }
}