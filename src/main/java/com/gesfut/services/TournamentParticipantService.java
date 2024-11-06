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
    List<ParticipantResponse>getParticipants(UUID code);
    ParticipantResponse getOneParticipants(Long teamId);
    List<ParticipantShortResponse> getParticipantsShort(UUID code);
    List<ParticipantShortResponse> participantsToResponseShort(Set<TournamentParticipant> tournamentsParticipant);

    }
