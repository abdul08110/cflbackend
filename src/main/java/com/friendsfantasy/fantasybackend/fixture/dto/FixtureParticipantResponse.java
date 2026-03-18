package com.friendsfantasy.fantasybackend.fixture.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FixtureParticipantResponse {
    private Long externalTeamId;
    private String teamName;
    private String shortName;
    private String logoUrl;
    private Boolean isHome;
}