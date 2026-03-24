package com.friendsfantasy.fantasybackend.room.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RoomAvailableContestResponse {
    private Long contestId;
    private Long communityId;
    private Long fixtureId;
    private String contestName;
    private Integer entryFeePoints;
    private Integer prizePoolPoints;
    private Integer winnerCount;
    private Integer maxSpots;
    private Integer spotsFilled;
    private Integer spotsLeft;
    private Boolean joinConfirmRequired;
    private Integer firstPrizePoints;
    private String contestStatus;
    private Long createdByUserId;
    private String createdByUsername;
    private String fixtureLeague;
    private Integer myEntriesCount;
    private Boolean joinedByMe;
    private Boolean canJoin;
    private Boolean canInvite;

    private String fixtureTitle;
    private LocalDateTime fixtureStartTime;
    private LocalDateTime fixtureDeadlineTime;

    private List<AvailableContestFixtureParticipantResponse> participants;
}
