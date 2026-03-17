package com.friendsfantasy.fantasybackend.repository;

import com.friendsfantasy.fantasybackend.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Integer> {
    List<Team> findByUserId(Integer userId);
}