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
    // Plan 22/10 #15: sin @Valid sobre la lista: cada equipo se valida en el servicio y los invalidos se informan sin frenar a los validos.
    public com.gesfut.dtos.responses.TeamImportResponse createMultipleTeams(@RequestBody List<TeamRequest> request) {
        return this.teamService.createMultipleTeams(request);
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
    public TeamResponse createTeam(@Valid @RequestBody TeamRequest request,BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) {
            // Auditoria 22/10 (#16): informar todos los errores juntos (nombre, color, jugadores), no solo el primero.
            java.util.Map<String, String> errors = new java.util.TreeMap<>();
            bindingResult.getFieldErrors().forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
            throw new BadRequestException(String.join(" ", errors.values()));
        }
       return this.teamService.createTeam(request);
    }



    // ~~~~~~~~~~~~ GET ~~~~~~~~~~~~
    @Operation(summary = "Retorna un equipo en base a su id..")
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')") // Revision critica #14
    public TeamResponse getTeamById(@PathVariable Long id) {
       return this.teamService.getTeamById(id);
    }

    @Operation(summary = "Retorna todas las participaciones de un equipo en torneos.")
    @GetMapping("/{idTeam}/tournaments")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')") // Revision critica #14
    public List<ParticipantShortResponse> getTeamTournaments(@PathVariable Long idTeam){
        return this.teamService.getTeamTournamentsParticipations(idTeam);
    }

    @Operation(summary = "Retorna estaidistcas de todos los jugadores ya esten o no jugando un torneo")
    @GetMapping("/{idTeam}/players-stats")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')") // Revision critica #14
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
    @Operation(summary = "Edita nombre, apellido, dorsal y DNI opcional de un jugador del plantel (solo el duenio).")
    @PutMapping("/players/{idPlayer}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public com.gesfut.dtos.responses.PlayerResponse editPlayer(@PathVariable Long idPlayer, @Valid @RequestBody com.gesfut.dtos.requests.PlayerEditRequest request) {
        return this.teamService.editPlayer(idPlayer, request);
    }

    @PutMapping("/change-status-player/{idPlayer}/{status}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void changeStatusPlayer(@PathVariable Long idPlayer, @PathVariable Boolean status){
        this.teamService.changeStatusPlayer(idPlayer, status);
    }

    @PutMapping("/change-status-team/{teamId}/{status}")
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public ResponseEntity<Map<String, String>> disableTeam(@PathVariable("teamId") Long teamId, @PathVariable("status") Boolean status) {
        this.teamService.disableTeam(teamId, status);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Estado cambiado correctamente");
        return ResponseEntity.ok(response);
    }


    // Plan 22/10 #2: "Eliminar equipo". Borra si nunca jugo; si tiene historial lo deshabilita.
    @Operation(summary = "Indica que pasara al eliminar el equipo (DELETE o DISABLE) sin cambiar nada.")
    @GetMapping("/{teamId}/delete-preview")
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public TeamDeletionResponse previewDeleteTeam(@PathVariable("teamId") Long teamId) {
        return this.teamService.previewDeleteTeam(teamId);
    }

    @Operation(summary = "Elimina el equipo si nunca jugo; si tiene historial lo deshabilita (conserva resultados).")
    @DeleteMapping("/{teamId}")
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public TeamDeletionResponse deleteTeam(@PathVariable("teamId") Long teamId) {
        return this.teamService.deleteTeam(teamId);
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
