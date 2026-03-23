package com.friendsfantasy.fantasybackend.contest.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ContestResponse {
    private Long contestId;
    private Long communityId;
    private Long fixtureId;
    private String contestName;
    private String contestType;
    private Integer maxSpots;
    private Integer spotsFilled;
    private Integer spotsLeft;
    private Integer entryFeePoints;
    private Integer prizePoolPoints;
    private Integer winnerCount;
    private Boolean joinConfirmRequired;
    private Integer firstPrizePoints;
    private String status;
    private List<ContestPrizeResponse> prizes;
}
