package com.friendsfantasy.fantasybackend.integration.sportmonks;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SportMonksCricketClient {

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
        String response = getClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fixtures")
                        .queryParam("api_token", apiToken)
                        .queryParam("filter[league_id]", leagueId)
                        .queryParam("filter[starts_between]", fromDate + "," + toDate)
                        .queryParam("include", "localteam,visitorteam,league,season,venue")
                        .build())
                .retrieve()
                .body(String.class);

        return readTree(response);
    }

    public JsonNode getFixtureById(Long externalFixtureId) {
        String response = getClient().get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fixtures/{id}")
                        .queryParam("api_token", apiToken)
                        .queryParam("include", "localteam,visitorteam,league,season,venue")
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
                    .queryParam("include", "localteam,visitorteam,lineup")
                    .build(externalFixtureId))
            .retrieve()
            .body(String.class);

    return readTree(response);
}
}