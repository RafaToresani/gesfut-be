package com.gesfut.services;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.TeamResponse;
import com.gesfut.models.team.Team;

import java.util.List;

public interface TeamService  {
    void createTeam(TeamRequest request);
    TeamResponse getTeamById(Long id);
    List<TeamResponse> getAllTeams();
    Team getTeamByIdSecured(Long id);
    String disableTeam(Long id, Boolean status);
    void addPlayerToTeam(Long teamId, PlayerRequest request);
}
