package com.friendsfantasy.fantasybackend.friend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FriendRequestResponse {
    private Long requestId;
    private Long senderUserId;
    private String senderUsername;
    private Long receiverUserId;
    private String receiverUsername;
    private String status;
    private LocalDateTime respondedAt;
    private LocalDateTime createdAt;
}