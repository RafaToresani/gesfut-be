-- Revision critica #4: reglas de sancion por torneo (null = 5 amarillas / 1 fecha por roja)
ALTER TABLE tournaments ADD COLUMN yellow_cards_to_suspend INT NULL, ADD COLUMN red_card_suspension_matches INT NULL;
