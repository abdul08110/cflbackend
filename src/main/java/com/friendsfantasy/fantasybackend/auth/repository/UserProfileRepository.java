package com.friendsfantasy.fantasybackend.auth.repository;

import com.friendsfantasy.fantasybackend.auth.entity.UserProfile;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
     boolean existsByEmail(String email);
    Optional<UserProfile> findByEmail(String email);
}