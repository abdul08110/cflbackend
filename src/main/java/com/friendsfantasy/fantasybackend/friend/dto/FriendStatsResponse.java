package com.friendsfantasy.fantasybackend.friend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FriendStatsResponse {
    private Long userId;
    private String username;
    private Integer totalContestsJoined;
    private Integer totalContestsWon;
    private Integer totalPointsWon;
    private Integer totalPointsSpent;
    private BigDecimal winRate;
    private Integer bestRank;
}