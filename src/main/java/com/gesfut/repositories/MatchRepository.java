package com.gesfut.repositories;

import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match,Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    Optional<Match> findForUpdate(Long id);

    boolean existsByHomeTeamAndAwayTeamAndMatchDay(Team homeTeam, Team awayTeam, MatchDay matchDay);
}
