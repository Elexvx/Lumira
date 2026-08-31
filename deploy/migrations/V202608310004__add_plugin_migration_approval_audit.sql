-- lumira:owner=plugin
-- lumira:migration-phase=expand
-- lumira:rollback=application-only
-- lumira:compatible-readers=202608310002..202608319999
-- lumira:cleanup-after=two-stable-releases

ALTER TABLE `sys_plugin_migration_request`
  ADD COLUMN `approved_by` varchar(128) DEFAULT NULL AFTER `created_by_uuid`,
  ADD COLUMN `approval_reason` varchar(512) DEFAULT NULL AFTER `approved_by`;

CREATE TABLE IF NOT EXISTS `sys_plugin_migration_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` bigint NOT NULL,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `operation_epoch` bigint NOT NULL,
  `package_digest` char(64) NOT NULL,
  `migration_digest` char(64) NOT NULL,
  `release_id` varchar(128) NOT NULL,
  `actor` varchar(128) NOT NULL,
  `detail_message` varchar(1024) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sys_plugin_migration_audit_request` (`request_id`,`id`),
  KEY `idx_sys_plugin_migration_audit_plugin` (`plugin_code`,`plugin_version`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
