package com.gesfut.services.impl;

import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Plan 22/10 #2: reglas para dar de baja un equipo de un torneo en curso. */
public final class TeamDisableRules {
    private TeamDisableRules() {}

    public static boolean isOngoing(Tournament t) {
        return t != null && !Boolean.TRUE.equals(t.getIsFinished());
    }

    /** En playoffs no se sabe que hacer con el cruce: se bloquea con un mensaje claro. */
    public static void checkCanDisable(TournamentParticipant p) {
        Tournament t = p.getTournament();
        if (!isOngoing(t) || t.getMatchDays() == null) return;
        boolean playoffs = t.getMatchDays().stream().anyMatch(d -> Boolean.TRUE.equals(d.getIsPlayOff()));
        if (playoffs) throw new IllegalArgumentException("No se puede deshabilitar el equipo mientras el torneo '" + t.getName()
                + "' est\u00e1 en playoffs. Esper\u00e1 a que termine el torneo.");
    }

    private static boolean involves(Match m, TournamentParticipant p) {
        return (m.getHomeTeam() != null && Objects.equals(m.getHomeTeam().getId(), p.getId()))
                || (m.getAwayTeam() != null && Objects.equals(m.getAwayTeam().getId(), p.getId()))
                || m.getHomeTeam() == p || m.getAwayTeam() == p;
    }

    /** Desactiva al participante y marca como fecha libre sus partidos pendientes. Devuelve los partidos tocados. */
    public static List<Match> disableParticipant(TournamentParticipant p) {
        p.setIsActive(false);
        List<Match> changed = new ArrayList<>();
        Tournament t = p.getTournament();
        if (!isOngoing(t) || t.getMatchDays() == null) return changed;
        for (MatchDay day : t.getMatchDays()) {
            if (Boolean.TRUE.equals(day.getIsFinished()) || day.getMatches() == null) continue;
            for (Match m : day.getMatches()) {
                if (Boolean.TRUE.equals(m.getIsFinished()) || !involves(m, p)) continue;
                m.setBye(true);
                changed.add(m);
            }
        }
        return changed;
    }
}
