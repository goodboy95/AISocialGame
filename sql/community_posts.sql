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
