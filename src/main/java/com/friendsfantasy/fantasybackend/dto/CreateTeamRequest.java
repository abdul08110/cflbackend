package com.friendsfantasy.fantasybackend.dto;

import java.util.List;

public class CreateTeamRequest {

    private Integer userId;
    private Integer matchId;
    private Integer captainId;
    private Integer viceCaptainId;
    private List<Integer> playerIds;

    public CreateTeamRequest() {
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

    public List<Integer> getPlayerIds() {
        return playerIds;
    }

    public void setPlayerIds(List<Integer> playerIds) {
        this.playerIds = playerIds;
    }
}