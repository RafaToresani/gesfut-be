package com.gesfut.services;

import com.gesfut.dtos.requests.EventRequest;
import com.gesfut.dtos.responses.EventResponse;
import com.gesfut.models.matchDay.Event;
import com.gesfut.models.matchDay.Match;

public interface EventService {

    Event createEvent(EventRequest eventRequest, Match match);

    EventResponse eventToResponse(Event event);
}
