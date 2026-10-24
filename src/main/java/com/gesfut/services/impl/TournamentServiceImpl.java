package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.*;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.exceptions.TeamDisableException;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.Statistics;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.MatchDayRepository;
import com.gesfut.repositories.PlayerParticipantRepository;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.repositories.TournamentRepository;
import com.gesfut.services.*;
import org.antlr.v4.runtime.misc.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Transactional(rollbackFor = Exception.class)
public class TournamentServiceImpl implements TournamentService {

    @Autowired
    private TournamentAccess access;
    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private UserEntityService userService;

    @Autowired
    private TeamService teamService;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private MatchService disciplineMatchService;

    @Autowired(required = false)
    private com.gesfut.repositories.MatchRepository byeMatchRepository;

    @Autowired
    private TournamentParticipantRepository participantRepository;

    @Autowired
    private MatchDayService matchDayService;

    @Autowired
    private PlayerParticipantRepository playerParticipantRepository;
    @Autowired
    private TournamentParticipantService participantService;

    @Autowired
    private MatchDayRepository matchDayRepository;

    @Override
    public String createTournament(TournamentRequest request) {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        if(this.tournamentRepository.existsByNameAndUserId(request.name(), user.getId())) throw new ResourceAlreadyExistsException("Ya existe un torneo con ese nombre.");
        Tournament tournament = tournamentRepository.save(
                Tournament
                        .builder()
                        .code(getRandomUUID())
                        .teams(new HashSet<>())
                        .name(request.name())
                        .user(user)
                        .isFinished(false)
                        .isActive(true)
                        .startDate(LocalDate.now())
                        .prizes(new HashSet<>())
                        .build()
        );
        return tournament.getCode().toString();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentShortResponse> findAllTournamentsShortAll() {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        return this.tournamentRepository.findAllByUserOrderByIdDesc(user).stream().map(tournament -> tournamentToResponseShort(tournament)).toList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public TournamentShortResponse findAllTournamentsShort(String tournamentCode) {
        return this.tournamentRepository.findByCode(UUID.fromString(tournamentCode))
                .map(this::tournamentToResponseShort)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
    }


    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public TournamentResponse findTournamentByCode(String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findDetailedByCode(UUID.fromString(code));
        if(tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");

        return tournamentToResponse(tournament.get());
    }

    /** Revision critica #2: finalizar es explicito, exige todo jugado (liga y playoffs) y es irreversible. */
    @Override
    @Transactional
    public void finishTournament(String code) {
        Tournament t = this.tournamentRepository.findByCode(UUID.fromString(code)).orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        access.requireOwner(t);
        access.requireOpen(t);
        String pending = finishBlocker(t);
        if (pending != null) throw new IllegalArgumentException(pending);
        t.setIsFinished(true);
        this.tournamentRepository.save(t);
    }

    @Override
    @Transactional
    public void updateDiscipline(String code, com.gesfut.dtos.requests.DisciplineRequest request) {
        Tournament t = this.tournamentRepository.findByCode(UUID.fromString(code)).orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        access.requireOwner(t);
        access.requireOpen(t);
        t.setYellowCardsToSuspend(request.yellowCardsToSuspend());
        t.setRedCardSuspensionMatches(request.redCardSuspensionMatches());
        this.tournamentRepository.save(t);
        if (disciplineMatchService != null) disciplineMatchService.rebuildDisciplineFor(t);
    }

    /** Motivo por el que todavia no se puede finalizar, o null. */
    static String finishBlocker(Tournament t) {
        if (t.getMatchDays() == null || t.getMatchDays().isEmpty()) return "El torneo todav\u00eda no tiene fechas.";
        if (t.getMatchDays().stream().anyMatch(d -> !Boolean.TRUE.equals(d.getIsFinished()))) return "Hay fechas sin cerrar. Cerralas antes de finalizar el torneo.";
        if (t.getMatchDays().stream().flatMap(d -> d.getMatches().stream()).anyMatch(m -> !Boolean.TRUE.equals(m.getIsFinished()) && !m.isByeMatch()))
            return "Hay partidos sin resultado.";
        var last = t.getMatchDays().stream().max(Comparator.comparingInt(MatchDay::getNumberOfMatchDay)).orElseThrow();
        if (Boolean.TRUE.equals(last.getIsPlayOff()) && last.getMatches().size() != 1) return "Faltan rondas de playoffs: gener\u00e1 la siguiente ronda hasta la final.";
        return null;
    }

    @Override
    public void initializeTournament(MatchDayRequest request, LocalDateTime startDate) {
        if (request.teams() == null || request.teams().size() < 2 || new HashSet<>(request.teams()).size() != request.teams().size())
            throw new IllegalArgumentException("Seleccioná al menos dos equipos distintos.");
        if (request.startDate() != null && (request.minutesPerMatch() == null || request.dayBetweenMatchDay() == null))
            throw new IllegalArgumentException("Indicá duración e intervalo para programar las fechas.");
        if(request.teams().size()%2 != 0) request.teams().add(getIdDummyParticipant());
        HashSet<TournamentParticipant> tournamentParticipants = addTeamsToTournament(request.tournamentCode(), request.teams());
        matchDayService.generateMatchDays(tournamentParticipants, request.tournamentCode(), startDate, request.minutesPerMatch(), request.dayBetweenMatchDay());
    }


    private Long getIdDummyParticipant() {
        Team team = this.teamService.getTeamByName();
        return team.getId();
    }

    @Override
    public HashSet<TournamentParticipant> addTeamsToTournament(String code, List<Long> teams){
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        if(tournament.isEmpty())  throw new ResourceNotFoundException("El torneo no existe" );
        verifyTournamentBelongsToManager(tournament.get(), user);
        teams.forEach(team -> {
            addTeamToTournament(team, tournament.get());
        });

        return new HashSet<>(this.participantRepository.findAllByTournament(tournament.get()));
    }

    @Override
    public void updateTournamentParticipants(MatchDayRequest request) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(request.tournamentCode()));
        if (tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");
        access.requireOwner(tournament.get());
        access.requireOpen(tournament.get());
        if (tournament.get().getMatchDays().stream().anyMatch(day -> Boolean.TRUE.equals(day.getIsPlayOff())))
            throw new IllegalArgumentException("No se pueden agregar equipos durante los playoffs.");
        if (request.teams() == null || request.teams().isEmpty() || new HashSet<>(request.teams()).size() != request.teams().size())
            throw new IllegalArgumentException("Seleccion\u00e1 equipos distintos.");
        // Revision critica #7: con el torneo empezado solo se suma un equipo si es impar; ocupa el lugar de Libre
        // en lo que falta jugar y las fechas ya jugadas quedan como libre.
        if (hasStarted(tournament.get())) {
            addTeamToStartedTournament(tournament.get(), request.teams());
            return;
        }
        List<TournamentParticipant> tournamentParticipants = tournament.get().getTeams().stream().toList();

        boolean isOdd = tournamentParticipants.stream()
                .anyMatch(particpant -> Match.isFreeParticipant(particpant));

        boolean hasHistory = tournament.get().getMatchDays().stream().anyMatch(day -> Boolean.TRUE.equals(day.getIsFinished()));
        if (isOdd && !hasHistory){
            Long id =  replaceFreeParticipant(request.teams().get(0),tournamentParticipants);
            request.teams().remove(id);
        }

        addTeamsToTournament(request.tournamentCode(), request.teams());
        tournamentParticipants = this.participantRepository.findAllByTournament(tournament.get()).stream().toList();

        List<TournamentParticipant> realTeams = new ArrayList<>(tournamentParticipants.stream().filter(p -> !Match.isFreeParticipant(p)).toList());
        if (realTeams.size() % 2 != 0) {
            var free = tournamentParticipants.stream().filter(p -> Match.isFreeParticipant(p)).findFirst();
            if (free.isEmpty()) {
                addTeamToTournament(getIdDummyParticipant(), tournament.get());
                free = participantRepository.findAllByTournament(tournament.get()).stream().filter(p -> Match.isFreeParticipant(p)).findFirst();
            }
            realTeams.add(free.orElseThrow());
        }
        matchDayService.reGenerateMatchDays(realTeams, request.tournamentCode());
    }

    static boolean hasStarted(Tournament t) {
        return t.getMatchDays().stream().anyMatch(d -> Boolean.TRUE.equals(d.getIsFinished())
                || d.getMatches().stream().anyMatch(m -> Boolean.TRUE.equals(m.getIsFinished()) && !m.isByeMatch()));
    }

    private void addTeamToStartedTournament(Tournament t, List<Long> teams) {
        var free = t.getTeams().stream().filter(Match::isFreeParticipant).findFirst();
        boolean odd = free.isPresent() && t.getTeams().stream().filter(p -> !Match.isFreeParticipant(p)).count() % 2 == 1;
        if (!odd) throw new IllegalArgumentException("El torneo ya empez\u00f3 y tiene cantidad par de equipos: no se pueden sumar equipos.");
        if (teams.size() != 1) throw new IllegalArgumentException("El torneo ya empez\u00f3: solo se puede sumar 1 equipo, que ocupa el lugar de la fecha libre.");
        addTeamToTournament(teams.get(0), t);
        TournamentParticipant added = this.participantRepository.findAllByTournament(t).stream()
                .filter(p -> p.getTeam() != null && Objects.equals(p.getTeam().getId(), teams.get(0)))
                .findFirst().orElseThrow(() -> new IllegalStateException("No se pudo sumar el equipo."));
        TournamentParticipant freeP = free.get();
        for (MatchDay day : t.getMatchDays()) {
            if (Boolean.TRUE.equals(day.getIsFinished())) continue; // fechas jugadas: quedan como libre
            for (Match m : day.getMatches()) {
                if (Boolean.TRUE.equals(m.getIsFinished())) continue;
                boolean changed = false;
                if (m.getHomeTeam() == freeP || Match.isFreeParticipant(m.getHomeTeam())) { m.setHomeTeam(added); changed = true; }
                if (m.getAwayTeam() == freeP || Match.isFreeParticipant(m.getAwayTeam())) { m.setAwayTeam(added); changed = true; }
                if (changed && Match.isInactiveParticipant(m.getHomeTeam()) == false && Match.isInactiveParticipant(m.getAwayTeam()) == false) m.setBye(false);
            }
            this.matchDayRepository.save(day);
        }
    }

    @Override
    public Boolean existsByCode(String tournamentCode) {
        return this.tournamentRepository.existsByCode(UUID.fromString(tournamentCode));
    }

    @Override
    public Boolean changeNameTournamentByCode(String code, String name) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        if (tournament.isEmpty()) return false;
        verifyTournamentBelongsToManager(tournament.get(), user);
        tournament.get().setName(name);
        this.tournamentRepository.save(tournament.get());
        return true;
    }

    @Override
    public Boolean changeIsActive(String code, Boolean isActive) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        if (tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");
        verifyTournamentBelongsToManager(tournament.get(), user);
        tournament.get().setIsActive(isActive);

        this.tournamentRepository.save(tournament.get());
        return true;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<MatchResponse> findMatchesByTournamentAndParticipant(String code, Long idParticipant){
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        if(tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");
        TournamentParticipant participant = this.participantRepository.findById(idParticipant).orElseThrow(() -> new ResourceNotFoundException("Participante no encontrado."));
        if(!participant.getTournament().equals(tournament.get())) throw new ResourceNotFoundException("Participante no encontrado en el torneo.");
        List<MatchResponse> matches = new ArrayList<>();
        tournament.get().getMatchDays().forEach(matchDay -> {
            matchDay.getMatches().forEach(match -> {
                if(match.getHomeTeam().equals(participant) || match.getAwayTeam().equals(participant)){
                    matches.add(new MatchResponse(
                            match.getId(),
                            match.getHomeTeam().getTeam().getName(),
                            match.getAwayTeam().getTeam().getName(),
                            matchDay.getNumberOfMatchDay(),
                            match.getGoalsHomeTeam(),
                            match.getGoalsAwayTeam(),
                            match.getEvents().stream().map(event -> new EventResponse(
                                    event.getId(),
                                    event.getQuantity(),
                                    event.getType(),
                                    event.getPlayerParticipant().getPlayer().getName() + " " + event.getPlayerParticipant().getPlayer().getLastName(),
                                    event.getPlayerParticipant().getPlayer().getTeam().getName(),
                                    event.getPlayerParticipant().getId()
                            )).toList(),
                            match.getIsFinished(),
                            match.formatMatchDate(match.getDate()),
                            match.getDescription(),
                            match.getMvpPlayer(),
                            match.getVsMatchIdWhoWin(),
                            match.getTiebreakWinnerId(),
                            match.resultLoadableToday(),
                            match.isByeMatch(),
                            match.restingTeamName()
                            ));
                }
            });
        });
        return matches;
    }

    @Override
    public boolean isMyTournament(String code) {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        return this.tournamentRepository.findByCodeAndUser(UUID.fromString(code), user).isPresent();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TopScorersResponse> findTopScorersByTournament(String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findDetailedByCode(UUID.fromString(code));
        if(tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");
        List<TopScorersResponse> response = new ArrayList<>();
        tournament.get().getTeams().forEach(team -> {
            team.getPlayerParticipants().forEach(player -> {
                if(player.getGoals() >= 1){
                    response.add(new TopScorersResponse(player.getPlayer().getName() + " " + player.getPlayer().getLastName(),player.getTournamentParticipant().getTeam().getName(), player.getGoals()));
                }
            });
        });
        return response.stream()
                .sorted(Comparator.comparingInt(TopScorersResponse::goals).reversed())
                .collect(Collectors.toList());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TopYellowCardsResponse> findTopYellowCardsByTournament(String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findDetailedByCode(UUID.fromString(code));
        if(tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");
        List<TopYellowCardsResponse> response = new ArrayList<>();
        tournament.get().getTeams().forEach(team -> {
            team.getPlayerParticipants().forEach(player -> {
                if(player.getYellowCards() >= 1){
                    response.add(new TopYellowCardsResponse(player.getPlayer().getName() + " " + player.getPlayer().getLastName(),player.getTournamentParticipant().getTeam().getName(), player.getYellowCards()));
                }
            });
        });
        return response.stream()
                .sorted(Comparator.comparingInt(TopYellowCardsResponse::yellowCards).reversed())
                .collect(Collectors.toList());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TopRedCardsResponse> findTopRedCardsByTournament(String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findDetailedByCode(UUID.fromString(code));
        if(tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");
        List<TopRedCardsResponse> response = new ArrayList<>();
        tournament.get().getTeams().forEach(team -> {
            team.getPlayerParticipants().forEach(player -> {
                if(player.getRedCards() >= 1){
                    response.add(new TopRedCardsResponse(player.getPlayer().getName() + " " + player.getPlayer().getLastName(),player.getTournamentParticipant().getTeam().getName(), player.getRedCards()));
                }
            });
        });
        return response.stream()
                .sorted(Comparator.comparingInt(TopRedCardsResponse::redCards).reversed())
                .collect(Collectors.toList());
    }



    private Long replaceFreeParticipant(Long id,List<TournamentParticipant> tournamentParticipants){
        Team team = teamService.getTeamByIdSecured(id);
        if(!team.getStatus()) throw new TeamDisableException("El equipo '"+ team.getName() + "' se encuentra deshabilitado.");
        if(this.participantRepository.existsByTournamentAndTeam(tournamentParticipants.getFirst().getTournament(), team)) throw new ResourceAlreadyExistsException("El equipo ya está participando en el torneo");
        TournamentParticipant newTeam = tournamentParticipants.stream()
                .filter(part -> Match.isFreeParticipant(part))
                .findFirst()
                .get();

        newTeam.setTeam(team);
        newTeam.setIsActive(true);
        newTeam.setStatistics(generateStatistics());

        this.playerParticipantRepository.deleteAll(newTeam.getPlayerParticipants());
        newTeam.getPlayerParticipants().clear();
        createPlayerParticipants(team, newTeam);

        this.participantRepository.save(newTeam);

        return team.getId();
    }

    @Override
    public void addTeamToTournament(Long idTeam, Tournament tournament) {

        Team team = teamService.getTeamByIdSecured(idTeam);
        if(this.participantRepository.existsByTournamentAndTeam(tournament, team)) throw new ResourceAlreadyExistsException("El equipo ya está participando en el torneo");
        if(!team.getStatus()) throw new TeamDisableException("El equipo '"+ team.getName() + "' se encuentra deshabilitado.");
        Statistics statistics = generateStatistics();
        HashSet <PlayerParticipant> playerParticipants = new HashSet<>();

        TournamentParticipant participant = TournamentParticipant
                .builder()
                .tournament(tournament)
                .team(team)
                .statistics(statistics)
                .isActive(true)
                .playerParticipants(new HashSet<>())
                .build();

        statistics.setParticipant(participant);
        participant = this.participantRepository.save(participant);
        createPlayerParticipants(team, participant);
    }

    public HashSet<PlayerParticipant> createPlayerParticipants(Team team, TournamentParticipant participant){
        HashSet<PlayerParticipant> playerParticipants = new HashSet<>();
        team.getPlayers().forEach(player -> {
            playerParticipants.add(
                    PlayerParticipant
                            .builder()
                            .player(player)
                            .tournamentParticipant(participant)
                            .events(new ArrayList<>())
                            .goals(0)
                            .isSuspended(false)
                            .consecutiveCards(0)
                            .redCards(0)
                            .yellowCards(0)
                            .isActive(true)
                            .isMvp(0)
                            .build()
            );
        });
        this.playerParticipantRepository.saveAll(playerParticipants);
        return playerParticipants;
    }


    @Override
    public void disableTeamFromTournament(TournamentParticipant tournamentParticipant) {
        // Plan 22/10 #2: sus partidos pendientes pasan a fecha libre del rival; lo jugado no se toca.
        TeamDisableRules.checkCanDisable(tournamentParticipant);
        var changed = TeamDisableRules.disableParticipant(tournamentParticipant);
        this.participantRepository.save(tournamentParticipant);
        if (byeMatchRepository != null) byeMatchRepository.saveAll(changed);
    }


    @Override
    @Transactional
    public void removeParticipantAndRegenerate(TournamentParticipant p) {
        Tournament t = p.getTournament();
        access.requireOwner(t);
        if (TeamDisableRules.hasStarted(t)) throw new IllegalArgumentException("El torneo '" + t.getName() + "' ya empez\u00f3: no se puede regenerar el fixture.");
        TeamDisableRules.checkCanRemove(p);
        // Se conserva la programacion original (inicio, minutos entre partidos y dias entre fechas) si la habia.
        List<MatchDay> ordered = t.getMatchDays().stream().sorted(Comparator.comparingInt(MatchDay::getNumberOfMatchDay)).toList();
        LocalDateTime start = null; Integer minutes = null, days = null;
        if (!ordered.isEmpty()) {
            List<LocalDateTime> first = ordered.get(0).getMatches().stream().filter(m -> !m.isByeMatch()).map(Match::getDate).filter(Objects::nonNull).sorted().toList();
            if (!first.isEmpty()) {
                start = first.get(0);
                minutes = first.size() > 1 ? (int) java.time.Duration.between(first.get(0), first.get(1)).toMinutes() : 60;
                if (minutes <= 0) minutes = 60;
                days = 7;
                if (ordered.size() > 1) {
                    var second = ordered.get(1).getMatches().stream().map(Match::getDate).filter(Objects::nonNull).min(Comparator.naturalOrder());
                    if (second.isPresent()) { long d = java.time.Duration.between(start.toLocalDate().atStartOfDay(), second.get().toLocalDate().atStartOfDay()).toDays(); if (d > 0) days = (int) d; }
                }
            }
        }
        var oldDays = new ArrayList<>(t.getMatchDays());
        t.getMatchDays().clear();
        matchDayRepository.deleteAll(oldDays);
        t.getTeams().remove(p);
        if (p.getTeam() != null && p.getTeam().getTournaments() != null) p.getTeam().getTournaments().remove(p);
        participantRepository.delete(p);
        List<TournamentParticipant> real = new ArrayList<>(t.getTeams().stream().filter(x -> !Match.isFreeParticipant(x)).toList());
        var free = t.getTeams().stream().filter(Match::isFreeParticipant).findFirst();
        if (real.size() % 2 != 0) {
            if (free.isEmpty()) {
                addTeamToTournament(getIdDummyParticipant(), t);
                free = participantRepository.findAllByTournament(t).stream().filter(Match::isFreeParticipant).findFirst();
            }
            real.add(free.orElseThrow());
        } else if (free.isPresent()) {
            t.getTeams().remove(free.get());
            participantRepository.delete(free.get());
        }
        matchDayRepository.flush();
        matchDayService.generateMatchDays(new HashSet<>(real), t.getCode().toString(), start, minutes, days);
    }

    public TournamentResponse tournamentToResponse(Tournament tournament){
        List<TournamentParticipant> ranked = new ArrayList<>(tournament.getTeams().stream().filter(p -> !Match.isFreeParticipant(p)).toList());
        sortParticipants(ranked);
        List<ParticipantResponse> leagueParticipants = ranked.stream().map(p -> {
            ParticipantResponse response = participantService.participantToResponse(p);
            return new ParticipantResponse(response.idParticipant(), response.idTeam(), response.name(), response.isActive(), leagueStatistics(p), response.playerParticipants());
        }).toList();
        List<MatchDay> leagueDays = tournament.getMatchDays().stream().filter(day -> !Boolean.TRUE.equals(day.getIsPlayOff())).toList();
        Long leagueChampion = !ranked.isEmpty() && !leagueDays.isEmpty() && leagueDays.stream().allMatch(day -> Boolean.TRUE.equals(day.getIsFinished())) ? ranked.getFirst().getId() : null;
        Long playoffChampion = null;
        var lastDay = tournament.getMatchDays().stream().max(Comparator.comparingInt(MatchDay::getNumberOfMatchDay));
        if (lastDay.isPresent() && Boolean.TRUE.equals(lastDay.get().getIsPlayOff()) && Boolean.TRUE.equals(lastDay.get().getIsFinished()) && lastDay.get().getMatches().size() == 1) {
            Match finalMatch = lastDay.get().getMatches().iterator().next();
            if (Boolean.TRUE.equals(finalMatch.getIsFinished())) {
                if (!Objects.equals(finalMatch.getGoalsHomeTeam(), finalMatch.getGoalsAwayTeam()) || finalMatch.getTiebreakWinnerId() != null) playoffChampion = whoWin(finalMatch).getId();
            }
        }
        return new TournamentResponse(
                tournament.getName(),
                tournament.getCode().toString(),
                tournament.getStartDate(),
                tournament.getUser().getName() + " " + tournament.getUser().getLastname(),
                tournament.getIsFinished(),
                !tournament.getTeams().isEmpty(),
                leagueParticipants,
                tournament.getMatchDays().stream().sorted(Comparator.comparingInt(MatchDay::getNumberOfMatchDay)).map(matchDay -> this.matchDayService.matchDayToResponse(matchDay)).collect(Collectors.toList()),
                leagueChampion,
                playoffChampion,
                tournament.yellowLimit(),
                tournament.redMatches()
        );
    }


    public TournamentShortResponse tournamentToResponseShort(Tournament tournament){
        return new TournamentShortResponse(
                tournament.getName(),
                tournament.getCode().toString(),
                tournament.getStartDate(),
                tournament.getIsFinished(),
                tournament.getIsActive(),
                !tournament.getTeams().isEmpty(),
                tournament.yellowLimit(),
                tournament.redMatches()
        );
    }




    private UUID getRandomUUID(){
        UUID code;
        do{
            code = UUID.randomUUID();
        }while(this.tournamentRepository.existsByCode(code));
        return code;
    }

    private void verifyTournamentBelongsToManager(Tournament tournament, UserEntity user){
        access.requireOwner(tournament);
    }


    private Statistics generateStatistics(){
        return Statistics
                .builder()
                .points(0)
                .matchesPlayed(0)
                .wins(0)
                .draws(0)
                .losses(0)
                .goalsFor(0)
                .goalsAgainst(0)
                .redCards(0)
                .yellowCards(0)
                .build();
    }

    @Override
    public void generatePlayOffs(String code, List<Long> qualifiedTeams) {
        generatePlayOffs(code, qualifiedTeams, null, null);
    }

    /** Revision critica #6: cada ronda de playoffs se genera con fecha y hora; los partidos se escalonan cada minutesPerMatch. */
    @Override
    @Transactional
    public void generatePlayOffs(String code, List<Long> qualifiedTeams, java.time.LocalDateTime startDate, Integer minutesPerMatch) {
        if (startDate != null) {
            if (startDate.isBefore(java.time.LocalDateTime.now().minusMinutes(1))) throw new IllegalArgumentException("Eleg\u00ed una fecha y hora futura para la ronda.");
            if (minutesPerMatch != null && (minutesPerMatch < 0 || minutesPerMatch > 600)) throw new IllegalArgumentException("Los minutos entre partidos deben estar entre 0 y 600.");
        }
        doGeneratePlayOffs(code, qualifiedTeams);
        if (startDate == null) return;
        Tournament t = this.tournamentRepository.findByCode(UUID.fromString(code)).orElseThrow();
        MatchDay round = t.getMatchDays().stream().max(Comparator.comparingInt(MatchDay::getNumberOfMatchDay)).orElseThrow();
        java.time.LocalDateTime previousLast = t.getMatchDays().stream().filter(d -> d != round)
                .flatMap(d -> d.getMatches().stream()).map(Match::getDate).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        if (previousLast != null && !startDate.isAfter(previousLast)) throw new IllegalArgumentException("La ronda debe empezar despu\u00e9s del \u00faltimo partido jugado.");
        java.time.LocalDateTime next = startDate;
        int step = minutesPerMatch == null ? 0 : minutesPerMatch;
        for (Match m : round.getMatches().stream().sorted(Comparator.comparingLong(Match::getId)).toList()) {
            m.setDate(next);
            next = next.plusMinutes(step);
        }
        this.matchDayRepository.save(round);
    }

    private void doGeneratePlayOffs(String code, List<Long> qualifiedTeams) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        if(tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");
        access.requireOwner(tournament.get());
        access.requireOpen(tournament.get());
        if(tournament.get().getMatchDays().stream().anyMatch(matchDay -> !matchDay.getIsFinished())) throw new RuntimeException("Hay jornadas sin cerrar.");
        if (tournament.get().getMatchDays().stream().anyMatch(matchDay -> matchDay.getMatches().stream().anyMatch(match -> !match.getIsFinished()))) throw new RuntimeException("Hay partidos sin finalizar.");
        Set<MatchDay> matchDays = tournament.get().getMatchDays().stream().sorted(Comparator.comparingInt(MatchDay::getNumberOfMatchDay)).collect(Collectors.toSet());
        MatchDay lastestMatchDay = matchDays.stream().max(Comparator.comparingInt(MatchDay::getNumberOfMatchDay)).orElseThrow(() -> new RuntimeException("No hay jornadas en el torneo."));
        if (Boolean.TRUE.equals(lastestMatchDay.getIsPlayOff())){
            nextPlayOffs(tournament.get(), lastestMatchDay);
        }else{
            firstPlayOffs(tournament.get(), qualifiedTeams);
        }
    }

    @Transactional
    private void nextPlayOffs(Tournament tournament, MatchDay lastMatchDay){
        if (lastMatchDay.getMatches().size() <= 1) throw new IllegalArgumentException("La final ya fue disputada; no hay otra ronda.");
        if (lastMatchDay.getMatches().size() % 2 != 0) throw new IllegalArgumentException("La ronda anterior no tiene un cuadro válido.");
        MatchDay newMatchDay = MatchDay.builder()
                .numberOfMatchDay(lastMatchDay.getNumberOfMatchDay() + 1)
                .tournament(tournament)
                .isFinished(false)
                .matches(new LinkedHashSet<>())
                .mvpPlayer(null)
                .isPlayOff(true)
                .build();

        List<Match> matches = new ArrayList<>(lastMatchDay.getMatches().stream().toList());
        matches.sort(Comparator.comparingLong(Match::getId));

        for (int i = 0; i < matches.size(); i+=2){
            Match match = matches.get(i);
            TournamentParticipant winner = whoWin(match);

            Match nextMatchfind = lastMatchDay.getMatches().stream()
                    .filter(m -> Objects.equals(m.getVsMatchIdWhoWin(), match.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("El cuadro de cruces no tiene una pareja válida."));
            TournamentParticipant winnerNextMatch = whoWin(nextMatchfind);

            System.out.printf("Winner del partido %s: %s\n", match.getId(), winner.getTeam().getName());
            System.out.printf("Winner del partido %s: %s\n", nextMatchfind.getId(), winnerNextMatch.getTeam().getName());

            Match newMatch = Match.builder()
                    .homeTeam(winner)
                    .awayTeam(winnerNextMatch)
                    .goalsHomeTeam(0)
                    .goalsAwayTeam(0)
                    .isFinished(false)
                    .matchDay(newMatchDay)
                    .events(new ArrayList<>())
                    .date(null)
                    .description("1")
                    .mvpPlayer(null)
                    .build();
            newMatchDay.getMatches().add(newMatch);
        }
        this.matchDayRepository.save(newMatchDay);
        setNextMatchPlayOff(newMatchDay);
        tournament.getMatchDays().add(newMatchDay);
        this.tournamentRepository.save(tournament);
    }

    private void firstPlayOffs(Tournament tournament, List<Long> qualifiedTeams) {
        if (qualifiedTeams == null || qualifiedTeams.isEmpty() || new HashSet<>(qualifiedTeams).size() != qualifiedTeams.size())
            throw new IllegalArgumentException("Seleccioná equipos distintos para los cruces.");
        List<TournamentParticipant> qualifiedParticipants = getQualifiedParticipants(tournament, qualifiedTeams);
        if (qualifiedParticipants.size() != qualifiedTeams.size() || qualifiedParticipants.stream().anyMatch(p -> Match.isFreeParticipant(p)))
            throw new IllegalArgumentException("Hay participantes que no pertenecen al torneo o son fechas libres.");
        // Plan 22/10 #2 (respuesta 3): un equipo dado de baja no clasifica.
        var inactive = qualifiedParticipants.stream().filter(Match::isInactiveParticipant).map(p -> p.getTeam().getName()).toList();
        if (!inactive.isEmpty())
            throw new IllegalArgumentException("No pueden clasificar a playoffs equipos dados de baja: " + String.join(", ", inactive) + ".");
        sortParticipants(qualifiedParticipants);
        validateQualifiedParticipants(qualifiedParticipants);
        createPlayoffMatches(tournament, qualifiedParticipants);
    }

    private List<TournamentParticipant> getQualifiedParticipants(Tournament tournament, List<Long> qualifiedTeams) {
        List<TournamentParticipant> qualifiedParticipants = new ArrayList<>();
        tournament.getTeams().forEach(team -> {
            if (qualifiedTeams.contains(team.getId())) {
                qualifiedParticipants.add(team);
            }
        });
        return qualifiedParticipants;
    }

    /**
     * Revision critica #8: puntos, diferencia de gol, goles a favor; entre los que siguen empatados,
     * enfrentamiento directo (puntos y luego diferencia entre ellos), luego fair play (menos tarjetas
     * amarillas + rojas en la liga), luego nombre. Solo partidos de liga.
     */
    void sortParticipants(List<TournamentParticipant> participants) {
        Map<Long, StatisticsResponse> st = new HashMap<>();
        participants.forEach(p -> st.put(p.getId(), leagueStatistics(p)));
        Comparator<TournamentParticipant> primary = Comparator
                .comparingInt((TournamentParticipant p) -> st.get(p.getId()).points()).reversed()
                .thenComparing(Comparator.comparingInt((TournamentParticipant p) -> st.get(p.getId()).goalsFor() - st.get(p.getId()).goalsAgainst()).reversed())
                .thenComparing(Comparator.comparingInt((TournamentParticipant p) -> st.get(p.getId()).goalsFor()).reversed());
        participants.sort(primary);
        List<TournamentParticipant> result = new ArrayList<>();
        int i = 0;
        while (i < participants.size()) {
            int j = i + 1;
            while (j < participants.size() && primary.compare(participants.get(i), participants.get(j)) == 0) j++;
            List<TournamentParticipant> group = new ArrayList<>(participants.subList(i, j));
            if (group.size() > 1) resolveTie(group);
            result.addAll(group);
            i = j;
        }
        participants.clear();
        participants.addAll(result);
    }

    private void resolveTie(List<TournamentParticipant> group) {
        Set<Long> ids = new HashSet<>();
        group.forEach(p -> ids.add(p.getId()));
        Map<Long, int[]> h2h = new HashMap<>(); // [puntos, diferencia]
        Map<Long, Integer> cards = new HashMap<>();
        group.forEach(p -> { h2h.put(p.getId(), new int[2]); cards.put(p.getId(), 0); });
        Tournament t = group.getFirst().getTournament();
        for (MatchDay day : t.getMatchDays()) {
            if (Boolean.TRUE.equals(day.getIsPlayOff())) continue;
            for (Match m : day.getMatches()) {
                if (!Boolean.TRUE.equals(m.getIsFinished()) || m.isByeMatch()) continue;
                Long h = m.getHomeTeam().getId(), a = m.getAwayTeam().getId();
                if (m.getEvents() != null) for (var e : m.getEvents()) {
                    if (e.getType() != com.gesfut.models.matchDay.EEventType.YELLOW_CARD && e.getType() != com.gesfut.models.matchDay.EEventType.RED_CARD) continue;
                    var owner = e.getPlayerParticipant() == null ? null : e.getPlayerParticipant().getTournamentParticipant();
                    if (owner != null && cards.containsKey(owner.getId())) cards.merge(owner.getId(), e.getQuantity() == null ? 1 : e.getQuantity(), Integer::sum);
                }
                if (!ids.contains(h) || !ids.contains(a)) continue;
                int gh = m.getGoalsHomeTeam(), ga = m.getGoalsAwayTeam();
                h2h.get(h)[1] += gh - ga; h2h.get(a)[1] += ga - gh;
                if (gh > ga) h2h.get(h)[0] += 3; else if (ga > gh) h2h.get(a)[0] += 3; else { h2h.get(h)[0]++; h2h.get(a)[0]++; }
            }
        }
        group.sort(Comparator.comparingInt((TournamentParticipant p) -> h2h.get(p.getId())[0]).reversed()
                .thenComparing(Comparator.comparingInt((TournamentParticipant p) -> h2h.get(p.getId())[1]).reversed())
                .thenComparingInt(p -> cards.get(p.getId()))
                .thenComparing(p -> p.getTeam().getName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TournamentParticipant::getId));
    }

    private StatisticsResponse leagueStatistics(TournamentParticipant p) {
        int played=0, wins=0, draws=0, losses=0, goalsFor=0, goalsAgainst=0;
        for (MatchDay day : p.getTournament().getMatchDays()) {
            if (Boolean.TRUE.equals(day.getIsPlayOff())) continue;
            for (Match m : day.getMatches()) {
                if (!Boolean.TRUE.equals(m.getIsFinished()) || m.isByeMatch()) continue;
                boolean home=Objects.equals(m.getHomeTeam().getId(), p.getId());
                if (!home && !Objects.equals(m.getAwayTeam().getId(), p.getId())) continue;
                int scored=home?m.getGoalsHomeTeam():m.getGoalsAwayTeam(), conceded=home?m.getGoalsAwayTeam():m.getGoalsHomeTeam();
                played++; goalsFor+=scored; goalsAgainst+=conceded;
                if (scored>conceded) wins++; else if(scored<conceded) losses++; else draws++;
            }
        }
        return new StatisticsResponse(3*wins+draws, played, wins, draws, losses, goalsFor, goalsAgainst);
    }

    private void validateQualifiedParticipants(List<TournamentParticipant> participants) {
        if (participants.isEmpty()) {
            throw new RuntimeException("No hay equipos clasificados.");
        }
        int count = participants.size();
        if (count < 2 || (count & (count - 1)) != 0) {
            throw new IllegalArgumentException("Seleccioná 2, 4, 8, 16 o 32 equipos para un cuadro sin pases libres.");
        }
    }

    private void createPlayoffMatches(Tournament tournament, List<TournamentParticipant> qualifiedParticipants) {
        int lastMatchDay = tournament.getMatchDays().stream()
                .map(MatchDay::getNumberOfMatchDay)
                .max(Integer::compareTo)
                .orElse(0);

        MatchDay matchDay = MatchDay.builder()
                .numberOfMatchDay(lastMatchDay + 1)
                .tournament(tournament)
                .isFinished(false)
                .matches(new LinkedHashSet<>())
                .mvpPlayer(null)
                .isPlayOff(true)
                .build();

        // Revision critica #1: siembra clasica (1v8 y 4v5 en una mitad, 2v7 y 3v6 en la otra; 1 y 2 solo en la final).
        // Los partidos se guardan en orden de cuadro (LinkedHashSet + IDENTITY): ids crecientes = orden de cuadro,
        // y la ronda siguiente empareja (0,1), (2,3)... de forma determinista.
        List<Integer> order = bracketSeedOrder(qualifiedParticipants.size());
        for (int k = 0; k < order.size(); k += 2) {
            Match match = Match.builder()
                    .homeTeam(qualifiedParticipants.get(order.get(k) - 1))
                    .awayTeam(qualifiedParticipants.get(order.get(k + 1) - 1))
                    .goalsHomeTeam(0)
                    .goalsAwayTeam(0)
                    .isFinished(false)
                    .matchDay(matchDay)
                    .events(new ArrayList<>())
                    .date(null)
                    .description("1")
                    .mvpPlayer(null)
                    .build();
            matchDay.getMatches().add(match);
        }
        this.matchDayRepository.save(matchDay);
        setNextMatchPlayOff(matchDay);
        tournament.getMatchDays().add(matchDay);
        this.tournamentRepository.save(tournament);
    }

    /** Orden de siembra en el cuadro para n = 2^k: n=8 -> [1,8,4,5,2,7,3,6]. */
    static List<Integer> bracketSeedOrder(int n) {
        List<Integer> order = new ArrayList<>(List.of(1, 2));
        while (order.size() < n) {
            int size = order.size() * 2;
            List<Integer> next = new ArrayList<>();
            for (int seed : order) { next.add(seed); next.add(size + 1 - seed); }
            order = next;
        }
        return order;
    }

    private void setNextMatchPlayOff(MatchDay matchDay) {
        List<Match> matches = new ArrayList<>(matchDay.getMatches());
        matches.sort(Comparator.comparingLong(Match::getId));

        for (int i = 0; i < matches.size(); i += 2) {
            if (i + 1 >= matches.size()) break;

            Match match = matches.get(i);
            Match nextMatch = matches.get(i + 1);

            match.setVsMatchIdWhoWin(nextMatch.getId());
            nextMatch.setVsMatchIdWhoWin(match.getId());

            System.out.printf("Partido ID: %s, Equipo Local: %s, Equipo Visitante: %s, Next Match ID: %s\n",
                    match.getId(), match.getHomeTeam().getTeam().getName(), match.getAwayTeam().getTeam().getName(), match.getVsMatchIdWhoWin());

            System.out.printf("Partido ID: %s, Equipo Local: %s, Equipo Visitante: %s, Next Match ID: %s\n",
                    nextMatch.getId(), nextMatch.getHomeTeam().getTeam().getName(), nextMatch.getAwayTeam().getTeam().getName(), nextMatch.getVsMatchIdWhoWin());
        }
    }


    private TournamentParticipant whoWin(Match match){
        if (match.getGoalsHomeTeam() > match.getGoalsAwayTeam()){
            return match.getHomeTeam();
        }else if (match.getGoalsHomeTeam() < match.getGoalsAwayTeam()){
            return match.getAwayTeam();
        }else{
            if (Objects.equals(match.getTiebreakWinnerId(), match.getHomeTeam().getId())) return match.getHomeTeam();
            if (Objects.equals(match.getTiebreakWinnerId(), match.getAwayTeam().getId())) return match.getAwayTeam();
            throw new IllegalArgumentException("Hay un cruce empatado sin ganador definido.");
        }
    }

}
