package com.gesfut.services;

import com.gesfut.dtos.requests.MatchDateRequest;
import com.gesfut.dtos.responses.MatchDayResponse;
import com.gesfut.dtos.responses.NewDateResponse;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.tournament.TournamentParticipant;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

public interface MatchDayService {
    void reGenerateMatchDays(List<TournamentParticipant> tournamentParticipants, String tournamentCode);
    void generateMatchDays(HashSet<TournamentParticipant> tournamentParticipants , String tournamentCode, LocalDateTime startDate, Integer minutesPerMatch , Integer dayBetweenMatchDay);
    MatchDayResponse matchDayToResponse(MatchDay matchDay);
    void updateStatusMatchDay(Long id, Boolean status, String playerMvp);
    List<MatchDayResponse> getMatchDaysByCode(String code);
    MatchDayResponse getLastMatchDayPlayed(String code);
    List<NewDateResponse> updateDateAllMatches(Long id, MatchDateRequest request, Integer plusMinutes);
}
