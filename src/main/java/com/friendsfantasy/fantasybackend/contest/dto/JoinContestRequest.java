package com.friendsfantasy.fantasybackend.contest.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JoinContestRequest {

    @NotNull
    private Long teamId;

    @JsonAlias("communityId")
    private Long roomId;

    private Boolean confirmJoin = false;
}
