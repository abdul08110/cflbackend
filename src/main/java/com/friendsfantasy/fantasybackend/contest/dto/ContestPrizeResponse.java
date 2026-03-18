package com.friendsfantasy.fantasybackend.contest.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContestPrizeResponse {
    private Integer rankFrom;
    private Integer rankTo;
    private Integer prizePoints;
}