package com.gesfut.services;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.dtos.responses.ParticipantShortResponse;
import com.gesfut.dtos.responses.PlayerResponse;
import com.gesfut.models.team.Player;
import com.gesfut.models.tournament.PlayerParticipant;
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
    void changeStatusPlayerParticipant(Long idParticipantPlayer, Boolean status);
    List<ParticipantShortResponse> getTeamTournamentsParticipations(Long id);
    ParticipantResponse addPlayerToTeamParticipant(String code, Long teamIdParticipant, Player player);
}
