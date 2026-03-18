package com.friendsfantasy.fantasybackend.contest.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class UserStatsResponse {
    private Long userId;
    private Integer totalContestsJoined;
    private Integer totalContestsWon;
    private Integer totalPointsWon;
    private Integer totalPointsSpent;
    private Integer totalRoomsCreated;
    private BigDecimal winRate;
    private Integer bestRank;
}