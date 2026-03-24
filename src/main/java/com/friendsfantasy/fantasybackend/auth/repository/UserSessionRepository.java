package com.friendsfantasy.fantasybackend.auth.repository;

import com.friendsfantasy.fantasybackend.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByRefreshTokenHashAndRevokedAtIsNull(String refreshTokenHash);

    List<UserSession> findAllByUserIdAndRevokedAtIsNull(Long userId);

    Optional<UserSession> findFirstByUserIdAndDeviceIdAndRevokedAtIsNullOrderByCreatedAtDesc(
            Long userId,
            String deviceId
    );
}
