-- DNI opcional del jugador
ALTER TABLE players ADD COLUMN dni VARCHAR(12) NULL, ADD KEY idx_players_dni (dni);
