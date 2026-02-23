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
