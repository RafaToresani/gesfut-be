package com.gesfut.services;

import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.models.tournament.TournamentParticipant;

import java.util.List;
import java.util.Set;

public interface TournamentParticipantService {
    List<ParticipantResponse> participantsToResponse(Set<TournamentParticipant> tournamentsParticipant);

    ParticipantResponse participantToResponse(TournamentParticipant item);
}
