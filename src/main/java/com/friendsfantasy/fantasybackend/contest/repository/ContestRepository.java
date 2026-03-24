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

    List<Contest> findByRoomIdOrderByCreatedAtDescIdDesc(Long roomId);

    List<Contest> findByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
            Long roomId,
            Collection<Contest.Status> statuses
    );

    List<Contest> findByContestTypeAndStatusInOrderByCreatedAtDescIdDesc(
            Contest.ContestType contestType,
            Collection<Contest.Status> statuses
    );

    List<Contest> findByStatusInOrderByCreatedAtDescIdDesc(Collection<Contest.Status> statuses);

    Optional<Contest> findByIdAndRoomId(Long contestId, Long roomId);

    List<Contest> findByFixtureIdInAndStatusInOrderByFixtureIdAscEntryFeePointsAscIdAsc(
            List<Long> fixtureIds,
            List<Contest.Status> statuses
    );

    List<Contest> findByFixtureIdAndContestTypeAndStatusIn(
            Long fixtureId,
            Contest.ContestType contestType,
            Collection<Contest.Status> statuses
    );

    long countByRoomId(Long roomId);
}
