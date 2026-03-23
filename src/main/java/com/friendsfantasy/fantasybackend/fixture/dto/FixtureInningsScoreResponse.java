package com.friendsfantasy.fantasybackend.fixture.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FixtureInningsScoreResponse {
    private String scoreboard;
    private String label;
    private Long teamId;
    private String teamName;
    private String shortName;
    private Integer score;
    private Integer wicketsOut;
    private String overs;
    private Boolean current;
    private Boolean superOver;
    private String summary;
}
