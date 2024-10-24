package com.gesfut.services.impl;

import com.gesfut.dtos.requests.EventRequest;
import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.dtos.responses.MatchResponse;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.matchDay.EEventType;
import com.gesfut.models.matchDay.Event;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.Statistics;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.*;
import com.gesfut.services.EventService;
import com.gesfut.services.MatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MatchServiceImpl implements MatchService {

    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private PlayerParticipantRepository playerParticipantRepository;
    @Autowired
    private StatisticsRepository statisticsRepository;
    @Autowired
    private EventService eventService;

    @Override
    public String loadMatchResult(MatchRequest request) {
        List<Event> events = new ArrayList<>();
        Match match = this.getMatch(request.matchId());
        if (match.getIsFinished())throw new ResourceNotFoundException("El partido ya está cerrado");
        if (match.getHomeTeam().getTeam().getName().equals("Free") || match.getAwayTeam().getTeam().getName().equals("Free")) throw new IllegalArgumentException("La fecha libre no puede cargar resultados.");
        request.events().forEach(eventRequest -> {
            // TODO hacer validaicones por si surgen errores
            Event event = this.eventService.createEvent(eventRequest,match);
            modifyPlayerStats(event);
            modifyTournamentParticipantStats(event);
            events.add(event);
        });
        closeMatch(events,match);
        return "Partido cargado";
    }

    @Override
    public MatchResponse getMatchById(Long id) {
        Optional<Match> match = this.matchRepository.findById(id);
        if(match.isEmpty()) throw new ResourceNotFoundException("El partido no existe.");
        return null;
    }

    private void modifyPlayerStats(Event event) {
        PlayerParticipant playerParticipant = event.getPlayerParticipant();
        switch (event.getType()) {
            case GOAL -> playerParticipant.setGoals(playerParticipant.getGoals() + event.getQuantity());
            case RED_CARD -> playerParticipant.setRedCards(playerParticipant.getRedCards() + event.getQuantity());
            case YELLOW_CARD -> playerParticipant.setYellowCards(playerParticipant.getYellowCards() + event.getQuantity());
        }
        this.playerParticipantRepository.save(playerParticipant);
    }

    private void modifyTournamentParticipantStats (Event event){
        Match match = event.getMatch();
        TournamentParticipant homeTeam = match.getHomeTeam();
        boolean isHomeTeam = homeTeam.getPlayerParticipants().contains(event.getPlayerParticipant());

        Statistics statisticsGood = new Statistics();
        Statistics statisticsBad = new Statistics();

        if (isHomeTeam) {
            statisticsGood = homeTeam.getStatistics();
            statisticsBad = match.getAwayTeam().getStatistics();
        } else {
            statisticsGood = match.getAwayTeam().getStatistics();
            statisticsBad = homeTeam.getStatistics();
        }

        switch (event.getType()) {
            case GOAL -> {
                statisticsGood.setGoalsFor(statisticsGood.getGoalsFor() + event.getQuantity());
                statisticsBad.setGoalsAgainst(statisticsBad.getGoalsAgainst() + event.getQuantity());
            }
            case RED_CARD -> statisticsGood.setRedCards(statisticsGood.getRedCards() + event.getQuantity());
            case YELLOW_CARD -> statisticsGood.setYellowCards(statisticsGood.getYellowCards() + event.getQuantity());
        }

        this.statisticsRepository.save(statisticsGood);
        this.statisticsRepository.save(statisticsBad);
    }

    private void closeMatch(List<Event> events, Match match) {

        events.forEach(event -> {
            if (event.getType() == EEventType.GOAL) {
                if (match.getHomeTeam().getPlayerParticipants().contains(event.getPlayerParticipant())) {
                   match.setGoalsHomeTeam(match.getGoalsHomeTeam() + event.getQuantity());
                } else if(match.getAwayTeam().getPlayerParticipants().contains(event.getPlayerParticipant())) {
                    match.setGoalsAwayTeam(match.getGoalsAwayTeam() + event.getQuantity());
                }else{
                    throw new ResourceNotFoundException("El jugador no pertenece a ningun equipo del partido");
                }
            }
        });

        match.getHomeTeam().getStatistics().setMatchesPlayed(match.getHomeTeam().getStatistics().getMatchesPlayed() + 1);
        match.getAwayTeam().getStatistics().setMatchesPlayed(match.getAwayTeam().getStatistics().getMatchesPlayed() + 1);

        if (match.getGoalsHomeTeam() > match.getGoalsAwayTeam()) {
            match.getHomeTeam().getStatistics().setWins(match.getHomeTeam().getStatistics().getWins() + 1);
            match.getAwayTeam().getStatistics().setLosses(match.getAwayTeam().getStatistics().getLosses() + 1);
            match.getHomeTeam().getStatistics().setPoints(match.getHomeTeam().getStatistics().getPoints() + 3);

        }else if (match.getGoalsAwayTeam() > match.getGoalsHomeTeam()) {
            match.getAwayTeam().getStatistics().setWins(match.getAwayTeam().getStatistics().getWins() + 1);
            match.getHomeTeam().getStatistics().setLosses(match.getHomeTeam().getStatistics().getLosses() + 1);
            match.getAwayTeam().getStatistics().setPoints(match.getAwayTeam().getStatistics().getPoints() + 3);

        }else {
            match.getHomeTeam().getStatistics().setDraws(match.getHomeTeam().getStatistics().getDraws() + 1);
            match.getAwayTeam().getStatistics().setDraws(match.getAwayTeam().getStatistics().getDraws() + 1);
            match.getHomeTeam().getStatistics().setPoints(match.getHomeTeam().getStatistics().getPoints() + 1);
            match.getAwayTeam().getStatistics().setPoints(match.getAwayTeam().getStatistics().getPoints() + 1);
        }

        match.setIsFinished(true);
        this.statisticsRepository.save(match.getHomeTeam().getStatistics());
        this.statisticsRepository.save(match.getAwayTeam().getStatistics());
        this.matchRepository.save(match);
    }


    @Override
    public void generateMatches(MatchDay matchDay, List<TournamentParticipant> teams, int numberOfTeams){
        Set<Match> matches = new HashSet<>();
        for (int j = 0; j < numberOfTeams / 2; j++) {
            TournamentParticipant homeTeam = teams.get(j);
            TournamentParticipant awayTeam = teams.get(numberOfTeams - 1 - j);
            Match newMatch = Match
                .builder()
                .homeTeam(homeTeam) // ?
                .awayTeam(awayTeam) // ?
                .isFinished(false)
                .goalsHomeTeam(0)
                .goalsAwayTeam(0)
                .matchDay(matchDay)
                .build();
            matchRepository.save(newMatch);
            matches.add(newMatch);
        }
    }

    private Match getMatch(Long matchId){
        Optional<Match> match = this.matchRepository.findById(matchId);
        if(match.isEmpty()) throw new ResourceNotFoundException("El id del partido no existe");
        return match.get();
    }

    @Override
    public MatchResponse matchToResponse(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getHomeTeam().getTeam().getName(),
                match.getAwayTeam().getTeam().getName(),
                match.getMatchDay().getNumberOfMatchDay(),
                match.getGoalsHomeTeam(),
                match.getGoalsAwayTeam(),
                match.getEvents().stream().map(event -> this.eventService.eventToResponse(event)).toList()
        );
    }


}
