package com.gesfut.controllers;

import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.services.TournamentService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tournaments")
public class TournamentController {

    @Autowired
    private TournamentService tournamentService;

    // ~~~~~~~~~~~~ POST ~~~~~~~~~~~~
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String createTournament(@Valid @RequestBody TournamentRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        this.tournamentService.createTournament(request);
        return "Torneo creado exitosamente.";
    }
}
