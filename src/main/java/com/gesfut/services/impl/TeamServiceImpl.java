package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.*;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.team.Player;
import com.gesfut.models.matchDay.Match;
import com.gesfut.repositories.MatchRepository;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.PlayerRepository;
import com.gesfut.repositories.TeamRepository;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.services.PlayerService;
import com.gesfut.services.TeamService;
import com.gesfut.services.TournamentParticipantService;
import com.gesfut.services.UserEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.gesfut.config.security.SecurityUtils.getCurrentUserEmail;

@Service
public class TeamServiceImpl implements TeamService {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlayerService playerService;
    @Autowired
    private TournamentParticipantService tournamentParticipantService;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private com.gesfut.repositories.PlayerParticipantRepository playerParticipantRepository;

    @Autowired
    private UserEntityService userService;

    @Autowired
    private TournamentParticipantRepository participantRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.gesfut.services.TournamentService tournamentService;

    @Override
    @Transactional
    public TeamResponse createTeam(TeamRequest request) {
        // "Free" es el rival de la fecha libre (equipo interno por usuario): ningun equipo real puede llamarse asi.
        if (request.name() != null && request.name().trim().equalsIgnoreCase("free"))
            throw new IllegalArgumentException("Ese nombre est\u00e1 reservado. Eleg\u00ed otro nombre para el equipo.");
        this.playerService.validatePlayers(request.players());
        UserEntity user = userService.findUserByEmail(getCurrentUserEmail());
        
        Team team = Team.builder()
                .name(request.name())
                .color(request.color())
                .user(user)
                .players(new HashSet<>())
                .tournaments(new HashSet<>())
                .status(true)
                .build();
        Team newTeam = teamRepository.save(team);
        if (request.players() != null) request.players().forEach(playerRequest -> playerService.createPlayer(playerRequest, team));
        return teamToResponse(newTeam);
    }

    @Override
    public void createDummyTeam(TeamRequest request, UserEntity user){
        this.playerService.validatePlayers(request.players());
        Team team = Team.builder()
                .name(request.name())
                .color(request.color())
                .user(user)
                .players(new HashSet<>())
                .tournaments(new HashSet<>())
                .status(true)
                .build();
        teamRepository.save(team);
        request.players().forEach(playerRequest -> {
            playerService.createPlayer(playerRequest, team);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public TeamResponse getTeamById(Long id) {
        Team team = getTeamByIdSecured(id); // Revision critica #14: solo el duenio
        return teamToResponse(team);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamResponse> getAllTeams() {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        List<Team> teams = this.teamRepository.findAllByUser(user);
        return teams.stream().map(team -> teamToResponse(team)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TeamDeletionResponse previewDeleteTeam(Long id) {
        Team team = getTeamByIdSecured(id);
        return deletionPlan(team, null);
    }

    @Override
    @Transactional
    public TeamDeletionResponse deleteTeam(Long id) {
        Team team = getTeamByIdSecured(id);
        TeamDeletionResponse plan = deletionPlan(team, null);
        if (plan.blockedReason() != null) throw new IllegalArgumentException(plan.blockedReason());
        // Plan 22/10 #2: torneos sin partidos jugados -> se lo quita y se regenera el fixture.
        for (TournamentParticipant p : participantRepository.findAllByTeamId(id)) {
            if (TeamDisableRules.regeneratesFixture(p)) tournamentService.removeParticipantAndRegenerate(p);
        }
        String regen = plan.regeneratedTournaments().isEmpty() ? "" : " Se regener\u00f3 el fixture de: " + String.join(", ", plan.regeneratedTournaments()) + ".";
        if ("DELETE".equals(plan.action())) {
            // El usuario tiene cascade ALL sobre sus equipos: si sigue en la coleccion, Hibernate cancela el borrado.
            if (team.getUser() != null && team.getUser().getTeams() != null) team.getUser().getTeams().remove(team);
            teamRepository.delete(team);
            return new TeamDeletionResponse(team.getId(), team.getName(), "DELETE", plan.ongoingTournaments(), plan.regeneratedTournaments(), null,
                    "Equipo '" + team.getName() + "' eliminado." + regen);
        }
        disableTeam(id, false);
        return new TeamDeletionResponse(team.getId(), team.getName(), "DISABLE", plan.ongoingTournaments(), plan.regeneratedTournaments(), null,
                "Equipo '" + team.getName() + "' deshabilitado. Sus resultados se conservan." + regen);
    }

    /** Si el equipo juega varios torneos, aclara de cual viene el bloqueo (eliminar/deshabilitar es global, afecta a todos). */
    private static void explain(TournamentParticipant p, boolean several, Runnable check) {
        try { check.run(); }
        catch (IllegalArgumentException e) {
            if (!several) throw e;
            throw new IllegalArgumentException("Este equipo tambi\u00e9n participa en el torneo '" + p.getTournament().getName() + "': " + e.getMessage());
        }
    }

    /** Plan 22/10 #2: torneos sin jugar -> se quita y se regenera; si no queda otro historial se borra, si no se deshabilita. */
    private TeamDeletionResponse deletionPlan(Team team, String message) {
        List<TournamentParticipant> participations = participantRepository.findAllByTeamId(team.getId());
        List<TournamentParticipant> regen = participations.stream().filter(TeamDisableRules::regeneratesFixture).toList();
        List<TournamentParticipant> others = participations.stream().filter(p -> !regen.contains(p)).toList();
        String action = others.isEmpty() ? "DELETE" : "DISABLE";
        List<String> ongoing = others.stream().filter(p -> DniRules.openTournament(p.getTournament()) && !Boolean.FALSE.equals(p.getIsActive()))
                .map(p -> p.getTournament().getName()).distinct().toList();
        List<String> regenNames = regen.stream().map(p -> p.getTournament().getName()).distinct().toList();
        String blocked = null;
        if (Match.isFreeParticipantTeam(team)) blocked = "La fecha libre no se puede eliminar.";
        else if ("DISABLE".equals(action) && Boolean.FALSE.equals(team.getStatus())) blocked = "El equipo ya est\u00e1 deshabilitado.";
        else {
            try {
                // Solo torneos abiertos: los finalizados/archivados son historial y no se tocan (antes bloqueaban con mensajes de otro torneo).
                boolean several = participations.stream().filter(p -> DniRules.openTournament(p.getTournament())).count() > 1;
                for (TournamentParticipant p : regen) explain(p, several, () -> TeamDisableRules.checkCanRemove(p));
                for (TournamentParticipant p : others) if (DniRules.openTournament(p.getTournament()) && !Boolean.FALSE.equals(p.getIsActive()))
                    explain(p, several, () -> TeamDisableRules.checkCanDisable(p));
            } catch (IllegalArgumentException e) { blocked = e.getMessage(); }
        }
        return new TeamDeletionResponse(team.getId(), team.getName(), action, ongoing, regenNames, blocked, message);
    }

    @Override
    @Transactional
    public String disableTeam(Long id, Boolean status) {
        if (status == null) throw new IllegalArgumentException("El estado es requerido.");
        Team team = getTeamByIdSecured(id);
        if (Match.isFreeParticipantTeam(team)) throw new IllegalArgumentException("La fecha libre no se puede modificar.");
        if (!status) {
            // Plan 22/10 #2: baja en torneos en curso. Primero se valida todo (playoffs) y despues se aplica.
            List<TournamentParticipant> ongoing = participantRepository.findAllByTeamId(id).stream()
                    .filter(p -> DniRules.openTournament(p.getTournament()) && !Boolean.FALSE.equals(p.getIsActive())).toList(); // archivados/finalizados: solo lectura
            ongoing.forEach(TeamDisableRules::checkCanDisable);
            for (TournamentParticipant p : ongoing) {
                List<Match> changed = TeamDisableRules.disableParticipant(p);
                participantRepository.save(p);
                matchRepository.saveAll(changed);
            }
        } else {
            // Plan 22/10 #2 (respuesta 2): vuelve a los torneos en curso donde estaba de baja.
            for (TournamentParticipant p : participantRepository.findAllByTeamId(id)) {
                if (!DniRules.openTournament(p.getTournament())) continue; // archivados/finalizados: solo lectura
                List<Match> changed = TeamDisableRules.enableParticipant(p);
                if (changed == null) continue;
                participantRepository.save(p);
                matchRepository.saveAll(changed);
            }
        }
        this.teamRepository.updateTeamStatus(id, status);
        this.playerService.updateStatusPlayersByTeam(id, status);
        String valueStatus  = "habilitado";
        if(!status) valueStatus = "deshabilitado";
        return team.getName() + " ha sido " + valueStatus;
    }

    @Override
    @Transactional // Bug retest (A): sin transaccion, team.getPlayers() fallaba con LazyInitializationException (500) al mandar DNI.
    public Player addPlayerToTeam(Long teamId, PlayerRequest request) {
        Team team = getTeamByIdSecured(teamId);
        this.playerService.verifyPlayer(request, team);
        DniRules.requireDniFreeInTournaments(request.dni(), request.name() + " " + request.lastName(), team);
       return this.playerService.createPlayer(request, team);
    }

    @Override
    public Team getTeamByName() {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        // Busca exactamente el equipo interno "Free" (Match.isFreeParticipantTeam); si quedara un equipo viejo "FREE"/"free"
        // del usuario, no se confunde con la fecha libre ni rompe por resultado duplicado.
        return this.teamRepository.findAllByUser(user).stream().filter(Match::isFreeParticipantTeam).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Equipo free no existe."));
    }

    @Override
    public void changeStatusPlayer(Long idPlayer, Boolean status) {
        Optional<Player> optPlayer = this.playerRepository.findById(idPlayer);
        if(optPlayer.isEmpty()) throw new ResourceNotFoundException("El jugador no existe.");

        getTeamByIdSecured(optPlayer.get().getTeam().getId());
        if (!status){
            // Item 3: los torneos archivados/finalizados son solo lectura; su historial no se toca.
            optPlayer.get().getPlayerParticipants().stream()
                    .filter(pp -> pp.getTournamentParticipant() != null && DniRules.openTournament(pp.getTournamentParticipant().getTournament()))
                    .forEach(playerParticipant -> {
                this.tournamentParticipantService.changeStatusPlayerParticipant(playerParticipant.getId(), status);
            });
        }
        this.playerRepository.updatePlayerStatusById(idPlayer, status);
    }

    /**
     * Editar jugador (solo el duenio). Cambia datos del plantel; goles/tarjetas siguen en el mismo
     * jugador porque no se crea uno nuevo. Mismas validaciones que el alta y bloqueo de DNI entre equipos.
     */
    @Override
    @Transactional
    public com.gesfut.dtos.responses.PlayerResponse editPlayer(Long idPlayer, com.gesfut.dtos.requests.PlayerEditRequest request) {
        Player player = this.playerRepository.findById(idPlayer).orElseThrow(() -> new ResourceNotFoundException("El jugador no existe."));
        if (player.getTeam() == null) throw new ResourceNotFoundException("El jugador no existe.");
        Team team = getTeamByIdSecured(player.getTeam().getId());
        if (com.gesfut.models.matchDay.Match.isFreeParticipantTeam(team)) throw new IllegalArgumentException("No se puede editar este jugador.");
        String name = request.name() == null ? null : request.name().trim();
        String lastName = request.lastName() == null ? null : request.lastName().trim();
        if (name == null || lastName == null || name.length() < 3 || lastName.length() < 3 || name.length() > 50 || lastName.length() > 50)
            throw new IllegalArgumentException("Nombre y apellido deben tener entre 3 y 50 caracteres.");
        if (request.number() == null || request.number() < 0 || request.number() > 99)
            throw new IllegalArgumentException("El dorsal debe estar entre 0 y 99.");
        String dni = com.gesfut.services.impl.DniRules.normalize(request.dni());
        for (Player other : team.getPlayers()) {
            if (other == player || java.util.Objects.equals(other.getId(), player.getId())) continue;
            if (dni != null && dni.equals(DniRules.key(other.getDni()))) throw new IllegalArgumentException("Ya hay un jugador con DNI " + dni + " en este equipo.");
            if (!Boolean.FALSE.equals(other.getStatus()) && java.util.Objects.equals(other.getNumber(), request.number()))
                throw new IllegalArgumentException("El dorsal " + request.number() + " ya lo usa " + other.getName() + " " + other.getLastName() + ".");
        }
        DniRules.requireDniFreeInTournaments(dni, name + " " + lastName, team);
        player.setName(name);
        player.setLastName(lastName);
        player.setNumber(request.number());
        player.setDni(dni);
        if (dni != null && team.getTournaments() != null) {
            for (var participation : team.getTournaments()) {
                if (participation.getTournament() == null || participation.getTournament().getId() == null) continue;
                DniRules.requireNotInOtherTeam(this.playerParticipantRepository.findAllByTournamentParticipant_Tournament_IdAndPlayer_Dni(participation.getTournament().getId(), dni), player, team);
            }
        }
        return this.playerService.playerToResponse(this.playerRepository.save(player));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantShortResponse> getTeamTournamentsParticipations(Long id) {
        getTeamByIdSecured(id); // Revision critica #14: solo el duenio
        return this.tournamentParticipantService.getTeamTournamentsParticipations(id);
    }

    @Override
    public Player getPlayerName(String name, String lastName, Long teamId){
        return this.playerRepository.findByNameAndLastNameAndTeamId(name,lastName,teamId);
    }

    @Override
    @Transactional(readOnly = true)
    public TeamWithAllStatsPlayerResponse getAllPlayerStatsByTeam(Long idTeam) {
        Optional<Team> teamFound = Optional.of(getTeamByIdSecured(idTeam)); // Revision critica #14: solo el duenio

        Team team = teamFound.get();
        TeamWithAllStatsPlayerResponse response = new TeamWithAllStatsPlayerResponse(
                idTeam,
                team.getName(),
                team.getStatus(),
                new HashSet<>()
        );

        // Mapa para acumular estadísticas por jugador
        Map<Long, PlayerParticipantResponse> playerStatsMap = new HashMap<>();

        // 1️⃣ Agregar todos los jugadores del equipo al mapa con estadísticas en 0
        for (Player player : team.getPlayers()) {
            playerStatsMap.put(player.getId(), new PlayerParticipantResponse(
                    0L,
                    player.getNumber(),
                    false,
                    0, // Goles iniciales
                    0, // Tarjetas rojas
                    0, // Tarjetas amarillas
                    0, // MVPs
                    player.getId(),
                    player.getName(),
                    player.getLastName(),
                    true,
                    player.getIsCaptain(),
                    player.getIsGoalKeeper(),
                    player.getStatus()
            ));
        }

        // 2️⃣ Recorrer los torneos del equipo para actualizar estadísticas
        for (TournamentParticipant tournament : team.getTournaments()) {
            for (PlayerParticipant participant : tournament.getPlayerParticipants()) {
                Long playerId = participant.getPlayer().getId();

                PlayerParticipantResponse currentStats = playerStatsMap.get(playerId);

                // ✅ Crear un nuevo objeto con los valores actualizados
                PlayerParticipantResponse updatedStats = new PlayerParticipantResponse(
                        participant.getPlayer().getId(),
                        currentStats.shirtNumber(),
                        participant.getIsSuspended(),
                        currentStats.goals() + participant.getGoals(), // Sumar goles
                        currentStats.redCards() + participant.getRedCards(), // Sumar rojas
                        currentStats.yellowCards() + participant.getYellowCards(), // Sumar amarillas
                        currentStats.isMvp() + participant.getIsMvp(), // Sumar MVPs
                        currentStats.playerId(),
                        currentStats.playerName(),
                        currentStats.playerLastName(),
                        participant.getIsActive(),
                        currentStats.isCaptain(),
                        currentStats.isGoalKeeper(),
                        participant.getPlayer().getStatus()
                );

                // ✅ Reemplazar en el mapa
                playerStatsMap.put(playerId, updatedStats);
            }
        }

        // 3️⃣ Agregar resultados a la respuesta
        response.playerParticipants().addAll(playerStatsMap.values());

        return response;
    }

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired
    private jakarta.validation.Validator validator;

    public static final int MAX_TEAMS_PER_IMPORT = 200;

    /**
     * Plan 22/10 #15: import parcial. Cada equipo se crea en su propia transaccion (todo o nada por equipo):
     * los validos quedan creados y los que fallan se informan con su motivo, sin dejar equipos a medias.
     */
    @Override
    public TeamImportResponse createMultipleTeams(List<TeamRequest> request) {
        if (request == null || request.isEmpty()) throw new IllegalArgumentException("No hay equipos para importar.");
        if (request.size() > MAX_TEAMS_PER_IMPORT) throw new IllegalArgumentException("Se pueden importar hasta " + MAX_TEAMS_PER_IMPORT + " equipos por archivo.");
        userService.findUserByEmail(getCurrentUserEmail());
        org.springframework.transaction.support.TransactionTemplate tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        List<String> created = new ArrayList<>();
        List<TeamImportResponse.Failure> failed = new ArrayList<>();
        for (int i = 0; i < request.size(); i++) {
            TeamRequest team = request.get(i);
            String name = team == null ? null : team.name();
            try {
                if (team == null) throw new IllegalArgumentException("Equipo vac\u00edo.");
                Set<jakarta.validation.ConstraintViolation<TeamRequest>> violations = validator.validate(team);
                if (!violations.isEmpty()) throw new IllegalArgumentException(violations.stream().map(jakarta.validation.ConstraintViolation::getMessage).sorted().distinct().reduce((a, b) -> a + "; " + b).orElse("Datos inv\u00e1lidos."));
                tx.executeWithoutResult(status -> createTeam(team));
                created.add(name);
            } catch (RuntimeException e) {
                failed.add(new TeamImportResponse.Failure(i, name, e.getMessage() == null ? "No se pudo crear el equipo." : e.getMessage()));
            }
        }
        return new TeamImportResponse(created.size(), created, failed);
    }


    @Override
    public Team getTeamByIdSecured(Long id) {
        Team team = teamById(id);
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        verifyTeamBelongsToManager(team, user);
        return team;
    }

    private TeamResponse teamToResponse(Team team){
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getColor(),
                team.getStatus(),
                playerService.playersToResponse(team.getPlayers()));

    }
    private void verifyTeamBelongsToManager(Team team, UserEntity user){
        if(team.getUser() == null || user == null || !java.util.Objects.equals(team.getUser().getId(), user.getId())) throw new org.springframework.security.access.AccessDeniedException("El equipo no pertenece a este usuario.");
    }
    private Team teamById(Long id){
        Optional<Team> team = this.teamRepository.findById(id);
        if (team.isEmpty()) throw new ResourceNotFoundException("El equipo no existe.");
        return team.get();
    }
}
