-- lumira:owner=plugin
-- lumira:migration-phase=expand
-- lumira:rollback=application-only
-- lumira:compatible-readers=202608310004..202609079999
-- lumira:cleanup-after=two-stable-releases

ALTER TABLE `sys_plugin_migration_request`
  ADD COLUMN `expected_schema_digest` char(64) DEFAULT NULL AFTER `schema_version`;

CREATE TABLE IF NOT EXISTS `plugin_migration_execution_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `migration_request_id` bigint NOT NULL,
  `plugin_code` varchar(64) NOT NULL,
  `release_id` varchar(128) NOT NULL,
  `migration_digest` char(64) NOT NULL,
  `schema_version` varchar(64) NOT NULL,
  `executor_type` varchar(32) NOT NULL,
  `executor_id` varchar(128) NOT NULL,
  `fence_token` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `active_request_id` bigint GENERATED ALWAYS AS (
    CASE WHEN `status` = 'STARTED' THEN `migration_request_id` ELSE NULL END
  ) STORED,
  `actual_schema_digest` char(64) DEFAULT NULL,
  `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` datetime DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_migration_execution_active` (`active_request_id`),
  KEY `idx_plugin_migration_execution_request` (`migration_request_id`,`id`),
  KEY `idx_plugin_migration_execution_release` (`release_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `plugin_schema_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `migration_request_id` bigint NOT NULL,
  `plugin_code` varchar(64) NOT NULL,
  `schema_version` varchar(64) NOT NULL,
  `object_type` varchar(16) NOT NULL,
  `object_name` varchar(255) NOT NULL,
  `definition_hash` char(64) NOT NULL,
  `captured_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `release_id` varchar(128) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_schema_snapshot_object` (`migration_request_id`,`object_type`,`object_name`),
  KEY `idx_plugin_schema_snapshot_plugin` (`plugin_code`,`schema_version`,`captured_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
