-- Consolidated baseline merged from local schema through the current file center split.
-- Keep future schema changes as V2 and above.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `audit_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `login_type` varchar(32) NOT NULL DEFAULT 'PASSWORD',
  `login_result` varchar(32) NOT NULL,
  `fail_reason` varchar(255) DEFAULT NULL,
  `login_ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `request_id` varchar(64) NOT NULL,
  `trace_id` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_login_log_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `audit_operate_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `operation` varchar(128) NOT NULL,
  `request_uri` varchar(255) NOT NULL,
  `request_method` varchar(16) NOT NULL,
  `request_id` varchar(64) NOT NULL,
  `trace_id` varchar(64) NOT NULL,
  `result_code` varchar(32) NOT NULL,
  `cost_ms` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `audit_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `module_name` varchar(64) DEFAULT NULL,
  `action_name` varchar(128) DEFAULT NULL,
  `operation_type` varchar(32) NOT NULL,
  `result_status` varchar(32) NOT NULL,
  `detail_message` varchar(512) DEFAULT NULL,
  `request_id` varchar(64) DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_audit_operation_log_module_created` (`module_name`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `file_object` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `storage_type` varchar(32) NOT NULL,
  `bucket` varchar(128) DEFAULT NULL,
  `object_key` varchar(255) NOT NULL,
  `original_filename` varchar(255) NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `file_size` bigint NOT NULL DEFAULT '0',
  `checksum` varchar(128) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_object_key` (`tenant_id`,`object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `msg_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `notice_type` varchar(32) NOT NULL,
  `target_scope` varchar(32) NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  `target_role_id` bigint DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `content` text NOT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `publish_status` varchar(32) NOT NULL DEFAULT 'PUBLISHED',
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msg_notice_tenant_type_status_created` (`tenant_id`,`notice_type`,`publish_status`,`created_at`),
  KEY `idx_msg_notice_tenant_target_created` (`tenant_id`,`target_user_id`,`created_at`),
  KEY `idx_msg_notice_tenant_target_role_created` (`tenant_id`,`target_role_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `msg_notice_read` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `notice_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `read_at` datetime NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_notice_read` (`tenant_id`,`notice_id`,`user_id`),
  KEY `idx_msg_notice_read_user_created` (`tenant_id`,`user_id`,`read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `platform_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `source_type` varchar(64) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_key` varchar(128) NOT NULL,
  `payload_json` longtext NOT NULL,
  `dispatch_status` varchar(32) NOT NULL DEFAULT 'RECORDED',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `last_error` varchar(1024) DEFAULT NULL,
  `trace_id` varchar(128) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_platform_event_outbox_tenant_status` (`tenant_id`,`dispatch_status`),
  KEY `idx_platform_event_outbox_retry` (`dispatch_status`,`next_retry_at`),
  KEY `idx_platform_event_outbox_created_at` (`created_at`),
  KEY `idx_platform_event_outbox_event_key` (`event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `plugin_2fa_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `action_type` varchar(32) NOT NULL,
  `detail_message` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `plugin_2fa_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `secret_base32` varchar(128) NOT NULL,
  `email` varchar(128) DEFAULT NULL,
  `mobile` varchar(32) DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `recovery_hashes_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_2fa_binding_rel` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `plugin_2fa_challenge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `challenge_id` varchar(64) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `challenge_type` varchar(32) NOT NULL DEFAULT 'LOGIN',
  `expires_at` datetime NOT NULL,
  `consumed_flag` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_2fa_challenge_id` (`challenge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `plugin_announcement_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `title` varchar(128) NOT NULL,
  `content` varchar(2000) NOT NULL,
  `published_flag` tinyint NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_announcement_notice_title` (`tenant_id`,`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `plugin_sms_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `action_type` varchar(32) NOT NULL,
  `detail_message` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `plugin_sms_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `mobile` varchar(32) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_sms_binding_rel` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `plugin_sms_challenge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `challenge_id` varchar(64) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `mobile` varchar(32) NOT NULL,
  `provider_type` varchar(32) NOT NULL,
  `verification_hash` varchar(128) NOT NULL,
  `expires_at` datetime NOT NULL,
  `consumed_flag` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_sms_challenge_id` (`challenge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `plugin_sms_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `config_json` json NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_sms_provider_config` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `config_key` varchar(128) NOT NULL,
  `config_name` varchar(128) NOT NULL DEFAULT '',
  `config_value` varchar(2000) NOT NULL,
  `config_scope` varchar(32) NOT NULL DEFAULT 'PLATFORM',
  `is_system` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(512) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_key` (`tenant_id`,`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_data_scope_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `rule_code` varchar(64) NOT NULL,
  `rule_name` varchar(128) NOT NULL,
  `rule_expr` varchar(512) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_data_scope_rule_code` (`tenant_id`,`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT '0',
  `dept_code` varchar(64) NOT NULL,
  `dept_name` varchar(128) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_department_code` (`tenant_id`,`dept_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_dict_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `dict_type_id` bigint NOT NULL,
  `item_value` varchar(64) NOT NULL,
  `item_label` varchar(128) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `remark` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_item_value` (`tenant_id`,`dict_type_id`,`item_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `dict_code` varchar(64) NOT NULL,
  `dict_name` varchar(128) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `is_system` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_type_code` (`tenant_id`,`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT '0',
  `menu_code` varchar(64) NOT NULL,
  `menu_name` varchar(128) NOT NULL,
  `menu_type` varchar(32) NOT NULL,
  `path` varchar(255) DEFAULT NULL,
  `component` varchar(255) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `icon` varchar(64) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `permission_key` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_menu_code` (`tenant_id`,`menu_code`),
  KEY `idx_sys_menu_tenant_status` (`tenant_id`,`status`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `permission_key` varchar(128) NOT NULL,
  `permission_name` varchar(128) NOT NULL,
  `permission_group` varchar(64) DEFAULT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'CORE',
  `plugin_code` varchar(64) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_key` (`tenant_id`,`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_plugin_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_name` varchar(128) NOT NULL,
  `plugin_type` varchar(32) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `author` varchar(128) DEFAULT NULL,
  `plugin_api_version` varchar(32) NOT NULL,
  `builtin_flag` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `sort_no` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_definition_code` (`plugin_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_plugin_dependency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `depends_on_plugin_code` varchar(64) NOT NULL,
  `min_version` varchar(32) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_dependency_rel` (`plugin_code`,`depends_on_plugin_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_plugin_menu_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `menu_code` varchar(64) NOT NULL,
  `menu_name` varchar(128) NOT NULL,
  `route_path` varchar(255) NOT NULL,
  `icon` varchar(64) DEFAULT NULL,
  `permission_key` varchar(128) DEFAULT NULL,
  `parent_menu_code` varchar(64) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_menu_rel` (`plugin_code`,`plugin_version`,`menu_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_plugin_permission_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `permission_key` varchar(128) NOT NULL,
  `permission_name` varchar(128) NOT NULL,
  `permission_group` varchar(64) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_permission_rel` (`plugin_code`,`plugin_version`,`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_plugin_runtime_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) DEFAULT NULL,
  `operation_type` varchar(32) NOT NULL,
  `lifecycle_status` varchar(32) NOT NULL,
  `result_status` varchar(32) NOT NULL,
  `detail_message` varchar(512) DEFAULT NULL,
  `request_id` varchar(64) DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `failure_stack` text,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_sys_plugin_runtime_log_plugin_created` (`plugin_code`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_plugin_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  `config_json` json DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_tenant_rel` (`tenant_id`,`plugin_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_plugin_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `version` varchar(32) NOT NULL,
  `package_path` varchar(512) DEFAULT NULL,
  `artifact_path` varchar(512) DEFAULT NULL,
  `frontend_manifest_path` varchar(512) DEFAULT NULL,
  `backend_jar_path` varchar(512) DEFAULT NULL,
  `checksum` varchar(128) DEFAULT NULL,
  `signature_path` varchar(512) DEFAULT NULL,
  `min_platform_version` varchar(32) NOT NULL,
  `install_status` varchar(32) NOT NULL DEFAULT 'UPLOADED',
  `load_status` varchar(32) NOT NULL DEFAULT 'UNLOADED',
  `health_status` varchar(32) NOT NULL DEFAULT 'UNKNOWN',
  `is_active` tinyint NOT NULL DEFAULT '0',
  `rollbackable` tinyint NOT NULL DEFAULT '0',
  `metadata_json` json DEFAULT NULL,
  `validation_report_json` json DEFAULT NULL,
  `staged_path` varchar(512) DEFAULT NULL,
  `installed_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_version_code_version` (`plugin_code`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_position` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `position_code` varchar(64) NOT NULL,
  `position_name` varchar(128) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_position_code` (`tenant_id`,`position_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `role_code` varchar(64) NOT NULL,
  `role_name` varchar(128) NOT NULL,
  `role_type` varchar(32) NOT NULL DEFAULT 'CUSTOM',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`tenant_id`,`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_menu_rel` (`tenant_id`,`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `permission_key` varchar(128) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission_rel` (`tenant_id`,`role_id`,`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `real_name` varchar(64) DEFAULT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `birth_month` varchar(16) DEFAULT NULL,
  `gender` varchar(16) DEFAULT NULL,
  `region` varchar(128) DEFAULT NULL,
  `available_time` varchar(255) DEFAULT NULL,
  `id_card_number` varchar(64) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `mobile` varchar(32) DEFAULT NULL,
  `email` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role_rel` (`tenant_id`,`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_user_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_tenant_rel` (`tenant_id`,`user_id`),
  KEY `idx_sys_user_tenant_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_user_tenant_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `display_name` varchar(128) NOT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `locale` varchar(32) DEFAULT 'zh-CN',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_tenant_profile` (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_verification_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `factor_code` varchar(32) NOT NULL,
  `factor_name` varchar(64) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  `bound` tinyint NOT NULL DEFAULT '0',
  `email_required` tinyint NOT NULL DEFAULT '0',
  `masked_contact` varchar(255) DEFAULT NULL,
  `secret_key` varchar(255) DEFAULT NULL,
  `recovery_codes_json` json DEFAULT NULL,
  `verified_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_verification_binding` (`tenant_id`,`user_id`,`factor_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_verification_challenge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `challenge_id` varchar(64) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `factor_code` varchar(32) NOT NULL,
  `challenge_type` varchar(16) NOT NULL,
  `expires_at` datetime NOT NULL,
  `consumed_flag` tinyint NOT NULL DEFAULT '0',
  `setup_secret` varchar(255) DEFAULT NULL,
  `setup_uri` varchar(512) DEFAULT NULL,
  `recovery_codes_json` json DEFAULT NULL,
  `code_hash` varchar(128) DEFAULT NULL,
  `masked_contact` varchar(255) DEFAULT NULL,
  `debug_code` varchar(32) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_verification_challenge` (`challenge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `task_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `job_code` varchar(64) NOT NULL,
  `job_name` varchar(128) NOT NULL,
  `cron_expr` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `last_run_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_job_code` (`tenant_id`,`job_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tenant_domain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `domain` varchar(255) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_domain_domain` (`domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tenant_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_code` varchar(64) NOT NULL,
  `tenant_name` varchar(128) NOT NULL,
  `tenant_short_name` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_info_code` (`tenant_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tenant_package` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `package_code` varchar(64) NOT NULL,
  `package_name` varchar(128) NOT NULL,
  `expire_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_package_tenant_code` (`tenant_id`,`package_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `tenant_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `quota_key` varchar(64) NOT NULL,
  `quota_value` bigint NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_quota_key` (`tenant_id`,`quota_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seed data for `sys_config`
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7001, 1001, 'platform.name', '平台名称', 'SaaS Foundation', 'PLATFORM', 1, '平台展示名称', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7002, 1001, 'tenant.theme', '租户主题', 'default', 'TENANT', 0, '租户级展示主题', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7003, 1001, 'security.idle-timeout-seconds', '空闲超时时间', '1800', 'PLATFORM', 1, '会话在无操作状态下允许保持的秒数', 0, '2026-03-30T17:42:45', 0, '2026-04-15T01:20:52', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7004, 1001, 'security.access-token-expire-seconds', 'Access Token 过期时间', '1800', 'PLATFORM', 1, 'Access Token 的有效秒数', 0, '2026-03-30T17:42:45', 0, '2026-03-30T17:42:45', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7005, 1001, 'security.refresh-token-expire-seconds', 'Refresh Token 刷新时限', '604800', 'PLATFORM', 1, 'Refresh Token 的有效秒数', 0, '2026-03-30T17:42:45', 0, '2026-03-30T17:42:45', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7006, 1001, 'branding.website-name', '站点名称', '宏翔商道', 'PLATFORM', 0, '控制台顶部与浏览器标题展示名称', 0, '2026-04-03T16:25:38', 1001, '2026-04-06T04:47:43', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7007, 1001, 'branding.website-favicon-url', '站点图标地址', '', 'PLATFORM', 0, '浏览器标签页 icon 地址', 0, '2026-04-03T16:25:38', 1001, '2026-04-06T04:47:43', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7008, 1001, 'branding.website-logo-url', '站点 Logo 地址', '/api/uploads/2026/04/06/b95bb9702acf4bf9beb9a9d0056f8cb9.svg', 'PLATFORM', 0, '控制台左上角品牌 Logo 地址', 0, '2026-04-03T16:25:38', 1001, '2026-04-06T04:47:43', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7009, 1001, 'branding.footer-icp', '页脚 ICP 备案', '', 'PLATFORM', 0, '页脚备案信息', 0, '2026-04-03T16:25:38', 1001, '2026-04-06T04:47:43', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7010, 1001, 'branding.footer-copyright', '页脚版权声明', '', 'PLATFORM', 0, '页脚版权声明', 0, '2026-04-03T16:25:38', 1001, '2026-04-06T04:47:43', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7013, 1001, 'agreement.user-agreement-markdown', '用户协议', '欢迎使用宏翔商道后台管理系统。

在使用本系统前，请仔细阅读并理解以下内容：

1. 您在登录、访问和使用本系统相关功能时，应遵守国家法律法规以及平台规则。
2. 您应妥善保管账号、密码及相关身份信息，不得将账号转借、共享或提供给无关第三方。
3. 平台可能会在提供服务所必需的范围内处理您的账号、日志与业务数据。
4. 如您不同意本协议内容，请停止使用本系统。

本协议自发布或更新之日起生效。', 'PLATFORM', 0, '用户协议 Markdown', 0, '2026-04-07T04:32:25', 1001, '2026-05-04T13:44:05', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7014, 1001, 'agreement.privacy-agreement-markdown', '隐私协议', '我们重视并保护您的个人信息。

在提供服务所必需的范围内，我们可能会收集、使用、存储和传输您的账号信息、操作日志和业务数据。

我们不会在未经授权的情况下向无关第三方披露您的个人信息，除非法律法规或监管要求另有规定。

如您对隐私保护有任何疑问，请联系系统管理员。', 'PLATFORM', 0, '隐私协议 Markdown', 0, '2026-04-07T04:32:25', 1001, '2026-05-04T13:44:05', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7106, 1002, 'branding.website-name', '站点名称', '宏翔商道', 'PLATFORM', 0, '控制台顶部与浏览器标题展示名称', 0, '2026-04-03T16:25:38', 0, '2026-04-03T16:25:38', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7107, 1002, 'branding.website-favicon-url', '站点图标地址', '', 'PLATFORM', 0, '浏览器标签页 icon 地址', 0, '2026-04-03T16:25:38', 0, '2026-04-03T16:25:38', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7108, 1002, 'branding.website-logo-url', '站点 Logo 地址', '', 'PLATFORM', 0, '控制台左上角品牌 Logo 地址', 0, '2026-04-03T16:25:38', 0, '2026-04-03T16:25:38', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7109, 1002, 'branding.footer-icp', '页脚 ICP 备案', '', 'PLATFORM', 0, '页脚备案信息', 0, '2026-04-03T16:25:38', 0, '2026-04-03T16:25:38', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7110, 1002, 'branding.footer-copyright', '页脚版权声明', '', 'PLATFORM', 0, '页脚版权声明', 0, '2026-04-03T16:25:38', 0, '2026-04-03T16:25:38', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7111, 1001, 'watermark.enabled', '水印开关', 'true', 'PLATFORM', 0, '全局水印开关', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7112, 1001, 'watermark.mode', '水印模式', 'TEXT', 'PLATFORM', 0, 'TEXT/IMAGE', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7113, 1001, 'watermark.text-lines', '水印文本', '宏翔商道
后台管理系统', 'PLATFORM', 0, '多行文本水印', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7114, 1001, 'watermark.image-url', '水印图片', '', 'PLATFORM', 0, '图片水印 URL', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7115, 1001, 'watermark.font-color', '字体颜色', 'rgba(0,0,0,0.15)', 'PLATFORM', 0, '字体颜色', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7116, 1001, 'watermark.font-size', '字体大小', '14', 'PLATFORM', 0, '字体大小', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7117, 1001, 'watermark.font-weight', '字体粗细', 'normal', 'PLATFORM', 0, '字体粗细', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7118, 1001, 'watermark.rotate', '旋转角度', '-22', 'PLATFORM', 0, '旋转角度', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7119, 1001, 'watermark.gap-x', '横向间距', '300', 'PLATFORM', 0, '横向间距', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7120, 1001, 'watermark.gap-y', '纵向间距', '200', 'PLATFORM', 0, '纵向间距', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7121, 1001, 'watermark.offset-x', '横向偏移', '0', 'PLATFORM', 0, '横向偏移', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7122, 1001, 'watermark.offset-y', '纵向偏移', '0', 'PLATFORM', 0, '纵向偏移', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7123, 1001, 'watermark.z-index', '层级', '9', 'PLATFORM', 0, 'z-index', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7124, 1001, 'watermark.opacity', '透明度', '0.15', 'PLATFORM', 0, '透明度', 1001, '2026-04-05T17:14:57', 1001, '2026-05-04T01:26:17', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7137, 1001, 'security.allow-multi-device-login', '多设备登录', '0', 'PLATFORM', 1, '是否允许同一账号在多个设备同时在线', 0, '2026-04-06T23:53:20', 0, '2026-04-06T23:53:20', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7201, 1001, 'branding.company-name', '公司名称', '宏翔商道', 'PLATFORM', 0, '页脚版权主体名称', 0, '2026-04-07T04:26:31', 0, '2026-04-07T04:26:31', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7202, 1001, 'branding.copyright-start-year', '版权起始年份', '2025', 'PLATFORM', 0, '页脚版权起始年份', 0, '2026-04-07T04:26:31', 0, '2026-04-07T04:26:31', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7203, 1002, 'branding.company-name', '公司名称', '宏翔商道', 'PLATFORM', 0, '页脚版权主体名称', 0, '2026-04-07T04:26:31', 0, '2026-04-07T04:26:31', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7204, 1002, 'branding.copyright-start-year', '版权起始年份', '2025', 'PLATFORM', 0, '页脚版权起始年份', 0, '2026-04-07T04:26:31', 0, '2026-04-07T04:26:31', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7205, 1001, 'branding.github-link-url', 'GitHub 链接', 'https://github.com/Elexvx/legendary-invention', 'PLATFORM', 0, '顶部 GitHub 图标跳转地址', 0, '2026-04-07T04:26:31', 0, '2026-04-07T04:26:31', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7206, 1001, 'branding.help-link-url', '帮助链接', 'https://github.com/Elexvx/legendary-invention/blob/main/README.md', 'PLATFORM', 0, '顶部帮助图标跳转地址', 0, '2026-04-07T04:26:31', 0, '2026-04-07T04:26:31', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7207, 1002, 'branding.github-link-url', 'GitHub 链接', 'https://github.com/Elexvx/legendary-invention', 'PLATFORM', 0, '顶部 GitHub 图标跳转地址', 0, '2026-04-07T04:26:31', 0, '2026-04-07T04:26:31', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7208, 1002, 'branding.help-link-url', '帮助链接', 'https://github.com/Elexvx/legendary-invention/blob/main/README.md', 'PLATFORM', 0, '顶部帮助图标跳转地址', 0, '2026-04-07T04:26:31', 0, '2026-04-07T04:26:31', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7213, 1001, 'security.captcha-enabled', '验证码开关', '0', 'PLATFORM', 1, '是否开启登录时的人机验证码', 0, '2026-04-07T05:42:47', 0, '2026-05-04T01:48:47', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7214, 1001, 'security.captcha-type', '验证码类型', 'IMAGE', 'PLATFORM', 1, '验证码类型：IMAGE=图片验证码', 0, '2026-04-07T05:42:47', 0, '2026-04-13T17:18:29', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7215, 1001, 'security.login-defense-window-minutes', '登录防御统计窗口', '5', 'PLATFORM', 1, '统计登录尝试与错误次数的时间窗口（分钟）', 0, '2026-04-07T05:42:47', 0, '2026-04-07T05:42:47', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7216, 1001, 'security.login-max-validation-attempts', '最大验证次数', '100', 'PLATFORM', 1, '统计窗口内允许的最大验证码/登录验证尝试次数', 0, '2026-04-07T05:42:47', 0, '2026-04-07T05:42:47', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7217, 1001, 'security.login-max-failure-count', '最大错误次数', '10', 'PLATFORM', 1, '统计窗口内允许的最大登录失败次数', 0, '2026-04-07T05:42:47', 0, '2026-04-07T05:42:47', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7218, 1001, 'security.password-min-length', '密码最短长度', '6', 'PLATFORM', 1, '用户密码允许的最少字符数', 0, '2026-04-07T05:42:47', 0, '2026-04-07T05:42:47', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7219, 1001, 'security.password-require-uppercase', '密码必须包含大写字母', '0', 'PLATFORM', 1, '强制密码包含 A-Z', 0, '2026-04-07T05:42:47', 0, '2026-04-07T05:42:47', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7220, 1001, 'security.password-require-lowercase', '密码必须包含小写字母', '0', 'PLATFORM', 1, '强制密码包含 a-z', 0, '2026-04-07T05:42:47', 0, '2026-04-07T05:42:47', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7221, 1001, 'security.password-require-special-character', '密码必须包含特殊字符', '0', 'PLATFORM', 1, '强制密码包含特殊字符', 0, '2026-04-07T05:42:47', 0, '2026-04-07T05:42:47', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7222, 1001, 'security.password-allow-consecutive-characters', '允许连续字符', '1', 'PLATFORM', 1, '是否允许密码中出现连续字符', 0, '2026-04-07T05:42:47', 0, '2026-04-07T05:42:47', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7265, 1001, 'profile.field.avatar.visible', '头像展示开关', 'true', 'PLATFORM', 0, '控制个人中心是否展示头像上传与预览区域', 1001, '2026-04-11T15:57:13', 1001, '2026-04-11T20:32:06', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7266, 1001, 'profile.field.real-name.visible', '姓名展示开关', 'true', 'PLATFORM', 0, '控制个人中心是否展示姓名字段', 1001, '2026-04-11T15:57:13', 1001, '2026-04-11T20:32:06', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7267, 1001, 'profile.field.mobile.visible', '手机号展示开关', 'true', 'PLATFORM', 0, '控制个人中心是否展示手机号字段', 1001, '2026-04-11T15:57:13', 1001, '2026-04-11T20:32:06', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7268, 1001, 'profile.field.email.visible', '邮箱展示开关', 'true', 'PLATFORM', 0, '控制个人中心是否展示邮箱字段', 1001, '2026-04-11T15:57:13', 1001, '2026-04-11T20:32:06', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7269, 1001, 'profile.field.birth-month.visible', '出生年月展示开关', 'true', 'PLATFORM', 0, '控制个人中心是否展示出生年月字段', 1001, '2026-04-11T15:57:13', 1001, '2026-04-11T20:32:06', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7270, 1001, 'profile.field.gender.visible', '性别展示开关', 'true', 'PLATFORM', 0, '控制个人中心是否展示性别字段', 1001, '2026-04-11T15:57:13', 1001, '2026-04-11T20:32:06', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7271, 1001, 'profile.field.region.visible', '所在地区展示开关', 'true', 'PLATFORM', 0, '控制个人中心是否展示所在地区字段', 1001, '2026-04-11T15:57:13', 1001, '2026-04-11T20:32:06', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7272, 1001, 'profile.field.available-time.visible', '可工作时间展示开关', 'true', 'PLATFORM', 0, '控制个人中心是否展示可工作时间字段', 1001, '2026-04-11T15:57:13', 1001, '2026-04-11T20:32:06', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7273, 1001, 'profile.field.id-card-number.visible', '身份证号码展示开关', 'true', 'PLATFORM', 0, '控制个人中心是否展示身份证号码字段', 1001, '2026-04-11T15:57:13', 1001, '2026-04-11T20:32:06', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7372, 1001, 'verification.totp.enabled', '2FA 启用', 'true', 'PLATFORM', 0, '是否启用 2FA 登录方式', 1001, '2026-04-25T09:24:01', 1001, '2026-05-04T01:31:01', 0);
INSERT IGNORE INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7373, 1001, 'verification.email-login.enabled', '邮箱验证码登录', 'false', 'PLATFORM', 0, '是否启用邮箱验证码登录', 1001, '2026-04-25T09:24:01', 1001, '2026-05-04T01:31:01', 0);

-- Seed data for `sys_dict_item`
INSERT IGNORE INTO `sys_dict_item` (`id`, `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `sort_no`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `status`, `remark`) VALUES (6001, 1001, 5001, 'ENABLED', '启用', 1, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0, 'ENABLED', NULL);
INSERT IGNORE INTO `sys_dict_item` (`id`, `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `sort_no`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `status`, `remark`) VALUES (6002, 1001, 5001, 'DISABLED', '停用', 2, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0, 'ENABLED', NULL);
INSERT IGNORE INTO `sys_dict_item` (`id`, `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `sort_no`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `status`, `remark`) VALUES (6003, 1001, 5002, 'SYSTEM', '系统角色', 1, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0, 'ENABLED', NULL);
INSERT IGNORE INTO `sys_dict_item` (`id`, `tenant_id`, `dict_type_id`, `item_value`, `item_label`, `sort_no`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `status`, `remark`) VALUES (6004, 1001, 5002, 'CUSTOM', '自定义角色', 2, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0, 'ENABLED', NULL);

-- Seed data for `sys_dict_type`
INSERT IGNORE INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `status`, `is_system`, `remark`) VALUES (5001, 1001, 'user_status', '用户状态', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0, 'ENABLED', 1, '系统用户状态字典');
INSERT IGNORE INTO `sys_dict_type` (`id`, `tenant_id`, `dict_code`, `dict_name`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `status`, `is_system`, `remark`) VALUES (5002, 1001, 'role_type', '角色类型', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0, 'ENABLED', 1, '系统角色类型字典');

-- Seed data for `sys_menu`
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3001, 1001, 0, 'dashboard.home', '首页', 'MENU', '/dashboard/home', '@/pages/dashboard/Home', 0, '2026-03-29T17:20:41', 1001, '2026-04-07T04:07:16', 0, 'DashboardOutlined', 0, 'dashboard:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3002, 1001, 0, 'settings.root', '系统设置', 'CATALOG', '/settings', NULL, 0, '2026-03-29T17:20:41', 0, '2026-04-20T23:51:46', 0, 'SettingOutlined', 20, 'system:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3003, 1001, 3002, 'settings.plugins', '插件管理中心', 'MENU', '/settings/plugins', '@/pages/settings/Plugins', 0, '2026-03-29T17:20:41', 1001, '2026-04-07T04:07:16', 0, 'ApiOutlined', 0, 'plugin:management:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3004, 1001, 3020, 'profile.center', '个人中心', 'MENU', '/user-center/profile', '@/pages/profile/Center', 0, '2026-03-29T17:20:41', 0, '2026-04-10T23:14:05', 0, 'UserOutlined', 25, 'profile:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3005, 1001, 0, 'tenant.overview', '租户中心', 'MENU', '/tenant/overview', '@/pages/tenant/Overview', 0, '2026-03-29T20:37:31', 1001, '2026-04-07T04:07:16', 0, 'ApartmentOutlined', 1, 'tenant:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3006, 1001, 3020, 'user.center.permissions', '权限管理', 'MENU', '/user-center/permissions', '@/pages/iam/Overview', 0, '2026-03-29T20:37:31', 0, '2026-04-14T02:28:36', 0, 'SafetyCertificateOutlined', 24, 'iam:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3007, 1001, 3016, 'settings.monitoring.audit', '审计中心', 'MENU', '/settings/monitoring/audit', '@/pages/settings/monitoring/Audit', 0, '2026-03-29T20:37:31', 0, '2026-04-14T02:28:36', 0, 'AuditOutlined', 25, 'audit:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3008, 1001, 3020, 'system.users', '用户管理', 'MENU', '/user-center/users', '@/pages/system/users', 0, '2026-03-29T20:37:31', 0, '2026-04-14T02:28:36', 0, 'UserOutlined', 21, 'system:user:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3009, 1001, 3020, 'system.roles', '角色管理', 'MENU', '/user-center/roles', '@/pages/system/roles', 0, '2026-03-29T20:37:31', 0, '2026-04-14T02:28:36', 0, 'SafetyOutlined', 23, 'system:role:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3010, 1001, 3002, 'settings.menus', '菜单管理', 'MENU', '/settings/menus', '@/pages/settings/menus', 0, '2026-03-29T20:37:31', 1001, '2026-04-07T04:07:16', 0, 'MenuOutlined', 1, 'system:menu:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3011, 1001, 3002, 'settings.dicts', '字典管理', 'MENU', '/settings/dicts', '@/pages/settings/dicts', 0, '2026-03-29T20:37:31', 1001, '2026-04-07T04:07:16', 0, 'DatabaseOutlined', 2, 'system:dict:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3012, 1001, 3002, 'system.configs', '参数配置', 'MENU', '/system/configs', '@/pages/system/configs', 0, '2026-03-29T20:37:31', 0, '2026-04-14T03:11:10', 1, 'SettingOutlined', 3, 'system:config:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3013, 1001, 3002, 'settings.security', '安全设置', 'MENU', '/settings/security', '@/pages/settings/security', 0, '2026-03-30T17:42:45', 1001, '2026-04-07T04:07:16', 0, 'SafetyOutlined', 4, 'system:config:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3014, 1001, 3002, 'settings.personalization', '个性化设置', 'MENU', '/settings/personalization', '@/pages/settings/personalization', 0, '2026-04-03T16:25:38', 1001, '2026-04-07T04:07:16', 0, 'SkinOutlined', 5, 'system:config:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3015, 1001, 3020, 'system.online-users', '在线用户', 'MENU', '/user-center/online-users', '@/pages/system/online-users', 0, '2026-04-05T22:53:05', 0, '2026-04-14T02:28:36', 0, 'UserSwitchOutlined', 22, 'system:online-user:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3016, 1001, 0, 'settings.monitoring.root', '系统监控', 'CATALOG', '/settings/monitoring', '@/pages/settings/monitoring/index', 0, '2026-04-06T11:55:39', 0, '2026-04-14T02:32:25', 0, 'FundOutlined', 21, 'system:monitor:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3017, 1001, 3016, 'settings.monitoring.service', '服务监控', 'MENU', '/settings/monitoring/service', '@/pages/settings/monitoring/Service', 0, '2026-04-06T11:55:39', 0, '2026-04-14T02:28:36', 0, 'RadarChartOutlined', 22, 'system:monitor:service:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3018, 1001, 3016, 'settings.monitoring.redis', 'Redis监控', 'MENU', '/settings/monitoring/redis', '@/pages/settings/monitoring/Redis', 0, '2026-04-06T11:55:39', 0, '2026-04-14T02:28:36', 0, 'DatabaseOutlined', 23, 'system:monitor:redis:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3019, 1001, 3016, 'settings.monitoring.api-docs', '接口文档', 'MENU', '/settings/monitoring/api-docs', '@/pages/settings/monitoring/ApiDocs', 0, '2026-04-06T11:55:39', 0, '2026-04-14T02:28:36', 0, 'FileTextOutlined', 24, 'system:monitor:docs:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3020, 1001, 0, 'user.center.root', '用户中心', 'CATALOG', '/user-center', '@/pages/user-center/index', 0, '2026-04-07T04:02:31', 0, '2026-04-14T02:31:58', 0, 'TeamOutlined', 18, 'user:center:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3025, 1001, 3002, 'settings.profile-fields', '字段管理', 'MENU', '/settings/profile-fields', '@/pages/settings/profile-fields', 0, '2026-04-11T15:36:20', 0, '2026-04-11T15:36:20', 0, 'FormOutlined', 29, 'system:config:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3026, 1001, 3002, 'settings.notifications', '站内信归档', 'MENU', '/settings/notifications', '@/pages/settings/notifications/index', 0, '2026-04-14T01:30:39', 0, '2026-04-23T01:00:47', 0, 'NotificationOutlined', 22, 'system:notification:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3027, 1001, 3002, 'settings.verification', '验证管理', 'MENU', '/settings/verification', '@/pages/settings/verification', 0, '2026-04-22T21:55:16', 0, '2026-04-22T21:55:16', 0, 'SafetyOutlined', 28, 'system:verification:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (3028, 1001, 0, 'files.root', '文件中心', 'CATALOG', '/settings/files', NULL, 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0, 'FolderOpenOutlined', 19, NULL, 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4001, 1002, 0, 'dashboard.home', '首页', 'MENU', '/dashboard/home', '@/pages/dashboard/Home', 0, '2026-03-29T17:20:41', 0, '2026-03-29T17:20:41', 0, 'DashboardOutlined', 10, 'dashboard:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4002, 1002, 0, 'settings.root', '系统设置', 'CATALOG', '/settings', NULL, 0, '2026-03-29T17:20:41', 0, '2026-04-20T23:51:46', 0, 'SettingOutlined', 20, 'system:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4003, 1002, 4002, 'settings.plugins', '插件管理中心', 'MENU', '/settings/plugins', '@/pages/settings/Plugins', 0, '2026-03-29T17:20:41', 0, '2026-03-29T17:20:41', 0, 'ApiOutlined', 21, 'plugin:management:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4004, 1002, 4020, 'profile.center', '个人中心', 'MENU', '/user-center/profile', '@/pages/profile/Center', 0, '2026-03-29T17:20:41', 0, '2026-04-10T23:14:05', 0, 'UserOutlined', 25, 'profile:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4008, 1002, 4020, 'system.users', '用户管理', 'MENU', '/user-center/users', '@/pages/system/users', 0, '2026-04-07T04:02:31', 0, '2026-04-07T04:02:31', 0, 'UserOutlined', 21, 'system:user:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4009, 1002, 4020, 'system.roles', '角色管理', 'MENU', '/user-center/roles', '@/pages/system/roles', 0, '2026-04-07T04:02:31', 0, '2026-04-07T04:02:31', 0, 'SafetyOutlined', 23, 'system:role:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4010, 1002, 0, 'tenant.overview', '租户中心', 'MENU', '/tenant/overview', '@/pages/tenant/Overview', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0, 'ApartmentOutlined', 15, 'tenant:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4011, 1002, 4020, 'user.center.permissions', '权限管理', 'MENU', '/user-center/permissions', '@/pages/iam/Overview', 0, '2026-03-29T20:37:31', 0, '2026-04-14T02:28:36', 0, 'SafetyCertificateOutlined', 24, 'iam:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4012, 1002, 4016, 'settings.monitoring.audit', '审计中心', 'MENU', '/settings/monitoring/audit', '@/pages/settings/monitoring/Audit', 0, '2026-03-29T20:37:31', 0, '2026-04-07T00:24:06', 0, 'AuditOutlined', 25, 'audit:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4014, 1002, 4002, 'settings.personalization', '个性化设置', 'MENU', '/settings/personalization', '@/pages/settings/personalization', 0, '2026-04-03T16:25:38', 0, '2026-04-03T16:25:38', 0, 'SkinOutlined', 28, 'system:config:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4015, 1002, 4020, 'system.online-users', '在线用户', 'MENU', '/user-center/online-users', '@/pages/system/online-users', 0, '2026-04-05T22:53:05', 0, '2026-04-07T04:02:31', 0, 'UserSwitchOutlined', 22, 'system:online-user:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4016, 1002, 0, 'settings.monitoring.root', '系统监控', 'CATALOG', '/settings/monitoring', '@/pages/settings/monitoring/index', 0, '2026-04-06T11:55:39', 0, '2026-04-14T02:32:25', 0, 'FundOutlined', 21, 'system:monitor:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4017, 1002, 4016, 'settings.monitoring.service', '服务监控', 'MENU', '/settings/monitoring/service', '@/pages/settings/monitoring/Service', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0, 'RadarChartOutlined', 22, 'system:monitor:service:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4018, 1002, 4016, 'settings.monitoring.redis', 'Redis监控', 'MENU', '/settings/monitoring/redis', '@/pages/settings/monitoring/Redis', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0, 'DatabaseOutlined', 23, 'system:monitor:redis:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4019, 1002, 4016, 'settings.monitoring.api-docs', '接口文档', 'MENU', '/settings/monitoring/api-docs', '@/pages/settings/monitoring/ApiDocs', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0, 'FileTextOutlined', 24, 'system:monitor:docs:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4020, 1002, 0, 'user.center.root', '用户中心', 'CATALOG', '/user-center', '@/pages/user-center/index', 0, '2026-04-07T04:02:31', 0, '2026-04-14T02:31:58', 0, 'TeamOutlined', 18, 'user:center:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4025, 1002, 4002, 'settings.profile-fields', '字段管理', 'MENU', '/settings/profile-fields', '@/pages/settings/profile-fields', 0, '2026-04-11T15:36:20', 0, '2026-04-11T15:36:20', 0, 'FormOutlined', 29, 'system:config:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4026, 1002, 4002, 'settings.notifications', '站内信归档', 'MENU', '/settings/notifications', '@/pages/settings/notifications/index', 0, '2026-04-14T01:30:39', 0, '2026-04-23T01:00:47', 0, 'NotificationOutlined', 22, 'system:notification:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4027, 1002, 4002, 'settings.verification', '验证管理', 'MENU', '/settings/verification', '@/pages/settings/verification', 0, '2026-04-22T21:55:16', 0, '2026-04-22T21:55:16', 0, 'SafetyOutlined', 28, 'system:verification:view', 'ENABLED');
INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES (4028, 1002, 0, 'files.root', '文件中心', 'CATALOG', '/settings/files', NULL, 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0, 'FolderOpenOutlined', 19, NULL, 'ENABLED');

-- Seed data for `sys_permission`
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1, 1001, 'dashboard:view', '查看首页', 'dashboard', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (2, 1001, 'system:view', '查看系统管理', 'system', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (3, 1001, 'profile:view', '查看个人中心', 'profile', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (4, 1001, 'plugin:management:view', '查看插件管理', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (5, 1001, 'plugin:management:upload', '上传插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (6, 1001, 'plugin:management:install', '安装插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7, 1001, 'plugin:management:upgrade', '升级插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (8, 1001, 'plugin:management:rollback', '回滚插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (9, 1001, 'plugin:management:enable', '启用插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (10, 1001, 'plugin:management:disable', '停用插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (11, 1001, 'plugin:management:logs', '查看插件日志', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (12, 1002, 'dashboard:view', '查看首页', 'dashboard', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (13, 1002, 'system:view', '查看系统管理', 'system', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (14, 1002, 'profile:view', '查看个人中心', 'profile', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (15, 1002, 'plugin:management:view', '查看插件管理', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (16, 1002, 'plugin:management:upload', '上传插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (17, 1002, 'plugin:management:install', '安装插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (18, 1002, 'plugin:management:upgrade', '升级插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (19, 1002, 'plugin:management:rollback', '回滚插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (20, 1002, 'plugin:management:enable', '启用插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (21, 1002, 'plugin:management:disable', '停用插件', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (22, 1002, 'plugin:management:logs', '查看插件日志', 'plugin', 'CORE', NULL, 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (27, 1001, 'tenant:view', '查看租户中心', 'tenant', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (28, 1001, 'audit:view', '查看审计中心', 'audit', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (29, 1001, 'audit:login:view', '查看登录日志', 'audit', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (30, 1001, 'audit:operation:view', '查看操作日志', 'audit', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (31, 1001, 'iam:view', '查看权限中心', 'iam', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (32, 1001, 'system:user:view', '查看用户管理', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (33, 1001, 'system:user:create', '创建用户', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (34, 1001, 'system:user:update', '编辑用户', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (35, 1001, 'system:user:status', '启停用户', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (36, 1001, 'system:role:view', '查看角色管理', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (37, 1001, 'system:role:create', '创建角色', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (38, 1001, 'system:role:update', '编辑角色', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (39, 1001, 'system:role:permissions', '分配角色权限', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (40, 1001, 'system:menu:view', '查看菜单管理', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (41, 1001, 'system:menu:create', '创建菜单', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (42, 1001, 'system:menu:update', '编辑菜单', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (43, 1001, 'system:menu:status', '启停菜单', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (44, 1001, 'system:dict:view', '查看字典管理', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (45, 1001, 'system:dict:create', '创建字典', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (46, 1001, 'system:dict:update', '编辑字典', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (47, 1001, 'system:config:view', '查看参数配置', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (48, 1001, 'system:config:update', '编辑参数配置', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (49, 1002, 'tenant:view', '查看租户中心', 'tenant', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (50, 1002, 'audit:view', '查看审计中心', 'audit', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (51, 1002, 'audit:login:view', '查看登录日志', 'audit', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (52, 1002, 'audit:operation:view', '查看操作日志', 'audit', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (53, 1002, 'iam:view', '查看权限中心', 'iam', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (54, 1002, 'system:user:view', '查看用户管理', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (55, 1002, 'system:user:create', '创建用户', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (56, 1002, 'system:user:update', '编辑用户', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (57, 1002, 'system:user:status', '启停用户', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (58, 1002, 'system:role:view', '查看角色管理', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (59, 1002, 'system:role:create', '创建角色', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (60, 1002, 'system:role:update', '编辑角色', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (61, 1002, 'system:role:permissions', '分配角色权限', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (62, 1002, 'system:menu:view', '查看菜单管理', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (63, 1002, 'system:menu:create', '创建菜单', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (64, 1002, 'system:menu:update', '编辑菜单', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (65, 1002, 'system:menu:status', '启停菜单', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (66, 1002, 'system:dict:view', '查看字典管理', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (67, 1002, 'system:dict:create', '创建字典', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (68, 1002, 'system:dict:update', '编辑字典', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (69, 1002, 'system:config:view', '查看参数配置', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (70, 1002, 'system:config:update', '编辑参数配置', 'system', 'CORE', NULL, 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (80, 1001, 'system:online-user:view', '查看在线用户', 'system', 'CORE', NULL, 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (81, 1001, 'system:online-user:kick', '踢出在线会话', 'system', 'CORE', NULL, 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (82, 1001, 'system:online-user:ban', '封禁在线用户', 'system', 'CORE', NULL, 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (83, 1002, 'system:online-user:ban', '封禁在线用户', 'system', 'CORE', NULL, 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (84, 1002, 'system:online-user:kick', '踢出在线会话', 'system', 'CORE', NULL, 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (85, 1002, 'system:online-user:view', '查看在线用户', 'system', 'CORE', NULL, 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (86, 1001, 'system:monitor:view', '查看系统监控', 'system', 'CORE', NULL, 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (87, 1001, 'system:monitor:service:view', '查看服务监控', 'system', 'CORE', NULL, 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (88, 1001, 'system:monitor:redis:view', '查看Redis监控', 'system', 'CORE', NULL, 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (89, 1001, 'system:monitor:docs:view', '查看接口文档', 'system', 'CORE', NULL, 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (90, 1002, 'system:monitor:docs:view', '查看接口文档', 'system', 'CORE', NULL, 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (91, 1002, 'system:monitor:redis:view', '查看Redis监控', 'system', 'CORE', NULL, 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (92, 1002, 'system:monitor:service:view', '查看服务监控', 'system', 'CORE', NULL, 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (93, 1002, 'system:monitor:view', '查看系统监控', 'system', 'CORE', NULL, 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (97, 1001, 'plugin:2fa:view', '查看 2FA 验证', '2fa', 'PLUGIN', '2fa', 0, '2026-04-10T05:45:16', 0, '2026-04-12T14:08:14', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (98, 1001, 'plugin:2fa:manage', '管理 2FA 验证', '2fa', 'PLUGIN', '2fa', 0, '2026-04-10T05:45:16', 0, '2026-04-12T14:08:14', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (101, 1001, 'plugin:sms:view', '查看短信验证', 'sms', 'PLUGIN', 'sms', 0, '2026-04-10T23:01:15', 0, '2026-04-10T23:01:15', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (102, 1001, 'plugin:sms:manage', '管理短信验证', 'sms', 'PLUGIN', 'sms', 0, '2026-04-10T23:01:15', 0, '2026-04-10T23:01:15', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (103, 1001, 'tenant:create', '创建租户', 'tenant', 'CORE', NULL, 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (104, 1001, 'tenant:update', '编辑租户', 'tenant', 'CORE', NULL, 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (105, 1001, 'tenant:delete', '删除租户', 'tenant', 'CORE', NULL, 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (106, 1002, 'tenant:create', '创建租户', 'tenant', 'CORE', NULL, 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (107, 1002, 'tenant:delete', '删除租户', 'tenant', 'CORE', NULL, 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (108, 1002, 'tenant:update', '编辑租户', 'tenant', 'CORE', NULL, 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (109, 1001, 'user:center:view', '查看用户中心', 'user-center', 'CORE', NULL, 0, '2026-04-11T12:00:32', 0, '2026-04-11T12:00:32', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (110, 1002, 'user:center:view', '查看用户中心', 'user-center', 'CORE', NULL, 0, '2026-04-11T12:00:32', 0, '2026-04-11T12:00:32', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (113, 1001, 'plugin:announcement:view', '查看公告', 'announcement', 'PLUGIN', 'announcement', 0, '2026-04-13T18:46:57', 0, '2026-04-13T18:46:57', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (114, 1001, 'plugin:announcement:write', '维护公告', 'announcement', 'PLUGIN', 'announcement', 0, '2026-04-13T18:46:57', 0, '2026-04-13T18:46:57', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (115, 1001, 'system:notification:view', '查看站内信归档', 'system', 'CORE', NULL, 0, '2026-04-14T01:30:39', 0, '2026-04-23T01:00:47', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (116, 1001, 'system:notification:write', '手动发布站内信', 'system', 'CORE', NULL, 0, '2026-04-14T01:30:39', 0, '2026-04-22T23:54:15', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (117, 1002, 'system:notification:view', '查看站内信归档', 'system', 'CORE', NULL, 0, '2026-04-14T01:30:39', 0, '2026-04-23T01:00:47', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (118, 1002, 'system:notification:write', '手动发布站内信', 'system', 'CORE', NULL, 0, '2026-04-14T01:30:39', 0, '2026-04-22T23:54:15', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (123, 1001, 'message:message:view', '查看站内信', 'message', 'CORE', NULL, 0, '2026-04-19T13:46:52', 0, '2026-04-23T01:00:47', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (124, 1001, 'message:message:write', '发送站内信', 'message', 'CORE', NULL, 0, '2026-04-19T13:46:52', 0, '2026-04-23T01:00:47', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (125, 1001, 'message:message:read', '标记站内信已读', 'message', 'CORE', NULL, 0, '2026-04-19T13:46:52', 0, '2026-04-23T01:00:47', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (126, 1001, 'message:message:retract', '撤回站内信', 'message', 'CORE', NULL, 0, '2026-04-19T13:46:52', 0, '2026-04-23T01:00:47', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (131, 1002, 'message:message:read', '标记站内信已读', 'message', 'CORE', NULL, 0, '2026-04-19T13:46:52', 0, '2026-04-23T01:00:47', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (132, 1002, 'message:message:retract', '撤回站内信', 'message', 'CORE', NULL, 0, '2026-04-19T13:46:52', 0, '2026-04-23T01:00:47', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (133, 1002, 'message:message:view', '查看站内信', 'message', 'CORE', NULL, 0, '2026-04-19T13:46:52', 0, '2026-04-23T01:00:47', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (134, 1002, 'message:message:write', '发送站内信', 'message', 'CORE', NULL, 0, '2026-04-19T13:46:52', 0, '2026-04-23T01:00:47', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (142, 1001, 'system:verification:view', '查看验证管理', 'system', 'CORE', NULL, 0, '2026-04-22T21:55:16', 0, '2026-04-22T21:55:16', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (143, 1001, 'system:verification:manage', '管理验证方式', 'system', 'CORE', NULL, 0, '2026-04-22T21:55:16', 0, '2026-04-22T21:55:16', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (144, 1002, 'system:verification:manage', '管理验证方式', 'system', 'CORE', NULL, 0, '2026-04-22T21:55:16', 0, '2026-04-22T21:55:16', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (145, 1002, 'system:verification:view', '查看验证管理', 'system', 'CORE', NULL, 0, '2026-04-22T21:55:16', 0, '2026-04-22T21:55:16', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (146, 1001, 'system:file:view', '查看文件中心', 'system', 'CORE', NULL, 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (147, 1001, 'system:file:upload', '上传文档', 'system', 'CORE', NULL, 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (148, 1001, 'system:file:delete', '删除文档', 'system', 'CORE', NULL, 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (149, 1002, 'system:file:delete', '删除文档', 'system', 'CORE', NULL, 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (150, 1002, 'system:file:upload', '上传文档', 'system', 'CORE', NULL, 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);
INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (151, 1002, 'system:file:view', '查看文件中心', 'system', 'CORE', NULL, 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);

-- Seed data for `sys_plugin_definition`
INSERT IGNORE INTO `sys_plugin_definition` (`id`, `plugin_code`, `plugin_name`, `plugin_type`, `description`, `author`, `plugin_api_version`, `builtin_flag`, `status`, `sort_no`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (5, 'sms', '短信验证码插件', 'BUSINESS', '为租户提供短信验证码登录与验证能力', 'legendary-invention', '1.0.0', 0, 'DISABLED', 0, 1001, '2026-04-10T23:00:22', 1001, '2026-04-15T00:58:46', 1);

-- Seed data for `sys_plugin_permission_rel`
INSERT IGNORE INTO `sys_plugin_permission_rel` (`id`, `plugin_code`, `plugin_version`, `permission_key`, `permission_name`, `permission_group`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (9, 'sms', '1.0.0', 'plugin:sms:view', '查看短信验证', 'sms', 1001, '2026-04-10T23:00:22', 1001, '2026-04-15T00:58:46', 1);
INSERT IGNORE INTO `sys_plugin_permission_rel` (`id`, `plugin_code`, `plugin_version`, `permission_key`, `permission_name`, `permission_group`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (10, 'sms', '1.0.0', 'plugin:sms:manage', '管理短信验证', 'sms', 1001, '2026-04-10T23:00:22', 1001, '2026-04-15T00:58:46', 1);

-- Seed data for `sys_plugin_tenant`
INSERT IGNORE INTO `sys_plugin_tenant` (`id`, `tenant_id`, `plugin_code`, `plugin_version`, `enabled`, `config_json`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (3, 1001, 'sms', '1.0.0', 0, NULL, 1001, '2026-04-10T23:01:15', 1001, '2026-04-15T00:58:46', 1);

-- Seed data for `sys_plugin_version`
INSERT IGNORE INTO `sys_plugin_version` (`id`, `plugin_code`, `version`, `package_path`, `artifact_path`, `frontend_manifest_path`, `backend_jar_path`, `checksum`, `signature_path`, `min_platform_version`, `install_status`, `load_status`, `health_status`, `is_active`, `rollbackable`, `metadata_json`, `validation_report_json`, `staged_path`, `installed_at`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (5, 'sms', '1.0.0', '/Users/johntao/Documents/GitHub/legendary-invention/backend/storage/plugin-staging/b4557807-13be-48dd-9cdc-e5a82789f174/sms-plugin-1.0.0.zip', '/Users/johntao/Documents/GitHub/legendary-invention/backend/storage/plugins/sms/1.0.0', '/Users/johntao/Documents/GitHub/legendary-invention/backend/storage/plugins/sms/1.0.0/frontend/manifest.json', '/Users/johntao/Documents/GitHub/legendary-invention/backend/storage/plugins/sms/1.0.0/backend/plugin.jar', 'fdcf8399fc6a30904a1539fe486583af5464cae646ce20c416dca18c592d128f', '/Users/johntao/Documents/GitHub/legendary-invention/backend/storage/plugin-staging/b4557807-13be-48dd-9cdc-e5a82789f174/extracted/signature.sig', '0.1.0', 'UNINSTALLED', 'UNLOADED', 'UNKNOWN', 0, 1, '{"kind": "BUSINESS", "author": "legendary-invention", "version": "1.0.0", "pluginCode": "sms", "pluginName": "短信验证码插件", "description": "为租户提供短信验证码登录与验证能力", "backendEntry": "backend/plugin.jar", "configSchema": "{}", "frontendEntry": "frontend/assets/sms-plugin.js", "menuDeclarations": [{"icon": "MessageOutlined", "sortNo": 201, "menuCode": "plugin.sms", "menuName": "短信验证码", "routePath": "/plugins/sms", "permissionKey": "plugin:sms:view", "parentMenuCode": null}], "pluginApiVersion": "1.0.0", "checksumAlgorithm": "SHA-256", "dependencyPlugins": [], "migrationStrategy": "versioned-sql", "minPlatformVersion": "0.1.0", "requiredPermissions": [{"permissionKey": "plugin:sms:view", "permissionName": "查看短信验证", "permissionGroup": "sms"}, {"permissionKey": "plugin:sms:manage", "permissionName": "管理短信验证", "permissionGroup": "sms"}]}', '{"routes": ["/plugins/sms"], "version": "1.0.0", "verified": true, "pluginCode": "sms", "pluginName": "短信验证码插件", "sharedDeps": ["react"]}', '/Users/johntao/Documents/GitHub/legendary-invention/backend/storage/plugin-staging/b4557807-13be-48dd-9cdc-e5a82789f174/extracted', '2026-04-10T23:01:10', 1001, '2026-04-10T23:00:22', 1001, '2026-04-15T00:58:46', 1);

-- Seed data for `sys_role`
INSERT IGNORE INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (2001, 1001, 'ADMIN', '平台管理员', 'BUILTIN', 0, '2026-03-30T14:28:54', 0, '2026-03-30T14:28:54', 0);
INSERT IGNORE INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (2002, 1002, 'ADMIN', '演示管理员', 'BUILTIN', 0, '2026-03-30T14:28:54', 0, '2026-03-30T14:28:54', 0);
INSERT IGNORE INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (2003, 1001, 'commonuser', '普通用户', 'CUSTOM', 1001, '2026-04-23T02:09:41', 1001, '2026-04-25T09:04:23', 0);

-- Seed data for `sys_role_permission`
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1, 1001, 2001, 'dashboard:view', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (2, 1001, 2001, 'plugin:management:disable', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (3, 1001, 2001, 'plugin:management:enable', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (4, 1001, 2001, 'plugin:management:install', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (5, 1001, 2001, 'plugin:management:logs', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (6, 1001, 2001, 'plugin:management:rollback', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (7, 1001, 2001, 'plugin:management:upgrade', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (8, 1001, 2001, 'plugin:management:upload', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (9, 1001, 2001, 'plugin:management:view', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (10, 1001, 2001, 'profile:view', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (11, 1001, 2001, 'system:view', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (16, 1002, 2002, 'dashboard:view', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (17, 1002, 2002, 'plugin:management:disable', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (18, 1002, 2002, 'plugin:management:enable', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (19, 1002, 2002, 'plugin:management:install', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (20, 1002, 2002, 'plugin:management:logs', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (21, 1002, 2002, 'plugin:management:rollback', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (22, 1002, 2002, 'plugin:management:upgrade', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (23, 1002, 2002, 'plugin:management:upload', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (24, 1002, 2002, 'plugin:management:view', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (25, 1002, 2002, 'profile:view', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (26, 1002, 2002, 'system:view', 0, '2026-03-29T17:10:10', 0, '2026-03-29T17:10:10', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (31, 1001, 2001, 'audit:login:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (32, 1001, 2001, 'audit:operation:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (33, 1001, 2001, 'audit:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (34, 1001, 2001, 'iam:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (35, 1001, 2001, 'system:config:update', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (36, 1001, 2001, 'system:config:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (37, 1001, 2001, 'system:dict:create', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (38, 1001, 2001, 'system:dict:update', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (39, 1001, 2001, 'system:dict:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (40, 1001, 2001, 'system:menu:create', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (41, 1001, 2001, 'system:menu:status', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (42, 1001, 2001, 'system:menu:update', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (43, 1001, 2001, 'system:menu:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (44, 1001, 2001, 'system:role:create', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (45, 1001, 2001, 'system:role:permissions', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (46, 1001, 2001, 'system:role:update', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (47, 1001, 2001, 'system:role:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (48, 1001, 2001, 'system:user:create', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (49, 1001, 2001, 'system:user:status', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (50, 1001, 2001, 'system:user:update', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (51, 1001, 2001, 'system:user:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (52, 1001, 2001, 'tenant:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (62, 1002, 2002, 'audit:login:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (63, 1002, 2002, 'audit:operation:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (64, 1002, 2002, 'audit:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (65, 1002, 2002, 'iam:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (66, 1002, 2002, 'system:config:update', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (67, 1002, 2002, 'system:config:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (68, 1002, 2002, 'system:dict:create', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (69, 1002, 2002, 'system:dict:update', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (70, 1002, 2002, 'system:dict:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (71, 1002, 2002, 'system:menu:create', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (72, 1002, 2002, 'system:menu:status', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (73, 1002, 2002, 'system:menu:update', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (74, 1002, 2002, 'system:menu:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (75, 1002, 2002, 'system:role:create', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (76, 1002, 2002, 'system:role:permissions', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (77, 1002, 2002, 'system:role:update', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (78, 1002, 2002, 'system:role:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (79, 1002, 2002, 'system:user:create', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (80, 1002, 2002, 'system:user:status', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (81, 1002, 2002, 'system:user:update', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (82, 1002, 2002, 'system:user:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (83, 1002, 2002, 'tenant:view', 0, '2026-03-29T20:37:31', 0, '2026-03-29T20:37:31', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (93, 1001, 2001, 'system:online-user:ban', 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (94, 1001, 2001, 'system:online-user:kick', 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (95, 1001, 2001, 'system:online-user:view', 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (96, 1002, 2002, 'system:online-user:ban', 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (97, 1002, 2002, 'system:online-user:kick', 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (98, 1002, 2002, 'system:online-user:view', 0, '2026-04-05T22:53:05', 0, '2026-04-05T22:53:05', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (99, 1001, 2001, 'system:monitor:docs:view', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (100, 1001, 2001, 'system:monitor:redis:view', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (101, 1001, 2001, 'system:monitor:service:view', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (102, 1001, 2001, 'system:monitor:view', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (106, 1002, 2002, 'system:monitor:docs:view', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (107, 1002, 2002, 'system:monitor:redis:view', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (108, 1002, 2002, 'system:monitor:service:view', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (109, 1002, 2002, 'system:monitor:view', 0, '2026-04-06T11:55:39', 0, '2026-04-06T11:55:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (113, 1001, 2001, 'plugin:2fa:view', 0, '2026-04-10T05:45:16', 0, '2026-04-12T14:08:14', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (114, 1001, 2001, 'plugin:2fa:manage', 0, '2026-04-10T05:45:16', 0, '2026-04-12T14:08:14', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (117, 1001, 2001, 'plugin:sms:view', 0, '2026-04-10T23:01:15', 0, '2026-04-10T23:01:15', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (118, 1001, 2001, 'plugin:sms:manage', 0, '2026-04-10T23:01:15', 0, '2026-04-10T23:01:15', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (119, 1001, 2001, 'tenant:create', 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (120, 1001, 2001, 'tenant:delete', 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (121, 1001, 2001, 'tenant:update', 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (122, 1002, 2002, 'tenant:create', 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (123, 1002, 2002, 'tenant:delete', 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (124, 1002, 2002, 'tenant:update', 0, '2026-04-10T23:28:46', 0, '2026-04-10T23:28:46', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (125, 1001, 2001, 'user:center:view', 0, '2026-04-11T12:00:32', 0, '2026-04-11T12:00:32', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (126, 1002, 2002, 'user:center:view', 0, '2026-04-11T12:00:32', 0, '2026-04-11T12:00:32', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (129, 1001, 2001, 'plugin:announcement:view', 0, '2026-04-13T18:46:57', 0, '2026-04-13T18:46:57', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (130, 1001, 2001, 'plugin:announcement:write', 0, '2026-04-13T18:46:57', 0, '2026-04-13T18:46:57', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (131, 1001, 2001, 'system:notification:view', 0, '2026-04-14T01:30:39', 0, '2026-04-14T01:30:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (132, 1001, 2001, 'system:notification:write', 0, '2026-04-14T01:30:39', 0, '2026-04-14T01:30:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (134, 1002, 2002, 'system:notification:view', 0, '2026-04-14T01:30:39', 0, '2026-04-14T01:30:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (135, 1002, 2002, 'system:notification:write', 0, '2026-04-14T01:30:39', 0, '2026-04-14T01:30:39', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (140, 1001, 2001, 'message:message:read', 0, '2026-04-19T13:46:52', 0, '2026-04-19T13:46:52', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (141, 1001, 2001, 'message:message:retract', 0, '2026-04-19T13:46:52', 0, '2026-04-19T13:46:52', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (142, 1001, 2001, 'message:message:view', 0, '2026-04-19T13:46:52', 0, '2026-04-19T13:46:52', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (143, 1001, 2001, 'message:message:write', 0, '2026-04-19T13:46:52', 0, '2026-04-19T13:46:52', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (155, 1002, 2002, 'message:message:read', 0, '2026-04-19T13:46:52', 0, '2026-04-19T13:46:52', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (156, 1002, 2002, 'message:message:retract', 0, '2026-04-19T13:46:52', 0, '2026-04-19T13:46:52', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (157, 1002, 2002, 'message:message:view', 0, '2026-04-19T13:46:52', 0, '2026-04-19T13:46:52', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (158, 1002, 2002, 'message:message:write', 0, '2026-04-19T13:46:52', 0, '2026-04-19T13:46:52', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (166, 1001, 2001, 'system:verification:manage', 0, '2026-04-22T21:55:16', 0, '2026-04-22T21:55:16', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (167, 1001, 2001, 'system:verification:view', 0, '2026-04-22T21:55:16', 0, '2026-04-22T21:55:16', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (169, 1002, 2002, 'system:verification:manage', 0, '2026-04-22T21:55:16', 0, '2026-04-22T21:55:16', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (170, 1002, 2002, 'system:verification:view', 0, '2026-04-22T21:55:16', 0, '2026-04-22T21:55:16', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (177, 1001, 2003, 'dashboard:view', 1001, '2026-04-25T09:04:22', 1001, '2026-04-25T09:04:22', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (178, 1001, 2003, 'profile:view', 1001, '2026-04-25T09:04:22', 1001, '2026-04-25T09:04:22', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (179, 1001, 2001, 'system:file:delete', 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (180, 1001, 2001, 'system:file:upload', 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (181, 1001, 2001, 'system:file:view', 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (182, 1002, 2002, 'system:file:delete', 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (183, 1002, 2002, 'system:file:upload', 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);
INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (184, 1002, 2002, 'system:file:view', 0, '2026-05-04T14:23:01', 0, '2026-05-04T14:23:01', 0);

-- Seed data for `sys_user`
INSERT IGNORE INTO `sys_user` (`id`, `username`, `nickname`, `real_name`, `avatar_url`, `birth_month`, `gender`, `region`, `available_time`, `id_card_number`, `password_hash`, `mobile`, `email`, `status`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1001, 'admin', '管理员', '系统管理员', '/api/uploads/2026/04/11/1b6cdbc6ef894b8daecb3c5831319b5d.png', '2003-01', 'MALE', NULL, NULL, NULL, '$2a$10$ko3RP4YpfVgyQC5pZjq5t.d1TKrqmBGoehczMjqn1k.pLeAAnTI9G', '13800000000', 'adm222in@example.com', 'ENABLED', 0, '2026-03-29T15:42:53', 1001, '2026-04-23T22:24:53', 0);
INSERT IGNORE INTO `sys_user` (`id`, `username`, `nickname`, `real_name`, `avatar_url`, `birth_month`, `gender`, `region`, `available_time`, `id_card_number`, `password_hash`, `mobile`, `email`, `status`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1002, '123456', NULL, NULL, '/api/uploads/2026/04/23/bdde0a68632448d7b6833d9fed698f3e.png', NULL, NULL, NULL, NULL, NULL, '$2a$10$IKPb6SXOgqZSIby5lNfJCOMPkTNRaepbb.2nW8EWHj7HGQxyulGGe', '15150587087', NULL, 'ENABLED', 1001, '2026-04-11T00:27:28', 1002, '2026-04-23T02:18:01', 0);

-- Seed data for `sys_user_role`
INSERT IGNORE INTO `sys_user_role` (`id`, `tenant_id`, `user_id`, `role_id`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1, 1001, 1001, 2001, 0, '2026-03-30T14:28:54', 0, '2026-03-30T14:28:54', 0);
INSERT IGNORE INTO `sys_user_role` (`id`, `tenant_id`, `user_id`, `role_id`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (2, 1002, 1001, 2002, 0, '2026-03-30T14:28:54', 0, '2026-03-30T14:28:54', 0);
INSERT IGNORE INTO `sys_user_role` (`id`, `tenant_id`, `user_id`, `role_id`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (3, 1001, 1002, 2003, 1001, '2026-04-23T02:16:13', 1001, '2026-04-23T02:16:13', 0);

-- Seed data for `sys_user_tenant`
INSERT IGNORE INTO `sys_user_tenant` (`id`, `tenant_id`, `user_id`, `is_default`, `status`, `joined_at`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1, 1001, 1001, 1, 'ENABLED', '2026-03-29T15:42:53', 0, '2026-03-29T15:42:53', 0, '2026-03-29T15:42:53', 0);
INSERT IGNORE INTO `sys_user_tenant` (`id`, `tenant_id`, `user_id`, `is_default`, `status`, `joined_at`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (2, 1002, 1001, 0, 'ENABLED', '2026-03-29T15:42:53', 0, '2026-03-29T15:42:53', 0, '2026-03-29T15:42:53', 0);
INSERT IGNORE INTO `sys_user_tenant` (`id`, `tenant_id`, `user_id`, `is_default`, `status`, `joined_at`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (3, 1001, 1002, 1, 'ENABLED', '2026-04-11T00:27:28', 1001, '2026-04-11T00:27:28', 1001, '2026-04-11T00:27:28', 0);

-- Seed data for `tenant_info`
INSERT IGNORE INTO `tenant_info` (`id`, `tenant_code`, `tenant_name`, `tenant_short_name`, `status`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1001, 'default', '默认租户', '默认', 'ENABLED', 0, '2026-03-29T15:42:53', 0, '2026-03-29T15:42:53', 0);
INSERT IGNORE INTO `tenant_info` (`id`, `tenant_code`, `tenant_name`, `tenant_short_name`, `status`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES (1002, 'demo', '演示租户', '演示', 'ENABLED', 0, '2026-03-29T15:42:53', 1001, '2026-04-24T16:50:39', 1);

ALTER TABLE `file_object`
  ADD COLUMN `uploaded_by` bigint DEFAULT NULL AFTER `tenant_id`,
  ADD COLUMN `uploaded_by_name` varchar(128) DEFAULT NULL AFTER `uploaded_by`,
  ADD COLUMN `file_extension` varchar(32) DEFAULT NULL AFTER `content_type`,
  ADD COLUMN `public_url` varchar(512) DEFAULT NULL AFTER `checksum`,
  ADD COLUMN `preview_mode` varchar(32) NOT NULL DEFAULT 'UNSUPPORTED' AFTER `public_url`,
  ADD COLUMN `previewable_flag` tinyint NOT NULL DEFAULT '0' AFTER `preview_mode`,
  ADD COLUMN `category` varchar(128) DEFAULT NULL AFTER `previewable_flag`,
  ADD COLUMN `tags` varchar(512) DEFAULT NULL AFTER `category`,
  ADD COLUMN `remark` varchar(512) DEFAULT NULL AFTER `tags`,
  ADD COLUMN `status` varchar(32) NOT NULL DEFAULT 'ENABLED' AFTER `remark`;

UPDATE `file_object`
SET
  `file_extension` = COALESCE(`file_extension`, LOWER(SUBSTRING_INDEX(`original_filename`, '.', -1))),
  `public_url` = COALESCE(`public_url`, CONCAT('/api/uploads/', `object_key`)),
  `preview_mode` = CASE
    WHEN LOWER(SUBSTRING_INDEX(`original_filename`, '.', -1)) IN ('png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp') THEN 'IMAGE'
    WHEN LOWER(SUBSTRING_INDEX(`original_filename`, '.', -1)) = 'pdf' THEN 'PDF'
    WHEN LOWER(SUBSTRING_INDEX(`original_filename`, '.', -1)) IN ('txt', 'md', 'csv', 'json', 'xml') THEN 'TEXT'
    ELSE `preview_mode`
  END,
  `previewable_flag` = CASE
    WHEN LOWER(SUBSTRING_INDEX(`original_filename`, '.', -1)) IN ('png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'pdf', 'txt', 'md', 'csv', 'json', 'xml') THEN 1
    ELSE `previewable_flag`
  END
WHERE `deleted` = 0;

INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
VALUES
  (152, 1001, 'system:file:manage', '查看全站文件管理', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (153, 1001, 'system:file:manage:delete', '删除全站文件', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (154, 1002, 'system:file:manage', '查看全站文件管理', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (155, 1002, 'system:file:manage:delete', '删除全站文件', 'system', 'CORE', NULL, 0, NOW(), 0, NOW(), 0);

INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`)
VALUES
  (3029, 1001, 3020, 'files.my', '我的文件', 'MENU', '/user-center/files', '@/pages/files/Center', 0, NOW(), 0, NOW(), 0, 'FileOutlined', 26, 'system:file:view', 'ENABLED'),
  (3030, 1001, 3028, 'files.all', '全站文件管理', 'MENU', '/settings/files/all', '@/pages/files/Center', 0, NOW(), 0, NOW(), 0, 'FolderOpenOutlined', 2, 'system:file:manage', 'ENABLED'),
  (4029, 1002, 4020, 'files.my', '我的文件', 'MENU', '/user-center/files', '@/pages/files/Center', 0, NOW(), 0, NOW(), 0, 'FileOutlined', 26, 'system:file:view', 'ENABLED'),
  (4030, 1002, 4028, 'files.all', '全站文件管理', 'MENU', '/settings/files/all', '@/pages/files/Center', 0, NOW(), 0, NOW(), 0, 'FolderOpenOutlined', 2, 'system:file:manage', 'ENABLED');

INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
VALUES
  (185, 1001, 2001, 'system:file:manage', 0, NOW(), 0, NOW(), 0),
  (186, 1001, 2001, 'system:file:manage:delete', 0, NOW(), 0, NOW(), 0),
  (187, 1002, 2002, 'system:file:manage', 0, NOW(), 0, NOW(), 0),
  (188, 1002, 2002, 'system:file:manage:delete', 0, NOW(), 0, NOW(), 0);

SET FOREIGN_KEY_CHECKS = 1;
