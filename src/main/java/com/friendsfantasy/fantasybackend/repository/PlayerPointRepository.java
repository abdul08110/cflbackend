package com.friendsfantasy.fantasybackend.repository;

import com.friendsfantasy.fantasybackend.entity.PlayerPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerPointRepository extends JpaRepository<PlayerPoint, Integer> {
    Optional<PlayerPoint> findByMatchIdAndPlayerId(Integer matchId, Integer playerId);
}