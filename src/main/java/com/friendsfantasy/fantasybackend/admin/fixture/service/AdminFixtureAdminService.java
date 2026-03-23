package com.friendsfantasy.fantasybackend.admin.fixture.service;

import com.friendsfantasy.fantasybackend.admin.fixture.dto.AdminUpcomingFixtureParticipantResponse;
import com.friendsfantasy.fantasybackend.admin.fixture.dto.AdminUpcomingFixtureResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureParticipantResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureSummaryResponse;
import com.friendsfantasy.fantasybackend.fixture.service.FixtureSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminFixtureAdminService {

    private final FixtureSyncService fixtureSyncService;

    public List<AdminUpcomingFixtureResponse> getUpcomingFixtures() {
        return fixtureSyncService.getUpcomingFixtures().stream()
                .map(this::mapFixture)
                .toList();
    }

    private AdminUpcomingFixtureResponse mapFixture(FixtureSummaryResponse fixture) {
        List<AdminUpcomingFixtureParticipantResponse> participants = fixture.getParticipants() == null
                ? List.of()
                : fixture.getParticipants().stream()
                .map(this::mapParticipant)
                .toList();

        return AdminUpcomingFixtureResponse.builder()
                .fixtureId(fixture.getFixtureId())
                .externalFixtureId(fixture.getExternalFixtureId())
                .externalLeagueId(fixture.getExternalLeagueId())
                .title(fixture.getTitle())
                .status(fixture.getStatus())
                .league(fixture.getLeague())
                .venue(fixture.getVenue())
                .startTime(fixture.getStartTime())
                .deadlineTime(fixture.getDeadlineTime())
                .participants(participants)
                .build();
    }

    private AdminUpcomingFixtureParticipantResponse mapParticipant(FixtureParticipantResponse participant) {
        return AdminUpcomingFixtureParticipantResponse.builder()
                .externalTeamId(participant.getExternalTeamId())
                .teamName(participant.getTeamName())
                .shortName(participant.getShortName())
                .logoUrl(participant.getLogoUrl())
                .isHome(participant.getIsHome())
                .build();
    }
}
