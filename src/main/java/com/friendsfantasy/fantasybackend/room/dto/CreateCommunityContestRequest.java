package com.friendsfantasy.fantasybackend.room.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCommunityContestRequest {

    @NotNull
    private Long fixtureId;

    @NotNull
    @Min(1)
    private Integer joiningPoints;

    @Min(1)
    @Max(3)
    private Integer winnerCount = 1;

    @Min(2)
    private Integer maxSpots;
}
