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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class MatchServiceImpl implements MatchService {

    @Autowired
    private TournamentAccess access;
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
        Match match = getWritableMatch(request.matchId());
        // Auditoria 22/10: un segundo envio del mismo resultado debe decir eso (409), no "el jugador no pertenece al partido".
        if (Boolean.TRUE.equals(match.getIsFinished())) throw new com.gesfut.exceptions.ResourceAlreadyExistsException("El resultado ya fue cargado. Us\u00e1 Editar resultado para cambiarlo.");
        validateResult(request, match);

        long mvpCount = request.events().stream()
                .filter(event -> event.type() == EEventType.MVP)
                .count();
        if (mvpCount > 1) throw new IllegalArgumentException("No pueden haber más de un MVP en el mismo partido");
        if (Boolean.TRUE.equals(match.getMatchDay().getIsFinished())) throw new IllegalArgumentException("La jornada está cerrada.");
        if (match.getIsFinished())throw new ResourceNotFoundException("El partido ya está cerrado");
        if (match.isByeMatch()) throw new IllegalArgumentException("La fecha libre no puede cargar resultados.");
        // Plan 22/10 #9: no se carga un resultado antes del dia del partido.
        if (!match.resultLoadableToday()) throw new IllegalArgumentException("Todav\u00eda no es el d\u00eda del partido: el resultado se carga desde ese d\u00eda.");

        // Las sanciones se reconstruyen cronológicamente después de guardar.

        request.events().forEach(eventRequest -> {
            Event event = this.eventService.createEvent(eventRequest,match);
            increasePlayerStats(event);
            increaseTournamentParticipantStats(event);
            events.add(event);
        });
        match.setTiebreakWinnerId(request.tiebreakWinnerId());
        closeMatch(events,match);
        rebuildDiscipline(match);
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
    public void generateMatches(MatchDay matchDay, List<TournamentParticipant> teams, int numberOfTeams, LocalDateTime startDate, Integer durationMatch) {

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
                .description("1")
                .mvpPlayer("")
                // Plan 22/10 #2: si uno de los dos esta dado de baja, el rival queda libre.
                .bye(Match.isInactiveParticipant(homeTeam) || Match.isInactiveParticipant(awayTeam))
                .build();
            matchRepository.save(newMatch);
            if(newMatch.formatMatchDate(newMatch.getDate()) == null) {
                System.out.println("DATE NULL");
                matchRepository.save(newMatch);
            }
            matches.add(newMatch);
            if (startDate != null) {
                if(newMatch.isByeMatch()){
                    startDate = startDate.plusMinutes(0);
                }else{
                    startDate = startDate.plusMinutes(durationMatch);
                    System.out.printf("DATE DAY: %s\n", startDate);
                }
            }
        }
    }

    @Override
    public void updateMatchResult(MatchRequest request) throws BadRequestException {
        Match match = getWritableMatch(request.matchId());
        validateResult(request, match);
        long mvpCount = request.events().stream()
                .filter(event -> event.type() == EEventType.MVP)
                .count();
        if (mvpCount > 1) throw new IllegalArgumentException("No pueden haber más de un MVP en el mismo partido");

        if(!match.getIsFinished()) throw new BadRequestException("Error: el partido no terminó");
        if(match.getMatchDay().getIsFinished()) throw new BadRequestException("Error: La jornada ya cerró.");

        /*Eliminar todos los eventos asociados a este partido
        * Actualizar las statistics de los equipos
        * Actualizar las tabla jugadoresxequipoxtorneo*/

        if (match.getMatchDay().getTournament().getMatchDays().stream().anyMatch(day -> day.getNumberOfMatchDay() > match.getMatchDay().getNumberOfMatchDay() && Boolean.TRUE.equals(day.getIsPlayOff()))) {
            throw new BadRequestException("No se puede cambiar un resultado después de generar la siguiente ronda.");
        }
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
        Match match = getWritableMatch(matchId);
        if (request.localDateTime() == null) throw new IllegalArgumentException("La fecha es requerida.");
        // Plan 22/10 #5: un partido suelto solo se programa con fecha futura.
        if (request.localDateTime().isBefore(LocalDateTime.now().minusMinutes(1))) throw new IllegalArgumentException("Eleg\u00ed una fecha y hora futura para el partido.");
        match.getMatchDay().getTournament().getMatchDays().stream()
            .filter(day -> day.getNumberOfMatchDay() == match.getMatchDay().getNumberOfMatchDay() - 1)
            .flatMap(day -> day.getMatches().stream()).map(Match::getDate).filter(Objects::nonNull)
            .max(Comparator.naturalOrder()).ifPresent(last -> {
                if (!request.localDateTime().isAfter(last)) throw new IllegalArgumentException("La fecha debe ser posterior a la jornada anterior.");
            });
        match.setDate(request.localDateTime());
        matchRepository.save(match);
        return new NewDateResponse(match.formatMatchDate(match.getDate()));
    }

    @Override
    public void updateMatchDescription(Long matchId, MatchDescriptionRequest request) {
        Match match = getWritableMatch(matchId);
        match.setDescription(request.description());
        matchRepository.save(match);
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
                match.getIsFinished(),
                match.getVsMatchIdWhoWin(),
                match.getTiebreakWinnerId(),
                Boolean.TRUE.equals(match.getMatchDay().getIsPlayOff())
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
        eventRepository.flush();
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
                match.getMvpPlayer(),
                match.getVsMatchIdWhoWin(),
                match.getTiebreakWinnerId(),
                match.resultLoadableToday(),
                match.isByeMatch(),
                match.restingTeamName()
        );
    }

    private Match getWritableMatch(Long id) {
        Match match = matchRepository.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("El partido no existe."));
        access.requireOwner(match.getMatchDay().getTournament());
        access.requireOpen(match.getMatchDay().getTournament());
        return match;
    }

    private void validateResult(MatchRequest request, Match match) {
        if (request.events() == null) throw new IllegalArgumentException("Los eventos son requeridos.");
        int homeGoals = 0, awayGoals = 0;
        Set<String> keys = new HashSet<>();
        int mvps = 0;
        for (var event : request.events()) {
            if (event == null || event.type() == null || event.quantity() == null || event.quantity() <= 0 || event.playerParticipantId() == null)
                throw new IllegalArgumentException("Evento inválido.");
            var player = playerParticipantRepository.findByIdAndTournamentParticipantIn(event.playerParticipantId(), List.of(match.getHomeTeam(), match.getAwayTeam()))
                .orElseThrow(() -> new ResourceNotFoundException("El jugador no pertenece al partido."));
            if (!keys.add(player.getId() + ":" + event.type())) throw new IllegalArgumentException("Hay eventos duplicados para un jugador.");
            if (event.type() == EEventType.MVP && (event.quantity() != 1 || ++mvps > 1)) throw new IllegalArgumentException("Debe haber como máximo un MVP.");
            if (event.type() == EEventType.RED_CARD && event.quantity() > 1 || event.type() == EEventType.YELLOW_CARD && event.quantity() > 2)
                throw new IllegalArgumentException("Cantidad de tarjetas inválida.");
            if (event.type() == EEventType.GOAL) {
                if (Objects.equals(player.getTournamentParticipant().getId(), match.getHomeTeam().getId())) homeGoals += event.quantity();
                else awayGoals += event.quantity();
            }
        }
        Long winner = request.tiebreakWinnerId();
        if (winner != null && (!Objects.equals(winner, match.getHomeTeam().getId()) && !Objects.equals(winner, match.getAwayTeam().getId())))
            throw new IllegalArgumentException("El ganador del desempate debe participar en el partido.");
        boolean playoff = Boolean.TRUE.equals(match.getMatchDay().getIsPlayOff());
        if (playoff && homeGoals == awayGoals && winner == null)
            throw new IllegalArgumentException("Seleccioná el ganador del desempate para cerrar el cruce.");
        if (winner != null && (!playoff || homeGoals != awayGoals))
            throw new IllegalArgumentException("El desempate solo corresponde a un cruce empatado.");
    }

    private void rebuildDiscipline(Match changedMatch) {
        var tournament = changedMatch.getMatchDay().getTournament();
        List<Match> matches = tournament.getMatchDays().stream().flatMap(day -> day.getMatches().stream())
            .filter(m -> Boolean.TRUE.equals(m.getIsFinished()))
            .sorted(Comparator.comparingInt((Match m) -> m.getMatchDay().getNumberOfMatchDay()).thenComparing(Match::getId)).toList();
        Map<Long, List<Event>> eventMap = new HashMap<>();
        matches.forEach(m -> eventMap.put(m.getId(), eventRepository.findAllByMatchId(m.getId())));
        for (var participant : tournament.getTeams()) {
            for (var player : participant.getPlayerParticipants()) {
                int consecutive = 0;
                boolean suspended = false;
                for (Match m : matches) {
                    if (!Objects.equals(m.getHomeTeam().getId(), participant.getId()) && !Objects.equals(m.getAwayTeam().getId(), participant.getId())) continue;
                    if (m.isByeMatch()) continue;
                    suspended = false; // Esta participación cumple la suspensión anterior.
                    int yellows = 0, reds = 0;
                    for (Event e : eventMap.get(m.getId())) {
                        if (!Objects.equals(e.getPlayerParticipant().getId(), player.getId())) continue;
                        if (e.getType() == EEventType.YELLOW_CARD) yellows += e.getQuantity();
                        if (e.getType() == EEventType.RED_CARD) reds += e.getQuantity();
                    }
                    consecutive += yellows;
                    if (reds > 0 || consecutive >= 2) { suspended = true; consecutive = 0; }
                }
                player.setConsecutiveCards(consecutive);
                player.setIsSuspended(suspended);
                playerParticipantRepository.save(player);
            }
        }
    }

}
