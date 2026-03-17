package com.friendsfantasy.fantasybackend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "contest_entries")
public class ContestEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "contest_id", nullable = false)
    private Integer contestId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "team_id", nullable = false)
    private Integer teamId;

    public ContestEntry() {
    }

    public ContestEntry(Integer id, Integer contestId, Integer userId, Integer teamId) {
        this.id = id;
        this.contestId = contestId;
        this.userId = userId;
        this.teamId = teamId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getContestId() {
        return contestId;
    }

    public void setContestId(Integer contestId) {
        this.contestId = contestId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }
}