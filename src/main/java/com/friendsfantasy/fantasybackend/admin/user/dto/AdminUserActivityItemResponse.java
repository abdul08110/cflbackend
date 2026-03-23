package com.friendsfantasy.fantasybackend.admin.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserActivityItemResponse {
    private String activityId;
    private String activityType;
    private Integer points;
    private String remarks;
    private Long adminId;
    private String adminUsername;
    private LocalDateTime createdAt;
}
