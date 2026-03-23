package com.friendsfantasy.fantasybackend.room.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SelectCommunityTeamRequest {

    @NotNull(message = "teamId is required")
    private Long teamId;
}
