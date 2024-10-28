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
import com.gesfut.services.MatchService;
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
    @Autowired
    private MatchService matchService;

    @Override
    public void generateMatchDays(HashSet<TournamentParticipant> tournamentParticipants, String tournamentCode) {
        Tournament tournament = getTournament(tournamentCode);
        if(!tournament.getMatchDays().isEmpty()) throw new ResourceAlreadyExistsException("El torneo ya cuenta con fechas.");
        int numberOfTeams = tournamentParticipants.size();
        int numberOfMatchDays = numberOfTeams - 1;
        //Set<Match> allMatches = new HashSet<>();

        List<TournamentParticipant> tournamentParticipantsList = new ArrayList<>(tournamentParticipants);
        //generate(tournament, tournamentParticipantsList, numberOfTeams, numberOfMatchDays, allMatches);
        generate(tournament, tournamentParticipantsList, numberOfTeams, numberOfMatchDays);
    }

    void generate(Tournament tournament, List<TournamentParticipant> teams, int numberOfTeams, int numberOfMatchDays) {
        for (int matchDayNumber = 0; matchDayNumber < numberOfMatchDays; matchDayNumber++) {
            MatchDay matchDay = matchDayRepository.save(
                    MatchDay.builder()
                            .numberOfMatchDay(matchDayNumber)
                            .tournament(tournament)
                            .isFinished(false)
                            .matches(new HashSet<>())
                            .build());
            this.matchService.generateMatches(matchDay, teams, numberOfTeams);
            rotateTeams(teams);
        }
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

    @Override
    public MatchDayResponse matchDayToResponse(MatchDay matchDay) {
        List<MatchResponse> matches = new ArrayList<>();
        for (Match match : matchDay.getMatches()) {
            matches.add(this.matchService.matchToResponse(match));
        }
        return new MatchDayResponse(matchDay.getId() ,matchDay.getNumberOfMatchDay(), matchDay.getIsFinished(), matches);
    }

    @Override
    public void updateStatusMatchDay(Long id, Boolean status) {
        Optional<MatchDay> matchDayOpt = this.matchDayRepository.findById(id);

        if(matchDayOpt.isEmpty()) throw new ResourceNotFoundException("El id de la jornada no existe.");

        MatchDay matchDay = matchDayOpt.get();

        matchDay.getMatches().forEach(match -> {
            if(!match.getIsFinished()){
                if(match.getHomeTeam().getTeam().getName().equals("Free") || match.getAwayTeam().getTeam().getName().equals("Free")){
                    match.setIsFinished(true);
                }else{
                throw new IllegalArgumentException("El partido " + match.getHomeTeam().getTeam().getName() + " vs " + match.getAwayTeam().getTeam().getName() + " no fue cargado");
                }
            }
        });

        matchDay.setIsFinished(status);
        this.matchDayRepository.save(matchDay);
    }


}