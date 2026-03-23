package com.friendsfantasy.fantasybackend.room.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRoomRequest {

    @NotNull
    private Long fixtureId;

    @JsonAlias("roomName")
    @NotBlank
    private String communityName;

    private Boolean isPrivate = true;

    @JsonAlias("maxMembers")
    @NotNull
    @Min(2)
    @Max(20)
    private Integer maxSpots = 20;

    @NotNull
    @Min(1)
    private Integer joiningPoints;
}
