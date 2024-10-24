package com.gesfut.services;

import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.dtos.responses.MatchResponse;

public interface MatchService {
    String loadMatchResult(MatchRequest request);

    MatchResponse getMatchById(Long id);
}
