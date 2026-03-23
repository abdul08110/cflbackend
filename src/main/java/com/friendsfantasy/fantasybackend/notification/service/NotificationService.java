package com.friendsfantasy.fantasybackend.notification.service;

import com.friendsfantasy.fantasybackend.notification.dto.NotificationResponse;
import com.friendsfantasy.fantasybackend.notification.dto.NotificationUnreadCountResponse;
import com.friendsfantasy.fantasybackend.notification.entity.Notification;
import com.friendsfantasy.fantasybackend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapNotification)
                .toList();
    }

    public NotificationUnreadCountResponse getUnreadCount(Long userId) {
        return NotificationUnreadCountResponse.builder()
                .unreadCount(notificationRepository.countByUserIdAndIsReadFalse(userId))
                .build();
    }

    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return mapNotification(notification);
    }

    @Transactional
    public Map<String, Object> markAllAsRead(Long userId) {
        List<Notification> notifications = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);

        for (Notification notification : notifications) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
        }

        notificationRepository.saveAll(notifications);

        return Map.of("updatedCount", notifications.size());
    }

    private NotificationResponse mapNotification(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .payloadJson(notification.getPayloadJson())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }
}
