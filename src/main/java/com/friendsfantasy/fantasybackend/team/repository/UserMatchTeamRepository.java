package com.friendsfantasy.fantasybackend.team.repository;

import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserMatchTeamRepository extends JpaRepository<UserMatchTeam, Long> {
    List<UserMatchTeam> findByFixtureIdAndUserIdOrderByCreatedAtDesc(Long fixtureId, Long userId);
    List<UserMatchTeam> findByFixtureIdOrderByCreatedAtDesc(Long fixtureId);
    Optional<UserMatchTeam> findByIdAndUserId(Long id, Long userId);
}
