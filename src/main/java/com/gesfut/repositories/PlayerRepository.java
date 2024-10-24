package com.gesfut.repositories;

import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    @Modifying
    @Query("UPDATE Player p SET p.status = :newStatus WHERE p.team.id = :teamId")
    void updatePlayerStatus(@Param("teamId") Long teamId, @Param("newStatus") Boolean newStatus);

    Boolean existsByNumberAndTeamId(Integer number, Long teamId);

    Boolean existsByIsCaptainAndTeamId(Boolean isCaptain, Long id);
}
