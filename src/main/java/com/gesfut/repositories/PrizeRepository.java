package com.gesfut.repositories;

import com.gesfut.models.tournament.EPrizeType;
import com.gesfut.models.tournament.Prize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrizeRepository extends JpaRepository<Prize, Long> {
    boolean existsByPositionAndTypeAndTournamentId(Integer position, EPrizeType type, Long tournamentId);

    List<Prize> findAllByTournamentId(Long tournamentId);
}
