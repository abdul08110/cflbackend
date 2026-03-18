package com.friendsfantasy.fantasybackend.contest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateContestRequest {

    @NotBlank
    private String contestName;

    @NotNull
    @Min(1)
    private Integer maxSpots;

    @NotNull
    @Min(1)
    private Integer entryFeePoints;

    @NotNull
    @Min(1)
    private Integer prizePoolPoints;

    @NotNull
    @Min(1)
    private Integer winnerCount;

    private Boolean joinConfirmRequired = false;

    @NotNull
    @Min(1)
    private Long scoringTemplateId = 1L;

    @Valid
    @NotNull
    private List<ContestPrizeRequest> prizes;
}