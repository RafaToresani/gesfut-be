package com.gesfut.services;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.responses.PlayerResponse;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;

import java.util.Set;

public interface PlayerService {
    Player createPlayer(PlayerRequest request, Team team);
    PlayerResponse playerToResponse(Player player);
    Set<PlayerResponse> playersToResponse(Set<Player> players);
    void updateStatusPlayersByTeam(Long team, Boolean status);
}
