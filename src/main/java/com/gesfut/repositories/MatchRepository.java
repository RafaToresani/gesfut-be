package com.gesfut.repositories;

import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRepository extends JpaRepository<Match,Long> {

    boolean existsByHomeTeamAndAwayTeamAndMatchDay(Team homeTeam, Team awayTeam, MatchDay matchDay);
}
