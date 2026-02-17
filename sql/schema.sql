-- AISocialGame 全量表结构（v1.1）

CREATE TABLE IF NOT EXISTS `users` (
  `id` CHAR(36) NOT NULL,
  `external_user_id` BIGINT NULL,
  `username` VARCHAR(64) NULL,
  `email` VARCHAR(191) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `session_id` VARCHAR(128) NULL,
  `access_token` VARCHAR(2048) NULL,
  `nickname` VARCHAR(64) NOT NULL,
  `avatar` VARCHAR(255) NULL,
  `coins` INT NOT NULL DEFAULT 0,
  `level` INT NOT NULL DEFAULT 1,
  `created_at` DATETIME NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_external_user_id` (`external_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `rooms` (
  `id` CHAR(36) NOT NULL,
  `game_id` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `max_players` INT NOT NULL,
  `is_private` TINYINT(1) NOT NULL DEFAULT 0,
  `password` VARCHAR(255) NULL,
  `comm_mode` VARCHAR(64) NULL,
  `config` LONGTEXT NULL,
  `seats` LONGTEXT NULL,
  `created_at` DATETIME NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rooms_game` (`game_id`),
  KEY `idx_rooms_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `game_states` (
  `room_id` VARCHAR(64) NOT NULL,
  `game_id` VARCHAR(64) NOT NULL,
  `phase` VARCHAR(64) NOT NULL,
  `round_number` INT NOT NULL DEFAULT 1,
  `current_seat` INT NULL,
  `players` LONGTEXT NULL,
  `logs` LONGTEXT NULL,
  `data` LONGTEXT NULL,
  `phase_ends_at` DATETIME NULL,
  `updated_at` DATETIME NULL,
  `created_at` DATETIME NULL,
  PRIMARY KEY (`room_id`),
  KEY `idx_state_game` (`game_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `community_posts` (
  `id` CHAR(36) NOT NULL,
  `author_name` VARCHAR(64) NOT NULL,
  `author_id` VARCHAR(36) NULL,
  `avatar` VARCHAR(255) NULL,
  `content` VARCHAR(1024) NOT NULL,
  `tags` LONGTEXT NULL,
  `likes` INT NOT NULL DEFAULT 0,
  `comments` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_posts_author` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
