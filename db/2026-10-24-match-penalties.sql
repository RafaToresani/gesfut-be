-- Revision critica #9: tanda de penales
ALTER TABLE matches ADD COLUMN penalties_home INT NULL, ADD COLUMN penalties_away INT NULL;
