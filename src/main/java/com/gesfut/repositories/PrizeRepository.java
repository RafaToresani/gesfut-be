package com.gesfut.repositories;

import com.gesfut.models.tournament.EPrizeType;
import com.gesfut.models.tournament.Prize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrizeRepository extends JpaRepository<Prize, Long> {
    boolean existsByPositionAndTypeAndTournamentId(Integer position, EPrizeType type, Long tournamentId);

    List<Prize> findAllByTournamentId(Long tournamentId);
    List<Prize> findAllByTournamentIdAndType(Long tournamentId, EPrizeType type);
    Optional<Prize> findByTournamentIdAndTypeAndPosition(Long tournamentId, EPrizeType type, Integer position);

    @Modifying
    @Query("DELETE FROM Prize p WHERE p.id = :id")
    void deletePrizeById(@Param("id") Long id);

}
