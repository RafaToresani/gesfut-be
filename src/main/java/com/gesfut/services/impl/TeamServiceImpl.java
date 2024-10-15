package com.gesfut.services.impl;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.gesfut.config.security.SecurityUtils.getCurrentUserEmail;

@Service
public class TeamServiceImpl implements TeamService {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private UserRepository userRepository;

    @Override
    public void createTeam(TeamRequest request){
        String userEmail = getCurrentUserEmail();
        Optional<UserEntity> user = userRepository.findByEmail(userEmail);
        if(user.isEmpty()) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        Team team = Team.builder()
                .name(request.name())
                .color(request.color())
                .user(user.get())
                .players(new HashSet<Player>())
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

        Set<PlayerResponse> players = new HashSet<>();
        team.get().getPlayers().forEach(player -> {
            players.add(playerService.playersToResponse(player));
        });

        return new TeamResponse(
                team.get().getId(),
                team.get().getName(),
                team.get().getColor(),
                players
        );
    }
}
