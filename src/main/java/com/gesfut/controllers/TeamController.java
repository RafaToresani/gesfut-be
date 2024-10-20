package com.gesfut.controllers;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.TeamResponse;
import com.gesfut.services.TeamService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    @Autowired
    private TeamService teamService;

    // ~~~~~~~~~~~~ POST ~~~~~~~~~~~~
    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void createTeam(@Valid @RequestBody TeamRequest request,BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) {
            throw new BadRequestException(Objects.requireNonNull(bindingResult.getFieldError()).getDefaultMessage());
        }
        this.teamService.createTeam(request);
    }

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
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public TeamResponse getTeamById(@PathVariable Long id) {
       return this.teamService.getTeamById(id);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<TeamResponse> getAllTeams(){
        return this.teamService.getAllTeams();
    }

    // ~~~~~~~~~~~~ PATCH ~~~~~~~~~~~~
    @PatchMapping("/change-status-team/")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public String disableTeam(@Param("team-id") Long id, @Param("status") Boolean status){
        return this.teamService.disableTeam(id, status);
    }

}
