-- Expand-only repair for upgraded databases created before the event catalog
-- read model was included in the fresh baseline.

CREATE TABLE IF NOT EXISTS `event_catalog_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_type` varchar(16) NOT NULL,
  `source_id` bigint NOT NULL,
  `source_uuid` varchar(64) DEFAULT NULL,
  `locale` varchar(64) DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `subtitle` varchar(128) DEFAULT NULL,
  `summary` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `registration_start` varchar(64) DEFAULT NULL,
  `registration_end` varchar(64) DEFAULT NULL,
  `event_start` varchar(64) DEFAULT NULL,
  `event_end` varchar(64) DEFAULT NULL,
  `event_time` varchar(64) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `tags` varchar(1000) DEFAULT NULL,
  `cta_label` varchar(64) DEFAULT NULL,
  `cta_href` varchar(512) DEFAULT NULL,
  `featured` tinyint NOT NULL DEFAULT '0',
  `sort` int NOT NULL DEFAULT '100',
  `version` bigint NOT NULL DEFAULT '0',
  `last_event_id` bigint NOT NULL DEFAULT '0',
  `source_updated_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_catalog_item_source` (`source_type`,`source_id`),
  KEY `idx_event_catalog_item_public` (`status`,`featured`,`event_start`,`id`),
  KEY `idx_event_catalog_item_source_uuid` (`source_type`,`source_uuid`),
  KEY `idx_event_catalog_item_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Existing owner rows predate the outbox projection. Seed the read model at
-- the current outbox high-water mark so later events can advance it safely.
INSERT INTO `event_catalog_item` (
    `source_type`, `source_id`, `source_uuid`, `locale`, `title`, `subtitle`, `summary`, `status`,
    `registration_start`, `registration_end`, `event_start`, `event_end`, `event_time`, `location`,
    `image_url`, `tags`, `cta_label`, `cta_href`, `featured`, `sort`, `version`, `last_event_id`,
    `source_updated_at`, `created_at`, `updated_at`
)
SELECT
    'ACTIVITY', a.`id`, a.`code`, a.`locale`, a.`title`, a.`subtitle`, a.`description`, a.`status`,
    NULL, NULL, a.`activity_date`, NULL, a.`activity_time`, a.`location`,
    a.`image_url`, a.`tags`, a.`cta_label`, a.`cta_href`, a.`featured`, a.`sort`, 1,
    (SELECT COALESCE(MAX(`id`), 0) FROM `platform_event_outbox`), a.`updated_at`, a.`created_at`, a.`updated_at`
FROM `aiadc_activity` a
WHERE a.`deleted` = 0
ON DUPLICATE KEY UPDATE
    `source_uuid` = VALUES(`source_uuid`),
    `locale` = VALUES(`locale`),
    `title` = VALUES(`title`),
    `subtitle` = VALUES(`subtitle`),
    `summary` = VALUES(`summary`),
    `status` = VALUES(`status`),
    `registration_start` = VALUES(`registration_start`),
    `registration_end` = VALUES(`registration_end`),
    `event_start` = VALUES(`event_start`),
    `event_end` = VALUES(`event_end`),
    `event_time` = VALUES(`event_time`),
    `location` = VALUES(`location`),
    `image_url` = VALUES(`image_url`),
    `tags` = VALUES(`tags`),
    `cta_label` = VALUES(`cta_label`),
    `cta_href` = VALUES(`cta_href`),
    `featured` = VALUES(`featured`),
    `sort` = VALUES(`sort`),
    `version` = GREATEST(`version`, VALUES(`version`)),
    `last_event_id` = GREATEST(`last_event_id`, VALUES(`last_event_id`)),
    `source_updated_at` = VALUES(`source_updated_at`),
    `updated_at` = VALUES(`updated_at`);

INSERT INTO `event_catalog_item` (
    `source_type`, `source_id`, `source_uuid`, `locale`, `title`, `subtitle`, `summary`, `status`,
    `registration_start`, `registration_end`, `event_start`, `event_end`, `event_time`, `location`,
    `image_url`, `tags`, `cta_label`, `cta_href`, `featured`, `sort`, `version`, `last_event_id`,
    `source_updated_at`, `created_at`, `updated_at`
)
SELECT
    'COMPETITION', c.`id`, c.`uuid`, c.`locale`, c.`title`, c.`short_name`, c.`description`, c.`status`,
    c.`registration_start`, c.`registration_end`, c.`competition_start`, c.`competition_end`, NULL, c.`location`,
    c.`image_url`, c.`tags`, NULL, NULL, c.`featured`, c.`sort`, 1,
    (SELECT COALESCE(MAX(`id`), 0) FROM `platform_event_outbox`), c.`updated_at`, c.`created_at`, c.`updated_at`
FROM `aiadc_competition` c
WHERE c.`deleted` = 0
ON DUPLICATE KEY UPDATE
    `source_uuid` = VALUES(`source_uuid`),
    `locale` = VALUES(`locale`),
    `title` = VALUES(`title`),
    `subtitle` = VALUES(`subtitle`),
    `summary` = VALUES(`summary`),
    `status` = VALUES(`status`),
    `registration_start` = VALUES(`registration_start`),
    `registration_end` = VALUES(`registration_end`),
    `event_start` = VALUES(`event_start`),
    `event_end` = VALUES(`event_end`),
    `event_time` = VALUES(`event_time`),
    `location` = VALUES(`location`),
    `image_url` = VALUES(`image_url`),
    `tags` = VALUES(`tags`),
    `cta_label` = VALUES(`cta_label`),
    `cta_href` = VALUES(`cta_href`),
    `featured` = VALUES(`featured`),
    `sort` = VALUES(`sort`),
    `version` = GREATEST(`version`, VALUES(`version`)),
    `last_event_id` = GREATEST(`last_event_id`, VALUES(`last_event_id`)),
    `source_updated_at` = VALUES(`source_updated_at`),
    `updated_at` = VALUES(`updated_at`);

UPDATE `event_catalog_item` c
LEFT JOIN `aiadc_activity` a ON a.`id` = c.`source_id` AND c.`source_type` = 'ACTIVITY' AND a.`deleted` = 0
SET c.`status` = 'archived'
WHERE c.`source_type` = 'ACTIVITY' AND a.`id` IS NULL;

UPDATE `event_catalog_item` c
LEFT JOIN `aiadc_competition` x ON x.`id` = c.`source_id` AND c.`source_type` = 'COMPETITION' AND x.`deleted` = 0
SET c.`status` = 'archived'
WHERE c.`source_type` = 'COMPETITION' AND x.`id` IS NULL;
