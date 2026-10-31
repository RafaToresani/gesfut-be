-- V1 baseline: esquema de Gesfut al commit aa35708 (exportado de la base de regresion con SHOW CREATE TABLE).
-- En bases que ya tienen el esquema se saltea (baseline-on-migrate, baseline-version=1).
SET FOREIGN_KEY_CHECKS=0;

CREATE TABLE `events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantity` int DEFAULT NULL,
  `type` tinyint DEFAULT NULL,
  `match_id` bigint DEFAULT NULL,
  `player_participant_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKalar6y8nmq6faf2e9ra3f2203` (`match_id`),
  KEY `FKgpbd2qo554s8ltotd02hdm9v0` (`player_participant_id`),
  CONSTRAINT `FKalar6y8nmq6faf2e9ra3f2203` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`),
  CONSTRAINT `FKgpbd2qo554s8ltotd02hdm9v0` FOREIGN KEY (`player_participant_id`) REFERENCES `player_participants` (`id`),
  CONSTRAINT `events_chk_1` CHECK ((`type` between 0 and 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `match_days` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_finished` bit(1) DEFAULT NULL,
  `play_off` bit(1) DEFAULT NULL,
  `mvp_player` varchar(255) DEFAULT NULL,
  `number_of_match_day` int DEFAULT NULL,
  `tournament_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKm3ynpchu2uh8diiu6bbpjwkkr` (`tournament_id`),
  CONSTRAINT `FKm3ynpchu2uh8diiu6bbpjwkkr` FOREIGN KEY (`tournament_id`) REFERENCES `tournaments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `matches` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `date` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `goals_away_team` int DEFAULT NULL,
  `goals_home_team` int DEFAULT NULL,
  `is_finished` bit(1) DEFAULT NULL,
  `mvp_player` varchar(255) DEFAULT NULL,
  `next_match` bigint DEFAULT NULL,
  `away_team_id` bigint DEFAULT NULL,
  `home_team_id` bigint DEFAULT NULL,
  `match_day_id` bigint DEFAULT NULL,
  `tiebreak_winner_id` bigint DEFAULT NULL,
  `bye` bit(1) DEFAULT NULL,
  `penalties_home` int DEFAULT NULL,
  `penalties_away` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6acucdtem52qufm5wcoeb61ub` (`away_team_id`),
  KEY `FKoq66it4eivn1gbnf11p21xadv` (`home_team_id`),
  KEY `FKf33k4m12xlqdp42lcxh4298hj` (`match_day_id`),
  CONSTRAINT `FK6acucdtem52qufm5wcoeb61ub` FOREIGN KEY (`away_team_id`) REFERENCES `tournament_participants` (`id`),
  CONSTRAINT `FKf33k4m12xlqdp42lcxh4298hj` FOREIGN KEY (`match_day_id`) REFERENCES `match_days` (`id`),
  CONSTRAINT `FKoq66it4eivn1gbnf11p21xadv` FOREIGN KEY (`home_team_id`) REFERENCES `tournament_participants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `player_participants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `consecutive_cards` int DEFAULT NULL,
  `goals` int DEFAULT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `is_mvp` int DEFAULT NULL,
  `is_suspended` bit(1) DEFAULT NULL,
  `red_cards` int DEFAULT NULL,
  `yellow_cards` int DEFAULT NULL,
  `player_id` bigint DEFAULT NULL,
  `tournament_participant_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKg6coomqjwpxw24o5yksa9urp2` (`player_id`),
  KEY `FKrpyowwema0jy2frgndmho5kov` (`tournament_participant_id`),
  CONSTRAINT `FKg6coomqjwpxw24o5yksa9urp2` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`),
  CONSTRAINT `FKrpyowwema0jy2frgndmho5kov` FOREIGN KEY (`tournament_participant_id`) REFERENCES `tournament_participants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `players` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_captain` bit(1) DEFAULT NULL,
  `is_goal_keeper` bit(1) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `number` int DEFAULT NULL,
  `status` bit(1) DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `dni` varchar(12) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5nglidr00c4dyybl171v6kask` (`team_id`),
  KEY `idx_players_dni` (`dni`),
  CONSTRAINT `FK5nglidr00c4dyybl171v6kask` FOREIGN KEY (`team_id`) REFERENCES `teams` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prizes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `position` int DEFAULT NULL,
  `type` tinyint DEFAULT NULL,
  `tournament_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpruy2re5unkkq7xlbtvwg4oyo` (`tournament_id`),
  CONSTRAINT `FKpruy2re5unkkq7xlbtvwg4oyo` FOREIGN KEY (`tournament_id`) REFERENCES `tournaments` (`id`),
  CONSTRAINT `prizes_chk_1` CHECK ((`type` between 0 and 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `result_corrections` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `match_id` bigint DEFAULT NULL,
  `user_email` varchar(255) DEFAULT NULL,
  `corrected_at` datetime(6) DEFAULT NULL,
  `old_result` text,
  `new_result` text,
  PRIMARY KEY (`id`),
  KEY `idx_result_corrections_match` (`match_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `revoked_tokens` (
  `token_hash` varchar(64) NOT NULL,
  `expires_at` bigint NOT NULL,
  PRIMARY KEY (`token_hash`),
  KEY `idx_revoked_tokens_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `draws` int DEFAULT NULL,
  `goals_against` int DEFAULT NULL,
  `goals_for` int DEFAULT NULL,
  `losses` int DEFAULT NULL,
  `matches_played` int DEFAULT NULL,
  `points` int DEFAULT NULL,
  `red_cards` int DEFAULT NULL,
  `wins` int DEFAULT NULL,
  `yellow_cards` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `teams` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `color` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `status` bit(1) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlm88j38y90erf2rum00e85gw8` (`user_id`),
  CONSTRAINT `FKlm88j38y90erf2rum00e85gw8` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tournament_participants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_active` bit(1) DEFAULT NULL,
  `statistics_id` bigint DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `tournament_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4jd4h1b1vujom3ywkp9uuc0oh` (`statistics_id`),
  KEY `FK5gcllxhpd4uublofnydb3wkum` (`team_id`),
  KEY `FK7m4qdyvu9tgkd7phoskg97nn5` (`tournament_id`),
  CONSTRAINT `FK5gcllxhpd4uublofnydb3wkum` FOREIGN KEY (`team_id`) REFERENCES `teams` (`id`),
  CONSTRAINT `FK7m4qdyvu9tgkd7phoskg97nn5` FOREIGN KEY (`tournament_id`) REFERENCES `tournaments` (`id`),
  CONSTRAINT `FK8j1uursyq4nho048539bxts2q` FOREIGN KEY (`statistics_id`) REFERENCES `statistics` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tournaments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` binary(16) DEFAULT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `is_finished` bit(1) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `yellow_cards_to_suspend` int DEFAULT NULL,
  `red_card_suspension_matches` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgkuyn0rfac98xfrldky9lf60i` (`user_id`),
  CONSTRAINT `FKgkuyn0rfac98xfrldky9lf60i` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) DEFAULT NULL,
  `email_verified` bit(1) DEFAULT NULL,
  `lastname` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `reset_token` varchar(255) DEFAULT NULL,
  `reset_token_expiration` datetime(6) DEFAULT NULL,
  `role` tinyint DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `users_chk_1` CHECK ((`role` between 0 and 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS=1;
