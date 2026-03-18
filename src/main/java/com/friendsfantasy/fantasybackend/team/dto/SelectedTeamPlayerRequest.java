package com.friendsfantasy.fantasybackend.team.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SelectedTeamPlayerRequest {

    @NotNull
    private Long fixturePlayerPoolId;
}