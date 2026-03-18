package com.friendsfantasy.fantasybackend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    private String mobileOrUsername;

    @NotBlank
    private String password;

    private String deviceId;
    private String deviceName;
    private String platform;
}