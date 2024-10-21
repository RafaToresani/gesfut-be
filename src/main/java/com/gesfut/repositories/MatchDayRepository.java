package com.gesfut.repositories;

import com.gesfut.models.matchDay.MatchDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchDayRepository extends JpaRepository<MatchDay, Long> {


}
