package com.gesfut.services;

import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.TournamentResponse;

import java.util.List;

public interface TournamentService {
    void createTournament(TournamentRequest request);

    List<TournamentResponse> findAllTournaments();

    TournamentResponse findTournamentByCode(String code);

    String deleteTournamentByCode(String code);
}
