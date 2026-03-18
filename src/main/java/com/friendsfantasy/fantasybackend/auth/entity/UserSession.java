package com.friendsfantasy.fantasybackend.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    public enum Platform {
        ANDROID, IOS, WEB
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Column(name = "refresh_token_hash", nullable = false, length = 255, unique = true)
    private String refreshTokenHash;

    @Column(name = "access_token_version", nullable = false)
    private Integer accessTokenVersion = 1;

    @Column(name = "push_token", length = 255)
    private String pushToken;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}