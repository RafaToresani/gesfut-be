package com.gesfut.services.impl;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.responses.EventResponse;
import com.gesfut.dtos.responses.MatchDayResponse;
import com.gesfut.dtos.responses.MatchResponse;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.matchDay.Event;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.*;
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

    @Autowired
    private TournamentParticipantRepository TournamentParticipantRepository;

    @Override
    public void generateMatchDays(HashSet<TournamentParticipant> tournamentParticipants, String tournamentCode) {
        Tournament tournament = getTournament(tournamentCode);
        if(!tournament.getMatchDays().isEmpty()) throw new ResourceAlreadyExistsException("El torneo ya cuenta con fechas.");
        int numberOfTeams = tournamentParticipants.size();
        int numberOfMatchDays = numberOfTeams - 1;
        Set<Match> allMatches = new HashSet<>();

        /*if (numberOfTeams % 2 == 0) {
            List<TournamentParticipant> tournamentParticipantsList = new ArrayList<>(tournamentParticipants);
            generate(tournament, tournamentParticipantsList, numberOfTeams, numberOfMatchDays, allMatches);
        } else {

            //CREAR EQUIPO
            Team dummyTeam = Team
                    .builder()
                    .name("FREE")
                    .players(new HashSet<>())
                    .build();
            dummyTeam = teamRepository.save(dummyTeam);

            //REALACIONAR A LA TABLA EQUIPOSXTORNEOS ES DECIR TOURANMENTPARTICIPANT
            TournamentParticipant tournamentParticipant = TournamentParticipant
                    .builder()
                    .team(dummyTeam)
                    .tournament(tournament)
                    .build();

            //GUARDAR EN LA TABLA EQUIPOSXTORNEOS
            tournamentParticipant = TournamentParticipantRepository.save(tournamentParticipant);

            //GUARDAR EN la lista de equipos del torneo
            tournamentParticipants.add(tournamentParticipant);

            //necsito pasar tournamentParticipantes que es un hashset a una arraylist!!! aca aca aca
            List<TournamentParticipant> tournamentParticipantsList = new ArrayList<>(tournamentParticipants);

            numberOfTeams++;
            numberOfMatchDays = numberOfTeams - 1;
            generate(tournament, tournamentParticipantsList, numberOfTeams, numberOfMatchDays, allMatches);
        }*/

        List<TournamentParticipant> tournamentParticipantsList = new ArrayList<>(tournamentParticipants);
        generate(tournament, tournamentParticipantsList, numberOfTeams, numberOfMatchDays, allMatches);
    }

    void generate(Tournament tournament, List<TournamentParticipant> teams, int numberOfTeams, int numberOfMatchDays, Set<Match> allMatches) {
        for (int matchDayNumber = 0; matchDayNumber < numberOfMatchDays; matchDayNumber++) {
            MatchDay matchDay = matchDayRepository.save(
                    MatchDay.builder()
                            .numberOfMatchDay(matchDayNumber)
                            .tournament(tournament)
                            .matches(new HashSet<>())
                            .build());
            Set<Match> matches = matchDay.getMatches();

            for (int j = 0; j < numberOfTeams / 2; j++) {
                TournamentParticipant homeTeam = teams.get(j);
                TournamentParticipant awayTeam = teams.get(numberOfTeams - 1 - j);
                if (!matchExists(allMatches, homeTeam, awayTeam)) {
                    Match newMatch = Match
                            .builder()
                            .homeTeam(homeTeam) // ?
                            .awayTeam(awayTeam) // ?
                            .isFinished(false)
                            .goalsHomeTeam(0)
                            .goalsAwayTeam(0)
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



    private boolean matchExists(Set<Match> matches, TournamentParticipant homeTeam, TournamentParticipant awayTeam) {
        return matches.stream().anyMatch(match ->
                (match.getHomeTeam().equals(homeTeam) && match.getAwayTeam().equals(awayTeam)) ||
                        (match.getHomeTeam().equals(awayTeam) && match.getAwayTeam().equals(homeTeam))
        );
    }

    private void rotateTeams(List<TournamentParticipant> teams) {
        // Guarda el último equipo y rota los demás
        TournamentParticipant lastTeam = teams.remove(teams.size() - 1);
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
                match.getId(),
                match.getHomeTeam().getTeam().getName(),
                match.getAwayTeam().getTeam().getName(),
                match.getMatchDay().getNumberOfMatchDay(),
                match.getGoalsHomeTeam(),
                match.getGoalsAwayTeam(),
                match.getEvents().stream().map(this::eventToResponse).toList()
        );
    }

    private EventResponse eventToResponse(Event event){
        return new EventResponse(
                event.getId(),
                event.getQuantity(),
                event.getType(),
                event.getPlayerParticipant().getPlayer().getName() + " " +
                event.getPlayerParticipant().getPlayer().getLastName());
    }
}