package com.gesfut.controllers;
import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.dtos.responses.ParticipantShortResponse;
import com.gesfut.dtos.responses.PlayerResponse;
import com.gesfut.dtos.responses.TeamResponse;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.services.TeamService;
import com.gesfut.services.TournamentParticipantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/team-participant")
public class TeamParticipantController {

    @Autowired
    private TournamentParticipantService tournamentParticipantService;
    @Autowired
    private TeamService teamService;

    @GetMapping("/{code}/teams")
    @ResponseStatus(HttpStatus.OK)
    public List<ParticipantResponse> getAllTeamsParticpants(@PathVariable String code) {
        return this.tournamentParticipantService.getParticipants(code);
    }

    @GetMapping("/teams/{teamId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public ParticipantResponse getTeamByIDParticipant(@PathVariable Long teamId) {
        return this.tournamentParticipantService.getOneParticipants(teamId);
    }

    @PutMapping("/change-status/{idParticipantPlayer}/{status}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void changeStatusPlayerParticipant( @PathVariable Long idParticipantPlayer, @PathVariable Boolean status){
        this.tournamentParticipantService.changeStatusPlayerParticipant(idParticipantPlayer, status);
    }

    @PutMapping("/{code}/add-player/{teamIdParticipant}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public ParticipantResponse addPlayerToTeam(@PathVariable String code, @PathVariable Long teamIdParticipant, @RequestBody PlayerRequest player){
        ParticipantResponse participant = this.tournamentParticipantService.getOneParticipants(teamIdParticipant);
        TeamResponse team = this.teamService.getTeamById(participant.idTeam());
        Player playerAux = this.teamService.getPlayerName(player.name(),player.lastName(),team.id());
        if(playerAux != null){
          return this.tournamentParticipantService.addPlayerToTeamParticipant(code, teamIdParticipant, playerAux);
        }
        Player playerCreated = this.teamService.addPlayerToTeam(participant.idTeam(), player);
        return this.tournamentParticipantService.addPlayerToTeamParticipant(code, teamIdParticipant, playerCreated);
    }
}
