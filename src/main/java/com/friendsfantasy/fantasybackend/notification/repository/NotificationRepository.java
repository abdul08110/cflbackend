package com.friendsfantasy.fantasybackend.notification.repository;

import com.friendsfantasy.fantasybackend.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndIsReadFalse(Long userId);
    boolean existsByUserIdAndTypeAndPayloadJson(Long userId, String type, String payloadJson);
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
