package com.gesfut.services.impl;

import com.gesfut.dtos.requests.EventRequest;
import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.matchDay.EEventType;
import com.gesfut.models.matchDay.Event;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.team.Player;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.Statistics;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.*;
import com.gesfut.services.EventService;
import com.gesfut.services.MatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    @Override
    public String loadMatchResult(MatchRequest request) {
        List<Event> events = new ArrayList<>();
        Match match = this.getMatch(request.matchId());
        if (match.getIsFinished()){throw new ResourceNotFoundException("El partido ya está cerrado");}
        request.events().forEach(eventRequest -> {
            // TODO hacer validaicones por si surgen errores
            Event event = this.eventRepository.save(createEvent(eventRequest,match));
            modifyPlayerStats(event);
            modifyTournamentParticipantStats(event);
            events.add(event);
        });
        closeMatch(events,match);
        return "Partido cargado";
    }

    private void modifyPlayerStats(Event event) {
        //AGREGAR VALIDACION PARA QUE NO SE GUARDE SI HAY ERROR EN EL EVENTO
        PlayerParticipant playerParticipant = event.getPlayerParticipant();
        switch (event.getType()) {
            case GOAL -> playerParticipant.setGoals(playerParticipant.getGoals() + event.getQuantity());
            case RED_CARD -> playerParticipant.setRedCards(playerParticipant.getRedCards() + event.getQuantity());
            case YELLOW_CARD -> playerParticipant.setYellowCards(playerParticipant.getYellowCards() + event.getQuantity());
        }
        this.playerParticipantRepository.save(playerParticipant);
    }

    private void modifyTournamentParticipantStats (Event event){
        //EVENT TIENE : MATCH, PLAYERPARTICIPANT, TYPE, QUANTITY
        //MATCH TIENE: HOMETEAM, AWAYTEAM QUE SON TOURAMENTPARTICIPANT
        //TOURNAMENTPARTICPANT TIENE: STATISTICS Y PLAYERPARTICIPANT

        Match match = event.getMatch();
        TournamentParticipant homeTeam = match.getHomeTeam();
        Boolean isHomeTeam = homeTeam.getPlayerParticipants().contains(event.getPlayerParticipant());

        Statistics statisticsGood = new Statistics();
        Statistics statisticsBad = new Statistics();

        if (isHomeTeam) {
            statisticsGood = homeTeam.getStatistics();
            statisticsBad = match.getAwayTeam().getStatistics();
        } else {
            statisticsGood = match.getAwayTeam().getStatistics();
            statisticsBad = homeTeam.getStatistics();
        }

        // Modificar las estadísticas basadas en el tipo de evento
        switch (event.getType()) {
            case GOAL -> {
                statisticsGood.setGoalsFor(statisticsGood.getGoalsFor() + event.getQuantity());
                statisticsBad.setGoalsAgainst(statisticsBad.getGoalsAgainst() + event.getQuantity());
            }
            case RED_CARD -> statisticsGood.setRedCards(statisticsGood.getRedCards() + event.getQuantity());
            case YELLOW_CARD -> statisticsGood.setYellowCards(statisticsGood.getYellowCards() + event.getQuantity());
        }

        // Guardar los cambios en el repositorio de estadísticas
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



    private Match getMatch(Long matchId){
        Optional<Match> match = this.matchRepository.findById(matchId);
        if(match.isEmpty()) throw new ResourceNotFoundException("El id del partido no existe");
        return match.get();
    }

    public Event createEvent(EventRequest eventRequest, Match match) {
        // Obtener los equipos que están participando en el partido (local y visitante)
        List<TournamentParticipant> teams = List.of(match.getHomeTeam(), match.getAwayTeam());

        // Buscar el jugador en los equipos usando el query method
        Optional<PlayerParticipant> playerParticipant = playerParticipantRepository
                .findByPlayerIdAndTournamentParticipantIn(eventRequest.playerParticipantId(), teams);

        // Si no se encuentra el jugador, lanzar una excepción
        if (playerParticipant.isEmpty()) {
            throw new ResourceNotFoundException("Uno de los jugadores asociados no existe en ningun equipo del partido.");
        }

        // Crear y retornar el evento
        return Event
                .builder()
                .match(match)
                .type(eventRequest.type())
                .playerParticipant(playerParticipant.get())
                .quantity(eventRequest.quantity())
                .build();
    }

}
