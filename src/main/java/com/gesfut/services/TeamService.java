package com.gesfut.services;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.*;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.models.user.UserEntity;

import java.util.List;
import java.util.Optional;

public interface TeamService  {
    void createTeam(TeamRequest request);
    void createDummyTeam(TeamRequest request, UserEntity user);
    TeamResponse getTeamById(Long id);
    List<TeamResponse> getAllTeams();
    Team getTeamByIdSecured(Long id);
    String disableTeam(Long id, Boolean status);
    Player addPlayerToTeam(Long teamId, PlayerRequest request);
    Team getTeamByName();
    void changeStatusPlayer(Long idPlayer, Boolean status);
    List<ParticipantShortResponse> getTeamTournamentsParticipations(Long id);
    Player getPlayerNumber(Integer number, Long teamId);
    Player getPlayerName(String name, String lastName, Long teamId);
    TeamWithAllStatsPlayerResponse getAllPlayerStatsByTeam(Long idTeam);
}

