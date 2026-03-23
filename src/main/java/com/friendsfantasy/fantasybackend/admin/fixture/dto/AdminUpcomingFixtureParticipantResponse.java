package com.friendsfantasy.fantasybackend.admin.fixture.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUpcomingFixtureParticipantResponse {
    private Long externalTeamId;
    private String teamName;
    private String shortName;
    private String logoUrl;
    private Boolean isHome;
}