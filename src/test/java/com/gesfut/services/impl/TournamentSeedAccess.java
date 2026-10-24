package com.gesfut.services.impl;

import java.util.List;

/** Acceso de test al orden de siembra (package-private). */
public final class TournamentSeedAccess {
    private TournamentSeedAccess() {}
    public static List<Integer> order(int n) { return TournamentServiceImpl.bracketSeedOrder(n); }
}
