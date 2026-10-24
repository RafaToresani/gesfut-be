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

    /** Plan 22/10 #2: el torneo ya arranco si hay algun partido jugado, una fecha cerrada o playoffs. */
    public static boolean hasStarted(Tournament t) {
        if (t == null || t.getMatchDays() == null) return false;
        return t.getMatchDays().stream().anyMatch(d -> Boolean.TRUE.equals(d.getIsFinished()) || Boolean.TRUE.equals(d.getIsPlayOff())
                || (d.getMatches() != null && d.getMatches().stream().anyMatch(m -> Boolean.TRUE.equals(m.getIsFinished()) && !m.isByeMatch())));
    }

    /** Torneo en curso que todavia no arranco: se quita al equipo y se regenera el fixture. */
    public static boolean regeneratesFixture(TournamentParticipant p) {
        return DniRules.openTournament(p.getTournament()) && !hasStarted(p.getTournament()) && !Boolean.FALSE.equals(p.getIsActive());
    }

    /** Minimo 2 equipos reales despues de quitarlo. */
    public static void checkCanRemove(TournamentParticipant p) {
        Tournament t = p.getTournament();
        long rest = t.getTeams() == null ? 0 : t.getTeams().stream().filter(x -> x != p && !Objects.equals(x.getId(), p.getId()) && !Match.isFreeParticipant(x)).count();
        if (rest < 2) throw new IllegalArgumentException("No se puede quitar a '" + p.getTeam().getName() + "' del torneo '" + t.getName()
                + "': quedar\u00eda con menos de 2 equipos.");
    }

    /** En playoffs no se sabe que hacer con el cruce: se bloquea con un mensaje claro. */
    public static void checkCanDisable(TournamentParticipant p) {
        Tournament t = p.getTournament();
        if (!isOngoing(t) || t.getMatchDays() == null) return;
        boolean playoffs = t.getMatchDays().stream().anyMatch(d -> Boolean.TRUE.equals(d.getIsPlayOff()));
        if (playoffs) throw new IllegalArgumentException("No se puede deshabilitar a '" + p.getTeam().getName() + "' mientras el torneo '" + t.getName()
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

    /** Plan 22/10 #2 (respuesta 2): al rehabilitar vuelve a un torneo en curso (sin playoffs) y juega lo que falta.
     *  Devuelve los partidos que dejan de ser libres, o null si no vuelve a ese torneo. Las fechas cerradas no se tocan. */
    public static List<Match> enableParticipant(TournamentParticipant p) {
        Tournament t = p.getTournament();
        if (!Boolean.FALSE.equals(p.getIsActive()) || !DniRules.openTournament(t) || t.getMatchDays() == null) return null;
        if (t.getMatchDays().stream().anyMatch(d -> Boolean.TRUE.equals(d.getIsPlayOff()))) return null;
        p.setIsActive(true);
        List<Match> changed = new ArrayList<>();
        for (MatchDay day : t.getMatchDays()) {
            if (Boolean.TRUE.equals(day.getIsFinished()) || day.getMatches() == null) continue;
            for (Match m : day.getMatches()) {
                if (Boolean.TRUE.equals(m.getIsFinished()) || !Boolean.TRUE.equals(m.getBye()) || !involves(m, p)) continue;
                TournamentParticipant rival = m.getHomeTeam() == p || (m.getHomeTeam() != null && Objects.equals(m.getHomeTeam().getId(), p.getId())) ? m.getAwayTeam() : m.getHomeTeam();
                if (Match.isInactiveParticipant(rival)) continue; // el rival tambien esta de baja: sigue libre
                m.setBye(false);
                changed.add(m);
            }
        }
        return changed;
    }
}
