package com.friendsfantasy.fantasybackend.room.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRoomRequest {

    @JsonAlias("roomName")
    @NotBlank
    private String communityName;

    @JsonAlias("maxMembers")
    @NotNull
    @Min(2)
    @Max(30)
    private Integer maxSpots = 20;
}
