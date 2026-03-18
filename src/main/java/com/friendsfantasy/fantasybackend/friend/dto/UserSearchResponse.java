package com.friendsfantasy.fantasybackend.friend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSearchResponse {
    private Long userId;
    private String username;
    private String mobileMasked;
    private Boolean alreadyFriend;
    private Boolean requestPending;
    private String requestDirection;
}