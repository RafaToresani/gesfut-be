package com.gesfut.services;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.responses.MatchDayResponse;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.tournament.TournamentParticipant;

import java.util.HashSet;

public interface MatchDayService {
    void generateMatchDays(HashSet<TournamentParticipant> tournamentParticipants , String tournamentCode);
    MatchDayResponse matchDayToResponse(MatchDay matchDay);

}
