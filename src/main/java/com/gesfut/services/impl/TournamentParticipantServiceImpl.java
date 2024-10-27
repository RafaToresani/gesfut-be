package com.gesfut.services.impl;

import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.services.StatisticsService;
import com.gesfut.services.TournamentParticipantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class TournamentParticipantServiceImpl implements TournamentParticipantService {

    @Autowired
    private TournamentParticipantRepository participantRepository;

    @Autowired
    private StatisticsService statisticsService;

    @Override
    public List<ParticipantResponse> participantsToResponse(Set<TournamentParticipant> tournamentsParticipant){
        List<ParticipantResponse> list = new ArrayList<>();
        tournamentsParticipant.forEach(item -> {
            list.add(participantToResponse(item));
        });
        return list;
    }

    @Override
    public ParticipantResponse participantToResponse(TournamentParticipant participant) {
        return new ParticipantResponse(
                participant.getTeam().getId(),
                participant.getTeam().getName(),
                participant.getIsActive(),
                this.statisticsService.statisticsToResponse(participant.getStatistics()));
    }

}
