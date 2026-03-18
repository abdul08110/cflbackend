package com.friendsfantasy.fantasybackend.room.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RoomMemberResponse {
    private Long userId;
    private String username;
    private String mobile;
    private String role;
    private String status;
    private LocalDateTime joinedAt;
}