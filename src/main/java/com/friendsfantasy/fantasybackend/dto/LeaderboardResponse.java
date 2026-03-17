package com.friendsfantasy.fantasybackend.dto;

public class LeaderboardResponse {

    private Integer userId;
    private String name;
    private Integer teamId;
    private Integer points;

    public LeaderboardResponse() {
    }

    public LeaderboardResponse(Integer userId, String name, Integer teamId, Integer points) {
        this.userId = userId;
        this.name = name;
        this.teamId = teamId;
        this.points = points;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }
}