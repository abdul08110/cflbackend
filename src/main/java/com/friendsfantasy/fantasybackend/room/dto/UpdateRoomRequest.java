package com.friendsfantasy.fantasybackend.room.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRoomRequest {

    @NotBlank
    private String communityName;

    @NotNull
    @Min(2)
    @Max(30)
    private Integer maxSpots;
}
