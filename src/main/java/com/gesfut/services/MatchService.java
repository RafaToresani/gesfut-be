package com.gesfut.services;

import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.dtos.responses.MatchDetailedResponse;
import com.gesfut.dtos.responses.MatchResponse;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.tournament.TournamentParticipant;
import org.apache.coyote.BadRequestException;

import java.util.List;
import java.util.Set;

public interface MatchService {
    String loadMatchResult(MatchRequest request);

    MatchResponse getMatchById(Long id);


    void generateMatches(MatchDay matchDay, List<TournamentParticipant> teams, int numberOfTeams);

    MatchResponse matchToResponse(Match match);

    void updateMatchResult(MatchRequest request) throws BadRequestException;

    MatchDetailedResponse getDetailedMatchById(Long id);
}
