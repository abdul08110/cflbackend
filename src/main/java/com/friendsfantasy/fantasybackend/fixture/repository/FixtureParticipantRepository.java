package com.friendsfantasy.fantasybackend.fixture.repository;

import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FixtureParticipantRepository extends JpaRepository<FixtureParticipant, Long> {
    List<FixtureParticipant> findByFixtureIdOrderByIsHomeDescTeamNameAsc(Long fixtureId);
    List<FixtureParticipant> findByFixtureIdInOrderByFixtureIdAscIsHomeDescTeamNameAsc(List<Long> fixtureIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select participant
            from FixtureParticipant participant
            where participant.fixtureId = :fixtureId
            order by participant.isHome desc, participant.teamName asc
            """)
    List<FixtureParticipant> findByFixtureIdOrderByIsHomeDescTeamNameAscForUpdate(@Param("fixtureId") Long fixtureId);

    Optional<FixtureParticipant> findByFixtureIdAndExternalTeamId(Long fixtureId, Long externalTeamId);

    void deleteByFixtureId(Long fixtureId);
}
