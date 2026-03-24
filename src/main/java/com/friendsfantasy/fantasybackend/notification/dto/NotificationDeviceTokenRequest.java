package com.friendsfantasy.fantasybackend.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotificationDeviceTokenRequest {

    @NotBlank
    private String deviceId;

    private String deviceName;
    private String platform;
    private String pushToken;
}
