package com.gesfut.services;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.*;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

public interface TournamentService {
    String createTournament(TournamentRequest request);

    List<TournamentShortResponse> findAllTournamentsShortAll();

    TournamentShortResponse findAllTournamentsShort(String tournamentCode);

    TournamentResponse findTournamentByCode(String code);

    void finishTournament(String code);

    void initializeTournament(MatchDayRequest request, LocalDateTime startDate);

    void addTeamToTournament(Long idTeam, Tournament tournament);

    void disableTeamFromTournament(TournamentParticipant tournamentParticipant);
    /** Plan 22/10 #2: quita al equipo de un torneo sin partidos jugados y regenera el fixture. */
    void removeParticipantAndRegenerate(TournamentParticipant tournamentParticipant);

    HashSet<TournamentParticipant> addTeamsToTournament(String code, List<Long> teams);

    void updateTournamentParticipants(MatchDayRequest request);

    Boolean existsByCode(String tournamentCode);

    Boolean changeNameTournamentByCode(String code, String name);

    Boolean changeIsActive(String code, Boolean isActive);

    List<MatchResponse> findMatchesByTournamentAndParticipant(String code, Long idParticipant);

    boolean isMyTournament(String code);

    List<TopScorersResponse> findTopScorersByTournament(String code);

    List<TopYellowCardsResponse> findTopYellowCardsByTournament(String code);

    List<TopRedCardsResponse> findTopRedCardsByTournament(String code);

    void generatePlayOffs(String code, List<Long> temasQualify);
}