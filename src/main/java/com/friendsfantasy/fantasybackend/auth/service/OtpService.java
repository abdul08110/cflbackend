package com.friendsfantasy.fantasybackend.auth.service;

import com.friendsfantasy.fantasybackend.auth.entity.OtpRequest;
import com.friendsfantasy.fantasybackend.auth.repository.OtpRequestRepository;
import com.friendsfantasy.fantasybackend.auth.repository.UserProfileRepository;
import com.friendsfantasy.fantasybackend.auth.repository.UserRepository;
import com.friendsfantasy.fantasybackend.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class OtpService {

    private final OtpRequestRepository otpRequestRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.otp.fixed-enabled:false}")
    private boolean fixedOtpEnabled;

    @Value("${app.otp.fixed-value:}")
    private String fixedOtpValue;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.brand.name:Community Fantasy League}")
    private String brandName;

    private static final int MAX_RESEND_IN_24_HOURS = 50;
    private static final int MAX_VERIFY_ATTEMPTS = 50;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public String sendOtp(String mobile, String email, String purposeText) {
        OtpRequest.Purpose purpose = parsePurpose(purposeText);

        if (purpose == OtpRequest.Purpose.REGISTER) {
            if (userProfileRepository.existsByEmail(email) || userRepository.existsByEmail(email)) {
                throw ApiException.conflict("Email already registered");
            }
            if (userRepository.existsByMobile(mobile)) {
                throw ApiException.conflict("Mobile already registered");
            }
        }
        long resendCount = otpRequestRepository.countByEmailAndPurposeAndCreatedAtAfter(
                email,
                purpose,
                LocalDateTime.now().minusHours(24));

        if (resendCount >= MAX_RESEND_IN_24_HOURS) {
            throw ApiException.tooManyRequests("OTP resend limit exceeded. Contact admin.");
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

        try {
            sendOtpEmail(email, otp, purpose);
        } catch (RuntimeException ex) {
            otpRequestRepository.delete(request);
            throw ex;
        }

        return otp;
    }

    @Transactional
    public boolean verifyOtp(String mobile, String email, String purposeText, String otp) {
        OtpRequest.Purpose purpose = parsePurpose(purposeText);

        OtpRequest request = otpRequestRepository
                .findTopByEmailAndPurposeAndStatusOrderByCreatedAtDesc(
                        email, purpose, OtpRequest.Status.PENDING)
                .orElseThrow(() -> ApiException.badRequest("OTP not found"));

        if (!request.getMobile().equals(mobile)) {
            throw ApiException.badRequest("Mobile mismatch");
        }
        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            request.setStatus(OtpRequest.Status.EXPIRED);
            otpRequestRepository.save(request);
            throw ApiException.badRequest("OTP expired");
        }

        if (request.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
            request.setStatus(OtpRequest.Status.FAILED);
            otpRequestRepository.save(request);
            throw ApiException.tooManyRequests("Too many wrong OTP attempts");
        }

        if (!request.getOtpCode().equals(otp)) {
            request.setAttemptCount(request.getAttemptCount() + 1);

            if (request.getAttemptCount() >= MAX_VERIFY_ATTEMPTS) {
                request.setStatus(OtpRequest.Status.FAILED);
            }

            otpRequestRepository.save(request);
            throw ApiException.badRequest("Invalid OTP");
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
            throw ApiException.badRequest("Invalid OTP purpose");
        }
    }

    private void sendOtpEmail(String toEmail, String otp, OtpRequest.Purpose purpose) {
        if (toEmail == null || toEmail.isBlank()) {
            throw ApiException.badRequest("Email is required to receive OTP");
        }

        String senderIdentity = (fromEmail != null && !fromEmail.isBlank())
                ? fromEmail
                : mailUsername;

        if (mailHost == null || mailHost.isBlank() || senderIdentity == null || senderIdentity.isBlank()) {
            throw ApiException.serviceUnavailable("Email service is not configured. Please contact support.");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderIdentity);
            message.setTo(toEmail);
            message.setSubject(buildSubject(purpose));
            message.setText(buildBody(otp, purpose));
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Unable to send OTP email to {} for {}", toEmail, purpose, e);
            throw ApiException.serviceUnavailable("Unable to send OTP email right now. Please try again shortly.");
        }
    }

    private String buildSubject(OtpRequest.Purpose purpose) {
        return switch (purpose) {
            case REGISTER -> brandName + " - Registration OTP";
            case LOGIN -> brandName + " - Login OTP";
            case RESET_PASSWORD -> brandName + " - Password Reset OTP";
            case CHANGE_PASSWORD -> brandName + " - Change Password OTP";
            case VERIFY_EMAIL -> brandName + " - Verify Email OTP";
        };
    }

    private String buildBody(String otp, OtpRequest.Purpose purpose) {
        return "Hello,\n\n"
                + "Use this one-time password to complete your " + purposeLabel(purpose) + " request for "
                + brandName + ".\n\n"
                + "OTP: " + otp + "\n"
                + "Valid for: " + otpExpiryMinutes + " minutes\n\n"
                + "For your security, never share this OTP with anyone.\n"
                + "If you did not request it, you can safely ignore this email.\n\n"
                + "Regards,\n"
                + brandName + " Support";
    }

    private String purposeLabel(OtpRequest.Purpose purpose) {
        return switch (purpose) {
            case REGISTER -> "registration";
            case LOGIN -> "login";
            case RESET_PASSWORD -> "password reset";
            case CHANGE_PASSWORD -> "password change";
            case VERIFY_EMAIL -> "email verification";
        };
    }
}
