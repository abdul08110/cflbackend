package com.friendsfantasy.fantasybackend.fixture.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureDetailResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureParticipantResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureSummaryResponse;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureParticipantRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.integration.sportmonks.SportMonksCricketClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FixtureSyncService {

    private final FixtureRepository fixtureRepository;
    private final FixtureParticipantRepository fixtureParticipantRepository;
    private final SportMonksCricketClient sportMonksCricketClient;
    private final ObjectMapper objectMapper;

    @Value("${sportmonks.ipl-league-id}")
    private Long iplLeagueId;

    @Value("${sportmonks.upcoming-sync-days:30}")
    private int upcomingSyncDays;

    @Value("${app.cricket-sport-id:1}")
    private Long cricketSportId;

    @Transactional
    public int syncUpcomingIplFixtures() {
        LocalDate from = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate to = from.plusDays(upcomingSyncDays);

        JsonNode root = sportMonksCricketClient.getUpcomingFixtures(
                iplLeagueId,
                from.toString(),
                to.toString()
        );

        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return 0;
        }

        int count = 0;
        for (JsonNode fixtureNode : data) {
            upsertFixtureFromNode(fixtureNode);
            count++;
        }

        return count;
    }

    @Transactional
    public void syncFixtureByExternalId(Long externalFixtureId) {
        JsonNode root = sportMonksCricketClient.getFixtureById(externalFixtureId);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new RuntimeException("Fixture not found in SportMonks");
        }
        upsertFixtureFromNode(data);
    }

    public List<FixtureSummaryResponse> getUpcomingFixtures() {
        List<Fixture> fixtures = fixtureRepository
                .findBySportIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                        cricketSportId,
                        LocalDateTime.now()
                );

        List<FixtureSummaryResponse> response = new ArrayList<>();

        for (Fixture fixture : fixtures) {
            List<FixtureParticipantResponse> participants = mapParticipants(fixture.getId());

            response.add(FixtureSummaryResponse.builder()
                    .fixtureId(fixture.getId())
                    .externalFixtureId(fixture.getExternalFixtureId())
                    .title(fixture.getTitle())
                    .status(fixture.getStatus())
                    .startTime(fixture.getStartTime())
                    .deadlineTime(fixture.getDeadlineTime())
                    .participants(participants)
                    .build());
        }

        return response;
    }

    public FixtureDetailResponse getFixtureDetail(Long fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        return FixtureDetailResponse.builder()
                .fixtureId(fixture.getId())
                .externalFixtureId(fixture.getExternalFixtureId())
                .externalLeagueId(fixture.getExternalLeagueId())
                .externalSeasonId(fixture.getExternalSeasonId())
                .title(fixture.getTitle())
                .status(fixture.getStatus())
                .startTime(fixture.getStartTime())
                .deadlineTime(fixture.getDeadlineTime())
                .participants(mapParticipants(fixture.getId()))
                .build();
    }

    private List<FixtureParticipantResponse> mapParticipants(Long fixtureId) {
        List<FixtureParticipant> participants =
                fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixtureId);

        List<FixtureParticipantResponse> response = new ArrayList<>();
        for (FixtureParticipant p : participants) {
            response.add(FixtureParticipantResponse.builder()
                    .externalTeamId(p.getExternalTeamId())
                    .teamName(p.getTeamName())
                    .shortName(p.getShortName())
                    .logoUrl(p.getLogoUrl())
                    .isHome(p.getIsHome())
                    .build());
        }
        return response;
    }

    private void upsertFixtureFromNode(JsonNode fixtureNode) {
        Long externalFixtureId = longValue(fixtureNode, "id");
        if (externalFixtureId == null) {
            return;
        }

        Fixture fixture = fixtureRepository
                .findBySportIdAndExternalFixtureId(cricketSportId, externalFixtureId)
                .orElseGet(Fixture::new);

        fixture.setSportId(cricketSportId);
        fixture.setExternalFixtureId(externalFixtureId);
        fixture.setExternalLeagueId(longValue(fixtureNode, "league_id"));
        fixture.setExternalSeasonId(longValue(fixtureNode, "season_id"));
        fixture.setStatus(textValue(fixtureNode, "status", "UNKNOWN"));

        LocalDateTime startTime = parseUtcDateTime(textValue(fixtureNode, "starting_at", null));
        if (startTime == null) {
            throw new RuntimeException("starting_at missing for fixture " + externalFixtureId);
        }

        fixture.setStartTime(startTime);
        fixture.setDeadlineTime(startTime);
        fixture.setTitle(buildTitle(fixtureNode));
        fixture.setRawJson(writeJson(fixtureNode));
        fixture.setLastSyncedAt(LocalDateTime.now());

        fixture = fixtureRepository.save(fixture);

        fixtureParticipantRepository.deleteByFixtureId(fixture.getId());

        JsonNode localTeam = fixtureNode.path("localteam");
        if (!localTeam.isMissingNode() && !localTeam.isNull()) {
            fixtureParticipantRepository.save(FixtureParticipant.builder()
                    .fixtureId(fixture.getId())
                    .externalTeamId(longValue(localTeam, "id"))
                    .teamName(textValue(localTeam, "name", "Home Team"))
                    .shortName(textValue(localTeam, "code", null))
                    .logoUrl(textValue(localTeam, "image_path", null))
                    .isHome(true)
                    .rawJson(writeJson(localTeam))
                    .build());
        }

        JsonNode visitorTeam = fixtureNode.path("visitorteam");
        if (!visitorTeam.isMissingNode() && !visitorTeam.isNull()) {
            fixtureParticipantRepository.save(FixtureParticipant.builder()
                    .fixtureId(fixture.getId())
                    .externalTeamId(longValue(visitorTeam, "id"))
                    .teamName(textValue(visitorTeam, "name", "Away Team"))
                    .shortName(textValue(visitorTeam, "code", null))
                    .logoUrl(textValue(visitorTeam, "image_path", null))
                    .isHome(false)
                    .rawJson(writeJson(visitorTeam))
                    .build());
        }
    }

    private String buildTitle(JsonNode fixtureNode) {
        String localName = fixtureNode.path("localteam").path("name").asText("");
        String visitorName = fixtureNode.path("visitorteam").path("name").asText("");

        if (!localName.isBlank() && !visitorName.isBlank()) {
            return localName + " vs " + visitorName;
        }

        String round = textValue(fixtureNode, "round", null);
        if (round != null && !round.isBlank()) {
            return "IPL - " + round;
        }

        return "IPL Fixture";
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String textValue(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? fallback : value.asText();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        return value.asLong();
    }

    private LocalDateTime parseUtcDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .atZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                    .toLocalDateTime();
        } catch (Exception e) {
            try {
                return Instant.parse(value)
                        .atZone(ZoneId.of("Asia/Kolkata"))
                        .toLocalDateTime();
            } catch (Exception ex) {
                throw new RuntimeException("Unable to parse fixture datetime: " + value);
            }
        }
    }
}