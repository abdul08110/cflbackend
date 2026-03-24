package com.friendsfantasy.fantasybackend.room.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RoomInvitationResponse {
    private Long invitationId;
    private Long communityId;
    private String communityName;
    private String invitedBy;
    private String invitedByUsername;
    private Integer joiningPoints;
    private Integer maxSpots;
    private Long joinedMembers;
    private Long invitedByUserId;
    private Long invitedUserId;
    private String invitedMobile;
    private String invitedUsername;
    private String status;
    private String inviteMessage;
    private LocalDateTime respondedAt;
    private LocalDateTime createdAt;
}
