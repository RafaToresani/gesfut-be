package com.gesfut.services;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.responses.MatchDayResponse;
import com.gesfut.models.matchDay.MatchDay;

public interface MatchDayService {
    void generateMatchDays(MatchDayRequest request);
    MatchDayResponse matchDayToResponse(MatchDay matchDay);

}
