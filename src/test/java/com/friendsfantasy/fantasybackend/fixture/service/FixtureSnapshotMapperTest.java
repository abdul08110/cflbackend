package com.friendsfantasy.fantasybackend.fixture.service;

import com.friendsfantasy.fantasybackend.fixture.dto.FixtureInningsScoreResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureLiveDataResponse;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureSnapshotMapperTest {

    private final FixtureSnapshotMapper mapper = new FixtureSnapshotMapper(new ObjectMapper());

    @Test
    void buildSnapshotMapsRunsNoteAndSuperOverState() {
        Fixture fixture = Fixture.builder()
                .id(10L)
                .externalLeagueId(2026L)
                .status("2nd Innings")
                .startTime(LocalDateTime.of(2026, 3, 23, 19, 30))
                .deadlineTime(LocalDateTime.of(2026, 3, 23, 19, 30))
                .rawJson("""
                        {
                          "status": "2nd Innings",
                          "type": "T20",
                          "note": "Scores level. Super Over to follow.",
                          "live": true,
                          "super_over": true,
                          "last_period": "2nd Innings",
                          "rpc_target": 175,
                          "rpc_overs": 20,
                          "league": {"name": "Indian Premier League"},
                          "venue": {"name": "M. Chinnaswamy Stadium"},
                          "runs": [
                            {"inning": "S1", "team_id": 1, "score": 174, "wickets_out": 6, "overs": "20"},
                            {"inning": "S2", "team_id": 2, "score": 174, "wickets_out": 8, "overs": "20"},
                            {"inning": "S3", "team_id": 1, "score": 10, "wickets_out": 1, "overs": "1"}
                          ]
                        }
                        """)
                .build();

        List<FixtureParticipant> participants = List.of(
                FixtureParticipant.builder()
                        .fixtureId(10L)
                        .externalTeamId(1L)
                        .teamName("Royal Challengers Bengaluru")
                        .shortName("RCB")
                        .isHome(true)
                        .build(),
                FixtureParticipant.builder()
                        .fixtureId(10L)
                        .externalTeamId(2L)
                        .teamName("Chennai Super Kings")
                        .shortName("CSK")
                        .isHome(false)
                        .build()
        );

        FixtureSnapshotMapper.FixtureSnapshot snapshot = mapper.buildSnapshot(fixture, participants);
        FixtureLiveDataResponse liveData = snapshot.liveData();

        assertThat(snapshot.league()).isEqualTo("Indian Premier League");
        assertThat(snapshot.venue()).isEqualTo("M. Chinnaswamy Stadium");
        assertThat(snapshot.format()).isEqualTo("T20");
        assertThat(snapshot.note()).isEqualTo("Scores level. Super Over to follow.");

        assertThat(liveData.getLive()).isTrue();
        assertThat(liveData.getSuperOver()).isTrue();
        assertThat(liveData.getSuperOverStatus()).isEqualTo("ACTIVE");
        assertThat(liveData.getRevisedTarget()).isEqualTo(175);
        assertThat(liveData.getRevisedOvers()).isEqualTo(20);

        assertThat(liveData.getInnings()).hasSize(3);
        FixtureInningsScoreResponse first = liveData.getInnings().getFirst();
        FixtureInningsScoreResponse second = liveData.getInnings().get(1);
        FixtureInningsScoreResponse third = liveData.getInnings().get(2);

        assertThat(first.getLabel()).isEqualTo("1st Innings");
        assertThat(first.getSummary()).isEqualTo("RCB 174/6 in 20 overs");

        assertThat(second.getLabel()).isEqualTo("2nd Innings");
        assertThat(second.getCurrent()).isTrue();
        assertThat(second.getSummary()).isEqualTo("CSK 174/8 in 20 overs");

        assertThat(third.getLabel()).isEqualTo("Super Over");
        assertThat(third.getSuperOver()).isTrue();
        assertThat(third.getSummary()).isEqualTo("RCB 10/1 in 1 overs");
    }

    @Test
    void buildSnapshotPreservesCancellationNote() {
        Fixture fixture = Fixture.builder()
                .id(11L)
                .status("Aban.")
                .rawJson("""
                        {
                          "status": "Aban.",
                          "type": "T20",
                          "note": "Match abandoned without a ball bowled"
                        }
                        """)
                .build();

        FixtureSnapshotMapper.FixtureSnapshot snapshot = mapper.buildSnapshot(fixture, List.of());

        assertThat(snapshot.note()).isEqualTo("Match abandoned without a ball bowled");
        assertThat(snapshot.liveData().getNote()).isEqualTo("Match abandoned without a ball bowled");
        assertThat(snapshot.liveData().getInnings()).isEmpty();
    }
}
