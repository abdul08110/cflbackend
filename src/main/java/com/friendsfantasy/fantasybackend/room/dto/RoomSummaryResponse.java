package com.friendsfantasy.fantasybackend.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RoomSummaryResponse {
    private Long communityId;
    private Long contestId;
    private Long fixtureId;
    private Long sportId;
    @JsonProperty("createdBy")
    private Long createdByUserId;
    private String createdByUsername;
    private String communityName;
    private String communityCode;
    private Boolean isPrivate;
    private Integer maxSpots;
    private Long joinedMembers;
    private Integer contestCount;
    private Integer joiningPoints;
    private Integer prizePoolPoints;
    private Integer winnerPayoutPoints;
    private String myRole;
    private String status;
    private Boolean isMember;
    private Boolean isInvited;
    private String contestStatus;
    private String fixtureStatus;
    private String fixtureTitle;
    private LocalDateTime fixtureStartTime;
    private LocalDateTime fixtureDeadlineTime;
    private Boolean teamCreated;
    private Boolean canCreateTeam;
    private Boolean canInvite;
    private Boolean canViewParticipantTeams;
    private Boolean canEdit;
    private Boolean canDelete;
}
