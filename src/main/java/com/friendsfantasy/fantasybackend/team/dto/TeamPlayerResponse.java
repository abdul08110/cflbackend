package com.friendsfantasy.fantasybackend.team.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TeamPlayerResponse {
    private Long fixturePlayerPoolId;
    private Long playerId;
    private String playerName;
    private String roleCode;
    private String teamSide;
    private BigDecimal creditValue;
    private Boolean isCaptain;
    private Boolean isViceCaptain;
}