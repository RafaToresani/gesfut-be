package com.gesfut.repositories;

import com.gesfut.models.matchDay.MatchDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchDayRepository extends JpaRepository<MatchDay, Long> {
    void deleteAllByTournamentCodeAndIsFinished(UUID code, boolean isFinished);
    List<MatchDay> findAllByTournamentCode(UUID code);
    Optional<MatchDay> findTopByTournament_CodeAndIsFinishedOrderByNumberOfMatchDayDesc(UUID code, boolean isFinished);
    Optional<MatchDay> findTopByTournament_CodeOrderByNumberOfMatchDayAsc(UUID code);
}
