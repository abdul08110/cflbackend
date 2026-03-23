package com.friendsfantasy.fantasybackend.fixture.repository;

import com.friendsfantasy.fantasybackend.fixture.entity.FixturePlayerPool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FixturePlayerPoolRepository extends JpaRepository<FixturePlayerPool, Long> {
    List<FixturePlayerPool> findByFixtureIdAndIsActiveTrueOrderByExternalTeamIdAscRoleCodeAscIdAsc(Long fixtureId);
    List<FixturePlayerPool> findByFixtureIdOrderByIdAsc(Long fixtureId);
    Optional<FixturePlayerPool> findByFixtureIdAndPlayerId(Long fixtureId, Long playerId);
    void deleteByFixtureId(Long fixtureId);
}
