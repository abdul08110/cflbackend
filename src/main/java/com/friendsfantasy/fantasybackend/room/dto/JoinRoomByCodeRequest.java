package com.friendsfantasy.fantasybackend.room.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinRoomByCodeRequest {

    @JsonAlias("roomCode")
    @NotBlank
    private String communityCode;
}
