package com.friendsfantasy.fantasybackend.room.dto;

import com.friendsfantasy.fantasybackend.contest.dto.LeaderboardEntryResponse;
import com.friendsfantasy.fantasybackend.contest.dto.ContestEntryResponse;
import com.friendsfantasy.fantasybackend.fixture.dto.FixtureLiveDataResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RoomDetailResponse {
    private RoomSummaryResponse community;
    private ContestEntryResponse myEntry;
    private FixtureLiveDataResponse fixtureLiveData;
    private List<RoomMemberResponse> members;
    private List<LeaderboardEntryResponse> leaderboard;
}
