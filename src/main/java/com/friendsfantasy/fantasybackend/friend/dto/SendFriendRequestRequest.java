package com.friendsfantasy.fantasybackend.friend.dto;

import lombok.Data;

@Data
public class SendFriendRequestRequest {
    private Long userId;
    private String username;
}