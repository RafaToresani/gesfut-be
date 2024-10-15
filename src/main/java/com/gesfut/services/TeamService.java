package com.gesfut.services;

import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.TeamResponse;
import com.gesfut.models.team.Team;

public interface TeamService  {

    void createTeam(TeamRequest request);
    TeamResponse getTeamById(Long id);

}
