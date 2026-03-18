package com.friendsfantasy.fantasybackend.contest.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LeaderboardEntryResponse {
    private Integer position;
    private Long entryId;
    private Long userId;
    private String username;
    private Long teamId;
    private String teamName;
    private BigDecimal fantasyPoints;
    private Integer rankNo;
    private Integer prizePointsAwarded;
    private String status;
}