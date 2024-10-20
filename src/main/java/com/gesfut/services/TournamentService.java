package com.gesfut.services;

import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.TournamentResponse;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;

import java.util.List;

public interface TournamentService {
    void createTournament(TournamentRequest request);

    List<TournamentResponse> findAllTournaments();

    TournamentResponse findTournamentByCode(String code);

    String deleteTournamentByCode(String code);

    String addTeamToTournament(Long idTeam, String code);

    void disableTeamFromTournament(TournamentParticipant tournamentParticipant);
}
