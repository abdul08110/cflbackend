package com.friendsfantasy.fantasybackend.fixture.repository;

import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FixtureRepository extends JpaRepository<Fixture, Long> {

    Optional<Fixture> findBySportIdAndExternalFixtureId(Long sportId, Long externalFixtureId);
    Optional<Fixture> findByExternalFixtureId(Long externalFixtureId);
    List<Fixture> findBySportIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(Long sportId, LocalDateTime now);
    List<Fixture> findBySportIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(Long sportId, LocalDateTime now, Pageable pageable);
    List<Fixture> findBySportIdAndDeadlineTimeGreaterThanOrderByStartTimeAsc(Long sportId, LocalDateTime now);
}
