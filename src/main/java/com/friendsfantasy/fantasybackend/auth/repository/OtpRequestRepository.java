package com.friendsfantasy.fantasybackend.auth.repository;

import com.friendsfantasy.fantasybackend.auth.entity.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRequestRepository extends JpaRepository<OtpRequest, Long> {
    Optional<OtpRequest> findTopByMobileAndPurposeAndStatusOrderByCreatedAtDesc(
            String mobile,
            OtpRequest.Purpose purpose,
            OtpRequest.Status status
    );
}