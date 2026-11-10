package com.gesfut.services.impl;

import com.gesfut.config.TimeConfig;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private com.gesfut.repositories.ResultCorrectionRepository resultCorrectionRepository;
    @Autowired
    private TournamentParticipantService tournamentParticipantService;
    @Autowired
    private Clock clock;

    private static final DateTimeFormatter RESULT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter RESULT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String loadMatchResult(MatchRequest request) {
        List<Event> events = new ArrayList<>();
        Match match = getWritableMatch(request.matchId());
        // Auditoria 22/10: un segundo envio del mismo resultado debe decir eso (409), no "el jugador no pertenece al partido".
        if (Boolean.TRUE.equals(match.getIsFinished())) throw new com.gesfut.exceptions.ResourceAlreadyExistsException("El resultado ya fue cargado. Us\u00e1 Corregir resultado para cambiarlo.");
        validateResult(request, match);
        checkSuspendedPlayers(request, match);

        long mvpCount = request.events().stream()
                .filter(event -> event.type() == EEventType.MVP)
                .count();
        if (mvpCount > 1) throw new IllegalArgumentException("No pueden haber más de un MVP en el mismo partido");
        if (Boolean.TRUE.equals(match.getMatchDay().getIsFinished())) throw new IllegalArgumentException("La jornada está cerrada.");
        if (match.getIsFinished())throw new ResourceNotFoundException("El partido ya está cerrado");
        if (match.isByeMatch()) throw new IllegalArgumentException("La fecha libre no puede cargar resultados.");
        // 10/11: el resultado se carga recien desde la hora programada del partido (hora de Argentina).
        requireMatchStarted(match);
        return applyResult(request, match);
    }

    /** Guarda eventos, estadisticas y cierre del partido (carga inicial o correccion). */
    private String applyResult(MatchRequest request, Match match) {
        List<Event> events = new ArrayList<>();

        // Las sanciones se reconstruyen cronológicamente después de guardar.

        request.events().forEach(eventRequest -> {
            Event event = this.eventService.createEvent(eventRequest,match);
            increasePlayerStats(event);
            increaseTournamentParticipantStats(event);
            events.add(event);
        });
        match.setTiebreakWinnerId(request.effectiveTiebreakWinner(match.getHomeTeam().getId(), match.getAwayTeam().getId()));
        match.setPenaltiesHome(request.penaltiesHome());
        match.setPenaltiesAway(request.penaltiesAway());
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
        access.requireOwner(match.get().getMatchDay().getTournament()); // Revision critica #14
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
            if (Match.swapHomeAway(matchDay.getNumberOfMatchDay(), j)) { var tmp = homeTeam; homeTeam = awayTeam; awayTeam = tmp; }
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
        requireMatchStarted(match);
        validateResult(request, match);
        long mvpCount = request.events().stream()
                .filter(event -> event.type() == EEventType.MVP)
                .count();
        if (mvpCount > 1) throw new IllegalArgumentException("No pueden haber m\u00e1s de un MVP en el mismo partido");
        if (!Boolean.TRUE.equals(match.getIsFinished())) throw new BadRequestException("Error: el partido no termin\u00f3");
        checkSuspendedPlayers(request, match);

        // Revision critica #5: se corrige aunque la jornada este cerrada. En playoffs, solo si la
        // ronda siguiente no tiene resultados; si cambia el ganador, se actualiza el cruce siguiente.
        Match nextRoundMatch = null;
        TournamentParticipant oldWinner = null;
        if (Boolean.TRUE.equals(match.getMatchDay().getIsPlayOff())) {
            int number = match.getMatchDay().getNumberOfMatchDay();
            var nextDay = match.getMatchDay().getTournament().getMatchDays().stream()
                    .filter(day -> Boolean.TRUE.equals(day.getIsPlayOff()) && day.getNumberOfMatchDay() > number)
                    .min(Comparator.comparingInt(MatchDay::getNumberOfMatchDay)).orElse(null);
            if (nextDay != null) {
                if (nextDay.getMatches().stream().anyMatch(m -> Boolean.TRUE.equals(m.getIsFinished())))
                    throw new BadRequestException("No se puede corregir: la ronda siguiente ya tiene resultados cargados.");
                oldWinner = winnerOf(match);
                final TournamentParticipant previous = oldWinner;
                nextRoundMatch = nextDay.getMatches().stream()
                        .filter(m -> m.getHomeTeam() == previous || m.getAwayTeam() == previous
                                || Objects.equals(m.getHomeTeam().getId(), previous.getId()) || Objects.equals(m.getAwayTeam().getId(), previous.getId()))
                        .findFirst().orElse(null);
            }
        }

        String before = summarize(match, this.eventRepository.findAllByMatchId(match.getId()));
        deleteAllRelationsFromMatch(match);
        applyResult(request, match);
        String after = summarize(match, this.eventRepository.findAllByMatchId(match.getId()));

        if (nextRoundMatch != null) {
            TournamentParticipant newWinner = winnerOf(match);
            if (!Objects.equals(newWinner.getId(), oldWinner.getId())) {
                if (Objects.equals(nextRoundMatch.getHomeTeam().getId(), oldWinner.getId())) nextRoundMatch.setHomeTeam(newWinner);
                else nextRoundMatch.setAwayTeam(newWinner);
                matchRepository.save(nextRoundMatch);
            }
        }
        resultCorrectionRepository.save(com.gesfut.models.audit.ResultCorrection.builder()
                .matchId(match.getId())
                .userEmail(com.gesfut.config.security.SecurityUtils.getCurrentUserEmail())
                .correctedAt(LocalDateTime.now())
                .oldResult(before)
                .newResult(after)
                .build());
    }

    @Override
    public List<com.gesfut.models.audit.ResultCorrection> getCorrections(Long matchId) {
        Match match = matchRepository.findById(matchId).orElseThrow(() -> new ResourceNotFoundException("El partido no existe."));
        access.requireOwner(match.getMatchDay().getTournament());
        return resultCorrectionRepository.findAllByMatchIdOrderByCorrectedAtDesc(matchId);
    }

    private static TournamentParticipant winnerOf(Match match) {
        if (match.getGoalsHomeTeam() > match.getGoalsAwayTeam()) return match.getHomeTeam();
        if (match.getGoalsAwayTeam() > match.getGoalsHomeTeam()) return match.getAwayTeam();
        if (Objects.equals(match.getTiebreakWinnerId(), match.getAwayTeam().getId())) return match.getAwayTeam();
        return match.getHomeTeam();
    }

    static String summarize(Match match, List<Event> events) {
        StringBuilder sb = new StringBuilder();
        sb.append(match.getHomeTeam().getTeam().getName()).append(' ').append(match.getGoalsHomeTeam())
          .append('-').append(match.getGoalsAwayTeam()).append(' ').append(match.getAwayTeam().getTeam().getName());
        if (match.getTiebreakWinnerId() != null) sb.append(" | desempate: ").append(match.getTiebreakWinnerId());
        events.stream()
              .map(e -> e.getType() + " " + e.getPlayerParticipant().getPlayer().getName() + " " + e.getPlayerParticipant().getPlayer().getLastName() + " x" + e.getQuantity())
              .sorted().forEach(x -> sb.append(" | ").append(x));
        return sb.toString();
    }

    @Override
    public MatchDetailedResponse getDetailedMatchById(Long id) {
        Match match = this.getMatch(id);
        access.requireOwner(match.getMatchDay().getTournament()); // Revision critica #14
        return this.matchDetailedToResponse(match);
    }

    @Override
    public NewDateResponse updateMatchDateAndDescription(Long matchId, MatchDateRequest request) {
        Match match = getWritableMatch(matchId);
        if (request.localDateTime() == null) throw new IllegalArgumentException("La fecha es requerida.");
        // 10/11: se puede programar en fecha/hora pasada (carga historica); solo se exige el orden entre jornadas.
        match.getMatchDay().getTournament().getMatchDays().stream()
            .filter(day -> day.getNumberOfMatchDay() == match.getMatchDay().getNumberOfMatchDay() - 1)
            .flatMap(day -> day.getMatches().stream()).map(Match::getDate).filter(Objects::nonNull)
            .max(Comparator.naturalOrder()).ifPresent(last -> {
                if (request.localDateTime().isBefore(last)) throw new IllegalArgumentException("La fecha no puede ser anterior a la fecha del \u00faltimo partido de la jornada anterior.");
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
                Boolean.TRUE.equals(match.getMatchDay().getIsPlayOff()),
                match.getPenaltiesHome(),
                match.getPenaltiesAway()
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
        match.setPenaltiesHome(null);
        match.setPenaltiesAway(null);
        match.setTiebreakWinnerId(null);
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
                match.resultLoadableAt(currentClock()),
                match.isByeMatch(),
                match.restingTeamName(),
                match.getPenaltiesHome(),
                match.getPenaltiesAway()
        );
    }

    private Match getWritableMatch(Long id) {
        Match match = matchRepository.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("El partido no existe."));
        access.requireOwner(match.getMatchDay().getTournament());
        access.requireOpen(match.getMatchDay().getTournament());
        return match;
    }

    /**
     * Un resultado (carga, edicion o correccion) solo se acepta desde la hora programada inclusive.
     * Match.date es hora de pared de Argentina (el front manda la hora ART con sufijo Z y Jackson descarta la Z):
     * se interpreta en TimeConfig.ARGENTINA y se compara por instante, asi no depende de la zona del servidor.
     * Sin fecha programada (date == null) no hay horario que respetar y no se bloquea.
     */
    private void requireMatchStarted(Match match) {
        LocalDateTime scheduled = match.getDate();
        if (scheduled == null) return;
        Instant start = scheduled.atZone(TimeConfig.ARGENTINA).toInstant();
        if (currentClock().instant().isBefore(start)) {
            throw new IllegalArgumentException("Todav\u00eda no se puede cargar el resultado: el partido est\u00e1 programado para el "
                    + scheduled.format(RESULT_DATE_FORMAT) + " a las " + scheduled.format(RESULT_TIME_FORMAT) + " (hora de Argentina).");
        }
    }

    // Respaldo para tests que construyen el servicio con new/@InjectMocks y no setean el Clock.
    private Clock currentClock() {
        return clock != null ? clock : Clock.system(TimeConfig.ARGENTINA);
    }

    private void validateResult(MatchRequest request, Match match) {        if (request.events() == null) throw new IllegalArgumentException("Los eventos son requeridos.");
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
        boolean hasPenalties = request.penaltiesHome() != null || request.penaltiesAway() != null;
        if (hasPenalties) {
            if (request.penaltiesHome() == null || request.penaltiesAway() == null)
                throw new IllegalArgumentException("Carg\u00e1 los penales de los dos equipos.");
            if (request.penaltiesHome() < 0 || request.penaltiesAway() < 0 || request.penaltiesHome() > 99 || request.penaltiesAway() > 99)
                throw new IllegalArgumentException("Resultado de penales inv\u00e1lido.");
            if (request.penaltiesHome().equals(request.penaltiesAway()))
                throw new IllegalArgumentException("Los penales no pueden terminar empatados.");
            Long derived = request.penaltiesHome() > request.penaltiesAway() ? match.getHomeTeam().getId() : match.getAwayTeam().getId();
            if (request.tiebreakWinnerId() != null && !Objects.equals(request.tiebreakWinnerId(), derived))
                throw new IllegalArgumentException("El ganador elegido no coincide con los penales.");
        }
        Long winner = request.effectiveTiebreakWinner(match.getHomeTeam().getId(), match.getAwayTeam().getId());
        if (hasPenalties && !(Boolean.TRUE.equals(match.getMatchDay().getIsPlayOff()) && homeGoals == awayGoals))
            throw new IllegalArgumentException("Los penales solo corresponden a un cruce de playoffs empatado.");
        if (winner != null && (!Objects.equals(winner, match.getHomeTeam().getId()) && !Objects.equals(winner, match.getAwayTeam().getId())))
            throw new IllegalArgumentException("El ganador del desempate debe participar en el partido.");
        boolean playoff = Boolean.TRUE.equals(match.getMatchDay().getIsPlayOff());
        if (playoff && homeGoals == awayGoals && winner == null)
            throw new IllegalArgumentException("Seleccioná el ganador del desempate para cerrar el cruce.");
        if (winner != null && (!playoff || homeGoals != awayGoals))
            throw new IllegalArgumentException("El desempate solo corresponde a un cruce empatado.");
    }

    // Revision critica #3: limites de sancion (#4 los hace configurables por torneo).
    int yellowLimit(com.gesfut.models.tournament.Tournament t) { return t.yellowLimit(); }
    int redMatches(com.gesfut.models.tournament.Tournament t) { return t.redMatches(); }

    @Override
    public void rebuildDisciplineFor(com.gesfut.models.tournament.Tournament tournament) {
        tournament.getMatchDays().stream().flatMap(d -> d.getMatches().stream()).findFirst().ifPresent(this::rebuildDiscipline);
    }

    private Map<Long, List<Event>> eventsOf(List<Match> matches) {
        Map<Long, List<Event>> eventMap = new HashMap<>();
        matches.forEach(m -> eventMap.put(m.getId(), eventRepository.findAllByMatchId(m.getId())));
        return eventMap;
    }

    private void rebuildDiscipline(Match changedMatch) {
        var tournament = changedMatch.getMatchDay().getTournament();
        List<Match> matches = DisciplineRules.chronological(tournament, null);
        var state = DisciplineRules.compute(tournament, matches, eventsOf(matches), yellowLimit(tournament), redMatches(tournament));
        for (var participant : tournament.getTeams()) {
            for (var player : participant.getPlayerParticipants()) {
                var st = state.get(player.getId());
                player.setConsecutiveCards(st == null ? 0 : st.yellows());
                player.setIsSuspended(st != null && st.suspended());
                playerParticipantRepository.save(player);
            }
        }
    }

    /** Revision critica #3: un jugador suspendido para este partido no puede tener goles, tarjetas ni MVP. */
    void checkSuspendedPlayers(MatchRequest request, Match match) {
        var tournament = match.getMatchDay().getTournament();
        List<Match> before = DisciplineRules.chronological(tournament, match);
        var state = DisciplineRules.compute(tournament, before, eventsOf(before), yellowLimit(tournament), redMatches(tournament));
        Set<String> names = new TreeSet<>();
        for (var e : request.events()) {
            var st = state.get(e.playerParticipantId());
            if (st == null || !st.suspended()) continue;
            names.add(playerParticipantRepository.findById(e.playerParticipantId())
                    .map(pp -> pp.getPlayer().getName() + " " + pp.getPlayer().getLastName()).orElse("Un jugador"));
        }
        if (!names.isEmpty()) throw new IllegalArgumentException((names.size() == 1 ? names.iterator().next() + " est\u00e1 suspendido" : String.join(", ", names) + " est\u00e1n suspendidos")
                + " para este partido: no puede" + (names.size() == 1 ? "" : "n") + " tener goles, tarjetas ni MVP.");
    }

}
