package com.gesfut.services;

import com.gesfut.dtos.requests.MatchRequest;

public interface MatchService {
    String loadMatchResult(MatchRequest request);
}
