package com.friendsfantasy.fantasybackend.room.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinRoomByCodeRequest {

    @NotBlank
    private String roomCode;
}