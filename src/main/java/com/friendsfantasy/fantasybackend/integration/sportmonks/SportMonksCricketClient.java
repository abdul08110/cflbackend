package com.friendsfantasy.fantasybackend.integration.sportmonks;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SportMonksCricketClient {

    private static final int MAX_FIXTURE_PAGES = 200;

    @Value("${sportmonks.cricket-base-url}")
    private String cricketBaseUrl;

    @Value("${sportmonks.token}")
    private String apiToken;

    private final ObjectMapper objectMapper;

    private RestClient restClient;

    private RestClient getClient() {
        if (restClient == null) {
            restClient = RestClient.builder()
                    .baseUrl(cricketBaseUrl)
                    .build();
        }
        return restClient;
    }

    public JsonNode getUpcomingFixtures(Long leagueId, String fromDate, String toDate) {
        ObjectNode mergedResponse = null;
        ArrayNode mergedData = objectMapper.createArrayNode();
        Set<Long> seenFixtureIds = new LinkedHashSet<>();

        for (int page = 1; page <= MAX_FIXTURE_PAGES; page++) {
            JsonNode pageResponse = getUpcomingFixturesPage(leagueId, fromDate, toDate, page);

            if (mergedResponse == null) {
                mergedResponse = pageResponse instanceof ObjectNode
                        ? ((ObjectNode) pageResponse).deepCopy()
                        : objectMapper.createObjectNode();
                mergedResponse.set("data", mergedData);
            }

            JsonNode data = pageResponse.path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }

            boolean addedFixtures = false;
            for (JsonNode fixtureNode : data) {
                Long fixtureId = fixtureNode.path("id").canConvertToLong()
                        ? fixtureNode.path("id").asLong()
                        : null;
                if (fixtureId != null && !seenFixtureIds.add(fixtureId)) {
                    continue;
                }
                mergedData.add(fixtureNode);
                addedFixtures = true;
            }

            if (!addedFixtures) {
                break;
            }

            if (!hasMorePages(pageResponse, page)) {
                break;
            }
        }

        if (mergedResponse == null) {
            ObjectNode emptyResponse = objectMapper.createObjectNode();
            emptyResponse.set("data", mergedData);
            return emptyResponse;
        }

        return mergedResponse;
    }

    private JsonNode getUpcomingFixturesPage(Long leagueId, String fromDate, String toDate, int page) {
        String response = getClient().get()
                .uri(uriBuilder -> {
                    uriBuilder
                            .path("/fixtures")
                            .queryParam("api_token", apiToken)
                            .queryParam("filter[starts_between]", fromDate + "," + toDate)
                            .queryParam("include", "localteam,visitorteam,league,season,venue")
                            .queryParam("sort", "starting_at")
                            .queryParam("page", page);
                    if (leagueId != null) {
                        uriBuilder.queryParam("filter[league_id]", leagueId);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(String.class);

        return readTree(response);
    }

    private boolean hasMorePages(JsonNode response, int currentPage) {
        JsonNode pagination = response.path("pagination");
        JsonNode hasMoreNode = pagination.path("has_more");
        if (!hasMoreNode.isMissingNode() && !hasMoreNode.isNull()) {
            if (hasMoreNode.isBoolean()) {
                return hasMoreNode.asBoolean();
            }

            String hasMoreText = hasMoreNode.asText("");
            if (!hasMoreText.isBlank()) {
                return Boolean.parseBoolean(hasMoreText);
            }
        }

        Integer responsePage = intValue(pagination, "current_page");
        Integer lastPage = intValue(pagination, "last_page");
        if (responsePage != null && lastPage != null) {
            return responsePage < lastPage;
        }

        JsonNode nextPageNode = pagination.path("next_page");
        if (!nextPageNode.isMissingNode() && !nextPageNode.isNull()) {
            String nextPage = nextPageNode.asText("");
            if (!nextPage.isBlank() && !"null".equalsIgnoreCase(nextPage)) {
                return true;
            }
        }

        JsonNode nextLinkNode = response.path("links").path("next");
        if (!nextLinkNode.isMissingNode() && !nextLinkNode.isNull()) {
            String nextLink = nextLinkNode.asText("");
            if (!nextLink.isBlank() && !"null".equalsIgnoreCase(nextLink)) {
                return true;
            }
        }

        return currentPage < MAX_FIXTURE_PAGES && response.path("data").isArray() && !response.path("data").isEmpty();
    }

    private Integer intValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.canConvertToInt()) {
            return value.asInt();
        }

        String text = value.asText("");
        if (text.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public JsonNode getFixtureById(Long externalFixtureId) {
        String response = getClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fixtures/{id}")
                        .queryParam("api_token", apiToken)
                        .queryParam("include", "localteam,visitorteam,league,season,venue,runs")
                        .build(externalFixtureId))
                .retrieve()
                .body(String.class);

        return readTree(response);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SportMonks response", e);
        }
    }

    public JsonNode getFixtureWithLineup(Long externalFixtureId) {
        String response = getClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fixtures/{id}")
                        .queryParam("api_token", apiToken)
                        .queryParam("include", "localteam,visitorteam,lineup,lineup.position")
                        .build(externalFixtureId))
                .retrieve()
                .body(String.class);

        return readTree(response);
    }

    public JsonNode getFixtureWithFantasyScoringData(Long externalFixtureId) {
        String response = getClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fixtures/{id}")
                        .queryParam("api_token", apiToken)
                        .queryParam("include", "localteam,visitorteam,league,season,venue,runs,lineup,batting,bowling,balls")
                        .build(externalFixtureId))
                .retrieve()
                .body(String.class);

        return readTree(response);
    }

    public JsonNode getTeamSquad(Long externalTeamId, Long externalSeasonId) {
        String response = getClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/teams/{id}/squad/{seasonId}")
                        .queryParam("api_token", apiToken)
                        .build(externalTeamId, externalSeasonId))
                .retrieve()
                .body(String.class);

        return readTree(response);
    }
}
