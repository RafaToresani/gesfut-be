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

        String dni = com.gesfut.services.impl.DniRules.normalize(request.dni());
        if (dni != null && team.getPlayers() != null && team.getPlayers().stream().anyMatch(p -> dni.equals(com.gesfut.services.impl.DniRules.key(p.getDni()))))
            throw new IllegalArgumentException("Ya hay un jugador con DNI " + dni + " en este equipo.");
        // Regla unica de dorsal para cualquier alta (individual, por torneo o en lote): no repetir el de un jugador activo.
        if (team.getId() != null) {
            var sameNumber = this.playerRepository.findActiveByNumberAndTeamId(request.number(), team.getId());
            if (!sameNumber.isEmpty())
                throw new IllegalArgumentException("El dorsal " + request.number() + " ya lo usa " + sameNumber.get(0).getName() + " " + sameNumber.get(0).getLastName() + ".");
        }
      return playerRepository.save(Player.builder()
                .dni(dni)
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
                player.getStatus(),
                player.getDni()
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
        // Misma regla que editar jugador (a2f86be): el dorsal no puede repetirse entre jugadores activos del equipo (400).
        var sameNumber = this.playerRepository.findActiveByNumberAndTeamId(playerRequest.number(), team.getId());
        if (!sameNumber.isEmpty()) {
            var other = sameNumber.get(0);
            throw new IllegalArgumentException("El dorsal " + playerRequest.number() + " ya lo usa " + other.getName() + " " + other.getLastName() + ".");
        }
        Boolean checkCaptain = this.playerRepository.existsByIsCaptainAndTeamId(playerRequest.isCaptain(), team.getId());
        if(checkCaptain && playerRequest.isCaptain()) throw  new ResourceNotFoundException("El equipo ya cuenta con capitan.");
        Boolean checkName = this.playerRepository.existsByNameAndLastNameAndTeamId(playerRequest.name(),playerRequest.lastName(),team.getId());
        if (checkName)throw new ResourceAlreadyExistsException("El equipo ya cuentacon ese jugador.");
    }

    @Override
    public void validatePlayers(Set<PlayerRequest> players) {
        if (players == null) return;
        Set<Integer> uniqueNumbers = new HashSet<>();
        boolean captainFound = false;

        for (PlayerRequest player : players) {
            if (!uniqueNumbers.add(player.number())) throw new IllegalArgumentException("El dorsal " + player.number() + " est\u00e1 repetido en la lista.");

            if (player.isCaptain()) {
                if (captainFound) {
                    throw new IllegalArgumentException("Solo puede haber un capit\u00e1n.");
                }
                captainFound = true;
            }
        }

        // C(b): capit\u00e1n y arquero ya no se exigen al crear; se piden al generar el fixture (TournamentServiceImpl.requireCompleteTeams).
    }

}
