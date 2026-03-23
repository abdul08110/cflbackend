package com.friendsfantasy.fantasybackend.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long notificationId;
    private String type;
    private String title;
    private String body;
    private String payloadJson;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
