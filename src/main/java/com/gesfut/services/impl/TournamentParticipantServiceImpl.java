package com.gesfut.services.impl;

import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.dtos.responses.ParticipantShortResponse;
import com.gesfut.dtos.responses.PlayerParticipantResponse;
import com.gesfut.dtos.responses.TeamResponse;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.PlayerParticipantRepository;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.repositories.TournamentRepository;
import com.gesfut.services.StatisticsService;
import com.gesfut.services.TeamService;
import com.gesfut.services.TournamentParticipantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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
                                playerParticipant.getPlayer().getIsGoalKeeper(),
                                playerParticipant.getPlayer().getStatus()
                        ))
                        .collect(Collectors.toSet())
        );
    }

    @Override
    public ParticipantShortResponse participantsToResponseShortOne(TournamentParticipant tournamentsParticipant){
        ParticipantShortResponse participantShortResponse = new ParticipantShortResponse(
                tournamentsParticipant.getTournament().getName(),
                tournamentsParticipant.getTournament().getCode().toString(),
                tournamentsParticipant.getId(),
                tournamentsParticipant.getIsActive()
        );
        return participantShortResponse;
    }

    @Override
    public void changeStatusPlayerParticipant(Long idParticipantPlayer, Boolean status) {
        Optional<PlayerParticipant> optPlayer = this.playerParticipantRepository.findById(idParticipantPlayer);
        if(optPlayer.isEmpty()) throw new ResourceNotFoundException("El id del participante no existe.");
        this.playerParticipantRepository.changeStatus(idParticipantPlayer, status);
    }

    @Override
    public List<ParticipantShortResponse> getTeamTournamentsParticipations(Long id) {
        List<TournamentParticipant> participants = new ArrayList<>(this.participantRepository.findAllByTeamId(id));
        return participants.stream().map(this::participantsToResponseShortOne).collect(Collectors.toList());
    }


    public boolean verifyPlayer(Set<PlayerParticipant> players, Player playerToAdd) {
        for (PlayerParticipant playerParticipant : players) {
            if (playerParticipant.getPlayer().getName().equals(playerToAdd.getName()) &&
                    playerParticipant.getPlayer().getLastName().equals(playerToAdd.getLastName())) {
                throw new ResourceAlreadyExistsException("El jugador ya existe en este torneo.");
            }
        }
        return false;
    }


    @Override
    public ParticipantResponse addPlayerToTeamParticipant(String code, Long teamIdParticipant, Player player) {
        Optional<Tournament> optTournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        if(optTournament.isEmpty()) throw new ResourceNotFoundException("El torneo no existe.");

        Optional<TournamentParticipant> optParticipant = this.participantRepository.findById(teamIdParticipant);
        if(optParticipant.isEmpty()) throw new ResourceNotFoundException("El participante no existe.");
        if (verifyPlayer(optParticipant.get().getPlayerParticipants(), player)) {
            throw new ResourceAlreadyExistsException("Ese jugador ya está jugando este torneo.");
        }
        optParticipant.get().getPlayerParticipants().add(
                PlayerParticipant.builder()
                .player(player)
                .tournamentParticipant(optParticipant.get())
                .goals(0)
                .redCards(0)
                .yellowCards(0)
                .isMvp(0)
                .isSuspended(false)
                .isActive(true)
                .build());
        return participantToResponse(this.participantRepository.save(optParticipant.get()));
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
