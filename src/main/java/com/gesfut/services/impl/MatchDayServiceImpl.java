package com.gesfut.services.impl;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.responses.MatchDayResponse;
import com.gesfut.dtos.responses.MatchResponse;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.repositories.MatchDayRepository;
import com.gesfut.repositories.MatchRepository;
import com.gesfut.repositories.TeamRepository;
import com.gesfut.repositories.TournamentRepository;
import com.gesfut.services.MatchDayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MatchDayServiceImpl implements MatchDayService {

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TeamRepository  teamRepository;

    @Autowired
    private MatchDayRepository matchDayRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Override
    public void generateMatchDays(MatchDayRequest request) {
        String code = request.tournamentCode();
        Optional<Tournament> tournament = tournamentRepository.findByCode(UUID.fromString(code));
        if (tournament.isEmpty()) {
            throw new ResourceNotFoundException("Tournament not found");
        }

        List<Team> teamsList = teamRepository.findAllById(request.teams());
        if (teamsList.size() != request.teams().size()) {
            throw new ResourceNotFoundException("One or more teams not found");
        }


        int numberOfTeams = teamsList.size();
        int numberOfMatchDays = numberOfTeams - 1;
        for (int matchDayNumber = 0; matchDayNumber < numberOfMatchDays; matchDayNumber++) {
            MatchDay matchDay = new MatchDay();
            matchDay.setNumberOfMatchDay(matchDayNumber + 1);
            matchDay.setTournament(tournament.get());
            List<Match> matches = new ArrayList<>();
            for (int j = 0; j < numberOfTeams / 2; j++) {
                Team homeTeam = teamsList.get(j);
                Team awayTeam = teamsList.get(numberOfTeams - 1 - j);
                if (!matchRepository.existsByHomeTeamAndAwayTeamAndMatchDay(homeTeam, awayTeam, matchDay)) {
                    Match match = new Match();
                    match.setHomeTeam(homeTeam);
                    match.setAwayTeam(awayTeam);
                    match.setMatchDay(matchDay);
                    matches.add(match);
                    matchRepository.save(match);
                }
            }
            matchDay.setMatches(matches);
            matchDayRepository.save(matchDay);
            Team lastTeam = teamsList.get(teamsList.size() - 1);
            teamsList.remove(lastTeam);
            teamsList.add(1, lastTeam);
        }
    }



    @Override
    public MatchDayResponse matchDayToResponse(MatchDay matchDay) {
        List<MatchResponse> matches = new ArrayList<>();
        for (Match match : matchDay.getMatches()) {
            matches.add(matchToResponse(match));
        }
        return new MatchDayResponse(matchDay.getNumberOfMatchDay(), matches);
    }

    private MatchResponse matchToResponse(Match match) {
        return new MatchResponse(
                match.getHomeTeam().getName(),
                match.getAwayTeam().getName(),0,0,0
        );
    }
}