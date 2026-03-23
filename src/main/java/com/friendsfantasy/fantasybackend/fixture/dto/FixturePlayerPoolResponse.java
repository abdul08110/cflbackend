package com.friendsfantasy.fantasybackend.fixture.dto;

import lombok.Builder;
import lombok.Data;

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
    private Boolean isAnnounced;
    private Boolean isPlaying;
    private String imageUrl;
}
