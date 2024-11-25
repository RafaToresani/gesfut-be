package com.gesfut.services;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.TournamentResponse;
import com.gesfut.dtos.responses.TournamentShortResponse;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;

import java.util.HashSet;
import java.util.List;

public interface TournamentService {
    String createTournament(TournamentRequest request);

    List<TournamentResponse> findAllTournaments();

    List<TournamentShortResponse> findAllTournamentsShortAll();

    TournamentShortResponse findAllTournamentsShort(String tournamentCode);

    TournamentResponse findTournamentByCode(String code);

    String changeStatusTournamentByCode(String code, Boolean status);

    void initializeTournament(MatchDayRequest request);

    void addTeamToTournament(Long idTeam, Tournament tournament);

    void disableTeamFromTournament(TournamentParticipant tournamentParticipant);

    HashSet<TournamentParticipant> addTeamsToTournament(String code, List<Long> teams);

    void updateTournamentParticipants(MatchDayRequest request);

    Boolean existsByCode(String tournamentCode);

    Boolean changeNameTournamentByCode(String code, String name);

    Boolean changeIsActive(String code, Boolean isActive);


}