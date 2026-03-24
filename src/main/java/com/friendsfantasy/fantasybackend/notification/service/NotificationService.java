package com.friendsfantasy.fantasybackend.notification.service;

import com.friendsfantasy.fantasybackend.auth.entity.UserSession;
import com.friendsfantasy.fantasybackend.auth.repository.UserSessionRepository;
import com.friendsfantasy.fantasybackend.notification.dto.NotificationDeviceTokenRequest;
import com.friendsfantasy.fantasybackend.notification.dto.NotificationResponse;
import com.friendsfantasy.fantasybackend.notification.dto.NotificationUnreadCountResponse;
import com.friendsfantasy.fantasybackend.notification.entity.Notification;
import com.friendsfantasy.fantasybackend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserSessionRepository userSessionRepository;
    private final PushNotificationService pushNotificationService;

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

    @Transactional
    public Notification createNotification(Long userId, String type, String title, String body, String payloadJson) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .payloadJson(payloadJson)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        dispatchAfterCommit(List.of(saved));
        return saved;
    }

    @Transactional
    public List<Notification> createNotifications(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return List.of();
        }

        for (Notification notification : notifications) {
            if (notification.getIsRead() == null) {
                notification.setIsRead(false);
            }
        }

        List<Notification> saved = notificationRepository.saveAll(notifications);
        dispatchAfterCommit(saved);
        return saved;
    }

    @Transactional
    public Map<String, Object> registerDeviceToken(Long userId, NotificationDeviceTokenRequest request) {
        String deviceId = request.getDeviceId().trim();
        UserSession session = userSessionRepository
                .findFirstByUserIdAndDeviceIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId, deviceId)
                .orElseThrow(() -> new RuntimeException("Active session not found for this device"));

        String pushToken = trimToNull(request.getPushToken());
        String deviceName = trimToNull(request.getDeviceName());
        if (deviceName != null) {
            session.setDeviceName(deviceName);
        }

        String platformText = trimToNull(request.getPlatform());
        if (platformText != null) {
            session.setPlatform(parsePlatform(platformText, session.getPlatform()));
        }

        session.setPushToken(pushToken);
        session.setLastUsedAt(LocalDateTime.now());
        userSessionRepository.save(session);

        return Map.of(
                "deviceId", session.getDeviceId(),
                "platform", session.getPlatform().name(),
                "pushEnabled", pushToken != null
        );
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

    private void dispatchAfterCommit(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            pushNotificationService.sendNotifications(notifications);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pushNotificationService.sendNotifications(notifications);
            }
        });
    }

    private UserSession.Platform parsePlatform(String platformText, UserSession.Platform fallback) {
        try {
            return UserSession.Platform.valueOf(platformText.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback != null ? fallback : UserSession.Platform.ANDROID;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
