package com.gesfut.services.impl;

import com.gesfut.models.matchDay.EEventType;
import com.gesfut.models.matchDay.Event;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;

import java.util.*;

/**
 * Revision critica #3/#4: sanciones calculadas en orden cronologico (fecha, id).
 * Una sancion se cumple en el siguiente partido del equipo (las fechas libres no cuentan).
 */
public final class DisciplineRules {
    private DisciplineRules() {}

    /** yellows = amarillas acumuladas desde la ultima sancion; remaining = partidos de suspension pendientes. */
    public record State(int yellows, int remaining) {
        public boolean suspended() { return remaining > 0; }
    }

    public static List<Match> chronological(Tournament t, Match stopBefore) {
        Comparator<Match> order = Comparator.comparingInt((Match m) -> m.getMatchDay().getNumberOfMatchDay()).thenComparing(Match::getId);
        return t.getMatchDays().stream().flatMap(d -> d.getMatches().stream())
                .filter(m -> Boolean.TRUE.equals(m.getIsFinished()) && !m.isByeMatch())
                .filter(m -> stopBefore == null || (!Objects.equals(m.getId(), stopBefore.getId()) && order.compare(m, stopBefore) < 0))
                .sorted(order).toList();
    }

    private static boolean plays(Match m, TournamentParticipant p) {
        return Objects.equals(m.getHomeTeam().getId(), p.getId()) || Objects.equals(m.getAwayTeam().getId(), p.getId());
    }

    /** Estado de cada jugador (por id de PlayerParticipant) despues de los partidos dados. */
    public static Map<Long, State> compute(Tournament t, List<Match> matches, Map<Long, List<Event>> eventsByMatch, int yellowLimit, int redMatches) {
        Map<Long, State> out = new HashMap<>();
        for (TournamentParticipant participant : t.getTeams()) {
            if (participant.getPlayerParticipants() == null) continue;
            for (PlayerParticipant player : participant.getPlayerParticipants()) {
                int yellows = 0, remaining = 0;
                for (Match m : matches) {
                    if (!plays(m, participant)) continue;
                    if (remaining > 0) remaining--; // este partido cumple la sancion
                    int y = 0, r = 0;
                    for (Event e : eventsByMatch.getOrDefault(m.getId(), List.of())) {
                        if (e.getPlayerParticipant() == null || !Objects.equals(e.getPlayerParticipant().getId(), player.getId())) continue;
                        if (e.getType() == EEventType.YELLOW_CARD) y += e.getQuantity();
                        if (e.getType() == EEventType.RED_CARD) r += e.getQuantity();
                    }
                    yellows += y;
                    int add = 0;
                    if (r > 0) { add = redMatches; yellows = 0; }
                    if (yellows >= yellowLimit) { add = Math.max(add, 1); yellows = 0; }
                    remaining += add;
                }
                out.put(player.getId(), new State(yellows, remaining));
            }
        }
        return out;
    }
}
