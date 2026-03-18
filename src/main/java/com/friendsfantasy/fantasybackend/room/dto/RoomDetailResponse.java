package com.friendsfantasy.fantasybackend.room.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RoomDetailResponse {
    private RoomSummaryResponse room;
    private List<RoomMemberResponse> members;
}