package com.gesfut.repositories;

import aj.org.objectweb.asm.commons.Remapper;
import com.gesfut.dtos.responses.TournamentShortResponse;
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
    /** Plan 22/10 #11: los torneos mas nuevos primero (id autoincremental = orden de creacion). */
    List<Tournament> findAllByUserOrderByIdDesc(UserEntity user);

    Optional<Tournament> findByCode(UUID uuid);

    // Lecturas públicas: trae usuario y participantes en una sola consulta.
    // Las colecciones anidadas (fechas, partidos, eventos) se cargan en lotes (hibernate.default_batch_fetch_size).
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "teams", "teams.team", "teams.statistics"})
    @Query("SELECT t FROM Tournament t WHERE t.code = :code")
    Optional<Tournament> findDetailedByCode(@org.springframework.data.repository.query.Param("code") UUID code);

    @Modifying
    @Query("DELETE FROM Tournament t WHERE t.code = :code")
    void deleteByCode(UUID code);

    Optional<Tournament> findByCodeAndUser(UUID uuid, UserEntity user);

    Boolean existsByNameAndUserId(String name, Long id);
}
