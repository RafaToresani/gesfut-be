package com.gesfut.services.impl;
import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.responses.PlayerResponse;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.repositories.PlayerRepository;
import com.gesfut.services.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PlayerServiceImpl implements PlayerService {

    @Autowired
    private PlayerRepository playerRepository;

    @Override
    public Player createPlayer(PlayerRequest request, Team team)
    {
        if(request.lastName() == null || request.name() == null || request.number() == null || request.isCaptain() == null || request.isGoalKeeper() == null){
            throw new IllegalArgumentException("Todos los campos son obligatorios.");
        }
        if(request.lastName().isEmpty() || request.name().isEmpty()){
            throw new IllegalArgumentException("Nombre y apellido no pueden estar vacios.");
        }

      return playerRepository.save(Player.builder()
                .name(request.name())
                .lastName(request.lastName())
                .number(request.number())
                .isCaptain(request.isCaptain())
                .isGoalKeeper(request.isGoalKeeper())
                .status(true)
                .team(team)
                .playerParticipants(new HashSet<>())
                .build());
    }



    @Override
    public PlayerResponse playerToResponse(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getName(),
                player.getLastName(),
                player.getNumber(),
                player.getIsCaptain(),
                player.getIsGoalKeeper(),
                player.getStatus()
        );
    }

    @Override
    public Set<PlayerResponse> playersToResponse(Set<Player> players){
        return players.stream().map(item -> playerToResponse(item)).collect(Collectors.toSet());
    }

    @Override
    public void updateStatusPlayersByTeam(Long team, Boolean status) {
        this.playerRepository.updatePlayerStatus(team, status);
    }

    @Override
    public void verifyPlayer(PlayerRequest playerRequest, Team team){
        Boolean checkNumber = this.playerRepository.existsByNumberAndTeamId(playerRequest.number(), team.getId());
        if(checkNumber) throw new ResourceAlreadyExistsException("El equipo ya cuenta con el dorsal " + playerRequest.number());
        Boolean checkCaptain = this.playerRepository.existsByIsCaptainAndTeamId(playerRequest.isCaptain(), team.getId());
        if(checkCaptain && playerRequest.isCaptain()) throw  new ResourceNotFoundException("El equipo ya cuenta con capitan.");
        Boolean checkName = this.playerRepository.existsByNameAndLastNameAndTeamId(playerRequest.name(),playerRequest.lastName(),team.getId());
        if (checkName)throw new ResourceAlreadyExistsException("El equipo ya cuentacon ese jugador.");
    }

    @Override
    public void validatePlayers(Set<PlayerRequest> players) {
        Set<Integer> uniqueNumbers = new HashSet<>();
        boolean captainFound = false;
        boolean goalKeeperFound = false;

        for (PlayerRequest player : players) {
            if (!uniqueNumbers.add(player.number())) throw new ResourceAlreadyExistsException("El número " + player.number() + " ya está asignado a otro jugador.");

            if (player.isCaptain()) {
                if (captainFound) {
                    throw new IllegalArgumentException("Solo puede haber un capitán.");
                }
                captainFound = true;
            }

            if (player.isGoalKeeper()) goalKeeperFound = true;
        }

        if (!captainFound) throw new IllegalArgumentException("Debe haber al menos un capitán.");

        if (!goalKeeperFound) throw new IllegalArgumentException("Debe haber al menos un portero.");
    }

}
