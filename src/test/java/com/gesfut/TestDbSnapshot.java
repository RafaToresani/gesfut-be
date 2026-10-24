package com.gesfut;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * Foto de filas de la base de prueba para dejarla como estaba despues de cada test
 * (los tests de resultados cargan/cierran partidos y, sin esto, la copia se queda sin partidos pendientes).
 */
final class TestDbSnapshot {
    private record Part(String table, String where, Object[] args, List<Map<String, Object>> rows) { }
    private final JdbcTemplate jdbc;
    private final List<Part> parts = new ArrayList<>();

    TestDbSnapshot(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    TestDbSnapshot take(String table, String where, Object... args) {
        parts.add(new Part(table, where, args, jdbc.queryForList("SELECT * FROM " + table + " WHERE " + where, args)));
        return this;
    }

    /** Borra las filas nuevas, reinserta las borradas y vuelve a poner los valores originales (por id). */
    void restore() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            for (Part p : parts) {
                Set<Object> ids = new HashSet<>();
                p.rows().forEach(r -> ids.add(((Number) r.get("id")).longValue()));
                for (Map<String, Object> now : jdbc.queryForList("SELECT id FROM " + p.table() + " WHERE " + p.where(), p.args()))
                    if (!ids.contains(((Number) now.get("id")).longValue())) jdbc.update("DELETE FROM " + p.table() + " WHERE id=?", now.get("id"));
                for (Map<String, Object> row : p.rows()) {
                    List<String> cols = new ArrayList<>(row.keySet());
                    String sql = "REPLACE INTO " + p.table() + " (" + String.join(",", cols.stream().map(c -> "`" + c + "`").toList())
                            + ") VALUES (" + String.join(",", Collections.nCopies(cols.size(), "?")) + ")";
                    jdbc.update(sql, cols.stream().map(row::get).toArray());
                }
            }
        } finally { jdbc.execute("SET FOREIGN_KEY_CHECKS=1"); }
    }
}
