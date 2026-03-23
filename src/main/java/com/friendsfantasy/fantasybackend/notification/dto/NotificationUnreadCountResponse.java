package com.friendsfantasy.fantasybackend.notification.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationUnreadCountResponse {
    private Long unreadCount;
}
