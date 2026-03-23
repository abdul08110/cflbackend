package com.friendsfantasy.fantasybackend.fixture.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FixtureSummaryResponse {
    private Long fixtureId;
    private Long externalFixtureId;
    private Long externalLeagueId;
    private String title;
    private String status;
    private String league;
    private String venue;
    private LocalDateTime startTime;
    private LocalDateTime deadlineTime;
    private List<FixtureParticipantResponse> participants;
}
