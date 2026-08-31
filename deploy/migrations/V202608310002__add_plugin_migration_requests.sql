-- lumira:owner=plugin
-- lumira:migration-phase=expand
-- lumira:rollback=application-only
-- lumira:compatible-readers=202607140001..202608319999
-- lumira:cleanup-after=two-stable-releases

CREATE TABLE IF NOT EXISTS `sys_plugin_migration_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `schema_version` varchar(64) NOT NULL,
  `phase` varchar(16) NOT NULL,
  `rollback_mode` varchar(32) NOT NULL,
  `compatible_readers` varchar(1024) NOT NULL,
  `table_namespace` varchar(128) NOT NULL,
  `operation_epoch` bigint NOT NULL,
  `package_digest` char(64) NOT NULL,
  `migration_digest` char(64) NOT NULL,
  `release_id` varchar(128) NOT NULL,
  `request_status` varchar(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
  `lifecycle_status` varchar(32) NOT NULL DEFAULT 'MIGRATION_PENDING',
  `script_payload` longtext NOT NULL,
  `failure_reason` varchar(1024) DEFAULT NULL,
  `recovery_action` varchar(1024) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approved_at` datetime DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_migration_request_digest` (`plugin_code`,`plugin_version`,`migration_digest`),
  UNIQUE KEY `uk_sys_plugin_migration_request_epoch` (`plugin_code`,`operation_epoch`),
  KEY `idx_sys_plugin_migration_request_status_id` (`request_status`,`phase`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
