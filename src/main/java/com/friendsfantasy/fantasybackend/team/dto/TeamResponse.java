package com.friendsfantasy.fantasybackend.team.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class TeamResponse {
    private Long teamId;
    private Long fixtureId;
    private String teamName;
    private Long captainPlayerId;
    private Long viceCaptainPlayerId;
    private BigDecimal totalCredits;
    private Boolean isLocked;
    private List<TeamPlayerResponse> players;
}