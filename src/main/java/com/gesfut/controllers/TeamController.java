package com.gesfut.controllers;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.ParticipantShortResponse;
import com.gesfut.dtos.responses.TeamResponse;
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

    @Operation(
            summary = "Permite agregar un jugador a un equipo ya creado.",
            description = "Solicita el id del equipo al que pertenecerá el jugador. " +
                    "El jugador debe tener un dorsal único, no puede ser capitán, porque el equipo ya contiene un capitán." +
                    "Recordatorio: crear el jugadorxtorneo así puede participar en el torneo jaja")
    @PostMapping("/add-player")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void addPlayerToTeam(@Valid @RequestBody PlayerRequest request, BindingResult bindingResult, @Param("team-id") Long teamId) throws BadRequestException {
        if(bindingResult.hasErrors()) {
            throw new BadRequestException(Objects.requireNonNull(bindingResult.getFieldError()).getDefaultMessage());
        }
        this.teamService.addPlayerToTeam(teamId, request);
    }

    // ~~~~~~~~~~~~ GET ~~~~~~~~~~~~
    @Operation(summary = "Retorna un equipo en base a su id..")
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public TeamResponse getTeamById(@PathVariable Long id) {
       return this.teamService.getTeamById(id);
    }

    @Operation(summary = "Retorna todas las participaciones de un equipo en torneos.")
    @GetMapping("/{idTeam}/tournaments")
    @ResponseStatus(HttpStatus.OK)
    public List<ParticipantShortResponse> getTeamTournaments(@PathVariable Long idTeam){
        return this.teamService.getTeamTournamentsParticipations(idTeam);
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

}
