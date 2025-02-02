package com.gesfut.services;

import com.gesfut.dtos.responses.MatchDayResponse;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.tournament.TournamentParticipant;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

public interface MatchDayService {
    void reGenerateMatchDays(List<TournamentParticipant> tournamentParticipants, String tournamentCode);
    void generateMatchDays(HashSet<TournamentParticipant> tournamentParticipants , String tournamentCode, LocalDateTime startDate);
    MatchDayResponse matchDayToResponse(MatchDay matchDay);
    void updateStatusMatchDay(Long id, Boolean status);
    List<MatchDayResponse> getMatchDaysByCode(String code);
    MatchDayResponse getLastMatchDayPlayed(String code);
}
