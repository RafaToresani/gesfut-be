package com.gesfut.repositories;

import com.gesfut.models.tournament.Statistics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatisticsRepository extends JpaRepository<Statistics, Long> {
}
