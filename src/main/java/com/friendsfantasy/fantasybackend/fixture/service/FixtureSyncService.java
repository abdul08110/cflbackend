package com.friendsfantasy.fantasybackend.fixture.service;

import com.friendsfantasy.fantasybackend.contest.service.ContestEntryService;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FixtureSyncService {

    private final FixtureRepository fixtureRepository;
    private final FixtureParticipantRepository fixtureParticipantRepository;
    private final SportMonksCricketClient sportMonksCricketClient;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ContestEntryService> contestEntryServiceProvider;
    private final FixtureSnapshotMapper fixtureSnapshotMapper;

    @Value("${sportmonks.cricket-league-ids:}")
    private String cricketLeagueIdsProperty;

    @Value("${sportmonks.default-league-id:1}")
    private Long defaultLeagueId;

    @Value("${sportmonks.upcoming-sync-days:30}")
    private int upcomingSyncDays;

    @Value("${sportmonks.upcoming-sync-stale-minutes:30}")
    private long upcomingSyncStaleMinutes;

    @Value("${sportmonks.upcoming-missing-league-refresh-minutes:5}")
    private long upcomingMissingLeagueRefreshMinutes;

    @Value("${app.cricket-sport-id:1}")
    private Long cricketSportId;

    @Value("${fantasy.fixtures.sync-on-read:false}")
    private boolean syncOnReadEnabled;

    @Value("${app.fixture.live-refresh-min-seconds:30}")
    private long fixtureLiveRefreshMinSeconds;

    private volatile boolean allLeagueBootstrapSyncCompleted;

    @Transactional
    public int syncUpcomingFixtures() {
        LocalDate from = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate to = from.plusDays(upcomingSyncDays);

        int count = 0;
        if (shouldSyncAllAccessibleLeagues()) {
            JsonNode root = sportMonksCricketClient.getUpcomingFixtures(
                    null,
                    from.toString(),
                    to.toString());

            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode fixtureNode : data) {
                    upsertFixtureFromNode(fixtureNode, true);
                    count++;
                }
            }
            allLeagueBootstrapSyncCompleted = true;
            return count;
        }

        for (Long leagueId : resolveConfiguredLeagueIds()) {
            JsonNode root = sportMonksCricketClient.getUpcomingFixtures(
                    leagueId,
                    from.toString(),
                    to.toString());

            JsonNode data = root.path("data");
            if (!data.isArray()) {
                continue;
            }

            for (JsonNode fixtureNode : data) {
                upsertFixtureFromNode(fixtureNode, true);
                count++;
            }
        }

        return count;
    }

    @Transactional
    public void syncFixtureByExternalId(Long externalFixtureId) {
        syncFixtureByExternalId(externalFixtureId, true);
    }

    @Transactional
    public void refreshFixtureMetadataByExternalId(Long externalFixtureId) {
        syncFixtureByExternalId(externalFixtureId, false);
    }

    private void syncFixtureByExternalId(Long externalFixtureId, boolean triggerContestSync) {
        JsonNode root = sportMonksCricketClient.getFixtureById(externalFixtureId);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new RuntimeException("Fixture not found in SportMonks");
        }
        upsertFixtureFromNode(data, triggerContestSync);
    }

    public List<FixtureSummaryResponse> getUpcomingFixtures() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        List<Fixture> fixtures = fixtureRepository
                .findBySportIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                        cricketSportId,
                        now);

        if (syncOnReadEnabled && shouldSyncUpcomingFixtures(fixtures, now)) {
            syncUpcomingFixtures();
            fixtures = fixtureRepository.findBySportIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                    cricketSportId,
                    now
            );
        }

        List<FixtureSummaryResponse> response = new ArrayList<>();

        for (Fixture fixture : fixtures) {
            response.add(mapFixtureSummary(fixture));
        }

        return response;
    }

    public FixtureDetailResponse getFixtureDetail(Long fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        fixture = refreshFixtureSnapshotIfNeeded(fixture);
        List<FixtureParticipant> participants = fixtureParticipantRepository
                .findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixture.getId());
        FixtureSnapshotMapper.FixtureSnapshot snapshot = fixtureSnapshotMapper.buildSnapshot(fixture, participants);

        return FixtureDetailResponse.builder()
                .fixtureId(fixture.getId())
                .externalFixtureId(fixture.getExternalFixtureId())
                .externalLeagueId(fixture.getExternalLeagueId())
                .externalSeasonId(fixture.getExternalSeasonId())
                .title(fixture.getTitle())
                .status(fixture.getStatus())
                .format(snapshot.format())
                .note(snapshot.note())
                .league(snapshot.league())
                .venue(snapshot.venue())
                .startTime(fixture.getStartTime())
                .deadlineTime(fixture.getDeadlineTime())
                .fixtureLiveData(snapshot.liveData())
                .participants(mapParticipants(participants))
                .build();
    }

    private List<FixtureParticipantResponse> mapParticipants(List<FixtureParticipant> participants) {
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

    private void upsertFixtureFromNode(JsonNode fixtureNode, boolean triggerContestSync) {
        Long externalFixtureId = longValue(fixtureNode, "id");
        if (externalFixtureId == null) {
            return;
        }

        Fixture fixture = fixtureRepository
                .findBySportIdAndExternalFixtureId(cricketSportId, externalFixtureId)
                .orElseGet(Fixture::new);

        fixture.setSportId(cricketSportId);
        fixture.setExternalFixtureId(externalFixtureId);
        fixture.setExternalLeagueId(longValue(fixtureNode, "league_id", "league.id"));
        fixture.setExternalSeasonId(longValue(fixtureNode, "season_id", "season.id"));
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
        fixtureRepository.flush();

        List<FixtureParticipant> existingParticipants =
                fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAscForUpdate(fixture.getId());
        Map<Long, FixtureParticipant> participantsByExternalTeamId = new HashMap<>();
        for (FixtureParticipant participant : existingParticipants) {
            if (participant.getExternalTeamId() != null) {
                participantsByExternalTeamId.put(participant.getExternalTeamId(), participant);
            }
        }
        Set<Long> syncedTeamIds = new HashSet<>();

        JsonNode localTeam = fixtureNode.path("localteam");
        if (!localTeam.isMissingNode() && !localTeam.isNull()) {
            upsertParticipant(fixture.getId(), localTeam, true, participantsByExternalTeamId, syncedTeamIds);
        }

        JsonNode visitorTeam = fixtureNode.path("visitorteam");
        if (!visitorTeam.isMissingNode() && !visitorTeam.isNull()) {
            upsertParticipant(fixture.getId(), visitorTeam, false, participantsByExternalTeamId, syncedTeamIds);
        }

        for (FixtureParticipant participant : existingParticipants) {
            if (participant.getExternalTeamId() == null || !syncedTeamIds.contains(participant.getExternalTeamId())) {
                fixtureParticipantRepository.delete(participant);
            }
        }

        if (triggerContestSync) {
            triggerContestUpdatesAfterFixtureSync(fixture.getId());
        }
    }

    private void triggerContestUpdatesAfterFixtureSync(Long fixtureId) {
        ContestEntryService contestEntryService = contestEntryServiceProvider.getIfAvailable();
        if (contestEntryService == null) {
            return;
        }

        contestEntryService.syncCancelledFixtureCommunityContests(fixtureId);
        contestEntryService.syncFixtureFantasyPoints(fixtureId, true);
    }

    private void upsertParticipant(
            Long fixtureId,
            JsonNode teamNode,
            boolean isHome,
            Map<Long, FixtureParticipant> participantsByExternalTeamId,
            Set<Long> syncedTeamIds
    ) {
        Long externalTeamId = longValue(teamNode, "id");
        if (externalTeamId == null) {
            return;
        }

        FixtureParticipant participant = participantsByExternalTeamId.getOrDefault(
                externalTeamId,
                FixtureParticipant.builder().fixtureId(fixtureId).externalTeamId(externalTeamId).build()
        );

        participant.setFixtureId(fixtureId);
        participant.setExternalTeamId(externalTeamId);
        participant.setTeamName(textValue(teamNode, "name", isHome ? "Home Team" : "Away Team"));
        participant.setShortName(textValue(teamNode, "code", null));
        participant.setLogoUrl(textValue(teamNode, "image_path", null));
        participant.setIsHome(isHome);
        participant.setRawJson(writeJson(teamNode));

        participant = saveParticipantResiliently(fixtureId, externalTeamId, participant);
        participantsByExternalTeamId.put(externalTeamId, participant);
        syncedTeamIds.add(externalTeamId);
    }

    private FixtureParticipant saveParticipantResiliently(
            Long fixtureId,
            Long externalTeamId,
            FixtureParticipant participant
    ) {
        try {
            return fixtureParticipantRepository.saveAndFlush(participant);
        } catch (DataIntegrityViolationException ex) {
            FixtureParticipant existing = fixtureParticipantRepository
                    .findByFixtureIdAndExternalTeamId(fixtureId, externalTeamId)
                    .orElseThrow(() -> ex);

            existing.setTeamName(participant.getTeamName());
            existing.setShortName(participant.getShortName());
            existing.setLogoUrl(participant.getLogoUrl());
            existing.setIsHome(participant.getIsHome());
            existing.setRawJson(participant.getRawJson());

            return fixtureParticipantRepository.save(existing);
        }
    }

    private String buildTitle(JsonNode fixtureNode) {
        String localName = fixtureNode.path("localteam").path("name").asText("");
        String visitorName = fixtureNode.path("visitorteam").path("name").asText("");

        if (!localName.isBlank() && !visitorName.isBlank()) {
            return localName + " vs " + visitorName;
        }

        String round = textValue(fixtureNode, "round", null);
        String leagueName = extractRelationshipText(fixtureNode, "league", "name");
        if (round != null && !round.isBlank()) {
            if (!leagueName.isBlank()) {
                return leagueName + " - " + round;
            }
            return "Match - " + round;
        }

        if (!leagueName.isBlank()) {
            return leagueName + " Fixture";
        }

        return "Cricket Fixture";
    }

    private String extractRelationshipText(JsonNode fixtureNode, String relation, String field) {
        JsonNode relatedNode = fixtureNode.path(relation);
        if (relatedNode.isArray()) {
            if (relatedNode.isEmpty()) {
                return "";
            }
            relatedNode = relatedNode.get(0);
        }

        return textValue(relatedNode, field, "");
    }

    private FixtureSummaryResponse mapFixtureSummary(Fixture fixture) {
        List<FixtureParticipant> participants = fixtureParticipantRepository
                .findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixture.getId());
        FixtureSnapshotMapper.FixtureSnapshot snapshot = fixtureSnapshotMapper.buildSnapshot(fixture, participants);

        return FixtureSummaryResponse.builder()
                .fixtureId(fixture.getId())
                .externalFixtureId(fixture.getExternalFixtureId())
                .externalLeagueId(fixture.getExternalLeagueId())
                .title(fixture.getTitle())
                .status(fixture.getStatus())
                .league(snapshot.league())
                .venue(snapshot.venue())
                .startTime(fixture.getStartTime())
                .deadlineTime(fixture.getDeadlineTime())
                .participants(mapParticipants(participants))
                .build();
    }

    private Fixture refreshFixtureSnapshotIfNeeded(Fixture fixture) {
        if (!shouldRefreshFixtureSnapshot(fixture)) {
            return fixture;
        }

        try {
            refreshFixtureMetadataByExternalId(fixture.getExternalFixtureId());
            return fixtureRepository.findById(fixture.getId()).orElse(fixture);
        } catch (Exception ex) {
            return fixture;
        }
    }

    private boolean shouldRefreshFixtureSnapshot(Fixture fixture) {
        if (fixture.getExternalFixtureId() == null) {
            return false;
        }

        if (fixture.getStartTime() == null || fixture.getStartTime().isAfter(LocalDateTime.now())) {
            return false;
        }

        if (fixtureLiveRefreshMinSeconds <= 0 || fixture.getLastSyncedAt() == null) {
            return true;
        }

        LocalDateTime threshold = LocalDateTime.now().minusSeconds(fixtureLiveRefreshMinSeconds);
        return fixture.getLastSyncedAt().isBefore(threshold);
    }

    private boolean shouldSyncUpcomingFixtures(List<Fixture> fixtures, LocalDateTime now) {
        if (fixtures.isEmpty()) {
            return true;
        }

        LocalDateTime latestSync = null;
        Set<Long> availableLeagueIds = new HashSet<>();
        for (Fixture fixture : fixtures) {
            if (fixture.getExternalLeagueId() != null) {
                availableLeagueIds.add(fixture.getExternalLeagueId());
            }
            LocalDateTime lastSyncedAt = fixture.getLastSyncedAt();
            if (lastSyncedAt != null && (latestSync == null || lastSyncedAt.isAfter(latestSync))) {
                latestSync = lastSyncedAt;
            }
        }

        if (latestSync == null || latestSync.isBefore(now.minusMinutes(upcomingSyncStaleMinutes))) {
            return true;
        }

        if (shouldSyncAllAccessibleLeagues()) {
            return !allLeagueBootstrapSyncCompleted;
        }

        for (Long configuredLeagueId : resolveConfiguredLeagueIds()) {
            if (!availableLeagueIds.contains(configuredLeagueId)) {
                return latestSync.isBefore(now.minusMinutes(upcomingMissingLeagueRefreshMinutes));
            }
        }

        return false;
    }

    private List<Long> resolveConfiguredLeagueIds() {
        if (shouldSyncAllAccessibleLeagues()) {
            return List.of();
        }

        Set<Long> leagueIds = new LinkedHashSet<>();
        Arrays.stream(cricketLeagueIdsProperty.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(value -> leagueIds.add(Long.parseLong(value)));

        if (leagueIds.isEmpty()) {
            leagueIds.add(defaultLeagueId);
        }

        return new ArrayList<>(leagueIds);
    }

    private boolean shouldSyncAllAccessibleLeagues() {
        return cricketLeagueIdsProperty == null
                || cricketLeagueIdsProperty.isBlank()
                || "all".equalsIgnoreCase(cricketLeagueIdsProperty.trim());
    }

    private JsonNode readJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String textValue(JsonNode node, String field, String fallback) {
        JsonNode value = nodeAtPath(node, field);
        return (value == null || value.isNull() || value.isMissingNode()) ? fallback : value.asText();
    }

    private Long longValue(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = nodeAtPath(node, field);
            if (value != null && !value.isNull() && !value.isMissingNode()) {
                return value.asLong();
            }
        }
        return null;
    }

    private JsonNode nodeAtPath(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return current;
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
