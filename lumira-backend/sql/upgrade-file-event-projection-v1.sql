-- Apply once before enabling the File lifecycle consumer.
CREATE TABLE IF NOT EXISTS `file_event_receipt` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `event_id` varchar(128) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `aggregate_id` varchar(128) NOT NULL,
  `aggregate_version` bigint unsigned NOT NULL,
  `payload_digest` varchar(71) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PROCESSING',
  `processed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_event_receipt_event` (`event_id`),
  KEY `idx_file_event_receipt_aggregate` (`aggregate_id`,`aggregate_version`),
  KEY `idx_file_event_receipt_status_created` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `file_event_projection` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `event_id` varchar(128) NOT NULL,
  `file_id` bigint unsigned NOT NULL,
  `aggregate_version` bigint unsigned NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `metadata` json NOT NULL,
  `projection_status` varchar(32) NOT NULL DEFAULT 'PROJECTED',
  `is_current` tinyint(1) NOT NULL DEFAULT '1',
  `last_event_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_event_projection_event` (`event_id`),
  UNIQUE KEY `uk_file_event_projection_file_version` (`file_id`,`aggregate_version`),
  KEY `idx_file_event_projection_current` (`file_id`,`is_current`,`aggregate_version`),
  KEY `idx_file_event_projection_file_version` (`file_id`,`aggregate_version`,`last_event_at`),
  KEY `idx_file_event_projection_status_updated` (`projection_status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
