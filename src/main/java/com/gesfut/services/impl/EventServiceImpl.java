package com.gesfut.services.impl;

import com.gesfut.repositories.EventRepository;
import com.gesfut.services.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EventServiceImpl implements EventService {

    @Autowired
    private EventRepository eventRepository;


}
