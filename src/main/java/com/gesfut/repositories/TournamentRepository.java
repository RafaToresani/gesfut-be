package com.gesfut.repositories;

import com.gesfut.models.tournament.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    Boolean existsByCode(UUID code);
}
