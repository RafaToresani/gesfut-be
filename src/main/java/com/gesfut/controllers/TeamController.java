package com.gesfut.controllers;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.*;
import com.gesfut.models.team.Player;
import com.gesfut.services.PlayerService;
import com.gesfut.services.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    @Autowired
    private TeamService teamService;
    @Autowired
    private PlayerService playerService;

    //crear varios equipos
    @Operation(
            summary = "Permite crear varios equipos.",
            description = "El equipo creado pertenecerá al usuario logueado. " +
                    "Además, requiere un color, un nombre y un listado de jugadores." +
                    "Cada jugador debe tener un dorsal único, al menos uno de los jugadores debe ser capitán, pudiendo haber sólo uno, y al menos uno de los jugadores debe ser arquero.")
    @PostMapping("/create-multiple")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void createMultipleTeams(@Valid @RequestBody List<TeamRequest> request,BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) {
            throw new BadRequestException(Objects.requireNonNull(bindingResult.getFieldError()).getDefaultMessage());
        }
        this.teamService.createMultipleTeams(request);
    }




    // ~~~~~~~~~~~~ POST ~~~~~~~~~~~~
    @Operation(
            summary = "Permite crear un nuevo equipo.",
            description = "El equipo creado pertenecerá al usuario logueado. " +
                    "Además, requiere un color, un nombre y un listado de jugadores." +
                    "Cada jugador debe tener un dorsal único, al menos uno de los jugadores debe ser capitán, pudiendo haber sólo uno, y al menos uno de los jugadores debe ser arquero.")
    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void createTeam(@Valid @RequestBody TeamRequest request,BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) {
            throw new BadRequestException(Objects.requireNonNull(bindingResult.getFieldError()).getDefaultMessage());
        }
        this.teamService.createTeam(request);
    }



    // ~~~~~~~~~~~~ GET ~~~~~~~~~~~~
    @Operation(summary = "Retorna un equipo en base a su id..")
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public TeamResponse getTeamById(@PathVariable Long id) {
       return this.teamService.getTeamById(id);
    }

    @Operation(summary = "Retorna todas las participaciones de un equipo en torneos.")
    @GetMapping("/{idTeam}/tournaments")
    @ResponseStatus(HttpStatus.OK)
    public List<ParticipantShortResponse> getTeamTournaments(@PathVariable Long idTeam){
        return this.teamService.getTeamTournamentsParticipations(idTeam);
    }

    @Operation(summary = "Retorna estaidistcas de todos los jugadores ya esten o no jugando un torneo")
    @GetMapping("/{idTeam}/players-stats")
    @ResponseStatus(HttpStatus.OK)
    public TeamWithAllStatsPlayerResponse getAllPlayerStatsByTeam(@PathVariable Long idTeam){
        return this.teamService.getAllPlayerStatsByTeam(idTeam);
    }


    @Operation(summary = "Retorna el listado de equipos.", description = "Los equipos deben pertenecer al usuario logueado.")
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public List<TeamResponse> getAllTeams(){
        return this.teamService.getAllTeams();
    }

    // ~~~~~~~~~~~~ PUT ~~~~~~~~~~~~
    @PutMapping("/change-status-player/{idPlayer}/{status}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void changeStatusPlayer(@PathVariable Long idPlayer, @PathVariable Boolean status){
        this.teamService.changeStatusPlayer(idPlayer, status);
    }

    @PutMapping("/change-status-team/{teamId}/{status}")
    public ResponseEntity<Map<String, String>> disableTeam(@PathVariable("teamId") Long teamId, @PathVariable("status") Boolean status) {
        this.teamService.disableTeam(teamId, status);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Estado cambiado correctamente");
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Permite agregar un jugador a un equipo ya creado.")
    @PutMapping("/add-player/{teamId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public PlayerResponse addPlayerToTeam(
            @PathVariable("teamId") Long teamId,
            @Valid @RequestBody PlayerRequest playerRequest, // Objeto recibido en el cuerpo
            BindingResult bindingResult) throws BadRequestException {

        if (bindingResult.hasErrors()) {
            throw new BadRequestException(
                    Objects.requireNonNull(bindingResult.getFieldError()).getDefaultMessage()
            );
        }

        Player created = this.teamService.addPlayerToTeam(teamId, playerRequest);
        return this.playerService.playerToResponse(created);
    }


}
