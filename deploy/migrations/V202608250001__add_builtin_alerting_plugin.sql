-- Expand-only production migration for the built-in alerting plugin.
-- The plugin and every channel/rule remain disabled after migration.

CREATE TABLE IF NOT EXISTS `alert_channel` (
  `id` bigint NOT NULL AUTO_INCREMENT, `name` varchar(128) NOT NULL, `channel_type` varchar(32) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0', `config_encrypted` longtext NOT NULL, `config_fingerprint` char(64) NOT NULL,
  `last_test_status` varchar(32) DEFAULT NULL, `last_test_error` varchar(1000) DEFAULT NULL, `last_test_at` datetime DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '1', `created_by` bigint NOT NULL DEFAULT '0', `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0', `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0', PRIMARY KEY (`id`),
  KEY `idx_alert_channel_enabled_type` (`deleted`,`enabled`,`channel_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `alert_contact_group` (
  `id` bigint NOT NULL AUTO_INCREMENT, `name` varchar(128) NOT NULL, `enabled` tinyint NOT NULL DEFAULT '1',
  `version` bigint NOT NULL DEFAULT '1', `created_by` bigint NOT NULL DEFAULT '0', `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0', `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0', PRIMARY KEY (`id`), UNIQUE KEY `uk_alert_contact_group_name` (`name`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `alert_contact_member` (
  `id` bigint NOT NULL AUTO_INCREMENT, `contact_group_id` bigint NOT NULL, `channel_id` bigint NOT NULL,
  `member_type` varchar(32) NOT NULL, `target_identifier` varchar(512) NOT NULL, `display_name` varchar(128) DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1', `created_by` bigint NOT NULL DEFAULT '0', `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0', `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0', PRIMARY KEY (`id`),
  KEY `idx_alert_contact_member_group` (`contact_group_id`,`deleted`,`enabled`), KEY `idx_alert_contact_member_channel` (`channel_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `alert_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT, `name` varchar(128) NOT NULL, `source_type` varchar(32) NOT NULL,
  `signal_key` varchar(128) NOT NULL, `comparator` varchar(8) NOT NULL, `threshold_value` decimal(24,8) NOT NULL,
  `window_seconds` int NOT NULL DEFAULT '300', `pending_seconds` int NOT NULL DEFAULT '300', `severity` varchar(16) NOT NULL DEFAULT 'WARNING',
  `contact_group_id` bigint NOT NULL, `enabled` tinyint NOT NULL DEFAULT '0', `labels_json` json DEFAULT NULL,
  `last_evaluated_at` datetime DEFAULT NULL, `evaluation_error` varchar(1000) DEFAULT NULL, `version` bigint NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL DEFAULT '0', `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0', `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0', PRIMARY KEY (`id`),
  KEY `idx_alert_rule_due` (`deleted`,`enabled`,`last_evaluated_at`,`id`), KEY `idx_alert_rule_contact_group` (`contact_group_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `alert_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT, `rule_id` bigint NOT NULL, `fingerprint` varchar(191) NOT NULL,
  `status` varchar(16) NOT NULL, `last_value` decimal(24,8) DEFAULT NULL, `started_at` datetime NOT NULL,
  `pending_since` datetime DEFAULT NULL, `firing_at` datetime DEFAULT NULL, `resolved_at` datetime DEFAULT NULL,
  `acknowledged_at` datetime DEFAULT NULL, `acknowledged_by` bigint DEFAULT NULL, `consecutive_ok` int NOT NULL DEFAULT '0',
  `last_notified_at` datetime DEFAULT NULL, `version` bigint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_alert_instance_rule_status` (`rule_id`,`status`,`id`),
  KEY `idx_alert_instance_status_updated` (`status`,`updated_at`), KEY `idx_alert_instance_fingerprint` (`fingerprint`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `alert_event` (
  `id` bigint NOT NULL AUTO_INCREMENT, `instance_id` bigint NOT NULL, `event_type` varchar(16) NOT NULL,
  `payload_json` json NOT NULL, `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_alert_event_instance` (`instance_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `alert_silence` (
  `id` bigint NOT NULL AUTO_INCREMENT, `name` varchar(128) NOT NULL, `rule_id` bigint DEFAULT NULL,
  `starts_at` datetime NOT NULL, `ends_at` datetime NOT NULL, `reason` varchar(500) NOT NULL, `enabled` tinyint NOT NULL DEFAULT '1',
  `version` bigint NOT NULL DEFAULT '1', `created_by` bigint NOT NULL DEFAULT '0', `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0', `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0', PRIMARY KEY (`id`),
  KEY `idx_alert_silence_active` (`deleted`,`enabled`,`starts_at`,`ends_at`,`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `alert_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT, `event_id` bigint NOT NULL, `instance_id` bigint NOT NULL, `channel_id` bigint NOT NULL,
  `member_type` varchar(24) NOT NULL, `recipient` varchar(512) NOT NULL, `dedupe_key` char(64) NOT NULL, `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0', `next_attempt_at` datetime NOT NULL, `claim_token` varchar(64) DEFAULT NULL,
  `claim_until` datetime DEFAULT NULL, `provider_message_id` varchar(256) DEFAULT NULL, `provider_response` varchar(1000) DEFAULT NULL,
  `last_error` varchar(1000) DEFAULT NULL, `sent_at` datetime DEFAULT NULL, `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alert_delivery_dedupe` (`dedupe_key`), KEY `idx_alert_delivery_claim` (`status`,`next_attempt_at`,`claim_until`,`id`),
  KEY `idx_alert_delivery_instance` (`instance_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `alert_delivery_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT, `delivery_id` bigint NOT NULL, `outcome` varchar(24) NOT NULL,
  `error_message` varchar(1000) DEFAULT NULL, `response_summary` varchar(1000) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`),
  KEY `idx_alert_delivery_attempt_delivery` (`delivery_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `alert_directory_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT, `channel_id` bigint NOT NULL, `user_id` bigint NOT NULL, `user_uuid` char(36) NOT NULL,
  `provider_user_id` varchar(256) NOT NULL, `provider_display_name` varchar(128) DEFAULT NULL, `match_source` varchar(24) NOT NULL,
  `mapping_status` varchar(24) NOT NULL, `manual_override` tinyint NOT NULL DEFAULT '0', `synced_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0', `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0', `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alert_directory_mapping_user` (`channel_id`,`user_uuid`,`deleted`),
  KEY `idx_alert_directory_mapping_status` (`channel_id`,`mapping_status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `alert_worker_lease` (
  `lease_name` varchar(64) NOT NULL, `owner_id` varchar(128) NOT NULL, `lease_until` datetime NOT NULL,
  `heartbeat_at` datetime NOT NULL, PRIMARY KEY (`lease_name`), KEY `idx_alert_worker_lease_until` (`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `sys_permission` (
  `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
  `created_by`, `updated_by`, `deleted`
) VALUES
  ('plugin:alerting:view', '查看告警中心', 'plugin', 'PLUGIN', 'builtin-alerting', 0, 0, 0),
  ('plugin:alerting:manage', '管理告警规则和联系人组', 'plugin', 'PLUGIN', 'builtin-alerting', 0, 0, 0),
  ('plugin:alerting:ack', '确认告警', 'plugin', 'PLUGIN', 'builtin-alerting', 0, 0, 0),
  ('plugin:alerting:silence', '管理告警静默', 'plugin', 'PLUGIN', 'builtin-alerting', 0, 0, 0),
  ('plugin:alerting:channel-manage', '管理告警渠道', 'plugin', 'PLUGIN', 'builtin-alerting', 0, 0, 0),
  ('plugin:alerting:directory-sync', '管理企业目录映射', 'plugin', 'PLUGIN', 'builtin-alerting', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`), `permission_group` = VALUES(`permission_group`),
  `source_type` = VALUES(`source_type`), `plugin_code` = VALUES(`plugin_code`),
  `updated_by` = VALUES(`updated_by`), `deleted` = 0;

INSERT INTO `sys_plugin_definition` (
  `plugin_code`, `plugin_name`, `plugin_type`, `description`, `author`, `plugin_api_version`,
  `builtin_flag`, `status`, `sort_no`, `schema_mode`, `supports_hot_disable`, `supports_data_purge`,
  `runtime_contributions_json`, `created_by`, `updated_by`, `deleted`
) VALUES (
  'builtin-alerting', '内置告警中心', 'OPERATIONS',
  'Rule-based production alerting with WeCom, Feishu, DingTalk and email delivery channels.',
  'Lumira', '1.0', 1, 'DISABLED', 920, 'SHARED', 1, 0,
  JSON_ARRAY('alert-evaluator', 'delivery-worker', 'channels', 'contact-groups', 'directory-mapping'), 0, 0, 0
)
ON DUPLICATE KEY UPDATE
  `plugin_name` = VALUES(`plugin_name`), `plugin_type` = VALUES(`plugin_type`), `description` = VALUES(`description`),
  `author` = VALUES(`author`), `plugin_api_version` = VALUES(`plugin_api_version`), `builtin_flag` = 1,
  `sort_no` = VALUES(`sort_no`), `schema_mode` = VALUES(`schema_mode`),
  `supports_hot_disable` = 1, `supports_data_purge` = 0,
  `runtime_contributions_json` = VALUES(`runtime_contributions_json`), `updated_by` = VALUES(`updated_by`), `deleted` = 0;

INSERT INTO `sys_plugin_version` (
  `plugin_code`, `version`, `min_platform_version`, `install_status`, `load_status`, `health_status`,
  `lifecycle_status`, `schema_status`, `is_active`, `rollbackable`, `metadata_json`, `validation_report_json`,
  `installed_at`, `created_by`, `updated_by`, `deleted`
) VALUES (
  'builtin-alerting', '1.0.0', '1.0.0', 'INSTALLED', 'LOADED', 'HEALTHY', 'DISABLED', 'READY', 1, 0,
  JSON_OBJECT('pluginCode','builtin-alerting','pluginName','内置告警中心','version','1.0.0','kind','OPERATIONS','builtin',true),
  JSON_OBJECT('builtin',true,'status','VERIFIED'), CURRENT_TIMESTAMP, 0, 0, 0
)
ON DUPLICATE KEY UPDATE
  `min_platform_version`=VALUES(`min_platform_version`), `install_status`=VALUES(`install_status`),
  `load_status`=VALUES(`load_status`), `health_status`=VALUES(`health_status`), `schema_status`=VALUES(`schema_status`),
  `is_active`=1, `metadata_json`=VALUES(`metadata_json`), `validation_report_json`=VALUES(`validation_report_json`),
  `updated_by`=VALUES(`updated_by`), `deleted`=0;

INSERT INTO `sys_plugin_menu_rel` (
  `plugin_code`, `plugin_version`, `menu_code`, `menu_name`, `route_path`, `icon`, `permission_key`,
  `parent_menu_code`, `sort_no`, `created_by`, `updated_by`, `deleted`
) VALUES (
  'builtin-alerting','1.0.0','plugin.alerting','告警中心','/settings/alerting','AlertOutlined',
  'plugin:alerting:view','settings.root',7,0,0,0
)
ON DUPLICATE KEY UPDATE
  `menu_name`=VALUES(`menu_name`), `route_path`=VALUES(`route_path`), `icon`=VALUES(`icon`),
  `permission_key`=VALUES(`permission_key`), `parent_menu_code`=VALUES(`parent_menu_code`),
  `sort_no`=VALUES(`sort_no`), `updated_by`=VALUES(`updated_by`), `deleted`=0;

INSERT INTO `sys_plugin_permission_rel` (
  `plugin_code`, `plugin_version`, `permission_key`, `permission_name`, `permission_group`,
  `created_by`, `updated_by`, `deleted`
) VALUES
  ('builtin-alerting','1.0.0','plugin:alerting:view','查看告警中心','alerting',0,0,0),
  ('builtin-alerting','1.0.0','plugin:alerting:manage','管理告警规则和联系人组','alerting',0,0,0),
  ('builtin-alerting','1.0.0','plugin:alerting:ack','确认告警','alerting',0,0,0),
  ('builtin-alerting','1.0.0','plugin:alerting:silence','管理告警静默','alerting',0,0,0),
  ('builtin-alerting','1.0.0','plugin:alerting:channel-manage','管理告警渠道','alerting',0,0,0),
  ('builtin-alerting','1.0.0','plugin:alerting:directory-sync','管理企业目录映射','alerting',0,0,0)
ON DUPLICATE KEY UPDATE
  `permission_name`=VALUES(`permission_name`), `permission_group`=VALUES(`permission_group`),
  `updated_by`=VALUES(`updated_by`), `deleted`=0;

INSERT INTO `sys_role_permission` (`role_id`,`permission_key`,`created_by`,`updated_by`,`deleted`)
SELECT 1001, p.`permission_key`, 0, 0, 0 FROM `sys_permission` p
WHERE p.`plugin_code` = 'builtin-alerting' AND p.`deleted` = 0
ON DUPLICATE KEY UPDATE `updated_by`=VALUES(`updated_by`), `deleted`=0;

INSERT INTO `ddd_read_model_version` (`context_name`,`scope`,`version`,`last_event_key`,`rebuilt_at`)
VALUES ('IAM','permission-snapshot',1,'builtin-alerting-permission-seed',NOW())
ON DUPLICATE KEY UPDATE `version`=`version`+1, `last_event_key`=VALUES(`last_event_key`), `rebuilt_at`=VALUES(`rebuilt_at`);
