package com.friendsfantasy.fantasybackend.contest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateEntryPointsRequest {

    @NotNull
    private BigDecimal fantasyPoints;
}