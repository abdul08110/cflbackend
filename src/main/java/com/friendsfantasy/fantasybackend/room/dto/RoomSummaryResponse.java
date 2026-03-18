package com.friendsfantasy.fantasybackend.room.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomSummaryResponse {
    private Long roomId;
    private Long sportId;
    private String roomName;
    private String roomCode;
    private Boolean isPrivate;
    private Integer maxMembers;
    private Long memberCount;
    private String myRole;
    private String status;
}