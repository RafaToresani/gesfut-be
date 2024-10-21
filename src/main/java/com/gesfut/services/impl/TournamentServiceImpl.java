package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.ParticipantResponse;
import com.gesfut.dtos.responses.StatisticsResponse;
import com.gesfut.dtos.responses.TournamentResponse;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.exceptions.TeamDisableException;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Statistics;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.models.user.UserEntity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    public String deleteTournamentByCode(String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        if (tournament.isEmpty()) return "El torneo no existe";
        verifyTournamentBelongsToManager(tournament.get(), user);

        participantRepository.deleteByTournamentId(tournament.get().getId());

        this.tournamentRepository.deleteByCode(UUID.fromString(code));
        return "Torneo eliminado exitosamente";
    }


    @Override
    public String addTeamToTournament(Long idTeam, String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        if(tournament.isEmpty()) return "El torneo no existe";
        verifyTournamentBelongsToManager(tournament.get(), user);

        Team team = teamService.getTeamByIdSecured(idTeam);
        if(this.participantRepository.existsByTournamentAndTeam(tournament.get(), team)) throw new ResourceAlreadyExistsException("El equipo ya está participando en el torneo");
        if(!team.getStatus()) throw new TeamDisableException("El equipo '"+ team.getName() + "' se encuentra deshabilitado.");
        Statistics statistics = generateStatistics();

        TournamentParticipant participant = TournamentParticipant
                .builder()
                .tournament(tournament.get())
                .team(team)
                .statistics(statistics)
                .isActive(true)
                .build();
        statistics.setParticipant(participant);
        this.participantRepository.save(participant);

        return "Equipo agregado exitosamente";
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
                .build();
    }
}
