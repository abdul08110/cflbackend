package com.friendsfantasy.fantasybackend.repository;

import com.friendsfantasy.fantasybackend.entity.ContestEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContestEntryRepository extends JpaRepository<ContestEntry, Integer> {
    List<ContestEntry> findByContestId(Integer contestId);
    boolean existsByContestIdAndUserIdAndTeamId(Integer contestId, Integer userId, Integer teamId);
    long countByContestId(Integer contestId);
}