package com.friendsfantasy.fantasybackend.admin.auth.service;

import com.friendsfantasy.fantasybackend.admin.auth.dto.AdminAuthResponse;
import com.friendsfantasy.fantasybackend.admin.auth.dto.AdminLoginRequest;
import com.friendsfantasy.fantasybackend.admin.auth.entity.AdminUser;
import com.friendsfantasy.fantasybackend.admin.auth.repository.AdminUserRepository;
import com.friendsfantasy.fantasybackend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthResponse login(AdminLoginRequest request) {
        AdminUser user = adminUserRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new RuntimeException("Invalid admin credentials"));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new RuntimeException("Admin account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid admin credentials");
        }

        user.setLastLoginAt(LocalDateTime.now());
        adminUserRepository.save(user);

        String accessToken = jwtService.generateAdminAccessToken(user);

        return AdminAuthResponse.builder()
                .adminId(user.getId())
                .username(user.getUsername())
                .accessToken(accessToken)
                .tokenType("Bearer")
                .build();
    }
}