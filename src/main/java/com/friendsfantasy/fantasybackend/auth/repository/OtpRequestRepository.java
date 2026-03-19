package com.friendsfantasy.fantasybackend.auth.repository;

import com.friendsfantasy.fantasybackend.auth.entity.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpRequestRepository extends JpaRepository<OtpRequest, Long> {

    Optional<OtpRequest> findTopByEmailAndPurposeAndStatusOrderByCreatedAtDesc(
            String email,
            OtpRequest.Purpose purpose,
            OtpRequest.Status status
    );

    long countByEmailAndPurposeAndCreatedAtAfter(
            String email,
            OtpRequest.Purpose purpose,
            LocalDateTime createdAt
    );
}