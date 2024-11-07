package com.gesfut.repositories;

import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.TournamentParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface PlayerParticipantRepository extends JpaRepository <PlayerParticipant, Long> {
    Optional<PlayerParticipant> findByIdAndTournamentParticipantIn(Long id, List<TournamentParticipant> tournamentParticipants);
}
