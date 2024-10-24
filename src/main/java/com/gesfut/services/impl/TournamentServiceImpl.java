package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.dtos.responses.StatisticsResponse;
import com.gesfut.dtos.responses.TournamentResponse;
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
import com.gesfut.services.MatchDayService;
import com.gesfut.services.TeamService;
import com.gesfut.services.TournamentService;
import com.gesfut.services.UserEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    @Override
    public void createTournament(TournamentRequest request) {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        tournamentRepository.save(
            Tournament
                    .builder()
                    .code(getRandomUUID())
                    .teams(new HashSet<>())
                    .name(request.name())
                    .user(user)
                    .isFinished(false)
                    .startDate(LocalDate.now())
                    .build()
        );
    }

    @Override
    public List<TournamentResponse> findAllTournaments() {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        return this.tournamentRepository.findAllByUser(user).stream().map(tournament -> tournamentToResponse(tournament)).toList();
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
    public void initializeTournament(MatchDayRequest request){
        HashSet<TournamentParticipant> tournamentParticipants = addTeamsToTournament(request.tournamentCode(), request.teams());
        matchDayService.generateMatchDays(tournamentParticipants, request.tournamentCode());
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
                            .redCards(0)
                            .yellowCards(0)
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
                this.participantRepository.findAllByTournament(tournament).stream().map(this::participantToResponse).collect(Collectors.toSet()),
                tournament.getMatchDays().stream().map(matchDay -> this.matchDayService.matchDayToResponse(matchDay)).collect(Collectors.toList())
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

    private ParticipantResponse participantToResponse(TournamentParticipant participant){
        return new ParticipantResponse(
                participant.getTeam().getId(),
                participant.getTeam().getName(),
                participant.getIsActive(),
                statisticsToResponse(participant.getStatistics()));
    }

    private StatisticsResponse statisticsToResponse(Statistics statistics) {
        return new StatisticsResponse(
                statistics.getPoints(),
                statistics.getMatchesPlayed(),
                statistics.getWins(),
                statistics.getDraws(),
                statistics.getLosses(),
                statistics.getGoalsFor(),
                statistics.getGoalsAgainst()
        );
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
