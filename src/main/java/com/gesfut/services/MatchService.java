package com.gesfut.services;

import com.gesfut.dtos.requests.MatchDateRequest;
import com.gesfut.dtos.requests.MatchDescriptionRequest;
import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.dtos.responses.MatchDetailedResponse;
import com.gesfut.dtos.responses.MatchResponse;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.tournament.TournamentParticipant;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;

import java.util.List;

public interface MatchService {
    String loadMatchResult(MatchRequest request);

    MatchResponse getMatchById(Long id);

    void generateMatches(MatchDay matchDay, List<TournamentParticipant> teams, int numberOfTeams);

    MatchResponse matchToResponse(Match match);

    void updateMatchResult(MatchRequest request) throws BadRequestException;

    MatchDetailedResponse getDetailedMatchById(Long id);

    void updateMatchDateAndDescription(Long matchId, @Valid MatchDateRequest request);

    void updateMatchDescription(Long matchId, @Valid MatchDescriptionRequest request);
}
