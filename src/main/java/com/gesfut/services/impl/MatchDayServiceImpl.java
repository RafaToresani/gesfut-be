package com.gesfut.services.impl;

import com.gesfut.dtos.requests.MatchDateRequest;
import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.responses.MatchDayResponse;
import com.gesfut.dtos.responses.MatchResponse;
import com.gesfut.dtos.responses.NewDateResponse;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.*;
import com.gesfut.services.MatchDayService;
import com.gesfut.services.MatchService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
public class MatchDayServiceImpl implements MatchDayService {

    @Autowired
    private TournamentAccess access;
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



    @Transactional
    @Override
    public void reGenerateMatchDays(List<TournamentParticipant> tournamentParticipants, String tournamentCode) {
        Tournament tournament = getTournament(tournamentCode);
        access.requireOwner(tournament);
        access.requireOpen(tournament);
        if (tournament.getMatchDays().stream().anyMatch(day -> Boolean.TRUE.equals(day.getIsPlayOff()) || (!Boolean.TRUE.equals(day.getIsFinished()) && day.getMatches().stream().anyMatch(m -> Boolean.TRUE.equals(m.getIsFinished())))))
            throw new IllegalArgumentException("No se puede regenerar una jornada abierta con partidos jugados ni un cuadro de playoffs.");
        List<MatchDay> closed = tournament.getMatchDays().stream().filter(day -> Boolean.TRUE.equals(day.getIsFinished())).toList();
        var pending = tournament.getMatchDays().stream().filter(day -> !Boolean.TRUE.equals(day.getIsFinished())).toList();
        tournament.getMatchDays().removeAll(pending);
        matchDayRepository.deleteAll(pending);
        matchDayRepository.flush();
        int next = closed.stream().mapToInt(MatchDay::getNumberOfMatchDay).max().orElse(-1) + 1;
        reGenerate(closed, tournament, new ArrayList<>(tournamentParticipants), tournamentParticipants.size(), next);
    }


    @Transactional
    private void reGenerate(List<MatchDay> matchDays, Tournament tournament, List<TournamentParticipant> teams, int numberOfTeams, int startingMatchDay) {
        Set<String> played = new HashSet<>();
        matchDays.forEach(day -> day.getMatches().forEach(m -> played.add(pairKey(m.getHomeTeam(), m.getAwayTeam()))));
        int number = startingMatchDay;
        for (int round = 0; round < numberOfTeams - 1; round++) {
            Set<Match> remaining = new HashSet<>();
            for (int i = 0; i < numberOfTeams / 2; i++) {
                var home = teams.get(i); var away = teams.get(numberOfTeams - 1 - i);
                if (Match.swapHomeAway(round, i)) { var tmp = home; home = away; away = tmp; }
                if (played.add(pairKey(home, away))) remaining.add(Match.builder().homeTeam(home).awayTeam(away)
                    .goalsHomeTeam(0).goalsAwayTeam(0).isFinished(false).events(new ArrayList<>()).description("1").build());
            }
            if (!remaining.isEmpty()) {
                MatchDay day = MatchDay.builder().tournament(tournament).numberOfMatchDay(number++).isFinished(false).isPlayOff(false).matches(remaining).build();
                remaining.forEach(m -> m.setMatchDay(day));
                matchDayRepository.save(day);
                tournament.getMatchDays().add(day);
            }
            rotateTeams(teams);
        }
    }



    private String pairKey(TournamentParticipant a, TournamentParticipant b) {
        return Math.min(a.getId(), b.getId()) + ":" + Math.max(a.getId(), b.getId());
    }

    @Override
    public void generateMatchDays(HashSet<TournamentParticipant> tournamentParticipants, String tournamentCode, LocalDateTime startDate, Integer plusMinutes, Integer plusDays) {
        Tournament tournament = getTournament(tournamentCode);
        access.requireOwner(tournament);
        access.requireOpen(tournament);
        if(!tournament.getMatchDays().isEmpty()) throw new ResourceAlreadyExistsException("El torneo ya cuenta con fechas.");
        int numberOfTeams = tournamentParticipants.size();
        int numberOfMatchDays = numberOfTeams - 1;
        //Set<Match> allMatches = new HashSet<>();

        List<TournamentParticipant> tournamentParticipantsList = new ArrayList<>(tournamentParticipants);
        //generate(tournament, tournamentParticipantsList, numberOfTeams, numberOfMatchDays, allMatches);
        generate(tournament, tournamentParticipantsList, numberOfTeams, numberOfMatchDays, startDate, plusMinutes, plusDays);
    }

    void generate(Tournament tournament, List<TournamentParticipant> teams, int numberOfTeams, int numberOfMatchDays, LocalDateTime startDate, Integer plusMinutes, Integer plusDays) {
        for (int matchDayNumber = 0; matchDayNumber < numberOfMatchDays; matchDayNumber++) {
            MatchDay matchDay = matchDayRepository.save(
                    MatchDay.builder()
                            .numberOfMatchDay(matchDayNumber)
                            .tournament(tournament)
                            .isFinished(false)
                            .matches(new HashSet<>())
                            .mvpPlayer(null)
                            .isPlayOff(false)
                            .build());
            this.matchService.generateMatches(matchDay, teams, numberOfTeams, startDate, plusMinutes);
            if(startDate != null){
                startDate = startDate.plusDays(plusDays);
            }else {
                startDate = null;
            }
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
        for (Match match : matchDay.getMatches().stream().sorted(Comparator.comparing(Match::getId)).toList()) {
            matches.add(this.matchService.matchToResponse(match));
        }
        return new MatchDayResponse(matchDay.getId() ,matchDay.getNumberOfMatchDay(), matchDay.getIsFinished(),matchDay.getMvpPlayer(), matches, matchDay.getIsPlayOff());
    }


    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void updateStatusMatchDay(Long id, Boolean status, String playerMvP) {
        Optional<MatchDay> matchDayOpt = this.matchDayRepository.findById(id);

        if(matchDayOpt.isEmpty()) throw new ResourceNotFoundException("El id de la jornada no existe.");

        MatchDay matchDay = matchDayOpt.get();
        access.requireOwner(matchDay.getTournament());
        access.requireOpen(matchDay.getTournament());

        matchDay.getMatches().forEach(match -> {
            if(!match.getIsFinished()){
                if(match.isByeMatch()){
                    match.setIsFinished(true);
                }else{
                throw new IllegalArgumentException("El partido " + match.getHomeTeam().getTeam().getName() + " vs " + match.getAwayTeam().getTeam().getName() + " no fue cargado");
                }
            }
        });


        if (status == null) throw new IllegalArgumentException("El estado es requerido.");
        if (!status && matchDay.getTournament().getMatchDays().stream().anyMatch(day -> day.getNumberOfMatchDay() > matchDay.getNumberOfMatchDay() && Boolean.TRUE.equals(day.getIsPlayOff())))
            throw new IllegalArgumentException("No se puede reabrir una jornada después de generar otra ronda.");
        // Revision critica #2: cerrar la ultima fecha ya no finaliza el torneo; eso es explicito ("Finalizar torneo").

        // Review 24/10: vacio = "Cerrar sin MVP"; si viene, tiene que ser uno de los MVP cargados en los partidos de esta fecha.
        String mvp = playerMvP == null ? "" : playerMvP.trim();
        if (!mvp.isEmpty()) {
            boolean valid = matchDay.getMatches() != null && matchDay.getMatches().stream()
                    .map(m -> m.getMvpPlayer() == null ? "" : m.getMvpPlayer().trim())
                    .anyMatch(mvp::equals);
            if (!valid) throw new IllegalArgumentException("El MVP elegido no es MVP de ning\u00fan partido de esta fecha. Eleg\u00ed uno de la lista o cerr\u00e1 sin MVP.");
        }
        matchDay.setMvpPlayer(mvp.isEmpty() ? playerMvP : mvp);
        matchDay.setIsFinished(status);
        this.matchDayRepository.save(matchDay);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<MatchDayResponse> getMatchDaysByCode(String code) {
        List<MatchDay> list = this.matchDayRepository.findAllByTournamentCode(UUID.fromString(code));

        return list.stream().sorted(Comparator.comparingInt(MatchDay::getNumberOfMatchDay)).map(matchDay -> matchDayToResponse(matchDay)).toList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public MatchDayResponse getLastMatchDayPlayed(String code) {
        Optional<MatchDay> lastClosedMatchDay = matchDayRepository.findTopByTournament_CodeAndIsFinishedOrderByNumberOfMatchDayDesc(UUID.fromString(code), true);

        MatchDay matchDay = lastClosedMatchDay.orElseGet(() ->
                matchDayRepository.findTopByTournament_CodeOrderByNumberOfMatchDayAsc(UUID.fromString(code))
                        .orElseThrow(() -> new ResourceNotFoundException("No se encontraron fechas para el torneo con código: " + code))
        );

        return matchDayToResponse(matchDay);
    }

    @Override
    public List<NewDateResponse> updateDateAllMatches(Long id, MatchDateRequest request, Integer plusMinutes) {
        List<NewDateResponse> newDates = new ArrayList<>();
        Optional<MatchDay> matchDayOpt = this.matchDayRepository.findById(id);
        if (matchDayOpt.isEmpty()) throw new ResourceNotFoundException("La jornada no existe.");

        MatchDay matchDay = matchDayOpt.get();
        access.requireOwner(matchDay.getTournament());
        access.requireOpen(matchDay.getTournament());
        if (request.localDateTime() == null || plusMinutes == null || plusMinutes <= 0) throw new IllegalArgumentException("Indicá fecha y duración positiva.");
        MatchDateRequest newDate = request;
        if( ( matchDay.getNumberOfMatchDay() > 0) && (matchDay.getNumberOfMatchDay()+1 <= matchDay.getTournament().getMatchDays().size())){
            MatchDay previousMatchDay = this.matchDayRepository.findByTournament_IdAndNumberOfMatchDay(matchDay.getTournament().getId(), matchDay.getNumberOfMatchDay() - 1).map(matchDay1 -> matchDay1).orElseThrow(() -> new ResourceNotFoundException("No se encontró la jornada anterior."));
            if (previousMatchDay.getMatches().stream().anyMatch(match -> match.getDate() == null)) throw new IllegalArgumentException("La jornada anterior no tiene partidos con fecha. Carguelos antes de continuar.");
            Match lastMatch = previousMatchDay.getMatches().stream().max(Comparator.comparing(Match::getDate)).orElseThrow(() -> new ResourceNotFoundException("No se encontró el último partido de la jornada anterior."));
            if(newDate.localDateTime().isBefore(lastMatch.getDate())) throw new IllegalArgumentException("La fecha no puede ser anterior a la fecha del último partido de la jornada anterior.");
        }

        for (Match match : matchDay.getMatches().stream().sorted(Comparator.comparing(Match::getId)).toList()) {
            this.matchService.updateMatchDateAndDescription(match.getId(), newDate);
            newDates.add(new NewDateResponse(match.formatMatchDate(newDate.localDateTime())));
            if (!match.isByeMatch()) newDate = new MatchDateRequest(newDate.localDateTime().plusMinutes(plusMinutes));
        }
        return newDates;
    }



}