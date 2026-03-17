package com.friendsfantasy.fantasybackend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "match_id", nullable = false)
    private Integer matchId;

    @Column(name = "captain_id", nullable = false)
    private Integer captainId;

    @Column(name = "vice_captain_id", nullable = false)
    private Integer viceCaptainId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Team() {
    }

    public Team(Integer id, Integer userId, Integer matchId, Integer captainId, Integer viceCaptainId, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.matchId = matchId;
        this.captainId = captainId;
        this.viceCaptainId = viceCaptainId;
        this.createdAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getMatchId() {
        return matchId;
    }

    public void setMatchId(Integer matchId) {
        this.matchId = matchId;
    }

    public Integer getCaptainId() {
        return captainId;
    }

    public void setCaptainId(Integer captainId) {
        this.captainId = captainId;
    }

    public Integer getViceCaptainId() {
        return viceCaptainId;
    }

    public void setViceCaptainId(Integer viceCaptainId) {
        this.viceCaptainId = viceCaptainId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}