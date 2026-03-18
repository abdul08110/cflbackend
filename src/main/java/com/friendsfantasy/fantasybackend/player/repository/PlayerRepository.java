package com.friendsfantasy.fantasybackend.player.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.friendsfantasy.fantasybackend.player.entity.Player;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findBySportIdAndExternalPlayerId(Long sportId, Long externalPlayerId);
}