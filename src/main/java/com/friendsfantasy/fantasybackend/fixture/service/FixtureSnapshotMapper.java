package com.friendsfantasy.fantasybackend.fixture.service;

import com.friendsfantasy.fantasybackend.fixture.dto.FixtureInningsScoreResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureLiveDataResponse;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class FixtureSnapshotMapper {

    private final ObjectMapper objectMapper;

    public FixtureSnapshotMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FixtureSnapshot buildSnapshot(Fixture fixture, List<FixtureParticipant> participants) {
        JsonNode fixtureNode = readJson(fixture.getRawJson());
        return new FixtureSnapshot(
                extractRelationshipText(fixtureNode, "league", "name", fallbackLeague(fixture)),
                extractRelationshipText(fixtureNode, "venue", "name", ""),
                textValue(fixtureNode, "type", ""),
                textValue(fixtureNode, "note", ""),
                buildLiveData(fixture, participants, fixtureNode)
        );
    }

    public FixtureLiveDataResponse buildLiveData(Fixture fixture, List<FixtureParticipant> participants) {
        return buildLiveData(fixture, participants, readJson(fixture.getRawJson()));
    }

    private FixtureLiveDataResponse buildLiveData(
            Fixture fixture,
            List<FixtureParticipant> participants,
            JsonNode fixtureNode
    ) {
        Map<Long, FixtureParticipant> participantsByExternalId = new HashMap<>();
        for (FixtureParticipant participant : participants) {
            if (participant.getExternalTeamId() != null) {
                participantsByExternalId.put(participant.getExternalTeamId(), participant);
            }
        }

        String format = textValue(fixtureNode, "type", "");
        boolean limitedOvers = isLimitedOversFixture(format);
        boolean superOver = booleanValue(fixtureNode, "super_over");
        String status = textValue(fixtureNode, "status", fixture.getStatus());
        String currentScoreboard = resolveCurrentScoreboard(status, textValue(fixtureNode, "last_period", ""));
        List<FixtureInningsScoreResponse> innings = buildInningsScores(
                fixtureNode.path("runs"),
                participantsByExternalId,
                limitedOvers,
                currentScoreboard
        );

        return FixtureLiveDataResponse.builder()
                .live(booleanValue(fixtureNode, "live") || isLiveStatus(status))
                .note(textValue(fixtureNode, "note", ""))
                .superOver(superOver)
                .superOverStatus(resolveSuperOverStatus(superOver, status))
                .lastPeriod(textValue(fixtureNode, "last_period", ""))
                .revisedTarget(nullableIntValue(fixtureNode, "rpc_target"))
                .revisedOvers(nullableIntValue(fixtureNode, "rpc_overs"))
                .innings(innings)
                .build();
    }

    private List<FixtureInningsScoreResponse> buildInningsScores(
            JsonNode runsNode,
            Map<Long, FixtureParticipant> participantsByExternalId,
            boolean limitedOvers,
            String currentScoreboard
    ) {
        if (!runsNode.isArray()) {
            return List.of();
        }

        List<InningsScoreDraft> drafts = new ArrayList<>();
        for (JsonNode runNode : runsNode) {
            String scoreboard = normalizeScoreboard(textValue(runNode, "inning", textValue(runNode, "scoreboard", "")));
            Long teamId = firstNonNull(
                    nullableLongValue(runNode, "team_id"),
                    nullableLongValue(runNode, "team.id")
            );
            Integer score = nullableIntValue(runNode, "score");
            Integer wicketsOut = nullableIntValue(runNode, "wickets_out");
            String overs = firstNonBlank(
                    textValue(runNode, "overs", ""),
                    textValue(runNode, "total_overs_played", "")
            );

            if (scoreboard.isBlank() && teamId == null && score == null && wicketsOut == null && overs.isBlank()) {
                continue;
            }

            drafts.add(new InningsScoreDraft(scoreboard, teamId, score, wicketsOut, overs));
        }

        drafts.sort(Comparator
                .comparingInt((InningsScoreDraft draft) -> scoreboardOrder(draft.scoreboard()))
                .thenComparing(draft -> draft.teamId() == null ? Long.MAX_VALUE : draft.teamId()));

        List<FixtureInningsScoreResponse> response = new ArrayList<>();
        int superOverCounter = 0;

        for (InningsScoreDraft draft : drafts) {
            boolean superOverInning = limitedOvers && scoreboardOrder(draft.scoreboard()) > 2;
            if (superOverInning) {
                superOverCounter++;
            }

            FixtureParticipant participant = draft.teamId() == null ? null : participantsByExternalId.get(draft.teamId());
            String teamName = participant != null
                    ? participant.getTeamName()
                    : textValueFromScoreboardFallback(draft.teamId(), participantsByExternalId);
            String shortName = participant != null ? firstNonBlank(participant.getShortName(), participant.getTeamName()) : teamName;

            response.add(FixtureInningsScoreResponse.builder()
                    .scoreboard(draft.scoreboard())
                    .label(buildLabel(draft.scoreboard(), superOverInning, superOverCounter))
                    .teamId(draft.teamId())
                    .teamName(teamName)
                    .shortName(shortName)
                    .score(draft.score())
                    .wicketsOut(draft.wicketsOut())
                    .overs(draft.overs())
                    .current(!currentScoreboard.isBlank() && currentScoreboard.equals(draft.scoreboard()))
                    .superOver(superOverInning)
                    .summary(buildSummary(shortName, draft.score(), draft.wicketsOut(), draft.overs()))
                    .build());
        }

        if (!response.isEmpty() && response.stream().noneMatch(item -> Boolean.TRUE.equals(item.getCurrent()))) {
            FixtureInningsScoreResponse last = response.getLast();
            response.set(
                    response.size() - 1,
                    FixtureInningsScoreResponse.builder()
                            .scoreboard(last.getScoreboard())
                            .label(last.getLabel())
                            .teamId(last.getTeamId())
                            .teamName(last.getTeamName())
                            .shortName(last.getShortName())
                            .score(last.getScore())
                            .wicketsOut(last.getWicketsOut())
                            .overs(last.getOvers())
                            .current(true)
                            .superOver(last.getSuperOver())
                            .summary(last.getSummary())
                            .build()
            );
        }

        return response;
    }

    private String buildLabel(String scoreboard, boolean superOverInning, int superOverCounter) {
        if (superOverInning) {
            return superOverCounter <= 1 ? "Super Over" : "Super Over " + superOverCounter;
        }

        return switch (scoreboard) {
            case "S1" -> "1st Innings";
            case "S2" -> "2nd Innings";
            case "S3" -> "3rd Innings";
            case "S4" -> "4th Innings";
            default -> scoreboard.isBlank() ? "Innings" : scoreboard;
        };
    }

    private String buildSummary(String shortName, Integer score, Integer wicketsOut, String overs) {
        if (shortName == null || shortName.isBlank()) {
            shortName = "Team";
        }

        if (score == null && (overs == null || overs.isBlank())) {
            return shortName;
        }

        StringBuilder summary = new StringBuilder(shortName);
        if (score != null) {
            summary.append(' ').append(score);
            if (wicketsOut != null) {
                summary.append('/').append(wicketsOut);
            }
        }
        if (overs != null && !overs.isBlank()) {
            summary.append(" in ").append(overs).append(" overs");
        }
        return summary.toString();
    }

    private String resolveSuperOverStatus(boolean superOver, String status) {
        if (!superOver) {
            return "NONE";
        }
        return isFinishedStatus(status) ? "COMPLETED" : "ACTIVE";
    }

    private boolean isLimitedOversFixture(String format) {
        String normalized = compact(format);
        return normalized.contains("T20")
                || normalized.contains("T10")
                || normalized.contains("ODI")
                || normalized.contains("LISTA")
                || normalized.contains("HUNDRED");
    }

    private boolean isLiveStatus(String status) {
        String normalized = compact(status);
        return normalized.contains("INNINGS")
                || normalized.contains("STUMPDAY")
                || normalized.contains("BREAK")
                || normalized.contains("INT")
                || normalized.contains("DELAYED");
    }

    private boolean isFinishedStatus(String status) {
        String normalized = compact(status);
        return normalized.contains("FINISH")
                || normalized.contains("COMPLET")
                || normalized.equals("RESULT")
                || normalized.equals("DONE");
    }

    private String resolveCurrentScoreboard(String status, String lastPeriod) {
        String normalized = compact(firstNonBlank(status, lastPeriod));
        if (normalized.contains("1STINNINGS")) {
            return "S1";
        }
        if (normalized.contains("2NDINNINGS")) {
            return "S2";
        }
        if (normalized.contains("3RDINNINGS")) {
            return "S3";
        }
        if (normalized.contains("4THINNINGS")) {
            return "S4";
        }
        return "";
    }

    private int scoreboardOrder(String scoreboard) {
        return switch (scoreboard) {
            case "S1" -> 1;
            case "S2" -> 2;
            case "S3" -> 3;
            case "S4" -> 4;
            default -> 99;
        };
    }

    private String normalizeScoreboard(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches("S\\d+")) {
            return normalized;
        }
        if (normalized.matches("\\d+")) {
            return "S" + normalized;
        }
        return normalized;
    }

    private String fallbackLeague(Fixture fixture) {
        return fixture.getExternalLeagueId() == null ? "" : "League " + fixture.getExternalLeagueId();
    }

    private String extractRelationshipText(JsonNode fixtureNode, String relation, String field, String fallback) {
        JsonNode relatedNode = fixtureNode.path(relation);
        if (relatedNode.isArray()) {
            if (relatedNode.isEmpty()) {
                return fallback;
            }
            relatedNode = relatedNode.get(0);
        }

        String value = textValue(relatedNode, field, "");
        return value.isBlank() ? fallback : value;
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

    private String textValue(JsonNode node, String path, String fallback) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        return current.isMissingNode() || current.isNull() ? fallback : current.asText(fallback);
    }

    private Integer nullableIntValue(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        if (current.isMissingNode() || current.isNull()) {
            return null;
        }
        if (current.canConvertToInt()) {
            return current.asInt();
        }

        String text = current.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long nullableLongValue(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        if (current.isMissingNode() || current.isNull()) {
            return null;
        }
        if (current.canConvertToLong()) {
            return current.asLong();
        }

        String text = current.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean booleanValue(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        if (current.isMissingNode() || current.isNull()) {
            return false;
        }
        if (current.isBoolean()) {
            return current.asBoolean();
        }
        if (current.isInt() || current.isLong()) {
            return current.asInt() == 1;
        }
        String value = current.asText("").trim().toLowerCase(Locale.ROOT);
        return "true".equals(value) || "1".equals(value) || "yes".equals(value);
    }

    private String compact(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String textValueFromScoreboardFallback(
            Long teamId,
            Map<Long, FixtureParticipant> participantsByExternalId
    ) {
        if (teamId != null && participantsByExternalId.containsKey(teamId)) {
            FixtureParticipant participant = participantsByExternalId.get(teamId);
            return firstNonBlank(participant.getShortName(), participant.getTeamName());
        }
        return "Team";
    }

    public record FixtureSnapshot(
            String league,
            String venue,
            String format,
            String note,
            FixtureLiveDataResponse liveData
    ) {
    }

    private record InningsScoreDraft(
            String scoreboard,
            Long teamId,
            Integer score,
            Integer wicketsOut,
            String overs
    ) {
    }
}
