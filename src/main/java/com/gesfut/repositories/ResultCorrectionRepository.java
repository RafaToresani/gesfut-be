package com.gesfut.repositories;

import com.gesfut.models.audit.ResultCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResultCorrectionRepository extends JpaRepository<ResultCorrection, Long> {
    List<ResultCorrection> findAllByMatchIdOrderByCorrectedAtDesc(Long matchId);
}
