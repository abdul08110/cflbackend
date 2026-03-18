package com.friendsfantasy.fantasybackend.contest.repository;

import com.friendsfantasy.fantasybackend.contest.entity.Contest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContestRepository extends JpaRepository<Contest, Long> {
    List<Contest> findByFixtureIdOrderByEntryFeePointsAscIdAsc(Long fixtureId);
}