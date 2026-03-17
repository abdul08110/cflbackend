package com.friendsfantasy.fantasybackend.repository;

import com.friendsfantasy.fantasybackend.entity.Contest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContestRepository extends JpaRepository<Contest, Integer> {
    List<Contest> findByMatchId(Integer matchId);
}