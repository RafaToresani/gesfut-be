package com.gesfut.services.impl;

import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.dtos.responses.ParticipantShortResponse;
import com.gesfut.dtos.responses.PlayerParticipantResponse;
import com.gesfut.dtos.responses.PlayerParticipantShortResponse;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.services.PlayerService;
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

    @Autowired
    private PlayerService playerService;
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
                participant.getId(),
                participant.getTeam().getId(),
                participant.getTeam().getName(),
                participant.getIsActive(),
                this.statisticsService.statisticsToResponse(participant.getStatistics()),
                participant.getPlayerParticipants().stream().map(this::playerParticipantToResponse).toList());
    }

    @Override
    public ParticipantShortResponse participantToShortResponse(TournamentParticipant participant) {
        return new ParticipantShortResponse(
                participant.getId(),
                participant.getTeam().getId(),
                participant.getTeam().getName(),
                participant.getPlayerParticipants().stream().map(this::playerParticipantToShortResponse).toList());
    }

    private PlayerParticipantShortResponse playerParticipantToShortResponse(PlayerParticipant playerParticipant) {
        return new PlayerParticipantShortResponse(
                playerParticipant.getId(),
                playerParticipant.getPlayer().getName(),
                playerParticipant.getPlayer().getLastName()
        );
    }

    public PlayerParticipantResponse playerParticipantToResponse(PlayerParticipant playerParticipant){
        return new PlayerParticipantResponse(
                playerParticipant.getId(),
                this.playerService.playerToResponse(playerParticipant.getPlayer()),
                playerParticipant.getIsSuspended(),
                playerParticipant.getGoals(),
                playerParticipant.getYellowCards(),
                playerParticipant.getRedCards(),
                playerParticipant.getIsMvp()
        );
    }
}
