package com.friendsfantasy.fantasybackend.admin.auth.config;

import com.friendsfantasy.fantasybackend.admin.auth.entity.AdminUser;
import com.friendsfantasy.fantasybackend.admin.auth.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.admin.seed-default.enabled", havingValue = "true")
public class AdminSeeder implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.seed-default.username:}")
    private String defaultAdminUsername;

    @Value("${app.admin.seed-default.password:}")
    private String defaultAdminPassword;

    @Value("${app.admin.seed-default.secondary-username:}")
    private String secondaryAdminUsername;

    @Value("${app.admin.seed-default.secondary-password:}")
    private String secondaryAdminPassword;

    @Override
    public void run(String... args) {
        if (defaultAdminUsername == null || defaultAdminUsername.isBlank()
                || defaultAdminPassword == null || defaultAdminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Default admin seeding is enabled, but username/password were not configured");
        }

        seedAdmin(defaultAdminUsername, defaultAdminPassword, "default");

        if (secondaryAdminUsername != null && !secondaryAdminUsername.isBlank()
                && secondaryAdminPassword != null && !secondaryAdminPassword.isBlank()) {
            seedAdmin(secondaryAdminUsername, secondaryAdminPassword, "secondary");
        }
    }

    private void seedAdmin(String username, String password, String label) {
        if (adminUserRepository.findByUsername(username).isPresent()) {
            log.info("{} admin user '{}' already exists", label, username);
            return;
        }

        AdminUser admin = AdminUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .active(true)
                .build();

        adminUserRepository.save(admin);
        log.info("{} admin user '{}' created", label, username);
    }
}
