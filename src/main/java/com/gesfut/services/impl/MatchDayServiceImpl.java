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



    @Transactional
    @Override
    public void reGenerateMatchDays(List<TournamentParticipant> tournamentParticipants, String tournamentCode) {
        Tournament tournament = getTournament(tournamentCode);

        // 1. Obtener jornadas ya jugadas y conservarlas
        List<MatchDay> finishedMatchDays = tournament.getMatchDays().stream()
                .filter(MatchDay::getIsFinished)  // Filtrar solo las jornadas finalizadas
                .toList();

        // 2. Eliminar jornadas no finalizadas de la base de datos
        this.matchDayRepository.deleteAllByTournamentCodeAndIsFinished(UUID.fromString(tournamentCode), false);

        // 3. Obtener el número de la siguiente jornada a partir de las finalizadas
        int nextMatchDayNumber = finishedMatchDays.size();

        // 4. Llamar a `reGenerate` a partir de la siguiente jornada disponible
        reGenerate(finishedMatchDays, tournament, tournamentParticipants, tournamentParticipants.size(), nextMatchDayNumber);
    }

    @Transactional
    private void reGenerate(List<MatchDay> matchDays, Tournament tournament, List<TournamentParticipant> teams, int numberOfTeams, int startingMatchDay) {
        Set<Match> playedMatches = new HashSet<>();

        // Conservar partidos ya jugados en jornadas finalizadas
        for (MatchDay matchDay : matchDays) {
            if (matchDay.getIsFinished()) {
                playedMatches.addAll(matchDay.getMatches());
            }
        }

        // Generar solo las jornadas que faltan, evitando duplicados de partidos
        for (int matchDayNumber = startingMatchDay; matchDayNumber < numberOfTeams - 1; matchDayNumber++) {
            MatchDay matchDay = matchDayRepository.save(
                    MatchDay.builder()
                            .numberOfMatchDay(matchDayNumber)
                            .tournament(tournament)
                            .isFinished(false)
                            .matches(new HashSet<>())
                            .build());

            // Generar partidos para la nueva jornada
            List<Match> newMatches = generateUniqueMatches(matchDay, teams, playedMatches);
            matchDay.getMatches().addAll(newMatches);
            matchDayRepository.save(matchDay);

            rotateTeams(teams); // Rota equipos para la siguiente jornada
        }
    }



    private List<Match> generateUniqueMatches(MatchDay matchDay, List<TournamentParticipant> teams, Set<Match> playedMatches) {
        List<Match> newMatches = new ArrayList<>();
        int numTeams = teams.size();

        for (int i = 0; i < numTeams / 2; i++) {
            TournamentParticipant team1 = teams.get(i);
            TournamentParticipant team2 = teams.get(numTeams - 1 - i);

            Match match = Match.builder()
                    .matchDay(matchDay)
                    .homeTeam(team1)
                    .awayTeam(team2)
                    .isFinished(false)
                    .build();


            // Solo agrega el partido si no se jugó en jornadas cerradas
            if (!playedMatches.contains(match)) {
                newMatches.add(match);
                playedMatches.add(match); // Agregar a jugados para evitar duplicados en futuras jornadas
            }
        }
        return newMatches;
    }





    @Override
    public void generateMatchDays(HashSet<TournamentParticipant> tournamentParticipants, String tournamentCode, LocalDateTime startDate) {
        Tournament tournament = getTournament(tournamentCode);
        if(!tournament.getMatchDays().isEmpty()) throw new ResourceAlreadyExistsException("El torneo ya cuenta con fechas.");
        int numberOfTeams = tournamentParticipants.size();
        int numberOfMatchDays = numberOfTeams - 1;
        //Set<Match> allMatches = new HashSet<>();

        List<TournamentParticipant> tournamentParticipantsList = new ArrayList<>(tournamentParticipants);
        //generate(tournament, tournamentParticipantsList, numberOfTeams, numberOfMatchDays, allMatches);
        generate(tournament, tournamentParticipantsList, numberOfTeams, numberOfMatchDays, startDate);
    }

    void generate(Tournament tournament, List<TournamentParticipant> teams, int numberOfTeams, int numberOfMatchDays, LocalDateTime startDate) {
        for (int matchDayNumber = 0; matchDayNumber < numberOfMatchDays; matchDayNumber++) {
            MatchDay matchDay = matchDayRepository.save(
                    MatchDay.builder()
                            .numberOfMatchDay(matchDayNumber)
                            .tournament(tournament)
                            .isFinished(false)
                            .matches(new HashSet<>())
                            .mvpPlayer(null)
                            .build());
            this.matchService.generateMatches(matchDay, teams, numberOfTeams, startDate);
            if(startDate != null){
                startDate = startDate.plusDays(7);
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
        for (Match match : matchDay.getMatches()) {
            matches.add(this.matchService.matchToResponse(match));
        }
        return new MatchDayResponse(matchDay.getId() ,matchDay.getNumberOfMatchDay(), matchDay.getIsFinished(),matchDay.getMvpPlayer(), matches);
    }


    @Override
    public void updateStatusMatchDay(Long id, Boolean status, String playerMvP) {
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


        if (matchDayOpt.get().getTournament().getMatchDays().size() == matchDayOpt.get().getNumberOfMatchDay() + 1) {
            matchDay.getTournament().setIsFinished(true);
            this.tournamentRepository.save(matchDay.getTournament());
        }

        matchDay.setMvpPlayer(playerMvP);
        matchDay.setIsFinished(status);
        this.matchDayRepository.save(matchDay);
    }

    @Override
    public List<MatchDayResponse> getMatchDaysByCode(String code) {
        List<MatchDay> list = this.matchDayRepository.findAllByTournamentCode(UUID.fromString(code));

        return list.stream().map(matchDay -> matchDayToResponse(matchDay)).toList();
    }

    @Override
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
        MatchDateRequest newDate = request;
        if(newDate.localDateTime().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("La fecha no puede ser anterior a la actual.");
        if( ( matchDay.getNumberOfMatchDay() > 0) && (matchDay.getNumberOfMatchDay()+1 <= matchDay.getTournament().getMatchDays().size())){
            MatchDay previousMatchDay = this.matchDayRepository.findById(matchDay.getId() - 1).map(matchDay1 -> matchDay1).orElseThrow(() -> new ResourceNotFoundException("No se encontró la jornada anterior."));
            if (previousMatchDay.getMatches().stream().anyMatch(match -> match.getDate() == null)) throw new IllegalArgumentException("La jornada anterior no tiene partidos con fecha. Carguelos antes de continuar.");
            Match lastMatch = previousMatchDay.getMatches().stream().max(Comparator.comparing(Match::getDate)).orElseThrow(() -> new ResourceNotFoundException("No se encontró el último partido de la jornada anterior."));
            if(newDate.localDateTime().isBefore(lastMatch.getDate())) throw new IllegalArgumentException("La fecha no puede ser anterior a la fecha del último partido de la jornada anterior.");
        }

        for (Match match : matchDay.getMatches()) {
            this.matchService.updateMatchDateAndDescription(match.getId(), newDate);
            newDate = new MatchDateRequest(newDate.localDateTime().plusMinutes(plusMinutes));
            NewDateResponse newDateResponse = new NewDateResponse(match.formatMatchDate(newDate.localDateTime()));
            newDates.add(newDateResponse);
        }
        return newDates;
    }



}