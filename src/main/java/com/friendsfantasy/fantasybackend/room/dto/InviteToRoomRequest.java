package com.friendsfantasy.fantasybackend.room.dto;

import lombok.Data;

@Data
public class InviteToRoomRequest {
    private String username;
    private String mobile;
    private String inviteMessage;
}