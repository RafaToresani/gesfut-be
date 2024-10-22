package com.gesfut.services.impl;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.responses.MatchDayResponse;
import com.gesfut.dtos.responses.MatchResponse;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
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
        Tournament tournament = getTournament(request.tournamentCode());
        if(!tournament.getMatchDays().isEmpty()) throw new ResourceAlreadyExistsException("El torneo ya cuenta con fechas.");
        List<Team> teams = getTeams(request.teams());
        int numberOfTeams = teams.size();
        int numberOfMatchDays = numberOfTeams - 1;
        Set<Match> allMatches = new HashSet<>();

        if (numberOfTeams % 2 == 0) {
            generate(tournament, teams, numberOfTeams, numberOfMatchDays, allMatches);
        } else {
            Team dummyTeam = Team.builder().name("FREE").players(new HashSet<>()).build();
            dummyTeam = teamRepository.save(dummyTeam);
            teams.add(dummyTeam);
            numberOfTeams++;
            numberOfMatchDays = numberOfTeams - 1;
            generate(tournament, teams, numberOfTeams, numberOfMatchDays, allMatches);
        }
    }

    void generate(Tournament tournament, List<Team> teams, int numberOfTeams, int numberOfMatchDays, Set<Match> allMatches) {

        for (int matchDayNumber = 0; matchDayNumber < numberOfMatchDays; matchDayNumber++) {
            MatchDay matchDay = matchDayRepository.save(
                    MatchDay.builder()
                            .numberOfMatchDay(matchDayNumber)
                            .tournament(tournament)
                            .matches(new HashSet<>())
                            .build());
            Set<Match> matches = matchDay.getMatches();

            for (int j = 0; j < numberOfTeams / 2; j++) {
                Team homeTeam = teams.get(j);
                Team awayTeam = teams.get(numberOfTeams - 1 - j);
                if (!matchExists(allMatches, homeTeam, awayTeam)) {
                    Match newMatch = Match
                            .builder()
                            .homeTeam(homeTeam)
                            .awayTeam(awayTeam)
                            .matchDay(matchDay)
                            .build();

                    matches.add(matchRepository.save(newMatch));
                    allMatches.add(newMatch);
                }
            }

            matchDay.setMatches(matches);
            matchDayRepository.save(matchDay);
            rotateTeams(teams);
        }
    }



    private boolean matchExists(Set<Match> matches, Team homeTeam, Team awayTeam) {
        return matches.stream().anyMatch(match ->
                (match.getHomeTeam().equals(homeTeam) && match.getAwayTeam().equals(awayTeam)) ||
                        (match.getHomeTeam().equals(awayTeam) && match.getAwayTeam().equals(homeTeam))
        );
    }

    private void rotateTeams(List<Team> teams) {
        // Guarda el último equipo y rota los demás
        Team lastTeam = teams.remove(teams.size() - 1);
        teams.add(1, lastTeam); // Coloca el último equipo en la segunda posición
    }

    private Tournament getTournament(String code){
        Optional<Tournament> tournament = tournamentRepository.findByCode(UUID.fromString(code));
        if (tournament.isEmpty()) {
            throw new ResourceNotFoundException("Torneo no encontrado.");
        }
        return tournament.get();
    }

    private List<Team> getTeams(List<Long> ids){
        List<Team> teamsList = teamRepository.findAllById(ids);
        if (teamsList.size() != ids.size()) {
            throw new ResourceNotFoundException("Uno o más equipos no existen.");
        }
        return teamsList;
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
                match.getAwayTeam().getName(),
                match.getMatchDay().getNumberOfMatchDay(),
                0,0
        );
    }
}