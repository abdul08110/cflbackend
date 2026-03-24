package com.friendsfantasy.fantasybackend.notification.service;

import com.friendsfantasy.fantasybackend.auth.entity.UserSession;
import com.friendsfantasy.fantasybackend.auth.repository.UserSessionRepository;
import com.friendsfantasy.fantasybackend.notification.entity.Notification;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private static final String FIREBASE_APP_NAME = "friends-fantasy-push";

    private final UserSessionRepository userSessionRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.push.enabled:true}")
    private boolean pushEnabled;

    @Value("${app.push.firebase-service-account-path:}")
    private String firebaseServiceAccountPath;

    @Value("${app.push.android-channel-id:fantasy_general_notifications}")
    private String androidChannelId;

    private volatile FirebaseApp firebaseApp;

    @PostConstruct
    void initialize() {
        if (!pushEnabled) {
            log.info("Push notifications are disabled by configuration.");
            return;
        }

        String path = trimToNull(firebaseServiceAccountPath);
        if (path == null) {
            log.info("Push notifications are enabled, but no Firebase service account path is configured.");
            return;
        }

        try (InputStream inputStream = openServiceAccount(path)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            firebaseApp = FirebaseApp.getApps().stream()
                    .filter(app -> FIREBASE_APP_NAME.equals(app.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options, FIREBASE_APP_NAME));

            log.info("Firebase push notifications initialized successfully.");
        } catch (Exception ex) {
            log.warn("Failed to initialize Firebase push notifications: {}", ex.getMessage());
            firebaseApp = null;
        }
    }

    @Async
    public void sendNotifications(Collection<Notification> notifications) {
        if (firebaseApp == null || notifications == null || notifications.isEmpty()) {
            return;
        }

        for (Notification notification : notifications) {
            sendSingleNotification(notification);
        }
    }

    private void sendSingleNotification(Notification notification) {
        try {
            List<UserSession> sessions = userSessionRepository.findAllByUserIdAndRevokedAtIsNull(notification.getUserId());
            Map<String, List<UserSession>> sessionsByToken = groupSessionsByToken(sessions);
            if (sessionsByToken.isEmpty()) {
                return;
            }

            List<String> tokens = new ArrayList<>(sessionsByToken.keySet());
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .putAllData(buildDataPayload(notification))
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(notification.getTitle())
                            .setBody(notification.getBody())
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(androidChannelId)
                                    .setSound("default")
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").build())
                            .build())
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance(firebaseApp).sendEachForMulticast(message);
            clearInvalidTokens(tokens, sessionsByToken, response);
        } catch (Exception ex) {
            log.warn("Failed to send push notification {} to user {}: {}",
                    notification.getId(), notification.getUserId(), ex.getMessage());
        }
    }

    private Map<String, List<UserSession>> groupSessionsByToken(List<UserSession> sessions) {
        Map<String, List<UserSession>> sessionsByToken = new LinkedHashMap<>();
        for (UserSession session : sessions) {
            String token = trimToNull(session.getPushToken());
            if (token == null) {
                continue;
            }
            sessionsByToken.computeIfAbsent(token, ignored -> new ArrayList<>()).add(session);
        }
        return sessionsByToken;
    }

    private Map<String, String> buildDataPayload(Notification notification) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("type", safeString(notification.getType()));
        data.put("title", safeString(notification.getTitle()));
        data.put("body", safeString(notification.getBody()));
        data.put("route", resolveRoute(notification.getType()));

        String payloadJson = trimToNull(notification.getPayloadJson());
        if (payloadJson != null) {
            data.put("payloadJson", payloadJson);
        }

        return data;
    }

    private void clearInvalidTokens(
            List<String> tokens,
            Map<String, List<UserSession>> sessionsByToken,
            BatchResponse response
    ) {
        if (response.getResponses().isEmpty()) {
            return;
        }

        List<UserSession> invalidSessions = new ArrayList<>();
        for (int i = 0; i < response.getResponses().size() && i < tokens.size(); i++) {
            if (response.getResponses().get(i).isSuccessful()) {
                continue;
            }

            FirebaseMessagingException exception = response.getResponses().get(i).getException();
            if (!isInvalidToken(exception)) {
                continue;
            }

            invalidSessions.addAll(sessionsByToken.getOrDefault(tokens.get(i), List.of()));
        }

        if (invalidSessions.isEmpty()) {
            return;
        }

        for (UserSession session : invalidSessions) {
            session.setPushToken(null);
        }
        userSessionRepository.saveAll(invalidSessions);
    }

    private boolean isInvalidToken(FirebaseMessagingException exception) {
        if (exception == null) {
            return false;
        }

        MessagingErrorCode errorCode = exception.getMessagingErrorCode();
        return errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private String resolveRoute(String type) {
        if (type == null || type.isBlank()) {
            return "/notifications";
        }

        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("FRIEND_")) {
            return "/friends";
        }
        if (normalized.startsWith("COMMUNITY_")) {
            return "/communities";
        }
        return "/notifications";
    }

    private InputStream openServiceAccount(String path) throws IOException {
        String resourcePath = path.contains(":") ? path : "file:" + path;
        Resource resource = resourceLoader.getResource(resourcePath);
        if (!resource.exists()) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        return resource.getInputStream();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
