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

    /** Clave de comparacion tolerante (solo digitos; null si no hay). No lanza: sirve tambien para filas viejas sin normalizar. */
    public static String key(String dni) {
        if (dni == null) return null;
        String digits = dni.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    static boolean activePlayer(Player p) { return p != null && !Boolean.FALSE.equals(p.getStatus()); }
    static boolean openTournament(com.gesfut.models.tournament.Tournament t) { return t != null && !Boolean.TRUE.equals(t.getIsFinished()) && !Boolean.FALSE.equals(t.getIsActive()); }

    /** Equipos reales (sin Free) que siguen activos en el torneo. */
    public static java.util.List<com.gesfut.models.team.Team> activeTeams(com.gesfut.models.tournament.Tournament t) {
        if (t == null || t.getTeams() == null) return java.util.List.of();
        return t.getTeams().stream().filter(p -> !Boolean.FALSE.equals(p.getIsActive()) && p.getTeam() != null
                && !com.gesfut.models.matchDay.Match.isFreeParticipantTeam(p.getTeam())).map(p -> p.getTeam()).toList();
    }

    /** Un mismo DNI (normalizado) en dos equipos distintos del conjunto -> 400 con el DNI y ambos equipos. Ignora Free y bajas. */
    public static void requireNoDuplicateDni(java.util.Collection<com.gesfut.models.team.Team> teams) {
        java.util.Map<String, com.gesfut.models.team.Team> seen = new java.util.HashMap<>();
        java.util.Set<Object> done = new java.util.HashSet<>();
        for (var team : teams) {
            if (team == null || com.gesfut.models.matchDay.Match.isFreeParticipantTeam(team) || team.getPlayers() == null) continue;
            if (!done.add(team.getId() != null ? team.getId() : team)) continue;
            for (Player p : team.getPlayers()) {
                String k = key(p.getDni());
                if (k == null || !activePlayer(p)) continue;
                var other = seen.putIfAbsent(k, team);
                if (other != null && other != team)
                    throw new IllegalArgumentException("El DNI " + k + " est\u00e1 en dos equipos: " + other.getName() + " y " + team.getName()
                            + ". Un jugador no puede jugar el mismo torneo en dos equipos; sacalo de uno antes de generar.");
            }
        }
    }

    /** Sumar equipos a un torneo: los nuevos no pueden repetir DNI entre si ni con los que ya juegan. */
    public static void requireNoDuplicateDni(com.gesfut.models.tournament.Tournament t, java.util.Collection<com.gesfut.models.team.Team> extraTeams) {
        java.util.List<com.gesfut.models.team.Team> all = new java.util.ArrayList<>(activeTeams(t));
        all.addAll(extraTeams);
        requireNoDuplicateDni(all);
    }

    /** Alta/edicion de un jugador de ownTeam: su DNI no puede estar en otro equipo activo de un torneo abierto donde juega ownTeam. */
    public static void requireDniFreeInTournaments(String dni, String fullName, com.gesfut.models.team.Team ownTeam) {
        String k = key(dni);
        if (k == null || ownTeam == null || ownTeam.getTournaments() == null) return;
        for (var participation : ownTeam.getTournaments()) {
            if (Boolean.FALSE.equals(participation.getIsActive()) || !openTournament(participation.getTournament())) continue;
            requireDniFreeInTournament(participation.getTournament(), k, fullName, ownTeam);
        }
    }

    public static void requireDniFreeInTournament(com.gesfut.models.tournament.Tournament t, String dni, String fullName, com.gesfut.models.team.Team ownTeam) {
        String k = key(dni);
        if (k == null) return;
        for (var team : activeTeams(t)) {
            if (ownTeam != null && Objects.equals(team.getId(), ownTeam.getId())) continue;
            if (team.getPlayers() == null) continue;
            for (Player p : team.getPlayers())
                if (activePlayer(p) && k.equals(key(p.getDni())))
                    throw new IllegalArgumentException("El jugador con DNI " + k + " (" + fullName + ") ya juega " + t.getName() + " en " + team.getName()
                            + ": no puede estar en dos equipos del mismo torneo.");
        }
    }

    /** Falla si el jugador (con DNI) ya juega el torneo en un equipo distinto de targetTeam. */
    public static void requireNotInOtherTeam(java.util.Collection<com.gesfut.models.tournament.PlayerParticipant> sameDniInTournament, Player player, com.gesfut.models.team.Team targetTeam) {
        if (player == null || player.getDni() == null || sameDniInTournament == null) return;
        for (var pp : sameDniInTournament) {
            var team = pp.getTournamentParticipant() == null ? null : pp.getTournamentParticipant().getTeam();
            if (team == null || pp.getPlayer() == null || !Objects.equals(key(player.getDni()), key(pp.getPlayer().getDni()))) continue;
            if (targetTeam != null && Objects.equals(team.getId(), targetTeam.getId())) continue;
            throw new IllegalArgumentException("El jugador con DNI " + player.getDni() + " (" + player.getName() + " " + player.getLastName()
                    + ") ya juega este torneo en " + team.getName() + ": no puede estar en dos equipos del mismo torneo.");
        }
    }
}
