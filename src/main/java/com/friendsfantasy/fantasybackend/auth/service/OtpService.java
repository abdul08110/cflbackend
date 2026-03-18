package com.friendsfantasy.fantasybackend.auth.service;

import com.friendsfantasy.fantasybackend.auth.entity.OtpRequest;
import com.friendsfantasy.fantasybackend.auth.repository.OtpRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRequestRepository otpRequestRepository;

    @Value("${app.otp.fixed-enabled:true}")
    private boolean fixedOtpEnabled;

    @Value("${app.otp.fixed-value:081181}")
    private String fixedOtpValue;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    public String sendOtp(String mobile, String purposeText) {
        OtpRequest.Purpose purpose = OtpRequest.Purpose.valueOf(purposeText.toUpperCase());

        String otp = fixedOtpEnabled
                ? fixedOtpValue
                : String.format("%06d", new Random().nextInt(1000000));

        OtpRequest request = OtpRequest.builder()
                .mobile(mobile)
                .purpose(purpose)
                .otpCode(otp)
                .status(OtpRequest.Status.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .attemptCount(0)
                .build();

        otpRequestRepository.save(request);

        System.out.println("OTP for " + mobile + " is: " + otp);

        return otp;
    }

    public boolean verifyOtp(String mobile, String purposeText, String otp) {
        OtpRequest.Purpose purpose = OtpRequest.Purpose.valueOf(purposeText.toUpperCase());

        OtpRequest request = otpRequestRepository
                .findTopByMobileAndPurposeAndStatusOrderByCreatedAtDesc(
                        mobile, purpose, OtpRequest.Status.PENDING
                )
                .orElseThrow(() -> new RuntimeException("OTP not found"));

        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            request.setStatus(OtpRequest.Status.EXPIRED);
            otpRequestRepository.save(request);
            throw new RuntimeException("OTP expired");
        }

        if (!request.getOtpCode().equals(otp)) {
            request.setAttemptCount(request.getAttemptCount() + 1);
            otpRequestRepository.save(request);
            throw new RuntimeException("Invalid OTP");
        }

        request.setStatus(OtpRequest.Status.VERIFIED);
        request.setVerifiedAt(LocalDateTime.now());
        otpRequestRepository.save(request);

        return true;
    }

    public boolean isOtpVerified(String mobile, OtpRequest.Purpose purpose) {
        return otpRequestRepository
                .findTopByMobileAndPurposeAndStatusOrderByCreatedAtDesc(
                        mobile, purpose, OtpRequest.Status.VERIFIED
                )
                .isPresent();
    }
}