-- result-integrity-seed.sql
-- Datos SOLO de prueba para ResultIntegrityIntegrationTests. Corre con @Sql BEFORE_TEST_METHOD,
-- despues de result-integrity-db-guard.sql y result-integrity-cleanup.sql, en su propia transaccion (commit), porque el test usa hilos
-- y verifica rollbacks del servicio: no puede ir dentro de una transaccion de test con rollback.
--
-- Reglas: ids fijos 990001/990002 (rango reservado para este test), nombres con prefijo TEST,
-- mail test+...@example.invalid, contrasena = hash ficticio (no corresponde a ninguna clave real).
-- Columnas segun el naming por defecto de Spring Boot (camelCase -> snake_case) sobre las entidades del 7/11;
-- se insertan solo las columnas necesarias. Ver supuestos en pc-changes.md.

-- Organizador TEST (role: ERole sin @Enumerated -> ORDINAL; MANAGER = 1)
-- password_changed_at y accepted_terms_at: NOT NULL nuevas de V2 (no estan en la entidad del 7/11; nombres
-- informados por Codigo, verificar contra V2 real en la PC). Valor fijo TEST.
INSERT INTO users (id, name, lastname, email, password, role, email_verified, password_changed_at, accepted_terms_at)
VALUES (990001, 'TEST Organizador', 'TEST Resultados', 'test+result-integrity@example.invalid',
        '$2a$10$TESTfakeHashNotARealPasswordTESTfakeHashNotARealPassw', 1, 1,
        '2026-01-01 00:00:00', '2026-01-01 00:00:00');

-- Torneo TEST (code: UUID ficticio 7e570000-0000-4000-8000-000000990001 como BINARY(16))
INSERT INTO tournaments (id, code, name, start_date, is_finished, is_active, user_id)
VALUES (990001, UNHEX('7E570000000040008000000000990001'), 'TEST Torneo Integridad Resultados',
        '2026-01-01', 0, 1, 990001);

-- Equipos TEST (ninguno se llama "Free")
INSERT INTO teams (id, name, color, status, user_id)
VALUES (990001, 'TEST Local', '#000000', 1, 990001),
       (990002, 'TEST Visitante', '#FFFFFF', 1, 990001);

-- Jugador TEST del equipo local
INSERT INTO players (id, name, last_name, number, is_captain, is_goal_keeper, status, team_id)
VALUES (990001, 'TEST Jugador', 'TEST Local', 10, 0, 0, 1, 990001);

-- Estadisticas en cero (el servicio suma sobre estos valores; no pueden ser NULL)
INSERT INTO statistics (id, points, matches_played, wins, draws, losses, goals_for, goals_against, red_cards, yellow_cards)
VALUES (990001, 0, 0, 0, 0, 0, 0, 0, 0, 0),
       (990002, 0, 0, 0, 0, 0, 0, 0, 0, 0);

-- Participantes del torneo
INSERT INTO tournament_participants (id, is_active, tournament_id, team_id, statistics_id)
VALUES (990001, 1, 990001, 990001, 990001),
       (990002, 1, 990001, 990002, 990002);

-- Jugador inscripto (local), sin goles ni tarjetas ni suspension
INSERT INTO player_participants (id, is_suspended, goals, red_cards, yellow_cards, is_mvp, is_active, consecutive_cards,
                                 player_id, tournament_participant_id)
VALUES (990001, 0, 0, 0, 0, 0, 1, 0, 990001, 990001);

-- Fecha 1 de fase regular, abierta, sin fechas de playoff posteriores
INSERT INTO match_days (id, number_of_match_day, is_finished, play_off, mvp_player, tournament_id)
VALUES (990001, 1, 0, 0, NULL, 990001);

-- Partido no terminado, 0-0, con fecha pasada (por si rige la regla "no cargar antes de la hora programada")
INSERT INTO matches (id, home_team_id, away_team_id, goals_home_team, goals_away_team, is_finished, match_day_id,
                     date, description, mvp_player)
VALUES (990001, 990001, 990002, 0, 0, 0, 990001, '2026-01-01 20:00:00', 'TEST partido integridad', '');
