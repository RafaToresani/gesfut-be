package com.gesfut.controllers;
import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.dtos.responses.ParticipantShortResponse;
import com.gesfut.dtos.responses.TeamResponse;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.services.TournamentParticipantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments")
public class TeamParticipantController {

    @Autowired
    private TournamentParticipantService tournamentParticipantService;

    @GetMapping("/{code}/teams")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public List<ParticipantResponse> getAllTeamsParticpants(@PathVariable String code) {
        return this.tournamentParticipantService.getParticipants(code);
    }

    @GetMapping("/teams/{teamId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public ParticipantResponse getTeamByIDParticipant(@PathVariable Long teamId) {
        return this.tournamentParticipantService.getOneParticipants(teamId);
    }

    @GetMapping("/{code}/teams-short")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public List<ParticipantShortResponse> getAllTeamsParticpantsShort(@PathVariable String code) {
        return this.tournamentParticipantService.getParticipantsShort(code);
    }


    @PutMapping("/change-status/{code}/{idParticipant}/{status}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void changeStatusPlayerParticipant(@PathVariable String code, @PathVariable Long idParticipant, @PathVariable Boolean status){
        this.tournamentParticipantService.changeStatusPlayerParticipant(code, idParticipant, status);
    }
}
