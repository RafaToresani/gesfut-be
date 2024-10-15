package com.gesfut.controllers;

import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.TeamResponse;
import com.gesfut.services.TeamService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    @Autowired
    private TeamService teamService;



    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void createTeam(@Valid @RequestBody TeamRequest request,BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) {
            throw new BadRequestException(Objects.requireNonNull(bindingResult.getFieldError()).getDefaultMessage());
        }
        this.teamService.createTeam(request);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public TeamResponse getTeamById(@PathVariable Long id) {
       return this.teamService.getTeamById(id);
    }
}
