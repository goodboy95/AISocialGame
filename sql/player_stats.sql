CREATE TABLE IF NOT EXISTS `player_stats` (
  `id` VARCHAR(100) NOT NULL,
  `player_id` VARCHAR(36) NOT NULL,
  `game_id` VARCHAR(32) NOT NULL,
  `display_name` VARCHAR(64) NOT NULL,
  `avatar` VARCHAR(255) NULL,
  `games_played` INT NOT NULL DEFAULT 0,
  `wins` INT NOT NULL DEFAULT 0,
  `score` INT NOT NULL DEFAULT 0,
  `updated_at` DATETIME NULL,
  `created_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stats_player` (`player_id`),
  KEY `idx_stats_game` (`game_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
