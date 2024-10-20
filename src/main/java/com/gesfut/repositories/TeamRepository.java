package com.gesfut.repositories;

import com.gesfut.models.team.Team;
import com.gesfut.models.user.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findAllByUser(UserEntity user);

    @Modifying
    @Query("DELETE FROM Team t WHERE t.id = :id")
    void deleteById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Team t SET t.status = :newStatus WHERE t.id = :teamId")
    void updateTeamStatus(@Param("teamId") Long teamId, @Param("newStatus") Boolean newStatus);
}
