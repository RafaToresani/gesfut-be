package com.gesfut.services;

import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.dtos.responses.MatchResponse;
import com.gesfut.dtos.responses.MatchShortResponse;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.tournament.TournamentParticipant;
import org.apache.coyote.BadRequestException;

import java.util.List;
import java.util.Set;

public interface MatchService {
    String loadMatchResult(MatchRequest request);

    MatchResponse getMatchById(Long id);


    MatchShortResponse getMatchShortById(Long id);

    MatchShortResponse matchToShortResponse(Match match);

    void generateMatches(MatchDay matchDay, List<TournamentParticipant> teams, int numberOfTeams);

    MatchResponse matchToResponse(Match match);

    void updateMatchResult(MatchRequest request) throws BadRequestException;
}
