package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.PlayerResponse;
import com.gesfut.dtos.responses.TeamResponse;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.TeamRepository;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.PlayerService;
import com.gesfut.services.TeamService;
import com.gesfut.services.UserEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.gesfut.config.security.SecurityUtils.getCurrentUserEmail;

@Service
public class TeamServiceImpl implements TeamService {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private UserEntityService userService;

    @Override
    public void createTeam(TeamRequest request) {
        UserEntity user = userService.findUserByEmail(getCurrentUserEmail());
        Team team = Team.builder()
                .name(request.name())
                .color(request.color())
                .user(user)
                .players(new HashSet<>())
                .tournaments(new HashSet<>())
                .build();

        teamRepository.save(team);

        request.players().forEach(playerRequest -> {
            playerService.createPlayer(playerRequest, team);

        });
    }

    @Override
    public TeamResponse getTeamById(Long id) {
        Optional<Team> team = teamRepository.findById(id);
        if (team.isEmpty()) {
            throw new ResourceNotFoundException("Equipo no encontrado");
        }

        return teamToResponse(team.get());
    }

    @Override
    public List<TeamResponse> getAllTeams() {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        List<Team> teams = this.teamRepository.findAllByUser(user);
        return teams.stream().map(team -> teamToResponse(team)).toList();
    }

    private TeamResponse teamToResponse(Team team){
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getColor(),
                playerService.playersToResponse(team.getPlayers()));
    }
}
