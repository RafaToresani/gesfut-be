-- Aplicar después de respaldar la base. No elimina datos ni recrea tablas.
-- El ejecutor debe comprobar la existencia de la columna antes del ALTER.
ALTER TABLE matches ADD COLUMN tiebreak_winner_id BIGINT NULL;
UPDATE matches SET goals_home_team = 0 WHERE goals_home_team IS NULL;
UPDATE matches SET goals_away_team = 0 WHERE goals_away_team IS NULL;
UPDATE match_days SET play_off = 0 WHERE play_off IS NULL;
