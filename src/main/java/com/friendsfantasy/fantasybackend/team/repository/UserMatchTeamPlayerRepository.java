package com.friendsfantasy.fantasybackend.team.repository;

import com.friendsfantasy.fantasybackend.team.entity.UserMatchTeamPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserMatchTeamPlayerRepository extends JpaRepository<UserMatchTeamPlayer, Long> {
    List<UserMatchTeamPlayer> findByUserMatchTeamIdOrderByIsSubstituteAscSubstitutePriorityAscIdAsc(Long userMatchTeamId);
    List<UserMatchTeamPlayer> findByUserMatchTeamIdInOrderByUserMatchTeamIdAscIsSubstituteAscSubstitutePriorityAscIdAsc(List<Long> userMatchTeamIds);
    void deleteByUserMatchTeamId(Long userMatchTeamId);
}
