package com.friendsfantasy.fantasybackend.admin.user.repository;

import com.friendsfantasy.fantasybackend.admin.user.entity.AdminUserStatusActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminUserStatusActivityRepository
        extends JpaRepository<AdminUserStatusActivity, Long> {

    List<AdminUserStatusActivity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
