package com.gesfut.repositories;

import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.TournamentParticipant;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface PlayerParticipantRepository extends JpaRepository <PlayerParticipant, Long> {
    Optional<PlayerParticipant> findByIdAndTournamentParticipantIn(Long id, List<TournamentParticipant> tournamentParticipants);

    @Modifying
    @Transactional
    @Query("UPDATE PlayerParticipant p SET p.isActive = :isActive WHERE p.id = :id")
    void changeStatus(@Param("id") Long id, @Param("isActive") Boolean isActive);
}
