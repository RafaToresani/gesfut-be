package com.gesfut.repositories;

import com.gesfut.models.matchDay.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository <Event, Long> {

}
