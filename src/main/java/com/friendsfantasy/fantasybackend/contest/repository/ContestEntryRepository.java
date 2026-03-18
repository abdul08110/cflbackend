package com.friendsfantasy.fantasybackend.contest.repository;

import com.friendsfantasy.fantasybackend.contest.entity.ContestEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContestEntryRepository extends JpaRepository<ContestEntry, Long> {

    boolean existsByContestIdAndUserMatchTeamId(Long contestId, Long userMatchTeamId);

    List<ContestEntry> findByContestIdAndUserIdOrderByJoinedAtAsc(Long contestId, Long userId);

    List<ContestEntry> findByContestIdOrderByJoinedAtAsc(Long contestId);

    Optional<ContestEntry> findByIdAndContestId(Long entryId, Long contestId);

    List<ContestEntry> findByUserIdOrderByJoinedAtDesc(Long userId);

    boolean existsByContestIdAndUserIdAndPrizePointsAwardedGreaterThan(Long contestId, Long userId, Integer prizePoints);
}