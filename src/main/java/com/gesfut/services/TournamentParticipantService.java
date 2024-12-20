package com.gesfut.services;

import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.dtos.responses.ParticipantShortResponse;
import com.gesfut.models.tournament.TournamentParticipant;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TournamentParticipantService {
    List<ParticipantResponse> participantsToResponse(Set<TournamentParticipant> tournamentsParticipant);
    ParticipantResponse participantToResponse(TournamentParticipant item);
    List<ParticipantResponse>getParticipants(String code);
    ParticipantResponse getOneParticipants(Long teamId);
    ParticipantShortResponse participantsToResponseShortOne(TournamentParticipant tournamentsParticipant);
    void changeStatusPlayerParticipant(String code, Long idParticipant, Boolean status);
    List<ParticipantShortResponse> getTeamTournamentsParticipations(Long id);
}
