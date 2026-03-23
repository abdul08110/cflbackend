package com.friendsfantasy.fantasybackend.room.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RoomAvailableContestResponse {
    private Long contestId;
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

    private String fixtureTitle;
    private LocalDateTime fixtureStartTime;
    private LocalDateTime fixtureDeadlineTime;

    private List<AvailableContestFixtureParticipantResponse> participants;
}