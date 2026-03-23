package com.friendsfantasy.fantasybackend.team.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateTeamRequest {

    @NotBlank
    private String teamName;

    @Valid
    @NotNull
    private List<SelectedTeamPlayerRequest> players;

    @Valid
    private List<SelectedTeamPlayerRequest> substitutes;

    @NotNull
    private Long captainPlayerId;

    @NotNull
    private Long viceCaptainPlayerId;
}
