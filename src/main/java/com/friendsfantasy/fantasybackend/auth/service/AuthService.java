package com.friendsfantasy.fantasybackend.auth.service;

import com.friendsfantasy.fantasybackend.common.ApiException;
import com.friendsfantasy.fantasybackend.auth.dto.*;
import com.friendsfantasy.fantasybackend.auth.entity.OtpRequest;
import com.friendsfantasy.fantasybackend.auth.entity.User;
import com.friendsfantasy.fantasybackend.auth.entity.UserProfile;
import com.friendsfantasy.fantasybackend.auth.entity.UserSession;
import com.friendsfantasy.fantasybackend.auth.repository.*;
import com.friendsfantasy.fantasybackend.security.JwtService;
import com.friendsfantasy.fantasybackend.security.TokenHashUtil;
import com.friendsfantasy.fantasybackend.security.UserPrincipal;
import com.friendsfantasy.fantasybackend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.friendsfantasy.fantasybackend.stats.service.UserStatsService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSessionRepository userSessionRepository;
    private final OtpService otpService;
    private final WalletService walletService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHashUtil tokenHashUtil;
    private final UserStatsService userStatsService;

    @Value("${jwt.refresh-token-expiry-days:30}")
    private long refreshTokenExpiryDays;

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw ApiException.conflict("Username already exists");
        }

        if (userRepository.existsByMobile(request.getMobile())) {
            throw ApiException.conflict("Mobile already registered");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw ApiException.conflict("Email already registered");
        }

        if (!otpService.isOtpVerified(request.getEmail(), OtpRequest.Purpose.REGISTER)) {
            throw ApiException.badRequest("Email OTP not verified");
        }

        User user = User.builder()
                .username(request.getUsername())
                .mobile(request.getMobile())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(User.Status.ACTIVE)
                .mobileVerified(false)
                .biometricEnabled(false)
                .lastLoginAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);

        UserProfile profile = UserProfile.builder()
                .user(user)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .build();

        userProfileRepository.save(profile);

        walletService.createWalletIfNotExists(user.getId());
        userStatsService.createIfMissing(user.getId());

        return user;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = findUserByLoginIdentifier(request.getMobileOrUsername())
                .orElseThrow(() -> ApiException.unauthorized("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid credentials");
        }

        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.forbidden("User account is not active");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);

        UserPrincipal principal = new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getMobile(),
                user.getPasswordHash(),
                true);

        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);

        String deviceId = (request.getDeviceId() == null || request.getDeviceId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getDeviceId();

        String deviceName = request.getDeviceName();
        UserSession.Platform platform = parsePlatform(request.getPlatform());

        UserSession session = UserSession.builder()
                .userId(user.getId())
                .deviceId(deviceId)
                .deviceName(deviceName)
                .platform(platform)
                .pushToken(trimToNull(request.getPushToken()))
                .refreshTokenHash(tokenHashUtil.sha256(refreshToken))
                .accessTokenVersion(1)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays))
                .lastUsedAt(LocalDateTime.now())
                .build();

        userSessionRepository.save(session);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(profile != null ? profile.getFullName() : user.getUsername())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .build();
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        String tokenType = jwtService.extractType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw ApiException.unauthorized("Invalid refresh token");
        }

        String refreshHash = tokenHashUtil.sha256(refreshToken);

        UserSession session = userSessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(refreshHash)
                .orElseThrow(() -> ApiException.unauthorized("Refresh session not found"));

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.unauthorized("Refresh token expired");
        }

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.forbidden("User account is not active");
        }

        UserPrincipal principal = new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getMobile(),
                user.getPasswordHash(),
                true);

        String newAccessToken = jwtService.generateAccessToken(principal);
        String newRefreshToken = jwtService.generateRefreshToken(principal);

        session.setRefreshTokenHash(tokenHashUtil.sha256(newRefreshToken));
        session.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays));
        session.setLastUsedAt(LocalDateTime.now());
        userSessionRepository.save(session);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(resolveFullName(user))
                .mobile(user.getMobile())
                .email(user.getEmail())
                .build();
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String refreshHash = tokenHashUtil.sha256(request.getRefreshToken());

        UserSession session = userSessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(refreshHash)
                .orElseThrow(() -> ApiException.unauthorized("Session not found"));

        session.setRevokedAt(LocalDateTime.now());
        userSessionRepository.save(session);
    }

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    public Optional<UserProfile> getProfileByUserId(Long userId) {
        return userProfileRepository.findById(userId);
    }

    @Transactional
    public Map<String, Object> sendForgotPasswordOtp(ForgotPasswordRequest request) {
        User user = findActiveUserByIdentifier(request.getIdentifier());
        otpService.sendOtp(user.getMobile(), user.getEmail(), OtpRequest.Purpose.RESET_PASSWORD.name());

        return Map.of(
                "userId", user.getId(),
                "email", maskEmail(user.getEmail()),
                "message", "OTP sent to your registered email"
        );
    }

    @Transactional
    public Map<String, Object> resetPassword(ResetPasswordRequest request) {
        validateNewPassword(request.getNewPassword(), request.getConfirmPassword());

        User user = findActiveUserByIdentifier(request.getIdentifier());
        otpService.verifyOtp(
                user.getMobile(),
                user.getEmail(),
                OtpRequest.Purpose.RESET_PASSWORD.name(),
                request.getOtp().trim()
        );

        updatePassword(user, request.getNewPassword().trim());

        return Map.of(
                "userId", user.getId(),
                "passwordReset", true
        );
    }

    @Transactional
    public Map<String, Object> changePassword(Long userId, ChangePasswordRequest request) {
        validateNewPassword(request.getNewPassword(), request.getConfirmPassword());

        User user = getById(userId);
        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.forbidden("User account is not active");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Old password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("New password must be different from the old password");
        }

        updatePassword(user, request.getNewPassword().trim());

        return Map.of(
                "userId", user.getId(),
                "passwordChanged", true
        );
    }

    @Transactional
    public Map<String, Object> sendChangePasswordOtp(Long userId) {
        User user = getById(userId);
        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.forbidden("User account is not active");
        }

        otpService.sendOtp(user.getMobile(), user.getEmail(), OtpRequest.Purpose.CHANGE_PASSWORD.name());

        return Map.of(
                "userId", user.getId(),
                "email", maskEmail(user.getEmail()),
                "message", "OTP sent to your registered email"
        );
    }

    @Transactional
    public Map<String, Object> changePasswordWithOtp(Long userId, ChangePasswordWithOtpRequest request) {
        validateNewPassword(request.getNewPassword(), request.getConfirmPassword());

        User user = getById(userId);
        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.forbidden("User account is not active");
        }

        otpService.verifyOtp(
                user.getMobile(),
                user.getEmail(),
                OtpRequest.Purpose.CHANGE_PASSWORD.name(),
                request.getOtp().trim()
        );

        updatePassword(user, request.getNewPassword().trim());
        revokeActiveSessions(user.getId());

        return Map.of(
                "userId", user.getId(),
                "passwordChanged", true
        );
    }

    private Optional<User> findUserByLoginIdentifier(String identifier) {
        String raw = identifier == null ? "" : identifier.trim();
        return userRepository.findByMobile(raw)
                .or(() -> userRepository.findByUsername(raw))
                .or(() -> userRepository.findByEmail(raw));
    }

    private User findActiveUserByIdentifier(String identifier) {
        User user = findUserByLoginIdentifier(identifier)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (user.getStatus() != User.Status.ACTIVE) {
            throw ApiException.forbidden("User account is not active");
        }
        return user;
    }

    private void validateNewPassword(String newPassword, String confirmPassword) {
        String trimmedPassword = newPassword == null ? "" : newPassword.trim();
        String trimmedConfirm = confirmPassword == null ? "" : confirmPassword.trim();

        if (trimmedPassword.isBlank()) {
            throw ApiException.badRequest("New password is required");
        }
        if (trimmedPassword.length() < 6) {
            throw ApiException.badRequest("New password must be at least 6 characters");
        }
        if (!trimmedPassword.equals(trimmedConfirm)) {
            throw ApiException.badRequest("Passwords do not match");
        }
    }

    private void updatePassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void revokeActiveSessions(Long userId) {
        List<UserSession> activeSessions = userSessionRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        if (activeSessions.isEmpty()) {
            return;
        }

        LocalDateTime revokedAt = LocalDateTime.now();
        for (UserSession session : activeSessions) {
            session.setRevokedAt(revokedAt);
        }
        userSessionRepository.saveAll(activeSessions);
    }

    private String resolveFullName(User user) {
        return userProfileRepository.findById(user.getId())
                .map(UserProfile::getFullName)
                .filter(value -> !value.isBlank())
                .orElse(user.getUsername());
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "registered email";
        }

        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];

        if (local.length() <= 2) {
            return "*".repeat(local.length()) + "@" + domain;
        }

        return local.substring(0, 2) + "*".repeat(local.length() - 2) + "@" + domain;
    }

    private UserSession.Platform parsePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return UserSession.Platform.ANDROID;
        }

        try {
            return UserSession.Platform.valueOf(platform.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UserSession.Platform.ANDROID;
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
