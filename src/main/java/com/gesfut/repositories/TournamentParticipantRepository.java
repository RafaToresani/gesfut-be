package com.gesfut.repositories;

import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, Long> {
    Set<TournamentParticipant> findAllByTournament(Tournament tournament);

    boolean existsByTournamentAndTeam(Tournament tournament, Team team);

    @Modifying
    @Query("DELETE FROM TournamentParticipant tp WHERE tp.tournament.id = :tournamentId")
    void deleteByTournamentId(Long tournamentId);
}
