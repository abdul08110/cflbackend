package com.friendsfantasy.fantasybackend.fixture.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FixturePlayerPoolResponse {
    private Long fixturePlayerPoolId;
    private Long playerId;
    private Long externalTeamId;
    private String teamName;
    private String playerName;
    private String shortName;
    private String roleCode;
    private BigDecimal creditValue;
    private BigDecimal selectionPercent;
    private Boolean isAnnounced;
    private Boolean isPlaying;
    private String imageUrl;
}
