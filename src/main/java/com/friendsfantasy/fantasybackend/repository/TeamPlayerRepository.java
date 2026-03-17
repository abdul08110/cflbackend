package com.friendsfantasy.fantasybackend.repository;

import com.friendsfantasy.fantasybackend.entity.TeamPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamPlayerRepository extends JpaRepository<TeamPlayer, Integer> {
    List<TeamPlayer> findByTeamId(Integer teamId);
}