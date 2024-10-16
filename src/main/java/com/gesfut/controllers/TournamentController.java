package com.gesfut.controllers;

import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.TournamentResponse;
import com.gesfut.services.TournamentService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
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
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public String createTournament(@Valid @RequestBody TournamentRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        this.tournamentService.createTournament(request);
        return "Torneo creado exitosamente.";
    }

    @PostMapping("/{tournament-code}/add-team")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public String addTeamToTournament(@PathVariable("tournament-code") String code,
                                      @RequestParam(name = "idTeam") Long idTeam) {
        return this.tournamentService.addTeamToTournament(idTeam, code);
    }

    // ~~~~~~~~~~~~ GET ~~~~~~~~~~~~
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public List<TournamentResponse> findAllTournaments(){
        return this.tournamentService.findAllTournaments();
    }

    @GetMapping("/{code}")
    @ResponseStatus(HttpStatus.OK)
    public TournamentResponse findTournamentByCode(String code){
        return this.tournamentService.findTournamentByCode(code);
    }

    // ~~~~~~~~~~~~ DELETE ~~~~~~~~~~~~
    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public String deleteTournamentByCode(String code){
        return this.tournamentService.deleteTournamentByCode(code);
    }
}
