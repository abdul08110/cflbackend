package com.friendsfantasy.fantasybackend.contest.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ContestEntryResponse {
    private Long entryId;
    private Long contestId;
    private Long teamId;
    private String teamName;
    private Long roomId;
    private Integer entryFeePoints;
    private BigDecimal fantasyPoints;
    private Integer rankNo;
    private Integer prizePointsAwarded;
    private String status;
    private LocalDateTime joinedAt;
}