package com.gesfut.services;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.TournamentResponse;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;

import java.util.HashSet;
import java.util.List;

public interface TournamentService {
    void createTournament(TournamentRequest request);

    List<TournamentResponse> findAllTournaments();

    TournamentResponse findTournamentByCode(String code);

    String deleteTournamentByCode(String code);

    void initializeTournament(MatchDayRequest request);

    void addTeamToTournament(Long idTeam, Tournament tournament);

    void disableTeamFromTournament(TournamentParticipant tournamentParticipant);

    HashSet<TournamentParticipant> addTeamsToTournament(String code, List<Long> teams);
}