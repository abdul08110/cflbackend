package com.friendsfantasy.fantasybackend.admin.fixture.service;

import com.friendsfantasy.fantasybackend.admin.fixture.dto.AdminUpcomingFixtureResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureParticipantResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureSummaryResponse;
import com.friendsfantasy.fantasybackend.fixture.service.FixtureSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFixtureAdminServiceTest {

    @Mock
    private FixtureSyncService fixtureSyncService;

    @Test
    void getUpcomingFixturesUsesSharedFixtureFeed() {
        AdminFixtureAdminService service = new AdminFixtureAdminService(fixtureSyncService);

        FixtureSummaryResponse firstFixture = FixtureSummaryResponse.builder()
                .fixtureId(11L)
                .externalFixtureId(1011L)
                .externalLeagueId(3L)
                .title("Mumbai Indians vs Chennai Super Kings")
                .status("NS")
                .league("Indian Premier League")
                .venue("Wankhede Stadium")
                .startTime(LocalDateTime.of(2026, 3, 28, 19, 30))
                .deadlineTime(LocalDateTime.of(2026, 3, 28, 19, 30))
                .participants(List.of(
                        FixtureParticipantResponse.builder()
                                .externalTeamId(1L)
                                .teamName("Mumbai Indians")
                                .shortName("MI")
                                .logoUrl("mi.png")
                                .isHome(true)
                                .build(),
                        FixtureParticipantResponse.builder()
                                .externalTeamId(2L)
                                .teamName("Chennai Super Kings")
                                .shortName("CSK")
                                .logoUrl("csk.png")
                                .isHome(false)
                                .build()
                ))
                .build();

        FixtureSummaryResponse secondFixture = FixtureSummaryResponse.builder()
                .fixtureId(12L)
                .externalFixtureId(1012L)
                .externalLeagueId(7L)
                .title("England vs Australia")
                .status("NS")
                .league("ICC Champions Trophy")
                .venue("Lord's")
                .startTime(LocalDateTime.of(2026, 3, 29, 15, 0))
                .deadlineTime(LocalDateTime.of(2026, 3, 29, 15, 0))
                .participants(List.of())
                .build();

        when(fixtureSyncService.getUpcomingFixtures()).thenReturn(List.of(firstFixture, secondFixture));

        List<AdminUpcomingFixtureResponse> response = service.getUpcomingFixtures();

        assertThat(response).hasSize(2);
        assertThat(response).extracting(AdminUpcomingFixtureResponse::getLeague)
                .containsExactly("Indian Premier League", "ICC Champions Trophy");
        assertThat(response).extracting(AdminUpcomingFixtureResponse::getExternalLeagueId)
                .containsExactly(3L, 7L);
        assertThat(response.getFirst().getParticipants()).hasSize(2);
        assertThat(response.getFirst().getVenue()).isEqualTo("Wankhede Stadium");
    }
}
