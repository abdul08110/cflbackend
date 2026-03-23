package com.friendsfantasy.fantasybackend.room.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AvailableContestFixtureParticipantResponse {
    private Long externalTeamId;
    private String teamName;
    private String shortName;
    private String logoUrl;
    private Boolean isHome;
}