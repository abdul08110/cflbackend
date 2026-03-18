package com.friendsfantasy.fantasybackend.contest.repository;

import com.friendsfantasy.fantasybackend.contest.entity.ContestPrize;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContestPrizeRepository extends JpaRepository<ContestPrize, Long> {
    List<ContestPrize> findByContestIdOrderByRankFromNoAsc(Long contestId);
    void deleteByContestId(Long contestId);
}