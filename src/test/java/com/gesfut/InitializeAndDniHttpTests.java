package com.gesfut;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.TeamService;
import com.gesfut.services.TournamentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Integracion real por HTTP (sin mocks) contra la base de regresion: inicializar con cantidad impar (Free) y par,
 * DNI repetido al sumar equipo / jugador, add-player con DNI (antes 500) y torneo archivado de solo lectura.
 * Crea su propio torneo y equipos y los borra al final.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.open-in-view=false" // como en produccion: sin sesion abierta en el controller
})
@AutoConfigureMockMvc
class InitializeAndDniHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TeamService teams;
    @Autowired TournamentService tournaments;
    @Autowired UserRepository users;
    @Autowired com.gesfut.config.security.jwt.JwtService jwt;
    final String prefix = "TEST INIT " + System.nanoTime() % 100000;
    UserEntity owner; String token; String code;
    final Map<String, Long> ids = new HashMap<>();

    void auth() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities())); }

    @BeforeEach void setUp() throws Exception {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        long uid = jdbc.queryForObject("SELECT user_id FROM teams WHERE LOWER(name)='free' AND user_id IS NOT NULL ORDER BY id LIMIT 1", Long.class);
        owner = users.findById(uid).orElseThrow();
        token = "Bearer " + jwt.getToken(owner, owner.getAuthorities());
        var r = call(post("/api/v1/tournaments").content("{\"name\":\"" + prefix + "\"}"));
        assertTrue(r.getResponse().getStatus() < 300, r.getResponse().getContentAsString());
        code = jdbc.queryForObject("SELECT BIN_TO_UUID(code) FROM tournaments WHERE name=?", String.class, prefix);
        auth();
        String[] dnis = {"40111222", "40333444", "40555666", "40777888", "40999000"};
        for (int i = 0; i < 5; i++) {
            String n = prefix + " " + (char) ('A' + i);
            teams.createTeam(new TeamRequest(n, "#000000", new LinkedHashSet<>(List.of(
                    new PlayerRequest("Capi" + i, "Arquero", true, true, 1, dnis[i]),
                    new PlayerRequest("Otro" + i, "Jugador", false, false, 2)))));
            ids.put(String.valueOf((char) ('A' + i)), jdbc.queryForObject("SELECT id FROM teams WHERE name=?", Long.class, n));
        }
    }

    @AfterEach void clean() {
        auth();
        jdbc.update("UPDATE tournaments SET is_active=1, is_finished=0 WHERE name=?", prefix);
        // Datos de prueba de partidos jugados: se limpian para que el torneo se borre (y no quede archivado).
        jdbc.update("DELETE e FROM events e JOIN matches m ON m.id=e.match_id JOIN match_days md ON md.id=m.match_day_id JOIN tournaments t ON t.id=md.tournament_id WHERE t.name=?", prefix);
        jdbc.update("UPDATE matches m JOIN match_days md ON md.id=m.match_day_id JOIN tournaments t ON t.id=md.tournament_id SET m.is_finished=0, m.mvp_player=NULL WHERE t.name=?", prefix);
        jdbc.update("DELETE p FROM prizes p JOIN tournaments t ON t.id=p.tournament_id WHERE t.name=?", prefix);
        try { tournaments.deleteTournament(code); } catch (Exception ignored) { }
        jdbc.update("DELETE pp FROM player_participants pp JOIN players p ON p.id=pp.player_id JOIN teams t ON t.id=p.team_id WHERE t.name LIKE ?", prefix + "%");
        jdbc.update("DELETE p FROM players p JOIN teams t ON t.id=p.team_id WHERE t.name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM teams WHERE name LIKE ?", prefix + "%");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tournaments WHERE name=?", Long.class, prefix));
        SecurityContextHolder.clearContext();
    }

    MvcResult call(MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)).andReturn();
    }
    String body(MvcResult r) throws Exception { return r.getResponse().getContentAsString(StandardCharsets.UTF_8); }
    MvcResult initialize(String... teamKeys) throws Exception {
        String list = String.join(",", Arrays.stream(teamKeys).map(k -> String.valueOf(ids.get(k))).toList());
        return call(post("/api/v1/tournaments/initialize").content("{\"tournamentCode\":\"" + code + "\",\"teams\":[" + list + "]}"));
    }
    long participants() { return jdbc.queryForObject("SELECT COUNT(*) FROM tournament_participants tp JOIN tournaments t ON t.id=tp.tournament_id WHERE t.name=?", Long.class, prefix); }
    long matchDays() { return jdbc.queryForObject("SELECT COUNT(*) FROM match_days md JOIN tournaments t ON t.id=md.tournament_id WHERE t.name=?", Long.class, prefix); }
    long participantId(String key) { return jdbc.queryForObject("SELECT tp.id FROM tournament_participants tp JOIN tournaments t ON t.id=tp.tournament_id WHERE t.name=? AND tp.team_id=?", Long.class, prefix, ids.get(key)); }

    @Test void imparConFreeInicializa() throws Exception {
        var r = initialize("A", "B", "C");
        assertEquals(200, r.getResponse().getStatus(), body(r));
        assertEquals(4, participants()); // 3 + Free
        assertTrue(matchDays() > 0);
    }

    @Test void parInicializa() throws Exception {
        var r = initialize("A", "B", "C", "D");
        assertEquals(200, r.getResponse().getStatus(), body(r));
        assertEquals(4, participants());
        assertTrue(matchDays() > 0);
    }

    @Test void revisarDetectaDniViejoSinNormalizar() throws Exception {
        jdbc.update("UPDATE players SET dni='40.111.222' WHERE team_id=? AND dni IS NOT NULL", ids.get("B"));
        var r = call(post("/api/v1/tournaments/check-teams").content("{\"tournamentCode\":\"" + code + "\",\"teams\":[" + ids.get("A") + "," + ids.get("B") + "]}"));
        assertEquals(400, r.getResponse().getStatus(), body(r));
        assertTrue(body(r).contains("40111222"), body(r));
    }

    @Test void sumarEquipoConDniRepetidoDa400() throws Exception {
        assertEquals(200, initialize("A", "B", "C").getResponse().getStatus());
        jdbc.update("UPDATE players SET dni='40111222' WHERE team_id=? AND dni IS NOT NULL", ids.get("D"));
        var r = call(post("/api/v1/tournaments/" + code + "/add-team").content("{\"tournamentCode\":\"" + code + "\",\"teams\":[" + ids.get("D") + "]}"));
        assertEquals(400, r.getResponse().getStatus(), body(r));
        assertTrue(body(r).contains("40111222"), body(r));
        assertEquals(4, participants());
    }

    @Test void addPlayerConDniNoDa500YControlaRepetidos() throws Exception {
        assertEquals(200, initialize("A", "B", "C", "D").getResponse().getStatus());
        String url = "/api/v1/team-participant/" + code + "/add-player/" + participantId("A");
        var ok = call(put(url).content("{\"name\":\"Nuevo\",\"lastName\":\"ConDni\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":30,\"dni\":\"41.222.333\"}"));
        assertEquals(200, ok.getResponse().getStatus(), body(ok));
        var dup = call(put(url).content("{\"name\":\"Otro\",\"lastName\":\"Repetido\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":31,\"dni\":\"40333444\"}"));
        assertEquals(400, dup.getResponse().getStatus(), body(dup));
        assertTrue(body(dup).contains("40333444") && body(dup).contains(prefix + " B"), body(dup));
        var noDni = call(put(url).content("{\"name\":\"Sin\",\"lastName\":\"Documento\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":33}"));
        assertEquals(200, noDni.getResponse().getStatus(), body(noDni));
        var team = call(put("/api/v1/teams/add-player/" + ids.get("A")).content("{\"name\":\"Tercero\",\"lastName\":\"Repetido\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":32,\"dni\":\"40-555-666\"}"));
        assertEquals(400, team.getResponse().getStatus(), body(team));
    }

    @Test void archivadoNoPermiteCambiarEstadoDeJugador() throws Exception {
        assertEquals(200, initialize("A", "B", "C", "D").getResponse().getStatus());
        long pp = jdbc.queryForObject("SELECT pp.id FROM player_participants pp WHERE pp.tournament_participant_id=? LIMIT 1", Long.class, participantId("A"));
        jdbc.update("UPDATE tournaments SET is_active=0 WHERE name=?", prefix);
        var r = call(put("/api/v1/team-participant/change-status/" + pp + "/false"));
        assertEquals(409, r.getResponse().getStatus(), body(r));
        jdbc.update("UPDATE tournaments SET is_active=1, is_finished=1 WHERE name=?", prefix);
        assertEquals(409, call(put("/api/v1/team-participant/change-status/" + pp + "/false")).getResponse().getStatus());
    }
    @Test void cincoEquiposInicializaConFree() throws Exception {
        var r = initialize("A", "B", "C", "D", "E");
        assertEquals(200, r.getResponse().getStatus(), body(r));
        assertEquals(6, participants()); // 5 + Free
    }

    @Test void equipoLlamadoFreeSeRechaza() throws Exception {
        for (String n : new String[]{"Free", " FREE ", "free"}) {
            var r = call(post("/api/v1/teams/create").content("{\"name\":\"" + n + "\",\"color\":\"#000000\",\"players\":[]}"));
            assertEquals(400, r.getResponse().getStatus(), body(r));
            assertTrue(body(r).contains("reservado"), body(r));
        }
    }

    @Test void deshabilitarYHabilitarEquipoNoTocaTorneoArchivado() throws Exception {
        assertEquals(200, initialize("A", "B", "C", "D").getResponse().getStatus());
        jdbc.update("UPDATE tournaments SET is_active=0 WHERE name=?", prefix);
        long tp = participantId("A");
        Object before = jdbc.queryForObject("SELECT is_active FROM tournament_participants WHERE id=?", Object.class, tp);
        assertEquals(200, call(put("/api/v1/teams/change-status-team/" + ids.get("A") + "/false")).getResponse().getStatus());
        assertEquals(before, jdbc.queryForObject("SELECT is_active FROM tournament_participants WHERE id=?", Object.class, tp));
        assertEquals(200, call(put("/api/v1/teams/change-status-team/" + ids.get("A") + "/true")).getResponse().getStatus());
        assertEquals(before, jdbc.queryForObject("SELECT is_active FROM tournament_participants WHERE id=?", Object.class, tp));
    }

    long playersOf(String key) { return jdbc.queryForObject("SELECT COUNT(*) FROM players WHERE team_id=?", Long.class, ids.get(key)); }

    long count(String sql, Object... a) { return jdbc.queryForObject(sql, Long.class, a); }

    @Test void addPlayerEnTorneoFinalizadoNoEscribeNada() throws Exception {
        assertEquals(200, initialize("A", "B", "C", "D").getResponse().getStatus());
        long players = count("SELECT COUNT(*) FROM players WHERE team_id=?", ids.get("A"));
        long playersAll = count("SELECT COUNT(*) FROM players");
        long pps = count("SELECT COUNT(*) FROM player_participants WHERE tournament_participant_id=?", participantId("A"));
        long ppsAll = count("SELECT COUNT(*) FROM player_participants");
        long tps = participants();
        jdbc.update("UPDATE tournaments SET is_finished=1 WHERE name=?", prefix);
        var r = call(put("/api/v1/team-participant/" + code + "/add-player/" + participantId("A")).content("{\"name\":\"Nuevo\",\"lastName\":\"Rechazado\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":40,\"dni\":\"42111999\"}"));
        assertEquals(409, r.getResponse().getStatus(), body(r));
        assertEquals(players, count("SELECT COUNT(*) FROM players WHERE team_id=?", ids.get("A")), "no debe quedar el jugador en el equipo global");
        assertEquals(playersAll, count("SELECT COUNT(*) FROM players"));
        assertEquals(pps, count("SELECT COUNT(*) FROM player_participants WHERE tournament_participant_id=?", participantId("A")));
        assertEquals(ppsAll, count("SELECT COUNT(*) FROM player_participants"));
        assertEquals(tps, participants());
    }

    @Test void addPlayerPorRutaDeEquipoDevuelveMensajeConDniYEquipo() throws Exception {
        assertEquals(200, initialize("A", "B", "C", "D").getResponse().getStatus());
        var r = call(put("/api/v1/teams/add-player/" + ids.get("A")).content("{\"name\":\"Dup2\",\"lastName\":\"Dni\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":42,\"dni\":\"40333444\"}"));
        assertEquals(400, r.getResponse().getStatus(), body(r));
        assertTrue(body(r).contains("\"error\"") && body(r).contains("40333444") && body(r).contains(prefix + " B"), body(r));
    }

    @Test void equipoConHistorialEnTorneoFinalizadoSePuedeDeshabilitar() throws Exception {
        assertEquals(200, initialize("A", "B", "C", "D").getResponse().getStatus());
        jdbc.update("UPDATE tournaments SET is_finished=1 WHERE name=?", prefix);
        long tp = participantId("A");
        Map<String, Object> tpBefore = jdbc.queryForMap("SELECT * FROM tournament_participants WHERE id=?", tp);
        long ppBefore = count("SELECT COUNT(*) FROM player_participants WHERE tournament_participant_id=?", tp);
        String ppStateBefore = jdbc.queryForList("SELECT CONCAT(id,':',COALESCE(is_active,'-')) FROM player_participants WHERE tournament_participant_id=? ORDER BY id", String.class, tp).toString();
        var p = call(get("/api/v1/teams/" + ids.get("A") + "/delete-preview"));
        assertEquals(200, p.getResponse().getStatus(), body(p));
        assertFalse(body(p).contains("participa en el torneo"), body(p));
        var d = call(put("/api/v1/teams/change-status-team/" + ids.get("A") + "/false"));
        assertEquals(200, d.getResponse().getStatus(), body(d));
        assertEquals(tpBefore, jdbc.queryForMap("SELECT * FROM tournament_participants WHERE id=?", tp), "el participante del torneo finalizado no cambia");
        assertEquals(ppBefore, count("SELECT COUNT(*) FROM player_participants WHERE tournament_participant_id=?", tp));
        assertEquals(ppStateBefore, jdbc.queryForList("SELECT CONCAT(id,':',COALESCE(is_active,'-')) FROM player_participants WHERE tournament_participant_id=? ORDER BY id", String.class, tp).toString());
    }

    /** Deja la fecha 1 jugada (todos los partidos cerrados, un MVP por partido) y devuelve su id. */
    long playFirstRound() {
        long md = jdbc.queryForObject("SELECT md.id FROM match_days md JOIN tournaments t ON t.id=md.tournament_id WHERE t.name=? ORDER BY md.number_of_match_day LIMIT 1", Long.class, prefix);
        jdbc.update("UPDATE matches SET is_finished=1, goals_home_team=1, goals_away_team=0, mvp_player=CONCAT('MVP Test ', id) WHERE match_day_id=?", md);
        // Un gol, una amarilla y una roja en el primer partido real (para goleadores/tarjetas).
        long match = jdbc.queryForObject("SELECT id FROM matches WHERE match_day_id=? ORDER BY id LIMIT 1", Long.class, md);
        long pp = jdbc.queryForObject("SELECT pp.id FROM player_participants pp JOIN matches m ON m.home_team_id=pp.tournament_participant_id WHERE m.id=? ORDER BY pp.id LIMIT 1", Long.class, match);
        for (int type = 0; type <= 2; type++) // GOAL, YELLOW_CARD, RED_CARD (ordinal)
            jdbc.update("INSERT INTO events (type, quantity, player_participant_id, match_id) VALUES (?, 1, ?, ?)", type, pp, match);
        return md;
    }
    String firstMvp(long md) { return jdbc.queryForObject("SELECT mvp_player FROM matches WHERE match_day_id=? ORDER BY id LIMIT 1", String.class, md); }
    MvcResult close(long md, String mvp) throws Exception {
        var b = put("/api/v1/match-days/close").param("matchDayId", String.valueOf(md)).param("status", "true");
        if (mvp != null) b = b.param("playerMvp", mvp);
        return call(b);
    }

    @Test void cerrarFechaValidaElMvp() throws Exception {
        assertEquals(200, initialize("A", "B", "C", "D").getResponse().getStatus());
        long md = playFirstRound();
        var bad = close(md, "Cualquiera Inventado");
        assertEquals(400, bad.getResponse().getStatus(), body(bad));
        assertTrue(body(bad).contains("MVP"), body(bad));
        assertEquals(0, count("SELECT COUNT(*) FROM match_days WHERE id=? AND is_finished=1", md));
        var empty = close(md, "");
        assertEquals(200, empty.getResponse().getStatus(), body(empty));
        jdbc.update("UPDATE match_days SET is_finished=0 WHERE id=?", md);
        var none = close(md, null);
        assertEquals(200, none.getResponse().getStatus(), body(none));
        jdbc.update("UPDATE match_days SET is_finished=0 WHERE id=?", md);
        String mvp = firstMvp(md);
        var ok = close(md, " " + mvp + " ");
        assertEquals(200, ok.getResponse().getStatus(), body(ok));
        assertEquals(mvp, jdbc.queryForObject("SELECT mvp_player FROM match_days WHERE id=?", String.class, md));
        jdbc.update("UPDATE match_days SET is_finished=0 WHERE id=?", md);
        assertEquals(400, close(md, mvp.toUpperCase().equals(mvp) ? mvp.toLowerCase() : mvp.toUpperCase()).getResponse().getStatus(), "distingue mayusculas");
    }

    /** Recorre TODOS los GET permitAll de SecurityConfig, sin token, con la fecha 1 jugada y cerrada con MVP. */
    @Test void vistaPublicaNoExponeDniNiEmails() throws Exception {
        assertEquals(200, initialize("A", "B", "C", "D").getResponse().getStatus());
        jdbc.update("UPDATE players SET dni='40111222' WHERE team_id=? AND dni IS NOT NULL", ids.get("A"));
        long md = playFirstRound();
        assertEquals(200, close(md, firstMvp(md)).getResponse().getStatus());
        var pr = call(post("/api/v1/prizes").content("{\"code\":\"" + code + "\",\"prizes\":[{\"type\":\"POSITION\",\"description\":\"Copa\",\"position\":1},{\"type\":\"FAIR_PLAY\",\"description\":\"Medalla\",\"position\":1},{\"type\":\"TOP_SCORER\",\"description\":\"Botin\",\"position\":1}]}"));
        assertEquals(201, pr.getResponse().getStatus(), body(pr));
        String ownerEmail = owner.getUsername();
        long pid = participantId("A");
        Map<String, Integer> urls = new LinkedHashMap<>();
        for (String u : new String[]{
                "/api/v1/tournaments/" + code, "/api/v1/tournaments/short/" + code,
                "/api/v1/tournaments/" + code + "/matches/" + pid,
                "/api/v1/tournaments/" + code + "/top-scorers", "/api/v1/tournaments/" + code + "/top-yellow-cards", "/api/v1/tournaments/" + code + "/top-red-cards",
                "/api/v1/match-days/" + code, "/api/v1/match-days/" + code + "/lastPlayed",
                "/api/v1/prizes/" + code, "/api/v1/prizes/" + code + "/POSITION", "/api/v1/prizes/" + code + "/FAIR_PLAY", "/api/v1/prizes/" + code + "/TOP_SCORER",
                "/api/v1/team-participant/" + code + "/teams"}) urls.put(u, 200);
        // Caen en el comodin GET /tournaments/* pero no son publicos: exigen sesion (401 sin token).
        urls.put("/api/v1/tournaments/short", 401);
        urls.put("/api/v1/tournaments/exist", 401);
        for (var e : urls.entrySet()) {
            String url = e.getKey();
            var r = mvc.perform(get(url)).andReturn(); // sin token
            String b = body(r), low = b.toLowerCase();
            assertEquals(e.getValue(), r.getResponse().getStatus(), url + " " + b);
            if (e.getValue() == 200) assertFalse(b.isBlank() || b.equals("[]"), "sin datos en " + url);
            assertFalse(low.contains("\"dni\"") || b.contains("40111222") || b.contains("40333444"), "DNI en " + url + ": " + b);
            assertFalse(low.contains("email") || b.contains(ownerEmail) || b.matches("(?s).*[\\w.+-]+@[\\w-]+\\.[\\w.]+.*"), "email en " + url + ": " + b);
            assertFalse(low.contains("phone") || low.contains("telefono") || low.contains("password") || low.contains("token"), "dato sensible en " + url + ": " + b);
        }
        assertTrue(body(mvc.perform(get("/api/v1/match-days/" + code + "/lastPlayed")).andReturn()).contains(firstMvp(md)), "lastPlayed devuelve la fecha jugada");
    }

    @Test void rutaPublicaConTokenBasuraSigueRespondiendo() throws Exception {
        for (String t : new String[]{"Bearer basura.no.firmada", "Bearer ", "Basura"}) {
            var r = mvc.perform(get("/api/v1/tournaments/" + code).header("Authorization", t)).andReturn();
            assertEquals(200, r.getResponse().getStatus(), t + " " + body(r));
        }
    }

    /** Review 24/10: sin token / token basura -> 401 (el front cierra sesion); con token valido pero sin permiso -> 403. */
    @Test void codigosDeSeguridad401y403() throws Exception {
        assertEquals(401, mvc.perform(get("/api/v1/teams")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/api/v1/teams").header("Authorization", "Bearer basura.no.firmada")).andReturn().getResponse().getStatus());
        String forged = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertEquals(401, mvc.perform(get("/api/v1/teams").header("Authorization", forged)).andReturn().getResponse().getStatus());
        assertEquals(200, call(get("/api/v1/teams")).getResponse().getStatus());
        Long other = jdbc.query("SELECT id FROM teams WHERE user_id IS NOT NULL AND user_id<>? ORDER BY id LIMIT 1", rs -> rs.next() ? rs.getLong(1) : null, owner.getId());
        Assumptions.assumeTrue(other != null, "La base de regresion no tiene equipos de otro usuario: no se puede probar el 403.");
        assertEquals(403, call(get("/api/v1/teams/" + other)).getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/v1/tournaments/" + code)).andReturn().getResponse().getStatus());
    }

    @Test void loginConClaveIncorrectaDa401() throws Exception {
        for (String email : new String[]{owner.getUsername(), "nadie-test@example.invalid"}) { // misma respuesta: no revela si la cuenta existe
            var login = mvc.perform(post("/api/v1/auth/login").with(r -> { r.setRemoteAddr("10.9.9." + Math.abs(email.hashCode() % 200)); return r; })
                    .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"password\":\"Clave-Incorrecta-123\"}")).andReturn();
            assertEquals(401, login.getResponse().getStatus(), body(login));
            assertFalse(body(login).toLowerCase().contains("token"), body(login));
        }
    }
}
