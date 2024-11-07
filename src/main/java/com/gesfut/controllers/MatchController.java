package com.gesfut.controllers;

import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.dtos.responses.MatchDetailedResponse;
import com.gesfut.dtos.responses.MatchResponse;
import com.gesfut.services.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @Operation(summary = "Permite cargar los resultados de un partido",
                description = "Solicita el id del partido, y un listado de eventos." +
                        "Cada evento está asociado a un jugador. El endpoint se encarga de guardar el listado de eventos, " +
                        "y asociarlos a las estadísticas del jugador. Además, modifica las estadísticas del equipo y determina quien ganó el partido")
    @PostMapping("/load-result")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public String loadMatchResult(@Valid @RequestBody MatchRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        return this.matchService.loadMatchResult(request);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public MatchResponse getMatchById(@PathVariable Long id){
        return this.matchService.getMatchById(id);
    }

    @GetMapping("/detailed/{id}")
    @ResponseStatus(HttpStatus.OK)
    public MatchDetailedResponse getMatchDetailedById(@PathVariable Long id){
        return this.matchService.getDetailedMatchById(id);
    }

    @PutMapping("/update-result")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void updateMatchResult(@Valid @RequestBody MatchRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());

        this.matchService.updateMatchResult(request);
    }
}
