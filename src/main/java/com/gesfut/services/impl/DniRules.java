package com.gesfut.services.impl;

import com.gesfut.models.team.Player;

import java.util.Objects;

/** Un jugador (identificado por DNI) no puede estar en dos equipos del mismo torneo. Sin DNI no se controla. */
public final class DniRules {
    private DniRules() {}

    public static String normalize(String dni) {
        if (dni == null) return null;
        String digits = dni.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        if (digits.length() < 7 || digits.length() > 8) throw new IllegalArgumentException("El DNI debe tener 7 u 8 d\u00edgitos.");
        return digits;
    }

    /** Falla si el jugador (con DNI) ya juega el torneo en un equipo distinto de targetTeam. */
    public static void requireNotInOtherTeam(java.util.Collection<com.gesfut.models.tournament.PlayerParticipant> sameDniInTournament, Player player, com.gesfut.models.team.Team targetTeam) {
        if (player == null || player.getDni() == null || sameDniInTournament == null) return;
        for (var pp : sameDniInTournament) {
            var team = pp.getTournamentParticipant() == null ? null : pp.getTournamentParticipant().getTeam();
            if (team == null || pp.getPlayer() == null || !player.getDni().equals(pp.getPlayer().getDni())) continue;
            if (targetTeam != null && Objects.equals(team.getId(), targetTeam.getId())) continue;
            throw new IllegalArgumentException("El jugador con DNI " + player.getDni() + " (" + player.getName() + " " + player.getLastName()
                    + ") ya juega este torneo en " + team.getName() + ": no puede estar en dos equipos del mismo torneo.");
        }
    }
}
