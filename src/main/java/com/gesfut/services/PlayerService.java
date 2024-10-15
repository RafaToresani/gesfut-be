package com.gesfut.services;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.responses.PlayerResponse;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;

public interface PlayerService {
    Player createPlayer(PlayerRequest request, Team team);
    PlayerResponse playersToResponse(Player player);

}
