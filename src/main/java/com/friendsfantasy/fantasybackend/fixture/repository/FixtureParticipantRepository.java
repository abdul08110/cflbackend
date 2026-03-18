package com.friendsfantasy.fantasybackend.fixture.repository;

import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixtureParticipantRepository extends JpaRepository<FixtureParticipant, Long> {

    List<FixtureParticipant> findByFixtureIdOrderByIsHomeDescTeamNameAsc(Long fixtureId);

    void deleteByFixtureId(Long fixtureId);
}