package com.gesfut.services.impl;
import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.responses.PlayerResponse;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.repositories.PlayerRepository;
import com.gesfut.services.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlayerServiceImpl implements PlayerService {

    @Autowired
    private PlayerRepository playerRepository;

    @Override
    public Player createPlayer(PlayerRequest request, Team team)
    {
        return playerRepository.save(Player.builder()
                .name(request.name())
                .lastName(request.lastName())
                .number(request.number())
                .isCaptain(request.isCaptain())
                .isGoalKeeper(request.isGoalKeeper())
                .isSuspended(false)
                .team(team)
                .build());
    }

    @Override
    public PlayerResponse playersToResponse(Player player) {
        return new PlayerResponse(
                player.getName(),
                player.getLastName(),
                player.getNumber(),
                player.getIsCaptain(),
                player.getIsGoalKeeper(),
                player.getIsSuspended()
        );
    }
}
