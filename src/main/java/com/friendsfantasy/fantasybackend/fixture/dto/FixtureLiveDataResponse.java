package com.friendsfantasy.fantasybackend.fixture.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FixtureLiveDataResponse {
    private Boolean live;
    private String note;
    private Boolean superOver;
    private String superOverStatus;
    private String lastPeriod;
    private Integer revisedTarget;
    private Integer revisedOvers;
    private List<FixtureInningsScoreResponse> innings;
}
