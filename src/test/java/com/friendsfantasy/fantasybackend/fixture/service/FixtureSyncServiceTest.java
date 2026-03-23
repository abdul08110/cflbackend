package com.friendsfantasy.fantasybackend.fixture.service;

import com.friendsfantasy.fantasybackend.contest.service.ContestEntryService;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureParticipantRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.integration.sportmonks.SportMonksCricketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureSyncServiceTest {

    @Mock
    private FixtureRepository fixtureRepository;

    @Mock
    private FixtureParticipantRepository fixtureParticipantRepository;

    @Mock
    private SportMonksCricketClient sportMonksCricketClient;

    @Mock
    private ObjectProvider<ContestEntryService> contestEntryServiceProvider;

    private FixtureSyncService fixtureSyncService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        fixtureSyncService = new FixtureSyncService(
                fixtureRepository,
                fixtureParticipantRepository,
                sportMonksCricketClient,
                objectMapper,
                contestEntryServiceProvider,
                new FixtureSnapshotMapper(objectMapper)
        );

        ReflectionTestUtils.setField(fixtureSyncService, "cricketSportId", 1L);
        ReflectionTestUtils.setField(fixtureSyncService, "upcomingSyncStaleMinutes", 30L);
        ReflectionTestUtils.setField(fixtureSyncService, "upcomingMissingLeagueRefreshMinutes", 5L);
        ReflectionTestUtils.setField(fixtureSyncService, "cricketLeagueIdsProperty", "all");
    }

    @Test
    void getUpcomingFixturesDoesNotSyncWhenSyncOnReadIsDisabled() {
        ReflectionTestUtils.setField(fixtureSyncService, "syncOnReadEnabled", false);
        when(fixtureRepository.findBySportIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertThat(fixtureSyncService.getUpcomingFixtures()).isEmpty();

        verifyNoInteractions(sportMonksCricketClient);
    }

    @Test
    void getUpcomingFixturesSyncsWhenSyncOnReadIsEnabledAndFixturesAreStale() {
        Fixture fixture = Fixture.builder()
                .id(99L)
                .sportId(1L)
                .externalFixtureId(5001L)
                .title("Team A vs Team B")
                .status("NS")
                .startTime(LocalDateTime.now().plusHours(2))
                .deadlineTime(LocalDateTime.now().plusHours(2))
                .lastSyncedAt(LocalDateTime.now().minusHours(2))
                .build();

        FixtureSyncService spyService = spy(fixtureSyncService);
        ReflectionTestUtils.setField(spyService, "syncOnReadEnabled", true);

        when(fixtureRepository.findBySportIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(fixture), List.of(fixture));
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(99L)).thenReturn(List.of());
        doReturn(0).when(spyService).syncUpcomingFixtures();

        assertThat(spyService.getUpcomingFixtures()).hasSize(1);

        verify(spyService).syncUpcomingFixtures();
    }

    @Test
    void refreshFixtureMetadataFallsBackToNestedLeagueAndSeasonIds() throws Exception {
        when(sportMonksCricketClient.getFixtureById(69518L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": {
                    "id": 69518,
                    "status": "NS",
                    "starting_at": "2026-03-28T14:00:00Z",
                    "league": {
                      "id": 1,
                      "name": "Indian Premier League"
                    },
                    "season": {
                      "id": 2026
                    },
                    "localteam": {
                      "id": 8,
                      "name": "Royal Challengers Bengaluru",
                      "code": "RCB",
                      "image_path": "rcb.png"
                    },
                    "visitorteam": {
                      "id": 9,
                      "name": "Sunrisers Hyderabad",
                      "code": "SRH",
                      "image_path": "srh.png"
                    }
                  }
                }
                """));
        when(fixtureRepository.findBySportIdAndExternalFixtureId(1L, 69518L)).thenReturn(Optional.empty());
        when(fixtureRepository.save(any(Fixture.class))).thenAnswer(invocation -> {
            Fixture fixture = invocation.getArgument(0);
            if (fixture.getId() == null) {
                fixture.setId(1L);
            }
            return fixture;
        });
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAscForUpdate(1L)).thenReturn(List.of());
        when(fixtureParticipantRepository.saveAndFlush(any(FixtureParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        fixtureSyncService.refreshFixtureMetadataByExternalId(69518L);

        ArgumentCaptor<Fixture> fixtureCaptor = ArgumentCaptor.forClass(Fixture.class);
        verify(fixtureRepository, times(1)).save(fixtureCaptor.capture());
        verify(fixtureParticipantRepository, times(2)).saveAndFlush(any(FixtureParticipant.class));

        Fixture savedFixture = fixtureCaptor.getValue();
        assertThat(savedFixture.getExternalLeagueId()).isEqualTo(1L);
        assertThat(savedFixture.getExternalSeasonId()).isEqualTo(2026L);
        assertThat(savedFixture.getTitle()).isEqualTo("Royal Challengers Bengaluru vs Sunrisers Hyderabad");
    }

    @Test
    void refreshFixtureMetadataRecoversFromDuplicateParticipantInsertRace() throws Exception {
        Fixture existingFixture = Fixture.builder()
                .id(1L)
                .sportId(1L)
                .externalFixtureId(69518L)
                .title("Old title")
                .status("NS")
                .startTime(LocalDateTime.now().plusDays(2))
                .deadlineTime(LocalDateTime.now().plusDays(2))
                .build();
        FixtureParticipant existingLocalParticipant = FixtureParticipant.builder()
                .id(81L)
                .fixtureId(1L)
                .externalTeamId(8L)
                .teamName("Royal Challengers Bengaluru")
                .build();

        when(sportMonksCricketClient.getFixtureById(69518L)).thenReturn(new ObjectMapper().readTree("""
                {
                  "data": {
                    "id": 69518,
                    "status": "NS",
                    "starting_at": "2026-03-28T14:00:00Z",
                    "league": {
                      "id": 1,
                      "name": "Indian Premier League"
                    },
                    "season": {
                      "id": 2026
                    },
                    "localteam": {
                      "id": 8,
                      "name": "Royal Challengers Bengaluru",
                      "code": "RCB",
                      "image_path": "rcb.png"
                    },
                    "visitorteam": {
                      "id": 9,
                      "name": "Sunrisers Hyderabad",
                      "code": "SRH",
                      "image_path": "srh.png"
                    }
                  }
                }
                """));
        when(fixtureRepository.findBySportIdAndExternalFixtureId(1L, 69518L)).thenReturn(Optional.of(existingFixture));
        when(fixtureRepository.save(any(Fixture.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAscForUpdate(1L)).thenReturn(List.of());
        when(fixtureParticipantRepository.saveAndFlush(argThat(participant ->
                participant != null && Objects.equals(participant.getExternalTeamId(), 8L))))
                .thenThrow(new DataIntegrityViolationException("Duplicate participant"));
        when(fixtureParticipantRepository.saveAndFlush(argThat(participant ->
                participant != null && Objects.equals(participant.getExternalTeamId(), 9L))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fixtureParticipantRepository.findByFixtureIdAndExternalTeamId(1L, 8L))
                .thenReturn(Optional.of(existingLocalParticipant));
        when(fixtureParticipantRepository.save(argThat(participant ->
                participant != null && Objects.equals(participant.getExternalTeamId(), 8L))))
                .thenAnswer(invocation -> invocation.getArgument(0));

        fixtureSyncService.refreshFixtureMetadataByExternalId(69518L);

        verify(fixtureParticipantRepository).findByFixtureIdAndExternalTeamId(1L, 8L);
        verify(fixtureParticipantRepository).save(argThat(participant ->
                participant != null
                        && Objects.equals(participant.getId(), 81L)
                        && Objects.equals(participant.getShortName(), "RCB")
                        && Objects.equals(participant.getLogoUrl(), "rcb.png")
                        && Boolean.TRUE.equals(participant.getIsHome())));
    }
}
