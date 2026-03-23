package com.friendsfantasy.fantasybackend.admin.user.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminUserActivityHistoryResponse {
    private Long userId;
    private Integer totalPointsAdded;
    private Integer totalPointsDeducted;
    private List<AdminUserActivityItemResponse> history;
}
