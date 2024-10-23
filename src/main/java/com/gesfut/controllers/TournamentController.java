package com.gesfut.controllers;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.TournamentResponse;
import com.gesfut.models.tournament.Tournament;
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


    @PostMapping("/initialize")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void initializeTournament(@Valid @RequestBody MatchDayRequest request, BindingResult bindingResult) throws BadRequestException {
        if (bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        this.tournamentService.initializeTournament(request);
    }




    @PostMapping("/{tournament-code}/add-team")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void addTeamToTournament(@PathVariable("tournament-code") Tournament tournament,
                                      @RequestParam(name = "idTeam") Long idTeam) {
        this.tournamentService.addTeamToTournament(idTeam, tournament);
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
    public TournamentResponse findTournamentByCode(@PathVariable String code){
        return this.tournamentService.findTournamentByCode(code);
    }

    // ~~~~~~~~~~~~ DELETE ~~~~~~~~~~~~
    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public String deleteTournamentByCode(@PathVariable String code){
        return this.tournamentService.deleteTournamentByCode(code);
    }



}
