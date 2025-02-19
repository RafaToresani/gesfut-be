package com.gesfut.services.impl;

import com.gesfut.dtos.requests.MatchDateRequest;
import com.gesfut.dtos.requests.MatchDescriptionRequest;
import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.dtos.responses.MatchDetailedResponse;
import com.gesfut.dtos.responses.MatchResponse;
import com.gesfut.dtos.responses.NewDateResponse;
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
import com.gesfut.services.TournamentParticipantService;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    @Autowired
    private TournamentParticipantService tournamentParticipantService;

    @Override
    public String loadMatchResult(MatchRequest request) {
        List<Event> events = new ArrayList<>();
        Match match = this.getMatch(request.matchId());

        long mvpCount = request.events().stream()
                .filter(event -> event.type() == EEventType.MVP)
                .count();
        if (mvpCount > 1) throw new IllegalArgumentException("No pueden haber más de un MVP en el mismo partido");
        if (match.getIsFinished())throw new ResourceNotFoundException("El partido ya está cerrado");
        if (match.getHomeTeam().getTeam().getName().equals("Free") || match.getAwayTeam().getTeam().getName().equals("Free")) throw new IllegalArgumentException("La fecha libre no puede cargar resultados.");

        this.clearPlayerSuspensions(match);

        request.events().forEach(eventRequest -> {
            Event event = this.eventService.createEvent(eventRequest,match);
            increasePlayerStats(event);
            increaseTournamentParticipantStats(event);
            events.add(event);
        });
        closeMatch(events,match);
        return "Partido cargado";
    }

    private void clearPlayerSuspensions(Match match) {
        match.getHomeTeam().getPlayerParticipants().forEach(player -> {
            if (player.getIsSuspended()) {
                this.playerParticipantRepository.changeIsSuspended(player.getId(), false);
            }
        });

        match.getAwayTeam().getPlayerParticipants().forEach(player -> {
            if (player.getIsSuspended()) {
                this.playerParticipantRepository.changeIsSuspended(player.getId(), false);
            }
        });
    }


    @Override
    public MatchResponse getMatchById(Long id) {
        Optional<Match> match = this.matchRepository.findById(id);
        if(match.isEmpty()) throw new ResourceNotFoundException("El partido no existe.");
        return matchToResponse(match.get());
    }

    private void increasePlayerStats(Event event) {
        PlayerParticipant playerParticipant = event.getPlayerParticipant();

        switch (event.getType()) {
            case GOAL ->
                    playerParticipant.setGoals(playerParticipant.getGoals() + event.getQuantity());

            case RED_CARD -> {
                playerParticipant.setRedCards(playerParticipant.getRedCards() + event.getQuantity());
                playerParticipant.setConsecutiveCards(0);
                playerParticipant.setIsSuspended(true);
            }

            case YELLOW_CARD -> {
                playerParticipant.setYellowCards(playerParticipant.getYellowCards() + event.getQuantity());
                int consecutiveYellows = playerParticipant.getConsecutiveCards() != null
                        ? playerParticipant.getConsecutiveCards()
                        : 0;

                consecutiveYellows += event.getQuantity();
                playerParticipant.setConsecutiveCards(consecutiveYellows);

                if (consecutiveYellows >= 2) {
                    playerParticipant.setIsSuspended(true);
                    playerParticipant.setConsecutiveCards(0);
                }
            }

            case MVP ->
                    playerParticipant.setIsMvp(playerParticipant.getIsMvp() + event.getQuantity());
        }

        this.playerParticipantRepository.save(playerParticipant);
    }


    private void increaseTournamentParticipantStats (Event event){
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
                    throw new ResourceNotFoundException("Uno de los jugadores no pertenece a ninguno de los dos equipos. ");
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

        if (events.stream().anyMatch(event -> event.getType() == EEventType.MVP)){
            match.setMvpPlayer(
                    events.stream().filter(event -> event.getType() == EEventType.MVP).findFirst().orElse(null).getPlayerParticipant().getPlayer().getNumber()
                            + " - " + events.stream().filter(event -> event.getType() == EEventType.MVP).findFirst().orElse(null).getPlayerParticipant().getPlayer().getName()
                            + " " + events.stream().filter(event -> event.getType() == EEventType.MVP).findFirst().orElse(null).getPlayerParticipant().getPlayer().getLastName()
                            + " - " + events.stream().filter(event -> event.getType() == EEventType.MVP).findFirst().orElse(null).getPlayerParticipant().getPlayer().getTeam().getName()
            );
        }else {
            match.setMvpPlayer(null);
        }


        match.setIsFinished(true);
        this.statisticsRepository.save(match.getHomeTeam().getStatistics());
        this.statisticsRepository.save(match.getAwayTeam().getStatistics());
        this.matchRepository.save(match);
    }


    @Override
    public void generateMatches(MatchDay matchDay, List<TournamentParticipant> teams, int numberOfTeams, LocalDateTime startDate) {

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
                .date(startDate)
                .description("0")
                .mvpPlayer("")
                .build();
            matchRepository.save(newMatch);
            if(newMatch.formatMatchDate(newMatch.getDate()) == null) {
                System.out.println("DATE NULL");
                matchRepository.save(newMatch);
            }
            matches.add(newMatch);
            if (startDate != null) {
                startDate = startDate.plusHours(1);
                System.out.printf("DATE DAY: %s\n", startDate);
            }
        }
    }

    @Override
    public void updateMatchResult(MatchRequest request) throws BadRequestException {
        Match match = getMatch(request.matchId());
        long mvpCount = request.events().stream()
                .filter(event -> event.type() == EEventType.MVP)
                .count();
        if (mvpCount > 1) throw new IllegalArgumentException("No pueden haber más de un MVP en el mismo partido");

        if(!match.getIsFinished()) throw new BadRequestException("Error: el partido no terminó");
        if(match.getMatchDay().getIsFinished()) throw new BadRequestException("Error: La jornada ya cerró.");

        /*Eliminar todos los eventos asociados a este partido
        * Actualizar las statistics de los equipos
        * Actualizar las tabla jugadoresxequipoxtorneo*/

        deleteAllRelationsFromMatch(match);
        loadMatchResult(request);
    }

    @Override
    public MatchDetailedResponse getDetailedMatchById(Long id) {
        Match match = this.getMatch(id);
        return this.matchDetailedToResponse(match);
    }

    @Override
    public NewDateResponse updateMatchDateAndDescription(Long matchId, MatchDateRequest request) {
        Optional<Match> optMatch = this.matchRepository.findById(matchId);
        //encontrar el ultimo partido de la jornada anterior
        MatchDay matchDay = optMatch.get().getMatchDay();
        matchDay.getTournament().getMatchDays().forEach(matchDay1 -> {
            if(matchDay1.getNumberOfMatchDay() == matchDay.getNumberOfMatchDay() - 1){
                Match lastMatch = matchDay1.getMatches().stream().reduce((first, second) -> second).orElse(null);
                if(lastMatch != null){
                    if(lastMatch.getDate().isAfter(request.localDateTime()) || lastMatch.getDate().isEqual(request.localDateTime())){
                        throw new IllegalArgumentException("La fecha del partido no puede ser anterior al último partido de la jornada anterior");
                    }
                }
            }
        });


        if(optMatch.isEmpty()) throw new ResourceNotFoundException("Partido no encontrado");

        optMatch.get().setDate(request.localDateTime());
        this.matchRepository.save(optMatch.get());

        NewDateResponse response = new NewDateResponse(
                optMatch.get().formatMatchDate(optMatch.get().getDate())
        );

        return  response;
    }

    @Override
    public void updateMatchDescription(Long matchId, MatchDescriptionRequest request) {
        Optional<Match> optMatch = this.matchRepository.findById(matchId);

        if(optMatch.isEmpty()) throw new ResourceNotFoundException("Partido no encontrado");

        optMatch.get().setDescription(request.description());
        this.matchRepository.save(optMatch.get());
    }

    private MatchDetailedResponse matchDetailedToResponse(Match match) {
        return new MatchDetailedResponse(
                match.getId(),
                tournamentParticipantService.participantToResponse(match.getHomeTeam()),
                tournamentParticipantService.participantToResponse(match.getAwayTeam()),
                match.getMatchDay().getNumberOfMatchDay(),
                match.getGoalsHomeTeam(),
                match.getGoalsAwayTeam(),
                match.getEvents().stream().map(event -> eventService.eventToResponse(event)).toList(),
                match.getIsFinished()
        );
    }

    private void deleteAllRelationsFromMatch(Match match) {
        List<Event> events = this.eventRepository.findAllByMatchId(match.getId());
        resetStatisticsMatch(match);
        events.forEach(event -> {
            decreasePlayerStats(event);
            decreaseTournamentParticipantStats(event);
            this.eventRepository.delete(event);
        });
        match.setGoalsHomeTeam(0);
        match.setGoalsAwayTeam(0);
        match.getEvents().clear();
    }

    private void decreasePlayerStats(Event event) {
        PlayerParticipant playerParticipant = event.getPlayerParticipant();
        switch (event.getType()) {
            case GOAL -> playerParticipant.setGoals(playerParticipant.getGoals() - event.getQuantity());
            case RED_CARD -> playerParticipant.setRedCards(playerParticipant.getRedCards() - event.getQuantity());
            case YELLOW_CARD -> playerParticipant.setYellowCards(playerParticipant.getYellowCards() - event.getQuantity());
            case MVP -> playerParticipant.setIsMvp(playerParticipant.getIsMvp()-event.getQuantity());
        }
        this.playerParticipantRepository.save(playerParticipant);
    }

    private void decreaseTournamentParticipantStats (Event event){
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
                statisticsGood.setGoalsFor(statisticsGood.getGoalsFor() - event.getQuantity());
                statisticsBad.setGoalsAgainst(statisticsBad.getGoalsAgainst() - event.getQuantity());
            }
            case RED_CARD -> statisticsGood.setRedCards(statisticsGood.getRedCards() - event.getQuantity());
            case YELLOW_CARD -> statisticsGood.setYellowCards(statisticsGood.getYellowCards() - event.getQuantity());
        }

        this.statisticsRepository.save(statisticsGood);
        this.statisticsRepository.save(statisticsBad);
    }

    private void resetStatisticsMatch (Match match) {
        match.getHomeTeam().getStatistics().setMatchesPlayed(match.getHomeTeam().getStatistics().getMatchesPlayed() - 1);
        match.getAwayTeam().getStatistics().setMatchesPlayed(match.getAwayTeam().getStatistics().getMatchesPlayed() - 1);

        if (match.getGoalsHomeTeam() > match.getGoalsAwayTeam()) {
            match.getHomeTeam().getStatistics().setWins(match.getHomeTeam().getStatistics().getWins() - 1);
            match.getAwayTeam().getStatistics().setLosses(match.getAwayTeam().getStatistics().getLosses() - 1);
            match.getHomeTeam().getStatistics().setPoints(match.getHomeTeam().getStatistics().getPoints() - 3);

        }else if (match.getGoalsAwayTeam() > match.getGoalsHomeTeam()) {
            match.getAwayTeam().getStatistics().setWins(match.getAwayTeam().getStatistics().getWins() - 1);
            match.getHomeTeam().getStatistics().setLosses(match.getHomeTeam().getStatistics().getLosses() - 1);
            match.getAwayTeam().getStatistics().setPoints(match.getAwayTeam().getStatistics().getPoints() - 3);

        }else {
            match.getHomeTeam().getStatistics().setDraws(match.getHomeTeam().getStatistics().getDraws() - 1);
            match.getAwayTeam().getStatistics().setDraws(match.getAwayTeam().getStatistics().getDraws() - 1);
            match.getHomeTeam().getStatistics().setPoints(match.getHomeTeam().getStatistics().getPoints() - 1);
            match.getAwayTeam().getStatistics().setPoints(match.getAwayTeam().getStatistics().getPoints() - 1);
        }
        match.setIsFinished(false);
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
                match.getEvents().stream().map(event -> this.eventService.eventToResponse(event)).toList(),
                match.getIsFinished(),
                match.formatMatchDate(match.getDate()),
                match.getDescription(),
                match.getMvpPlayer()
        );
    }
}
