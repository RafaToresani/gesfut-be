package com.gesfut.controllers;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.TournamentResponse;
import com.gesfut.dtos.responses.TournamentShortResponse;
import com.gesfut.services.TournamentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tournaments")
public class TournamentController {

    @Autowired
    private TournamentService tournamentService;

    // ~~~~~~~~~~~~ POST ~~~~~~~~~~~~
    @Operation(summary = "Permite crear un torneo", description = "Permite crear un torneo al usuario logueado.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public String createTournament(@Valid @RequestBody TournamentRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        return this.tournamentService.createTournament(request);
    }


    @Operation(summary = "Permite inicializar el torneo",
            description = "Recibe el código del torneo y un listado de los ids de los equipos que ya han sido cargados previamente. " +
                    "Este endpoint se encarga de generar la participación de los equipos en el torneo, la participación de los jugadores de cada equipo y las estadísticas de ambos. " +
                    "Además, crea las jornadas y los partidos. Si el número de equipos es impar, cada fecha uno de los equipos descansará.")
    @PostMapping("/initialize")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void initializeTournament(@Valid @RequestBody MatchDayRequest request, BindingResult bindingResult) throws BadRequestException {
        if (bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        this.tournamentService.initializeTournament(request);
    }

    @Operation(summary = "Recordatorio: rehacer endpoint entero.")
    @PostMapping("/{tournament-code}/add-team")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void addTeamToTournament(@Valid @RequestBody MatchDayRequest request, BindingResult bindingResult) throws BadRequestException {
        if (bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        this.tournamentService.updateTournamentParticipants(request);
    }

    // ~~~~~~~~~~~~ GET ~~~~~~~~~~~~
    @Operation(summary = "Retorna el listado torneos del usuario logueado Respuesta full.")
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public List<TournamentResponse> findAllTournaments(){
        return this.tournamentService.findAllTournaments();
    }

    @Operation(summary = "Retorna un torneo en base a su código. Accesible para todos.")
    @GetMapping("/{code}")
    @ResponseStatus(HttpStatus.OK)
    public TournamentResponse findTournamentByCode(@PathVariable String code){
        return this.tournamentService.findTournamentByCode(code);
    }

    @Operation(summary = "Retorna el listado torneos del usuario logueado codigo y nombre.")
    @GetMapping("/short")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public List<TournamentShortResponse> findAllTournamentsShortAll(){
        return this.tournamentService.findAllTournamentsShortAll();
    }

    @Operation(summary = "Retorna el listado torneos del usuario logueado codigo y nombre.")
    @GetMapping("/short/{code}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public TournamentShortResponse findTournamentsShort(@PathVariable String code){
        return this.tournamentService.findAllTournamentsShort(code);
    }

    @Operation(summary = "Verifica si el equipo existe o no.")
    @GetMapping("/exist/{code}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public Boolean existTournament(@PathVariable String code){
        return this.tournamentService.existsByCode(code);
    }

    // ~~~~~~~~~~~~ DELETE ~~~~~~~~~~~~
    @Operation(summary = "Permite deshabilitar un torneo.")
    @PatchMapping("/change-status-tournament")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public String deleteTournamentByCode(@Param(value = "tournament-code") String code, @Param(value = "status") Boolean status){
        return this.tournamentService.changeStatusTournamentByCode(code, status);
    }

    // ~~~~~~~~~~~~ PUT ~~~~~~~~~~~~
    @PutMapping("/change-name-tournament/{tournament-code}/{name}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public Boolean changeNameTournament(@PathVariable("tournament-code") String code, @PathVariable("name") String name) {
        return this.tournamentService.changeNameTournamentByCode(code, name);
    }

    @PutMapping("/change-isActive/{tournament-code}/{isActive}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public Boolean changeIsActive(@PathVariable("tournament-code") String code, @PathVariable("isActive") Boolean isActive) {
        return this.tournamentService.changeIsActive(code, isActive);
    }

}
