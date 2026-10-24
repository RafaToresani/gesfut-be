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
@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
public class TournamentParticipantServiceImpl implements TournamentParticipantService {

    @Autowired
    private TournamentAccess access;
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
                        .collect(Collectors.toSet()),
                participant.getTeam().getColor()
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

        access.requireOwner(optPlayer.get().getTournamentParticipant().getTournament());
        access.requireOpen(optPlayer.get().getTournamentParticipant().getTournament()); // Item 3: archivado = solo lectura
        if (status == null) throw new IllegalArgumentException("El estado es requerido.");
        if (status){
            if (!optPlayer.get().getPlayer().getStatus()){
                optPlayer.get().getPlayer().setStatus(status);
                this.playerParticipantRepository.save(optPlayer.get());
            }
        }
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
    @org.springframework.transaction.annotation.Transactional
    public ParticipantResponse addPlayerToTeamParticipant(String code, Long teamIdParticipant, Player player) {
        Optional<Tournament> optTournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        if(optTournament.isEmpty()) throw new ResourceNotFoundException("El torneo no existe.");
        access.requireOwner(optTournament.get());
        access.requireOpen(optTournament.get());

        Optional<TournamentParticipant> optParticipant = this.participantRepository.findById(teamIdParticipant);
        if(optParticipant.isEmpty()) throw new ResourceNotFoundException("El participante no existe.");
        if (!Objects.equals(optParticipant.get().getTournament().getId(), optTournament.get().getId()) || !Objects.equals(player.getTeam().getId(), optParticipant.get().getTeam().getId())) throw new IllegalArgumentException("El jugador o participante no pertenece al equipo y torneo indicado.");
        DniRules.requireDniFreeInTournament(optTournament.get(), player.getDni(), player.getName() + " " + player.getLastName(), optParticipant.get().getTeam());
        if (player.getDni() != null) DniRules.requireNotInOtherTeam(
                playerParticipantRepository.findAllByTournamentParticipant_Tournament_IdAndPlayer_Dni(optTournament.get().getId(), player.getDni()), player, optParticipant.get().getTeam());
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

    @Autowired @org.springframework.context.annotation.Lazy
    private com.gesfut.services.TeamService teamServiceForAdd;

    /** Alta de jugador desde "Torneo actual": todo en una transaccion. Primero se valida (torneo existe, es del usuario,
     *  esta abierto, el participante es de ese torneo) y recien despues se crea/reusa el jugador; si falla, rollback. */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ParticipantResponse addPlayerFromRequest(String code, Long teamIdParticipant, com.gesfut.dtos.requests.PlayerRequest request) {
        Tournament tournament = this.tournamentRepository.findByCode(UUID.fromString(code)).orElseThrow(() -> new ResourceNotFoundException("El torneo no existe."));
        access.requireOwner(tournament);
        access.requireOpen(tournament);
        TournamentParticipant participant = this.participantRepository.findById(teamIdParticipant).orElseThrow(() -> new ResourceNotFoundException("El participante no existe."));
        if (!Objects.equals(participant.getTournament().getId(), tournament.getId())) throw new IllegalArgumentException("El equipo no participa de este torneo.");
        Long teamId = participant.getTeam().getId();
        Player existing = this.teamServiceForAdd.getPlayerName(request.name(), request.lastName(), teamId);
        if (existing != null) {
            // C(c): solo se reutiliza un jugador ACTIVO con el MISMO dorsal.
            String who = existing.getName() + " " + existing.getLastName();
            if (Boolean.FALSE.equals(existing.getStatus()))
                throw new IllegalArgumentException(who + " est\u00e1 dado de baja en el equipo; volv\u00e9 a habilitarlo antes de sumarlo al torneo.");
            if (!Objects.equals(existing.getNumber(), request.number()))
                throw new IllegalArgumentException("Ya existe " + who + " con el dorsal " + existing.getNumber() + "; editalo o us\u00e1 otro nombre.");
            return addPlayerToTeamParticipant(code, teamIdParticipant, existing);
        }
        Player created = this.teamServiceForAdd.addPlayerToTeam(teamId, request);
        return addPlayerToTeamParticipant(code, teamIdParticipant, created);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ParticipantResponse> getParticipants(String code) {
        return participantsToResponse(this.participantRepository.findAllByTournamentCode(UUID.fromString(code)));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ParticipantResponse getOneParticipants(Long teamId) {
        // Revision critica #14: por id numerico solo el duenio del torneo.
        TournamentParticipant participant = this.participantRepository.findById(teamId)
                .orElseThrow(() -> new com.gesfut.exceptions.ResourceNotFoundException("Participante no encontrado."));
        access.requireOwner(participant.getTournament());
        return participantToResponse(participant);
    }



}
