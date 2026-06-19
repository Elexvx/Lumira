SET @schema_mode_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_plugin_definition'
    AND column_name = 'schema_mode'
);
SET @add_schema_mode_sql := IF(
  @schema_mode_exists = 0,
  'ALTER TABLE `sys_plugin_definition` ADD COLUMN `schema_mode` varchar(32) NOT NULL DEFAULT ''ISOLATED''',
  'SELECT 1'
);
PREPARE add_schema_mode_stmt FROM @add_schema_mode_sql;
EXECUTE add_schema_mode_stmt;
DEALLOCATE PREPARE add_schema_mode_stmt;

SET @supports_hot_disable_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_plugin_definition'
    AND column_name = 'supports_hot_disable'
);
SET @add_supports_hot_disable_sql := IF(
  @supports_hot_disable_exists = 0,
  'ALTER TABLE `sys_plugin_definition` ADD COLUMN `supports_hot_disable` tinyint NOT NULL DEFAULT ''1''',
  'SELECT 1'
);
PREPARE add_supports_hot_disable_stmt FROM @add_supports_hot_disable_sql;
EXECUTE add_supports_hot_disable_stmt;
DEALLOCATE PREPARE add_supports_hot_disable_stmt;

SET @supports_data_purge_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_plugin_definition'
    AND column_name = 'supports_data_purge'
);
SET @add_supports_data_purge_sql := IF(
  @supports_data_purge_exists = 0,
  'ALTER TABLE `sys_plugin_definition` ADD COLUMN `supports_data_purge` tinyint NOT NULL DEFAULT ''0''',
  'SELECT 1'
);
PREPARE add_supports_data_purge_stmt FROM @add_supports_data_purge_sql;
EXECUTE add_supports_data_purge_stmt;
DEALLOCATE PREPARE add_supports_data_purge_stmt;

SET @runtime_contributions_json_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_plugin_definition'
    AND column_name = 'runtime_contributions_json'
);
SET @add_runtime_contributions_json_sql := IF(
  @runtime_contributions_json_exists = 0,
  'ALTER TABLE `sys_plugin_definition` ADD COLUMN `runtime_contributions_json` json DEFAULT NULL',
  'SELECT 1'
);
PREPARE add_runtime_contributions_json_stmt FROM @add_runtime_contributions_json_sql;
EXECUTE add_runtime_contributions_json_stmt;
DEALLOCATE PREPARE add_runtime_contributions_json_stmt;

SET @lifecycle_status_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_plugin_version'
    AND column_name = 'lifecycle_status'
);
SET @add_lifecycle_status_sql := IF(
  @lifecycle_status_exists = 0,
  'ALTER TABLE `sys_plugin_version` ADD COLUMN `lifecycle_status` varchar(32) NOT NULL DEFAULT ''INSTALLED''',
  'SELECT 1'
);
PREPARE add_lifecycle_status_stmt FROM @add_lifecycle_status_sql;
EXECUTE add_lifecycle_status_stmt;
DEALLOCATE PREPARE add_lifecycle_status_stmt;

SET @schema_status_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_plugin_version'
    AND column_name = 'schema_status'
);
SET @add_schema_status_sql := IF(
  @schema_status_exists = 0,
  'ALTER TABLE `sys_plugin_version` ADD COLUMN `schema_status` varchar(32) NOT NULL DEFAULT ''PENDING''',
  'SELECT 1'
);
PREPARE add_schema_status_stmt FROM @add_schema_status_sql;
EXECUTE add_schema_status_stmt;
DEALLOCATE PREPARE add_schema_status_stmt;

CREATE TABLE IF NOT EXISTS `sys_plugin_schema_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `step_name` varchar(128) NOT NULL,
  `direction` varchar(16) NOT NULL,
  `script_path` varchar(512) DEFAULT NULL,
  `execution_status` varchar(32) NOT NULL,
  `detail_message` varchar(1024) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sys_plugin_schema_history_plugin_created` (`plugin_code`,`plugin_version`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `word` varchar(128) NOT NULL,
  `normalized_word` varchar(128) NOT NULL,
  `category` varchar(64) DEFAULT NULL,
  `severity` varchar(32) DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_sensitive_word_tenant_normalized` (`tenant_id`,`normalized_word`,`deleted`),
  KEY `idx_sys_sensitive_word_tenant_enabled` (`tenant_id`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
(10001,1001,'plugin:sensitive-words:view','查看敏感词拦截','sensitive-words','PLUGIN','sensitive-words',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0),
(10002,1001,'plugin:sensitive-words:manage','管理敏感词拦截','sensitive-words','PLUGIN','sensitive-words',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0),
(10003,1001,'plugin:sensitive-words:import','导入敏感词','sensitive-words','PLUGIN','sensitive-words',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `source_type` = VALUES(`source_type`),
  `plugin_code` = VALUES(`plugin_code`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
(10001,1001,2001,'plugin:sensitive-words:view',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0),
(10002,1001,2001,'plugin:sensitive-words:manage',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0),
(10003,1001,2001,'plugin:sensitive-words:import',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0)
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_plugin_definition` (`id`, `plugin_code`, `plugin_name`, `plugin_type`, `description`, `author`, `plugin_api_version`, `builtin_flag`, `status`, `sort_no`, `schema_mode`, `supports_hot_disable`, `supports_data_purge`, `runtime_contributions_json`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
(10001,'sensitive-words','敏感词拦截','BUSINESS','全局检测后台输入中的敏感词并阻止提交','lumira','1.0.0',1,'ENABLED',30,'ISOLATED',1,1,JSON_ARRAY('routes','menus','permissions','importers','interceptors'),0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0)
ON DUPLICATE KEY UPDATE
  `plugin_name` = VALUES(`plugin_name`),
  `plugin_type` = VALUES(`plugin_type`),
  `description` = VALUES(`description`),
  `author` = VALUES(`author`),
  `plugin_api_version` = VALUES(`plugin_api_version`),
  `builtin_flag` = VALUES(`builtin_flag`),
  `status` = VALUES(`status`),
  `sort_no` = VALUES(`sort_no`),
  `schema_mode` = VALUES(`schema_mode`),
  `supports_hot_disable` = VALUES(`supports_hot_disable`),
  `supports_data_purge` = VALUES(`supports_data_purge`),
  `runtime_contributions_json` = VALUES(`runtime_contributions_json`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_plugin_version` (`id`, `plugin_code`, `version`, `package_path`, `artifact_path`, `frontend_manifest_path`, `backend_jar_path`, `checksum`, `signature_path`, `min_platform_version`, `install_status`, `load_status`, `health_status`, `lifecycle_status`, `schema_status`, `is_active`, `rollbackable`, `metadata_json`, `validation_report_json`, `staged_path`, `installed_at`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
(10001,'sensitive-words','1.0.0',NULL,NULL,NULL,NULL,NULL,NULL,'0.1.0','LOADED','LOADED','HEALTHY','ENABLED','READY',1,0,JSON_OBJECT('pluginCode','sensitive-words','pluginName','敏感词拦截','version','1.0.0','pluginApiVersion','1.0.0','kind','BUSINESS','minPlatformVersion','0.1.0','schemaMode','ISOLATED','supportsHotDisable',true,'supportsDataPurge',true,'runtimeContributions',JSON_ARRAY('routes','menus','permissions','importers','interceptors'),'checksumAlgorithm','SHA-256'),JSON_OBJECT('builtin',true),NULL,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0)
ON DUPLICATE KEY UPDATE
  `min_platform_version` = VALUES(`min_platform_version`),
  `install_status` = VALUES(`install_status`),
  `load_status` = VALUES(`load_status`),
  `health_status` = VALUES(`health_status`),
  `lifecycle_status` = VALUES(`lifecycle_status`),
  `schema_status` = VALUES(`schema_status`),
  `is_active` = VALUES(`is_active`),
  `rollbackable` = VALUES(`rollbackable`),
  `metadata_json` = VALUES(`metadata_json`),
  `validation_report_json` = VALUES(`validation_report_json`),
  `installed_at` = VALUES(`installed_at`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_plugin_permission_rel` (`id`, `plugin_code`, `plugin_version`, `permission_key`, `permission_name`, `permission_group`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
(10001,'sensitive-words','1.0.0','plugin:sensitive-words:view','查看敏感词拦截','sensitive-words',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0),
(10002,'sensitive-words','1.0.0','plugin:sensitive-words:manage','管理敏感词拦截','sensitive-words',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0),
(10003,'sensitive-words','1.0.0','plugin:sensitive-words:import','导入敏感词','sensitive-words',0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_plugin_menu_rel` (`id`, `plugin_code`, `plugin_version`, `menu_code`, `parent_menu_code`, `menu_name`, `route_path`, `icon`, `permission_key`, `sort_no`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
(10001,'sensitive-words','1.0.0','plugin.sensitive-words','settings.root','敏感词拦截','/plugins/sensitive-words','StopOutlined','plugin:sensitive-words:view',30,0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0)
ON DUPLICATE KEY UPDATE
  `parent_menu_code` = VALUES(`parent_menu_code`),
  `menu_name` = VALUES(`menu_name`),
  `route_path` = VALUES(`route_path`),
  `icon` = VALUES(`icon`),
  `permission_key` = VALUES(`permission_key`),
  `sort_no` = VALUES(`sort_no`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_plugin_tenant` (`id`, `tenant_id`, `plugin_code`, `plugin_version`, `enabled`, `config_json`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
(10001,1001,'sensitive-words','1.0.0',1,JSON_OBJECT('builtin',true),0,CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,0)
ON DUPLICATE KEY UPDATE
  `plugin_version` = VALUES(`plugin_version`),
  `enabled` = VALUES(`enabled`),
  `config_json` = VALUES(`config_json`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;
