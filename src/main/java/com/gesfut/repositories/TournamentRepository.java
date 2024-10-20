package com.gesfut.repositories;

import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.user.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    Boolean existsByCode(UUID code);

    List<Tournament> findAllByUser(UserEntity user);

    Optional<Tournament> findByCode(UUID uuid);

    @Modifying
    @Query("DELETE FROM Tournament t WHERE t.code = :code")
    void deleteByCode(UUID code);

}
