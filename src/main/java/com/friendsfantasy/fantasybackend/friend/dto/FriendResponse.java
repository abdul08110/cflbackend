package com.friendsfantasy.fantasybackend.friend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FriendResponse {
    private Long userId;
    private String username;
    private String mobileMasked;
}