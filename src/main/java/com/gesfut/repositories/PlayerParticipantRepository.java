package com.gesfut.repositories;

import com.gesfut.models.tournament.PlayerParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface PlayerParticipantRepository extends JpaRepository <PlayerParticipant, Long> {

}
