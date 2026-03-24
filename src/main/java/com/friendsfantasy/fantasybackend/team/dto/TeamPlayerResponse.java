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
    private String teamName;
    private BigDecimal creditValue;
    private String imageUrl;
    private BigDecimal fantasyPoints;
    private Boolean isCaptain;
    private Boolean isViceCaptain;
    private Boolean isSubstitute;
    private Integer substitutePriority;
}
