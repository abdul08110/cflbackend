package com.friendsfantasy.fantasybackend.room.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRoomRequest {

    @NotNull
    private Long sportId;

    @NotBlank
    private String roomName;

    private Boolean isPrivate = true;

    @Min(2)
    private Integer maxMembers = 25;
}