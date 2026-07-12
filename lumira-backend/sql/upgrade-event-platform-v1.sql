-- Apply once to existing Lumira databases before enabling Redis Stream consumers.
CREATE TABLE IF NOT EXISTS `event_consumer_receipt` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `source_module` varchar(64) NOT NULL,
  `aggregate_id` varchar(191) NOT NULL,
  `processed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `result_status` varchar(32) NOT NULL DEFAULT 'SUCCEEDED',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_consumer_receipt_consumer_event` (`consumer_name`,`event_id`),
  KEY `idx_event_consumer_receipt_event_type_processed` (`event_type`,`processed_at`),
  KEY `idx_event_consumer_receipt_aggregate` (`source_module`,`aggregate_id`,`processed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `async_task` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL,
  `task_type` varchar(128) NOT NULL,
  `owner_module` varchar(64) NOT NULL,
  `scope_id` bigint unsigned DEFAULT NULL,
  `correlation_id` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `progress` int NOT NULL DEFAULT 0,
  `result_ref` varchar(512) DEFAULT NULL,
  `error_code` varchar(128) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_async_task_task_id` (`task_id`),
  KEY `idx_async_task_owner_status_created` (`owner_module`,`status`,`created_at`),
  KEY `idx_async_task_correlation` (`correlation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
