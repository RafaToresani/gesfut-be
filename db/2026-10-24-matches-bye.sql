-- Plan 22/10 #2: fecha libre por equipo dado de baja.
ALTER TABLE matches ADD COLUMN bye BIT(1) NULL;
