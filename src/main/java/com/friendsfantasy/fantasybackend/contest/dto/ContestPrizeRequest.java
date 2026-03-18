package com.friendsfantasy.fantasybackend.contest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContestPrizeRequest {

    @NotNull
    @Min(1)
    private Integer rankFrom;

    @NotNull
    @Min(1)
    private Integer rankTo;

    @NotNull
    @Min(1)
    private Integer prizePoints;
}