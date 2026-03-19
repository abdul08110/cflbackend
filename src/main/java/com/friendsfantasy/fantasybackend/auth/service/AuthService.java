package com.friendsfantasy.fantasybackend.auth.service;

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
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByMobile(request.getMobile())) {
            throw new RuntimeException("Mobile already registered");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        if (!otpService.isOtpVerified(request.getEmail(), OtpRequest.Purpose.REGISTER)) {
            throw new RuntimeException("Email OTP not verified");
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
        User user = userRepository.findByMobile(request.getMobileOrUsername())
                .or(() -> userRepository.findByUsername(request.getMobileOrUsername()))
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new RuntimeException("User account is not active");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

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
        String platformText = (request.getPlatform() == null || request.getPlatform().isBlank())
                ? "ANDROID"
                : request.getPlatform().toUpperCase();

        UserSession.Platform platform = UserSession.Platform.valueOf(platformText);

        UserSession session = UserSession.builder()
                .userId(user.getId())
                .deviceId(deviceId)
                .deviceName(deviceName)
                .platform(platform)
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
                .mobile(user.getMobile())
                .build();
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        String tokenType = jwtService.extractType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new RuntimeException("Invalid refresh token");
        }

        String refreshHash = tokenHashUtil.sha256(refreshToken);

        UserSession session = userSessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(refreshHash)
                .orElseThrow(() -> new RuntimeException("Refresh session not found"));

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token expired");
        }

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new RuntimeException("User account is not active");
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
                .mobile(user.getMobile())
                .build();
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String refreshHash = tokenHashUtil.sha256(request.getRefreshToken());

        UserSession session = userSessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(refreshHash)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        session.setRevokedAt(LocalDateTime.now());
        userSessionRepository.save(session);
    }

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}