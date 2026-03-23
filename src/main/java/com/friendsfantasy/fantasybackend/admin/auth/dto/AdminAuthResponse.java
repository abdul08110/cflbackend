package com.friendsfantasy.fantasybackend.admin.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAuthResponse {
    private Long adminId;
    private String username;
    private String accessToken;
    private String tokenType;
}