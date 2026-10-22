-- 22/10/2026 Bloque rendimiento: búsqueda por código de torneo (ya aplicado en la base local GESFUT2)
CREATE UNIQUE INDEX ux_tournaments_code ON tournaments(code);
