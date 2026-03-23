package com.friendsfantasy.fantasybackend.admin.user.dto;

import com.friendsfantasy.fantasybackend.auth.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserSummaryResponse {
    private Long userId;
    private String username;
    private String fullName;
    private String mobile;
    private String email;
    private User.Status status;
    private Integer walletBalance;
    private Integer lifetimeEarnedPoints;
    private Integer lifetimeSpentPoints;
    private LocalDateTime createdAt;
}
