package com.friendsfantasy.fantasybackend.contest.repository;

import com.friendsfantasy.fantasybackend.contest.entity.Contest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    List<Contest> findByFixtureIdOrderByEntryFeePointsAscIdAsc(Long fixtureId);

    List<Contest> findByFixtureIdAndContestTypeOrderByEntryFeePointsAscIdAsc(
            Long fixtureId,
            Contest.ContestType contestType
    );

    Optional<Contest> findByRoomId(Long roomId);

    List<Contest> findByFixtureIdInAndStatusInOrderByFixtureIdAscEntryFeePointsAscIdAsc(
            List<Long> fixtureIds,
            List<Contest.Status> statuses
    );

    List<Contest> findByFixtureIdAndContestTypeAndStatusIn(
            Long fixtureId,
            Contest.ContestType contestType,
            Collection<Contest.Status> statuses
    );
}
