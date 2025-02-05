package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.*;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.exceptions.TeamDisableException;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.Statistics;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.PlayerParticipantRepository;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.repositories.TournamentRepository;
import com.gesfut.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class TournamentServiceImpl implements TournamentService {

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private UserEntityService userService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private TournamentParticipantRepository participantRepository;

    @Autowired
    private MatchDayService matchDayService;

    @Autowired
    private PlayerParticipantRepository playerParticipantRepository;
    @Autowired
    private TournamentParticipantService participantService;

    @Override
    public String createTournament(TournamentRequest request) {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
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
    public List<TournamentResponse> findAllTournaments() {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        return this.tournamentRepository.findAllByUser(user).stream().map(tournament -> tournamentToResponse(tournament)).toList();
    }
    @Override
    public List<TournamentShortResponse> findAllTournamentsShortAll() {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        return this.tournamentRepository.findAllByUser(user).stream().map(tournament -> tournamentToResponseShort(tournament)).toList();
    }

    @Override
    public TournamentShortResponse findAllTournamentsShort(String tournamentCode) {
        return this.tournamentRepository.findByCode(UUID.fromString(tournamentCode))
                .map(this::tournamentToResponseShort)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
    }


    @Override
    public TournamentResponse findTournamentByCode(String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        if(tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");

        return tournamentToResponse(tournament.get());
    }

    @Override
    @Transactional
    public String changeStatusTournamentByCode(String code, Boolean status) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        if (tournament.isEmpty()) return "El torneo no existe";
        verifyTournamentBelongsToManager(tournament.get(), user);
        tournament.get().setIsFinished(status);
        this.tournamentRepository.save(tournament.get());
        String response;

        if(status)
            response= "habilitado";
        else
            response= "deshabilitado";

        return "Torneo " + response + " exitosamente.";
    }

    @Override
    public void initializeTournament(MatchDayRequest request, LocalDateTime startDate) {
        if(request.teams().size()%2 != 0) request.teams().add(getIdDummyParticipant());
        HashSet<TournamentParticipant> tournamentParticipants = addTeamsToTournament(request.tournamentCode(), request.teams());
        matchDayService.generateMatchDays(tournamentParticipants, request.tournamentCode(), startDate);
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

        return (HashSet<TournamentParticipant>) this.participantRepository.findAllByTournament(tournament.get());
    }

    @Override
    public void updateTournamentParticipants(MatchDayRequest request) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(request.tournamentCode()));
        if (tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");
        List<TournamentParticipant> tournamentParticipants = tournament.get().getTeams().stream().toList();

        boolean isOdd = tournamentParticipants.stream()
                .anyMatch(particpant -> particpant.getTeam().getName().equals("Free"));

        if (isOdd){
            Long id =  replaceFreeParticipant(request.teams().get(0),tournamentParticipants);
            request.teams().remove(id);
        }

        addTeamsToTournament(request.tournamentCode(), request.teams());
        tournamentParticipants = this.participantRepository.findAllByTournament(tournament.get()).stream().toList();

        if (tournamentParticipants.size()%2 != 0) {
            addTeamToTournament(getIdDummyParticipant(), tournament.get());
            tournamentParticipants = this.participantRepository.findAllByTournament(tournament.get()).stream().toList();
        }

        matchDayService.reGenerateMatchDays(tournamentParticipants, request.tournamentCode());
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
        if (isActive){
            tournament.get().setIsFinished(false);
        }else {
            tournament.get().setIsFinished(true);
        }
        this.tournamentRepository.save(tournament.get());
        return true;
    }

    @Override
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
                            match.getDescription()));
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
    public List<TopScorersResponse> findTopScorersByTournament(String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
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
    public List<TopYellowCardsResponse> findTopYellowCardsByTournament(String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
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
    public List<TopRedCardsResponse> findTopRedCardsByTournament(String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
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
                .filter(part -> part.getTeam().getName().equals("Free"))
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
        tournamentParticipant.setIsActive(false);
        this.participantRepository.save(tournamentParticipant);
    }


    public TournamentResponse tournamentToResponse(Tournament tournament){
        return new TournamentResponse(
                tournament.getName(),
                tournament.getCode().toString(),
                tournament.getStartDate(),
                tournament.getUser().getName() + " " + tournament.getUser().getLastname(),
                tournament.getIsFinished(),
                !tournament.getTeams().isEmpty(),
                this.participantService.participantsToResponse(tournament.getTeams()),
                tournament.getMatchDays().stream().map(matchDay -> this.matchDayService.matchDayToResponse(matchDay)).collect(Collectors.toList())
        );
    }


    public TournamentShortResponse tournamentToResponseShort(Tournament tournament){
        return new TournamentShortResponse(
                tournament.getName(),
                tournament.getCode().toString(),
                tournament.getStartDate(),
                tournament.getIsFinished(),
                tournament.getIsActive(),
                !tournament.getTeams().isEmpty()
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
        if(!tournament.getUser().equals(user)) throw new RuntimeException("El torneo no pertenece a este usuario.");
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


}
