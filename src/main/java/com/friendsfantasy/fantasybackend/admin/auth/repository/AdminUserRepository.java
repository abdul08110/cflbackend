package com.friendsfantasy.fantasybackend.admin.auth.repository;

import com.friendsfantasy.fantasybackend.admin.auth.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUsername(String username);
}