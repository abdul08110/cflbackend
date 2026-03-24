package com.friendsfantasy.fantasybackend.room.dto;

import lombok.Data;

@Data
public class InviteToCommunityContestRequest {
    private String username;
    private String mobile;
    private String inviteMessage;
}
