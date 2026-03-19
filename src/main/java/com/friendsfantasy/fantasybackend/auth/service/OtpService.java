package com.friendsfantasy.fantasybackend.auth.service;

import com.friendsfantasy.fantasybackend.auth.entity.OtpRequest;
import com.friendsfantasy.fantasybackend.auth.repository.OtpRequestRepository;
import com.friendsfantasy.fantasybackend.auth.repository.UserProfileRepository;
import com.friendsfantasy.fantasybackend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRequestRepository otpRequestRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.otp.fixed-enabled:true}")
    private boolean fixedOtpEnabled;

    @Value("${app.otp.fixed-value:081181}")
    private String fixedOtpValue;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.mail.from}")
    private String fromEmail;

    private static final int MAX_RESEND_IN_24_HOURS = 50;
    private static final int MAX_VERIFY_ATTEMPTS = 50;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public String sendOtp(String mobile, String email, String purposeText) {
        OtpRequest.Purpose purpose = parsePurpose(purposeText);

        if (purpose == OtpRequest.Purpose.REGISTER && userProfileRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepository.existsByMobile(mobile)) {
            throw new RuntimeException("Mobile already registered");
        }
        long resendCount = otpRequestRepository.countByEmailAndPurposeAndCreatedAtAfter(
                email,
                purpose,
                LocalDateTime.now().minusHours(24));

        if (resendCount >= MAX_RESEND_IN_24_HOURS) {
            throw new RuntimeException("OTP resend limit exceeded. Contact admin.");
        }

        String otp = fixedOtpEnabled
                ? fixedOtpValue
                : String.format("%06d", new Random().nextInt(1000000));

        OtpRequest request = OtpRequest.builder()
                .mobile(mobile)
                .email(email)
                .purpose(purpose)
                .otpCode(otp)
                .status(OtpRequest.Status.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .attemptCount(0)
                .build();

        otpRequestRepository.save(request);

        sendOtpEmail(email, otp, purpose);

        return otp;
    }

    @Transactional
    public boolean verifyOtp(String mobile, String email, String purposeText, String otp) {
        OtpRequest.Purpose purpose = parsePurpose(purposeText);

        OtpRequest request = otpRequestRepository
                .findTopByEmailAndPurposeAndStatusOrderByCreatedAtDesc(
                        email, purpose, OtpRequest.Status.PENDING)
                .orElseThrow(() -> new RuntimeException("OTP not found"));

        if (!request.getMobile().equals(mobile)) {
            throw new RuntimeException("Mobile mismatch");
        }
        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            request.setStatus(OtpRequest.Status.EXPIRED);
            otpRequestRepository.save(request);
            throw new RuntimeException("OTP expired");
        }

        if (request.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
            request.setStatus(OtpRequest.Status.FAILED);
            otpRequestRepository.save(request);
            throw new RuntimeException("Too many wrong OTP attempts");
        }

        if (!request.getOtpCode().equals(otp)) {
            request.setAttemptCount(request.getAttemptCount() + 1);

            if (request.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
                request.setStatus(OtpRequest.Status.FAILED);
            }

            otpRequestRepository.save(request);
            throw new RuntimeException("Invalid OTP");
        }

        request.setStatus(OtpRequest.Status.VERIFIED);
        request.setVerifiedAt(LocalDateTime.now());
        otpRequestRepository.save(request);

        return true;
    }

    public boolean isOtpVerified(String email, OtpRequest.Purpose purpose) {
        return otpRequestRepository
                .findTopByEmailAndPurposeAndStatusOrderByCreatedAtDesc(
                        email, purpose, OtpRequest.Status.VERIFIED)
                .isPresent();
    }

    private OtpRequest.Purpose parsePurpose(String purposeText) {
        try {
            return OtpRequest.Purpose.valueOf(purposeText.trim().toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Invalid OTP purpose");
        }
    }

    private void sendOtpEmail(String toEmail, String otp, OtpRequest.Purpose purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(buildSubject(purpose));
            message.setText(buildBody(otp, purpose));
            mailSender.send(message);
        } catch (MailException e) {
            throw new RuntimeException("Failed to send OTP email");
        }
    }

    private String buildSubject(OtpRequest.Purpose purpose) {
        return switch (purpose) {
            case REGISTER -> "Friends Fantasy - Registration OTP";
            case LOGIN -> "Friends Fantasy - Login OTP";
            case RESET_PASSWORD -> "Friends Fantasy - Reset Password OTP";
            case CHANGE_PASSWORD -> "Friends Fantasy - Change Password OTP";
            case VERIFY_EMAIL -> "Friends Fantasy - Verify Email OTP";
        };
    }

    private String buildBody(String otp, OtpRequest.Purpose purpose) {
        return "Hello,\n\n"
                + "Your OTP for " + purpose.name().replace("_", " ").toLowerCase() + " is: " + otp + "\n\n"
                + "This OTP is valid for " + otpExpiryMinutes + " minutes.\n"
                + "Do not share this OTP with anyone.\n\n"
                + "Regards,\n"
                + "Friends Fantasy Team";
    }
}