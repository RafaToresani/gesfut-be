package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.ParticipantShortResponse;
import com.gesfut.dtos.responses.TeamResponse;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.PlayerRepository;
import com.gesfut.repositories.TeamRepository;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.services.PlayerService;
import com.gesfut.services.TeamService;
import com.gesfut.services.TournamentParticipantService;
import com.gesfut.services.UserEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.gesfut.config.security.SecurityUtils.getCurrentUserEmail;

@Service
public class TeamServiceImpl implements TeamService {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlayerService playerService;
    @Autowired
    private TournamentParticipantService tournamentParticipantService;
    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private UserEntityService userService;

    @Autowired
    private TournamentParticipantRepository participantRepository;

    @Override
    public void createTeam(TeamRequest request) {
        this.playerService.validatePlayers(request.players());
        UserEntity user = userService.findUserByEmail(getCurrentUserEmail());
        Team team = Team.builder()
                .name(request.name())
                .color(request.color())
                .user(user)
                .players(new HashSet<>())
                .tournaments(new HashSet<>())
                .status(true)
                .build();
        teamRepository.save(team);
        request.players().forEach(playerRequest -> {
            playerService.createPlayer(playerRequest, team);
        });
    }

    @Override
    public void createDummyTeam(TeamRequest request, UserEntity user){
        this.playerService.validatePlayers(request.players());
        Team team = Team.builder()
                .name(request.name())
                .color(request.color())
                .user(user)
                .players(new HashSet<>())
                .tournaments(new HashSet<>())
                .status(true)
                .build();
        teamRepository.save(team);
        request.players().forEach(playerRequest -> {
            playerService.createPlayer(playerRequest, team);
        });
    }

    @Override
    public TeamResponse getTeamById(Long id) {
        Team team = teamById(id);
        return teamToResponse(team);
    }

    @Override
    public List<TeamResponse> getAllTeams() {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        List<Team> teams = this.teamRepository.findAllByUser(user);
        return teams.stream().map(team -> teamToResponse(team)).toList();
    }

    @Override
    @Transactional
    public String disableTeam(Long id, Boolean status) {
        Team team = getTeamByIdSecured(id);
        System.out.println("team: " + team);
        this.teamRepository.updateTeamStatus(id, status);
        this.playerService.updateStatusPlayersByTeam(id, status);
        String valueStatus  = "habilitado";
        if(!status) valueStatus = "deshabilitado";
        return team.getName() + " ha sido " + valueStatus;
    }

    @Override
    public void addPlayerToTeam(Long teamId, PlayerRequest request) {
        Team team = getTeamByIdSecured(teamId);
        this.playerService.verifyPlayer(request, team);
        this.playerService.createPlayer(request, team);
    }

    @Override
    public Team getTeamByName() {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        Optional<Team> team = this.teamRepository.findByNameAndUserId("free", user.getId());
        if(team.isEmpty()) throw new ResourceNotFoundException("Equipo free no existe.");
        return team.get();
    }

    @Override
    public void changeStatusPlayer(Long idPlayer, Boolean status) {
        Optional<Player> optPlayer = this.playerRepository.findById(idPlayer);
        if(optPlayer.isEmpty()) throw new ResourceNotFoundException("El jugador no existe.");

        optPlayer.get().getPlayerParticipants().forEach(player -> {
            this.tournamentParticipantService.changeStatusPlayerParticipant(player.getTournamentParticipant().getTournament().getCode().toString(), player.getId(), status);
        });
        this.playerRepository.updatePlayerStatusById(idPlayer, status);
    }

    @Override
    public List<ParticipantShortResponse> getTeamTournamentsParticipations(Long id) {
        return this.tournamentParticipantService.getTeamTournamentsParticipations(id);
    }

    @Override
    public Team getTeamByIdSecured(Long id) {
        Team team = teamById(id);
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        verifyTeamBelongsToManager(team, user);
        return team;
    }

    private TeamResponse teamToResponse(Team team){
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getColor(),
                team.getStatus(),
                playerService.playersToResponse(team.getPlayers()));

    }
    private void verifyTeamBelongsToManager(Team team, UserEntity user){
        if(!team.getUser().equals(user)) throw new RuntimeException("El equipo no pertenece a este usuario.");
    }
    private Team teamById(Long id){
        Optional<Team> team = this.teamRepository.findById(id);
        if (team.isEmpty()) throw new ResourceNotFoundException("El equipo no existe.");
        return team.get();
    }
}
