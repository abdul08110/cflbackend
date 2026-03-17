package com.friendsfantasy.fantasybackend.repository;

import com.friendsfantasy.fantasybackend.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Integer> {
    List<Player> findByTeamIn(List<String> teams);
}