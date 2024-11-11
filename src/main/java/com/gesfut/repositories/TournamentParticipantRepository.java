package com.gesfut.repositories;

import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, Long> {
    Set<TournamentParticipant> findAllByTournament(Tournament tournament);

    boolean existsByTournamentAndTeam(Tournament tournament, Team team);

    Set<TournamentParticipant>findAllByTournamentCode(UUID tournamentCode);



    @Modifying
    @Query("DELETE FROM TournamentParticipant tp WHERE tp.tournament.id = :tournamentId")
    void deleteByTournamentId(Long tournamentId);

    List<TournamentParticipant> findAllByTeamId(Long id);
}
