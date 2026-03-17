package com.friendsfantasy.fantasybackend.repository;

import com.friendsfantasy.fantasybackend.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Integer> {
}