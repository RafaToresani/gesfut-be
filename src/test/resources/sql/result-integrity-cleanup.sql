-- result-integrity-cleanup.sql
-- Borra SOLO lo sembrado por result-integrity-seed.sql y lo que el test genera sobre ese partido.
-- Corre ANTES del seed (por si una corrida anterior se corto) y con @Sql AFTER_TEST_METHOD.
-- Cada DELETE tiene WHERE por los ids sembrados (990001/990002) Y por el prefijo TEST del torneo/equipo/mail.
-- Nunca DELETE sin WHERE ni TRUNCATE. Si un id fijo existiera sin prefijo TEST, no se borra
-- (y el seed falla por clave duplicada, de forma visible).
-- Orden: de hijos a padres, respetando las FKs.
-- Requiere que result-integrity-db-guard.sql haya corrido antes en el mismo @Sql.

-- Captura (ANTES de borrar participantes) que estadisticas pertenecen a participantes del torneo TEST.
-- tournament_participants.statistics_id -> statistics.id: el participante referencia a la estadistica, asi que
-- la estadistica se borra DESPUES del participante y el vinculo TEST se pierde; por eso se guarda antes en una
-- variable de sesion (ScriptUtils corre todo el script en la misma conexion y transaccion).
-- No se usa DELETE multi-tabla tp+statistics: MySQL puede procesar las tablas en un orden que viola la FK.
SET @gesfut_test_stats := NULL;
SET @gesfut_test_stats := (SELECT GROUP_CONCAT(s.id)
                             FROM statistics s
                             JOIN tournament_participants tp ON tp.statistics_id = s.id
                             JOIN tournaments t ON t.id = tp.tournament_id
                            WHERE s.id IN (990001, 990002)
                              AND tp.id IN (990001, 990002)
                              AND t.id = 990001 AND t.name LIKE 'TEST %');

-- Eventos de los partidos del torneo TEST
DELETE FROM events
 WHERE match_id IN (SELECT m.id FROM matches m
                      JOIN match_days md ON md.id = m.match_day_id
                      JOIN tournaments t ON t.id = md.tournament_id
                     WHERE t.id = 990001 AND t.name LIKE 'TEST %');

-- Eventos que apunten al jugador inscripto TEST (por si quedara alguno fuera del partido)
DELETE FROM events
 WHERE player_participant_id IN (SELECT pp.id FROM player_participants pp
                                   JOIN tournament_participants tp ON tp.id = pp.tournament_participant_id
                                   JOIN tournaments t ON t.id = tp.tournament_id
                                  WHERE pp.id = 990001 AND t.id = 990001 AND t.name LIKE 'TEST %');

-- Auditoria de correcciones (result_corrections, V1) de los partidos del torneo TEST: la escribe el servicio al corregir una fecha cerrada
DELETE FROM result_corrections
 WHERE match_id IN (SELECT m.id FROM matches m
                      JOIN match_days md ON md.id = m.match_day_id
                      JOIN tournaments t ON t.id = md.tournament_id
                     WHERE t.id = 990001 AND t.name LIKE 'TEST %');
-- Partidos de las fechas del torneo TEST (incluye cualquiera que el servicio hubiera creado ahi)
DELETE FROM matches
 WHERE match_day_id IN (SELECT md.id FROM match_days md
                          JOIN tournaments t ON t.id = md.tournament_id
                         WHERE t.id = 990001 AND t.name LIKE 'TEST %');

-- Fechas del torneo TEST
DELETE FROM match_days
 WHERE tournament_id IN (SELECT t.id FROM tournaments t WHERE t.id = 990001 AND t.name LIKE 'TEST %');

-- Jugadores inscriptos en participantes del torneo TEST
DELETE FROM player_participants
 WHERE tournament_participant_id IN (SELECT tp.id FROM tournament_participants tp
                                       JOIN tournaments t ON t.id = tp.tournament_id
                                      WHERE tp.id IN (990001, 990002) AND t.id = 990001 AND t.name LIKE 'TEST %');

-- Participantes del torneo TEST
DELETE FROM tournament_participants
 WHERE id IN (990001, 990002)
   AND tournament_id IN (SELECT t.id FROM tournaments t WHERE t.id = 990001 AND t.name LIKE 'TEST %');

-- Estadisticas: solo las capturadas arriba como del torneo TEST (ids sembrados + vinculo TEST),
-- y solo si ya no las referencia ningun participante. Sin subconsulta sobre statistics (evita el error 1093).
-- Si la captura dio NULL (no habia vinculo TEST), FIND_IN_SET da NULL y no se borra nada.
DELETE FROM statistics
 WHERE id IN (990001, 990002)
   AND FIND_IN_SET(id, @gesfut_test_stats) > 0
   AND NOT EXISTS (SELECT 1 FROM tournament_participants tp WHERE tp.statistics_id = statistics.id);

-- Premios del torneo TEST (el seed no crea; por si el servicio los creara)
DELETE FROM prizes
 WHERE tournament_id IN (SELECT t.id FROM tournaments t WHERE t.id = 990001 AND t.name LIKE 'TEST %');

-- Jugadores y equipos TEST
DELETE FROM players
 WHERE id = 990001 AND name LIKE 'TEST %';

DELETE FROM teams
 WHERE id IN (990001, 990002) AND name LIKE 'TEST %';

-- Torneo TEST
DELETE FROM tournaments
 WHERE id = 990001 AND name LIKE 'TEST %';

-- Organizador TEST
DELETE FROM users
 WHERE id = 990001 AND email LIKE 'test+%@example.invalid';

-- No dejar la variable en la conexion (vuelve al pool).
SET @gesfut_test_stats := NULL;
