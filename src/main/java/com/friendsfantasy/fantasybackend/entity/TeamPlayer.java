package com.friendsfantasy.fantasybackend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "team_players")
public class TeamPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "team_id", nullable = false)
    private Integer teamId;

    @Column(name = "player_id", nullable = false)
    private Integer playerId;

    public TeamPlayer() {
    }

    public TeamPlayer(Integer id, Integer teamId, Integer playerId) {
        this.id = id;
        this.teamId = teamId;
        this.playerId = playerId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Integer playerId) {
        this.playerId = playerId;
    }
}