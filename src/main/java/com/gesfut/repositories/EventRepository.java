package com.gesfut.repositories;

import com.gesfut.models.matchDay.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository <Event, Long> {
    List<Event> findAllByMatchId(Long id);
}
