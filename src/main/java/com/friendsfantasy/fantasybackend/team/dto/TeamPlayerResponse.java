package com.friendsfantasy.fantasybackend.team.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeamPlayerResponse {
    private Long fixturePlayerPoolId;
    private Long playerId;
    private String playerName;
    private String roleCode;
    private String teamSide;
    private Boolean isCaptain;
    private Boolean isViceCaptain;
    private Boolean isSubstitute;
    private Integer substitutePriority;
}
