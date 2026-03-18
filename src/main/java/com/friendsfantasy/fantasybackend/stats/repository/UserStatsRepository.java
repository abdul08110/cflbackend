package com.friendsfantasy.fantasybackend.stats.repository;

import com.friendsfantasy.fantasybackend.stats.entity.UserStats;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatsRepository extends JpaRepository<UserStats, Long> {
}