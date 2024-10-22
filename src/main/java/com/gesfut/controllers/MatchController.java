package com.gesfut.controllers;

import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.services.MatchService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    @Autowired
    private MatchService matchService;


    @PostMapping("/load-result")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public String loadMatchResult(@Valid @RequestBody MatchRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        return this.matchService.loadMatchResult(request);
    }
}
