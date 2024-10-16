package com.gesfut.services;

import com.gesfut.dtos.requests.TournamentRequest;

public interface TournamentService {
    void createTournament(TournamentRequest request);
}
