package com.gesfut.services.impl;

import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.dtos.responses.ParticipantShortResponse;
import com.gesfut.dtos.responses.PlayerParticipantResponse;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.PlayerParticipantRepository;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.repositories.TournamentRepository;
import com.gesfut.services.StatisticsService;
import com.gesfut.services.TournamentParticipantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TournamentParticipantServiceImpl implements TournamentParticipantService {

    @Autowired
    private TournamentParticipantRepository participantRepository;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private PlayerParticipantRepository playerParticipantRepository;
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
                participant.getId(),
                participant.getTeam().getId(),
                participant.getTeam().getName(),
                participant.getIsActive(),
                this.statisticsService.statisticsToResponse(participant.getStatistics()),
                participant.getPlayerParticipants().stream()
                        .map(playerParticipant -> new PlayerParticipantResponse(
                                playerParticipant.getId(),
                                playerParticipant.getPlayer().getNumber(),
                                playerParticipant.getIsSuspended(),
                                playerParticipant.getGoals(),
                                playerParticipant.getRedCards(),
                                playerParticipant.getYellowCards(),
                                playerParticipant.getIsMvp(),  // Valor predeterminado
                                playerParticipant.getPlayer().getId(),
                                playerParticipant.getPlayer().getName(),
                                playerParticipant.getPlayer().getLastName(),
                                playerParticipant.getIsActive(),
                                playerParticipant.getPlayer().getIsCaptain(),
                                playerParticipant.getPlayer().getIsGoalKeeper()
                        ))
                        .collect(Collectors.toSet())
        );
    }

    @Override
    public ParticipantShortResponse participantsToResponseShortOne(TournamentParticipant tournamentsParticipant){
        ParticipantShortResponse participantShortResponse = new ParticipantShortResponse(
                tournamentsParticipant.getTournament().getName(),
                tournamentsParticipant.getPlayerParticipants().stream().map(playerParticipant -> new PlayerParticipantResponse(
                        playerParticipant.getId(),
                        playerParticipant.getPlayer().getNumber(),
                        playerParticipant.getIsSuspended(),
                        playerParticipant.getGoals(),
                        playerParticipant.getRedCards(),
                        playerParticipant.getYellowCards(),
                        playerParticipant.getIsMvp(),
                        playerParticipant.getPlayer().getId(),
                        playerParticipant.getPlayer().getName(),
                        playerParticipant.getPlayer().getLastName(),
                        playerParticipant.getIsActive(),
                        playerParticipant.getPlayer().getIsCaptain(),
                        playerParticipant.getPlayer().getIsGoalKeeper()
                )).collect(Collectors.toSet()),
                tournamentsParticipant.getIsActive()
        );
        return participantShortResponse;
    }

    @Override
    public void changeStatusPlayerParticipant(String code, Long idParticipant, Boolean status) {
        Optional<Tournament> optTournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        if(optTournament.isEmpty()) throw new ResourceNotFoundException("El torneo no existe.");

        Optional<PlayerParticipant> optPlayer = this.playerParticipantRepository.findById(idParticipant);
        if(optPlayer.isEmpty()) throw new ResourceNotFoundException("El id del participante no existe.");

        this.playerParticipantRepository.changeStatus(idParticipant, status);
    }

    @Override
    public List<ParticipantShortResponse> getTeamTournamentsParticipations(Long id) {
        List<TournamentParticipant> participants = new ArrayList<>(this.participantRepository.findAllByTeamId(id));
        return participants.stream().map(this::participantsToResponseShortOne).collect(Collectors.toList());
    }

    @Override
    public List<ParticipantResponse> getParticipants(String code) {
        return participantsToResponse(this.participantRepository.findAllByTournamentCode(UUID.fromString(code)));
    }

    @Override
    public ParticipantResponse getOneParticipants(Long teamId) {
        return participantToResponse(this.participantRepository.findById(teamId).orElse(null));
    }



}
