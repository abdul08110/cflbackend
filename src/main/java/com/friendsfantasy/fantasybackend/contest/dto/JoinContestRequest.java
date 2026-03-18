package com.friendsfantasy.fantasybackend.contest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JoinContestRequest {

    @NotNull
    private Long teamId;

    private Long roomId;

    private Boolean confirmJoin = false;
}