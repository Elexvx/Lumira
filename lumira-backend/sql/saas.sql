-- Lumira consolidated database initialization script.
-- Generated from all service migration modules while Flyway is disabled before first production launch.
-- Includes minimum bootstrap data required by infrastructure components such as XXL-JOB Admin.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `ai_conversation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `employee_id` bigint unsigned NOT NULL,
  `owner_user_id` bigint unsigned NOT NULL,
  `owner_user_uuid` varchar(64) NOT NULL DEFAULT '',
  `conversation_code` varchar(64) NOT NULL,
  `title` varchar(255) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `is_pinned` tinyint unsigned NOT NULL DEFAULT '0',
  `latest_message_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_conversation_code` (`conversation_code`),
  KEY `idx_ai_conversation_owner` (`owner_user_id`,`owner_user_uuid`,`is_pinned`,`latest_message_at`,`is_deleted`),
  KEY `idx_ai_conversation_employee` (`employee_id`,`latest_message_at`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_employee` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `nickname` varchar(128) NOT NULL,
  `position` varchar(128) DEFAULT NULL,
  `avatar_key` varchar(255) DEFAULT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `greeting` varchar(1024) DEFAULT NULL,
  `system_prompt` text,
  `default_llm_service_id` bigint unsigned DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_username` (`username`,`is_deleted`),
  KEY `idx_ai_employee_enabled_sort` (`enabled`,`sort_order`,`id`,`is_deleted`),
  KEY `idx_ai_employee_llm` (`default_llm_service_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_employee_knowledge_base` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `employee_id` bigint unsigned NOT NULL,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_knowledge_base_rel` (`employee_id`,`knowledge_base_id`),
  KEY `idx_ai_employee_knowledge_base_employee` (`employee_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_employee_skill` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `employee_id` bigint unsigned DEFAULT NULL,
  `skill_code` varchar(128) NOT NULL,
  `permission_mode` varchar(32) NOT NULL DEFAULT 'deny',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_skill` (`employee_id`,`skill_code`),
  KEY `idx_ai_employee_skill_code` (`skill_code`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_base` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `kb_code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `visibility_scope` varchar(32) NOT NULL DEFAULT 'PERSONAL',
  `owner_user_id` bigint unsigned NOT NULL DEFAULT '0',
  `owner_user_uuid` varchar(64) NOT NULL DEFAULT '',
  `document_count` bigint NOT NULL DEFAULT '0',
  `chunk_count` bigint NOT NULL DEFAULT '0',
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_base_code` (`kb_code`),
  UNIQUE KEY `uk_ai_knowledge_base_owner_name` (`owner_user_id`,`owner_user_uuid`,`name`,`is_deleted`),
  KEY `idx_ai_knowledge_base_status` (`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_owner` (`owner_user_id`,`owner_user_uuid`,`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_access` (`owner_user_id`,`owner_user_uuid`,`visibility_scope`,`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_creator_uuid` (`created_by`,`created_by_uuid`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_base_acl` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `subject_type` varchar(32) NOT NULL,
  `subject_id` bigint unsigned NOT NULL,
  `permission` varchar(32) NOT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_acl_subject` (`knowledge_base_id`,`subject_type`,`subject_id`,`permission`,`is_deleted`),
  KEY `idx_ai_knowledge_acl_subject` (`subject_type`,`subject_id`,`permission`,`is_deleted`),
  KEY `idx_ai_knowledge_acl_base` (`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_acl_creator_uuid` (`created_by`,`created_by_uuid`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_chunk` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `document_id` bigint unsigned NOT NULL,
  `chunk_index` int unsigned NOT NULL,
  `content` text NOT NULL,
  `search_text` text NOT NULL,
  `token_count` int unsigned NOT NULL DEFAULT '0',
  `embedding_model` varchar(64) DEFAULT NULL,
  `embedding_dim` int unsigned NOT NULL DEFAULT '0',
  `embedding_vector_json` json DEFAULT NULL,
  `embedding_vector_blob` mediumblob DEFAULT NULL,
  `embedding_norm` double DEFAULT NULL,
  `vector_indexed_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_chunk_index` (`document_id`,`chunk_index`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_base` (`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_document` (`document_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_vector` (`knowledge_base_id`,`is_deleted`,`embedding_model`,`update_time`),
  KEY `idx_ai_knowledge_chunk_acl` (`knowledge_base_id`,`document_id`,`is_deleted`,`update_time`,`id`),
  FULLTEXT KEY `ft_ai_knowledge_chunk_search_text` (`search_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_base_stats` (
  `knowledge_base_id` bigint unsigned NOT NULL,
  `document_count` bigint unsigned NOT NULL DEFAULT '0',
  `chunk_count` bigint unsigned NOT NULL DEFAULT '0',
  `vector_indexed_chunk_count` bigint unsigned NOT NULL DEFAULT '0',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_document` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `file_id` bigint unsigned DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `original_file_name` varchar(255) DEFAULT NULL,
  `file_extension` varchar(32) DEFAULT NULL,
  `mime_type` varchar(255) DEFAULT NULL,
  `file_size_bytes` bigint unsigned DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'INDEXING',
  `parse_error` varchar(1024) DEFAULT NULL,
  `extracted_text` longtext,
  `extracted_char_count` int unsigned NOT NULL DEFAULT '0',
  `chunk_count` int unsigned NOT NULL DEFAULT '0',
  `index_retry_count` int NOT NULL DEFAULT '0',
  `index_next_retry_at` datetime DEFAULT NULL,
  `index_last_error` varchar(512) DEFAULT NULL,
  `index_claim_token` varchar(64) DEFAULT NULL,
  `index_claim_expires_at` datetime DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `simulated_role_id` bigint unsigned DEFAULT NULL,
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_knowledge_document_base` (`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_document_file` (`file_id`),
  KEY `idx_ai_knowledge_document_index_retry` (`status`,`is_deleted`,`index_next_retry_at`,`update_time`,`id`),
  KEY `idx_ai_knowledge_document_index_claim` (`index_claim_token`),
  KEY `idx_ai_knowledge_document_status` (`knowledge_base_id`,`status`,`is_deleted`),
  KEY `idx_ai_knowledge_document_creator_uuid` (`created_by`,`created_by_uuid`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_llm_model` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `llm_service_id` bigint unsigned NOT NULL,
  `model_code` varchar(128) NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_llm_model_code` (`llm_service_id`,`model_code`,`is_deleted`),
  KEY `idx_ai_llm_model_service` (`llm_service_id`,`enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_llm_service` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `provider` varchar(64) NOT NULL,
  `code` varchar(64) NOT NULL,
  `title` varchar(128) NOT NULL,
  `base_url` varchar(512) DEFAULT NULL,
  `api_key_encrypted` varchar(2048) DEFAULT NULL,
  `default_model` varchar(128) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `timeout_ms` int unsigned NOT NULL DEFAULT '60000',
  `temperature` decimal(4,2) DEFAULT NULL,
  `max_tokens` int unsigned DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_llm_service_code` (`code`,`is_deleted`),
  KEY `idx_ai_llm_service_provider` (`provider`,`enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint unsigned NOT NULL,
  `role` varchar(32) NOT NULL,
  `content` longtext NOT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_conversation` (`conversation_id`,`create_time`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_skill` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `skill_code` varchar(128) NOT NULL,
  `skill_name` varchar(128) NOT NULL,
  `category` varchar(64) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW',
  `read_only` tinyint unsigned NOT NULL DEFAULT '1',
  `need_confirm` tinyint unsigned NOT NULL DEFAULT '0',
  `permission_key` varchar(128) DEFAULT NULL,
  `input_schema_json` longtext,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_skill_code` (`skill_code`,`is_deleted`),
  KEY `idx_ai_skill_category_enabled` (`category`,`enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `ai_skill`
(`skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `permission_key`, `input_schema_json`, `enabled`, `is_deleted`)
VALUES
('audit.ai_call.search', '检索 AI 工具审计', 'audit', '按数字员工、技能编码和结果状态检索 AI 调用审计日志。', 'MEDIUM', 1, 0, 'audit:view', '{"type":"object","properties":{}}', 1, 0),
('file.object.search', '检索文件对象', 'file', '按关键词、类型和状态检索文件中心对象。', 'MEDIUM', 1, 0, 'system:file:view', '{"type":"object","properties":{}}', 1, 0),
('system.config.read', '读取非敏感系统配置', 'system', '按配置键读取非敏感平台配置。', 'MEDIUM', 1, 0, 'system:config:view', '{"type":"object","properties":{}}', 1, 0),
('system.menu.list', '读取系统菜单与模块入口', 'system', '按当前账号权限读取系统菜单、路由、权限键和状态。', 'LOW', 1, 0, 'system:menu:view', '{"type":"object","properties":{}}', 1, 0),
('system.permission.snapshot', '读取当前权限上下文', 'system', '返回当前登录用户、角色、部门和权限集合。', 'LOW', 1, 0, NULL, '{"type":"object","properties":{}}', 1, 0),
('system.user.create', '新增系统用户', 'system', '在当前账号权限范围内新增系统用户。', 'HIGH', 0, 1, 'system:user:create', '{"type":"object","properties":{}}', 1, 0),
('system.user.search', '检索系统用户', 'system', '按关键词和状态检索当前系统用户。', 'MEDIUM', 1, 0, 'system:user:view', '{"type":"object","properties":{}}', 1, 0),
('system.user.update', '编辑系统用户', 'system', '在当前账号权限范围内编辑用户基础信息、角色和部门。', 'HIGH', 0, 1, 'system:user:update', '{"type":"object","properties":{}}', 1, 0)
ON DUPLICATE KEY UPDATE `is_deleted` = 0;

CREATE TABLE `ai_tool_audit_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `owner_user_id` bigint unsigned NOT NULL DEFAULT '0',
  `owner_user_uuid` varchar(64) NOT NULL DEFAULT '',
  `skill_code` varchar(128) NOT NULL,
  `tool_name` varchar(128) DEFAULT NULL,
  `permission_mode` varchar(32) DEFAULT NULL,
  `confirm_required` tinyint unsigned NOT NULL DEFAULT '0',
  `confirm_result` tinyint unsigned DEFAULT NULL,
  `supervisor_verdict` varchar(32) DEFAULT NULL,
  `supervisor_message` varchar(1024) DEFAULT NULL,
  `policy_match` varchar(1024) DEFAULT NULL,
  `confirmed_by` bigint unsigned DEFAULT NULL,
  `confirmed_by_uuid` varchar(64) DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `result_status` varchar(32) NOT NULL,
  `detail_message` varchar(1024) DEFAULT NULL,
  `request_payload_json` longtext,
  `response_payload_json` longtext,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_audit_created` (`create_time`),
  KEY `idx_ai_tool_audit_owner` (`owner_user_id`,`owner_user_uuid`,`conversation_id`,`employee_id`,`create_time`),
  KEY `idx_ai_tool_audit_employee` (`employee_id`,`create_time`),
  KEY `idx_ai_tool_audit_skill` (`skill_code`,`result_status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_tool_call_plan` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `owner_user_id` bigint unsigned NOT NULL,
  `owner_user_uuid` varchar(64) NOT NULL DEFAULT '',
  `tool_code` varchar(128) NOT NULL,
  `tool_name` varchar(128) DEFAULT NULL,
  `action_type` varchar(64) DEFAULT NULL,
  `risk_level` varchar(32) NOT NULL DEFAULT 'LOW',
  `summary` varchar(1024) DEFAULT NULL,
  `permission_key` varchar(128) DEFAULT NULL,
  `requires_confirm` tinyint unsigned NOT NULL DEFAULT '1',
  `supervisor_verdict` varchar(32) NOT NULL DEFAULT 'REQUIRE_CONFIRM',
  `supervisor_message` varchar(1024) DEFAULT NULL,
  `policy_verdict` varchar(32) NOT NULL DEFAULT 'ALLOW',
  `policy_message` varchar(1024) DEFAULT NULL,
  `arguments_json` longtext,
  `arguments_hash` varchar(128) DEFAULT NULL,
  `authorization_snapshot_json` longtext,
  `approval_required` tinyint NOT NULL DEFAULT '0',
  `approved_by` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `expires_at` datetime NOT NULL,
  `confirmed_by` bigint unsigned DEFAULT NULL,
  `confirmed_by_uuid` varchar(64) DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_plan_owner` (`owner_user_id`,`owner_user_uuid`,`status`,`expires_at`),
  KEY `idx_ai_tool_plan_conversation` (`conversation_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_tool_policy` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `policy_name` varchar(128) NOT NULL,
  `tool_code` varchar(128) NOT NULL DEFAULT '*',
  `action_type` varchar(64) DEFAULT NULL,
  `risk_level` varchar(32) DEFAULT NULL,
  `match_type` varchar(32) NOT NULL DEFAULT 'KEYWORD',
  `match_value` varchar(512) DEFAULT NULL,
  `verdict` varchar(32) NOT NULL DEFAULT 'DENY',
  `message` varchar(1024) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_policy_enabled` (`enabled`,`is_deleted`),
  KEY `idx_ai_tool_policy_tool` (`tool_code`,`enabled`,`is_deleted`),
  KEY `idx_ai_tool_policy_runtime` (`enabled`,`is_deleted`,`tool_code`,`action_type`,`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `login_type` varchar(32) NOT NULL,
  `login_result` varchar(32) NOT NULL,
  `fail_reason` varchar(512) DEFAULT NULL,
  `login_ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `trace_id` varchar(128) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_login_created` (`created_at`),
  KEY `idx_audit_login_user_created` (`user_id`,`created_at`),
  KEY `idx_audit_login_user_uuid_created` (`user_uuid`,`created_at`),
  KEY `idx_audit_login_result_created` (`login_result`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `username` varchar(64) DEFAULT NULL,
  `module_name` varchar(64) NOT NULL,
  `action_name` varchar(128) NOT NULL,
  `operation_type` varchar(32) NOT NULL,
  `result_status` varchar(32) NOT NULL,
  `detail_message` varchar(1024) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `trace_id` varchar(128) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_audit_operation_created` (`created_at`),
  KEY `idx_audit_operation_user_created` (`user_id`,`created_at`),
  KEY `idx_audit_operation_user_uuid_created` (`user_uuid`,`created_at`),
  KEY `idx_audit_operation_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_audit_operation_module_created` (`module_name`,`created_at`),
  KEY `idx_audit_operation_result_created` (`result_status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ddd_read_model_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `context_name` varchar(64) NOT NULL,
  `scope` varchar(128) NOT NULL,
  `version` bigint NOT NULL DEFAULT '1',
  `last_event_key` varchar(255) DEFAULT NULL,
  `rebuilt_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ddd_read_model_version_scope` (`context_name`,`scope`),
  KEY `idx_ddd_read_model_version_context` (`context_name`,`updated_at`),
  KEY `idx_ddd_read_model_version_event_key` (`last_event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `file_object` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `storage_type` varchar(32) NOT NULL,
  `bucket` varchar(128) DEFAULT NULL,
  `object_key` varchar(255) NOT NULL,
  `uploaded_by` bigint DEFAULT NULL,
  `uploaded_by_uuid` char(36) DEFAULT NULL,
  `uploaded_by_name` varchar(128) DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `visibility_scope` varchar(32) NOT NULL DEFAULT 'PERSONAL',
  `original_filename` varchar(255) NOT NULL,
  `file_extension` varchar(32) DEFAULT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `file_size` bigint NOT NULL DEFAULT '0',
  `checksum` varchar(128) DEFAULT NULL,
  `public_url` varchar(512) DEFAULT NULL,
  `preview_mode` varchar(32) NOT NULL DEFAULT 'UNSUPPORTED',
  `previewable_flag` tinyint NOT NULL DEFAULT '0',
  `category` varchar(128) DEFAULT NULL,
  `tags` varchar(512) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_file_object_key` (`object_key`),
  KEY `idx_file_object_uploader` (`uploaded_by`,`uploaded_by_uuid`,`deleted`),
  KEY `idx_file_object_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_file_object_department` (`department_id`,`deleted`),
  KEY `idx_file_object_visibility` (`visibility_scope`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file_processing_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_id` bigint NOT NULL,
  `task_type` varchar(64) NOT NULL,
  `artifact_type` varchar(64) NOT NULL,
  `artifact_path` varchar(512) DEFAULT NULL,
  `content_text` mediumtext,
  `content_length` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_processing_artifact` (`file_id`,`artifact_type`),
  KEY `idx_file_processing_artifact_file` (`file_id`,`deleted`),
  KEY `idx_file_processing_artifact_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_file_processing_artifact_type` (`artifact_type`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file_processing_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_id` bigint NOT NULL,
  `task_type` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `priority` int NOT NULL DEFAULT '0',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `claimed_by` varchar(128) DEFAULT NULL,
  `claim_token` varchar(128) DEFAULT NULL,
  `claim_expires_at` datetime DEFAULT NULL,
  `claimed_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `last_error` varchar(1024) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_processing_task_file_type` (`file_id`,`task_type`),
  KEY `idx_file_processing_task_status_retry` (`status`,`next_retry_at`,`priority`,`created_at`),
  KEY `idx_file_processing_task_file` (`file_id`,`deleted`),
  KEY `idx_file_processing_task_queue` (`deleted`,`status`,`next_retry_at`,`priority`,`created_at`,`id`),
  KEY `idx_file_processing_task_created` (`deleted`,`status`,`created_at`,`id`),
  KEY `idx_file_processing_task_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_file_processing_batch_claim` (`deleted`,`status`,`next_retry_at`,`priority`,`created_at`,`id`),
  KEY `idx_file_processing_claim_token` (`claim_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file_storage_space` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(128) NOT NULL,
  `storage_key` varchar(64) NOT NULL,
  `provider` varchar(32) NOT NULL,
  `root_path` varchar(255) DEFAULT NULL,
  `bucket_name` varchar(128) DEFAULT NULL,
  `endpoint` varchar(255) DEFAULT NULL,
  `region` varchar(128) DEFAULT NULL,
  `access_key_id` varchar(255) DEFAULT NULL,
  `access_key_secret` varchar(2048) DEFAULT NULL,
  `rename_strategy` varchar(32) NOT NULL DEFAULT 'APPEND_RANDOM_ID',
  `max_file_size_mb` int NOT NULL DEFAULT '20',
  `allowed_mime_types` varchar(1024) NOT NULL DEFAULT '*',
  `default_flag` tinyint NOT NULL DEFAULT '0',
  `retain_file_on_record_delete` tinyint NOT NULL DEFAULT '0',
  `anonymous_access_allowed` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_storage_space_key` (`storage_key`),
  KEY `idx_file_storage_space_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_file_storage_space_default` (`default_flag`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user` (
  `id` bigint NOT NULL,
  `user_no` varchar(64) NOT NULL,
  `display_name` varchar(128) DEFAULT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `user_type` varchar(32) NOT NULL DEFAULT 'REGISTERED',
  `source` varchar(64) NOT NULL DEFAULT 'LEGACY_SYS_USER',
  `registered_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_user_no` (`user_no`),
  KEY `idx_iam_user_status_created` (`status`,`created_at`,`id`,`deleted`),
  KEY `idx_iam_user_source_created` (`source`,`created_at`,`id`,`deleted`),
  KEY `idx_iam_user_last_login` (`last_login_at`,`id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `credential_type` varchar(32) NOT NULL,
  `credential_secret` varchar(512) NOT NULL,
  `algorithm` varchar(64) NOT NULL DEFAULT 'BCRYPT',
  `version` int NOT NULL DEFAULT '1',
  `expire_at` datetime DEFAULT NULL,
  `last_changed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_credential_user_type` (`user_id`,`user_uuid`,`credential_type`,`version`),
  KEY `idx_iam_credential_type_status` (`credential_type`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `device_id` varchar(128) NOT NULL,
  `device_name` varchar(128) DEFAULT NULL,
  `device_type` varchar(32) DEFAULT NULL,
  `os` varchar(64) DEFAULT NULL,
  `browser` varchar(64) DEFAULT NULL,
  `last_ip` varchar(64) DEFAULT NULL,
  `last_active_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `trusted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_device_user_device` (`user_id`,`user_uuid`,`device_id`),
  KEY `idx_iam_device_user_active` (`user_id`,`user_uuid`,`last_active_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_source` varchar(64) NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_uuid` char(36) DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `detail_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_iam_event_user_created` (`user_id`,`user_uuid`,`created_at`),
  KEY `idx_iam_event_type_created` (`event_type`,`created_at`),
  KEY `idx_iam_event_ip_created` (`ip`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `identity_type` varchar(32) NOT NULL,
  `identifier` varchar(255) NOT NULL,
  `identifier_normalized` varchar(255) NOT NULL,
  `verified` tinyint NOT NULL DEFAULT '0',
  `primary_identity` tinyint NOT NULL DEFAULT '0',
  `bound_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_used_at` datetime DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity` (`identity_type`,`identifier_normalized`),
  KEY `idx_iam_identity_user` (`user_id`,`user_uuid`,`identity_type`,`deleted`),
  KEY `idx_iam_identity_last_used` (`last_used_at`,`id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `nickname` varchar(128) DEFAULT NULL,
  `real_name` varchar(128) DEFAULT NULL,
  `gender` varchar(32) DEFAULT NULL,
  `birth_month` varchar(16) DEFAULT NULL,
  `region` varchar(128) DEFAULT NULL,
  `locale` varchar(32) DEFAULT NULL,
  `timezone` varchar(64) DEFAULT NULL,
  `bio` varchar(1000) DEFAULT NULL,
  `extra_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_profile_user` (`user_id`,`user_uuid`),
  KEY `idx_iam_profile_real_name` (`real_name`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_security_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `mfa_enabled` tinyint NOT NULL DEFAULT '0',
  `password_login_enabled` tinyint NOT NULL DEFAULT '1',
  `sms_login_enabled` tinyint NOT NULL DEFAULT '1',
  `email_login_enabled` tinyint NOT NULL DEFAULT '1',
  `passkey_enabled` tinyint NOT NULL DEFAULT '0',
  `login_notify_enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iam_security_user` (`user_id`,`user_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_delivery_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_id` bigint DEFAULT NULL,
  `channel` varchar(32) NOT NULL,
  `target_scope` varchar(32) NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  `target_user_uuid` varchar(64) DEFAULT NULL,
  `target_user_name` varchar(64) DEFAULT NULL,
  `target_email` varchar(128) DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `content` text NOT NULL,
  `send_status` varchar(32) NOT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` varchar(64) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msg_delivery_log_channel_created` (`channel`,`created_at`),
  KEY `idx_msg_delivery_log_status_created` (`send_status`,`created_at`),
  KEY `idx_msg_delivery_log_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_msg_delivery_log_target_user_uuid` (`target_user_id`,`target_user_uuid`,`created_at`),
  KEY `idx_msg_delivery_log_notice` (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_type` varchar(32) NOT NULL,
  `target_scope` varchar(32) NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  `target_user_uuid` varchar(64) DEFAULT NULL,
  `target_role_id` bigint DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `content` text NOT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `publish_status` varchar(32) NOT NULL DEFAULT 'PUBLISHED',
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` varchar(64) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msg_notice_type_status_created` (`notice_type`,`publish_status`,`created_at`),
  KEY `idx_msg_notice_target_created` (`target_user_id`,`created_at`),
  KEY `idx_msg_notice_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_msg_notice_target_role_created` (`target_role_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_notice_read` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) NOT NULL,
  `read_at` datetime NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_notice_read` (`notice_id`,`user_id`,`user_uuid`),
  KEY `idx_msg_notice_read_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_msg_notice_read_user_created` (`user_id`,`user_uuid`,`read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `source_type` varchar(64) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `event_key` varchar(128) NOT NULL,
  `payload_json` longtext NOT NULL,
  `status` varchar(32) NOT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error_message` varchar(512) DEFAULT NULL,
  `claimed_by` varchar(128) DEFAULT NULL,
  `claim_token` varchar(128) DEFAULT NULL,
  `claim_expires_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_outbox_event` (`source_type`,`event_type`,`event_key`),
  KEY `idx_payment_outbox_user_uuid` (`user_uuid`,`created_at`),
  KEY `idx_payment_outbox_status` (`status`,`next_retry_at`),
  KEY `idx_payment_outbox_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `provider_code` varchar(64) NOT NULL,
  `provider_order_no` varchar(128) NOT NULL,
  `subject` varchar(255) NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency` varchar(16) NOT NULL,
  `status` varchar(32) NOT NULL,
  `payment_url` text DEFAULT NULL,
  `client_ip` varchar(64) DEFAULT NULL,
  `notify_url` varchar(1024) DEFAULT NULL,
  `return_url` varchar(1024) DEFAULT NULL,
  `request_json` longtext,
  `response_json` longtext,
  `idempotency_key` varchar(128) DEFAULT NULL,
  `failure_code` varchar(64) DEFAULT NULL,
  `failure_message` varchar(512) DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_order_order_no` (`order_no`),
  UNIQUE KEY `uk_payment_order_idempotency_key` (`idempotency_key`),
  KEY `idx_payment_order_status` (`status`),
  KEY `idx_payment_order_provider` (`provider_code`,`provider_order_no`),
  KEY `idx_payment_order_owner_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(64) NOT NULL,
  `provider_name` varchar(128) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `environment` varchar(32) NOT NULL,
  `encrypted_config_json` longtext NOT NULL,
  `configured` tinyint(1) NOT NULL DEFAULT '0',
  `last_tested_at` datetime DEFAULT NULL,
  `last_test_success` tinyint(1) DEFAULT NULL,
  `last_test_message` varchar(512) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_provider_config_provider` (`provider_code`),
  KEY `idx_payment_provider_config_deleted` (`deleted`),
  KEY `idx_payment_provider_config_provider_deleted` (`provider_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `refund_no` varchar(64) NOT NULL,
  `order_no` varchar(64) NOT NULL,
  `provider_code` varchar(64) NOT NULL,
  `provider_refund_no` varchar(128) NOT NULL,
  `amount_minor` bigint NOT NULL,
  `currency` varchar(16) NOT NULL,
  `status` varchar(32) NOT NULL,
  `reason` varchar(255) NOT NULL,
  `request_json` longtext,
  `response_json` longtext,
  `idempotency_key` varchar(128) DEFAULT NULL,
  `failure_code` varchar(64) DEFAULT NULL,
  `failure_message` varchar(512) DEFAULT NULL,
  `refunded_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_refund_refund_no` (`refund_no`),
  UNIQUE KEY `uk_payment_refund_idempotency_key` (`idempotency_key`),
  KEY `idx_payment_refund_status` (`status`),
  KEY `idx_payment_refund_order_no` (`order_no`),
  KEY `idx_payment_refund_owner_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_webhook_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(64) NOT NULL,
  `event_id` varchar(128) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `nonce` varchar(128) DEFAULT NULL,
  `request_timestamp` varchar(64) DEFAULT NULL,
  `payload_json` longtext NOT NULL,
  `signature` varchar(2048) DEFAULT NULL,
  `signature_valid` tinyint(1) NOT NULL DEFAULT '0',
  `processed` tinyint(1) NOT NULL DEFAULT '0',
  `process_message` varchar(512) DEFAULT NULL,
  `received_at` datetime NOT NULL,
  `processed_at` datetime DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_webhook_event_provider_event` (`provider_code`,`event_id`),
  KEY `idx_payment_webhook_event_nonce` (`provider_code`,`nonce`),
  KEY `idx_payment_webhook_event_status` (`processed`,`retry_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `platform_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `source_type` varchar(64) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_key` varchar(128) NOT NULL,
  `payload_json` longtext NOT NULL,
  `dispatch_status` varchar(32) NOT NULL DEFAULT 'RECORDED',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `claimed_by` varchar(128) DEFAULT NULL,
  `claim_token` varchar(128) DEFAULT NULL,
  `claim_expires_at` datetime DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `last_error` varchar(1024) DEFAULT NULL,
  `trace_id` varchar(128) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_platform_event_outbox_retry` (`dispatch_status`,`next_retry_at`),
  KEY `idx_platform_event_outbox_created_at` (`created_at`),
  KEY `idx_platform_event_outbox_event_key` (`event_key`),
  KEY `idx_platform_event_outbox_user_uuid` (`user_uuid`,`created_at`),
  KEY `idx_platform_event_outbox_owner_queue` (`source_type`,`created_at`,`id`,`dispatch_status`,`next_retry_at`,`deleted`),
  KEY `idx_platform_event_outbox_batch_claim` (`source_type`,`deleted`,`dispatch_status`,`next_retry_at`,`created_at`,`id`),
  KEY `idx_platform_event_outbox_claim_token` (`claim_token`),
  KEY `idx_platform_event_outbox_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `platform_update_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_type` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL,
  `target_version` varchar(64) DEFAULT NULL,
  `target_commit` varchar(64) DEFAULT NULL,
  `server_image` varchar(255) DEFAULT NULL,
  `frontend_image` varchar(255) DEFAULT NULL,
  `updater_task_id` varchar(64) DEFAULT NULL,
  `backup_path` varchar(512) DEFAULT NULL,
  `log_summary` text,
  `error_message` text,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_by_name` varchar(128) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_platform_update_task_creator_uuid` (`created_by_uuid`,`created_at`),
  KEY `idx_platform_update_task_created_at` (`created_at`),
  KEY `idx_platform_update_task_status` (`status`),
  KEY `idx_platform_update_task_updater_task_id` (`updater_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plugin_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `event_type` varchar(128) NOT NULL,
  `event_key` varchar(191) NOT NULL,
  `payload_json` json NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error_message` varchar(512) DEFAULT NULL,
  `claimed_by` varchar(128) DEFAULT NULL,
  `claim_token` varchar(128) DEFAULT NULL,
  `claim_expires_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_event_outbox_event` (`event_type`,`event_key`),
  KEY `idx_plugin_event_outbox_user_uuid` (`user_uuid`,`created_at`),
  KEY `idx_plugin_event_outbox_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_plugin_event_outbox_status` (`status`,`next_retry_at`),
  KEY `idx_plugin_event_outbox_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(128) NOT NULL,
  `config_name` varchar(128) NOT NULL DEFAULT '',
  `config_value` varchar(2000) NOT NULL,
  `config_scope` varchar(32) NOT NULL DEFAULT 'PLATFORM',
  `is_system` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(512) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_key` (`config_key`),
  KEY `idx_sys_config_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `dept_code` varchar(64) NOT NULL,
  `dept_name` varchar(128) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_department_code` (`dept_code`),
  KEY `idx_sys_department_parent` (`parent_id`,`deleted`),
  KEY `idx_sys_department_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_dict_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_type_id` bigint NOT NULL,
  `item_value` varchar(64) NOT NULL,
  `item_label` varchar(128) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `remark` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_item_value` (`dict_type_id`,`item_value`),
  KEY `idx_sys_dict_item_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_code` varchar(64) NOT NULL,
  `dict_name` varchar(128) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `is_system` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_type_code` (`dict_code`),
  KEY `idx_sys_dict_type_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_export_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `module_key` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `request_payload` json DEFAULT NULL,
  `selected_fields` json DEFAULT NULL,
  `total_count` bigint DEFAULT '0',
  `file_id` bigint DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `claimed_by` varchar(128) DEFAULT NULL,
  `claim_token` varchar(128) DEFAULT NULL,
  `claim_expires_at` datetime DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_sys_export_task_creator_uuid` (`created_by_uuid`,`created_at`),
  KEY `idx_sys_export_task_creator` (`created_by`,`created_at`),
  KEY `idx_sys_export_task_status` (`status`,`created_at`),
  KEY `idx_sys_export_task_claim_token` (`claim_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_localization_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_id` bigint NOT NULL,
  `message_key` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `default_message` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_locale` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'zh-CN',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_entry_namespace_key` (`namespace_id`,`message_key`),
  KEY `idx_sys_localization_entry_status` (`status`,`updated_at`),
  KEY `idx_sys_localization_entry_source` (`source_type`,`source_ref`),
  KEY `idx_sys_localization_entry_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_localization_language` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locale_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `language_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `native_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fallback_locale` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_language_locale` (`locale_code`),
  KEY `idx_sys_localization_language_status` (`status`,`sort_no`),
  KEY `idx_sys_localization_language_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_localization_namespace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `namespace_code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `namespace_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_namespace_code` (`namespace_code`),
  KEY `idx_sys_localization_namespace_status` (`status`,`sort_no`),
  KEY `idx_sys_localization_namespace_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_localization_release` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locale_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `release_version` bigint NOT NULL,
  `fallback_locale` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bundle_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active_flag` tinyint(1) NOT NULL DEFAULT '1',
  `published_by` bigint DEFAULT NULL,
  `published_by_uuid` char(36) DEFAULT NULL,
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_release_locale_version` (`locale_code`,`release_version`),
  KEY `idx_sys_localization_release_locale_active` (`locale_code`,`active_flag`,`release_version`),
  KEY `idx_sys_localization_release_publisher_uuid` (`published_by`,`published_by_uuid`,`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_localization_translation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `locale_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `translated_message` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `translation_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TRANSLATED',
  `machine_generated` tinyint(1) NOT NULL DEFAULT '0',
  `review_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `translated_by` bigint DEFAULT NULL,
  `translated_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_translation_entry_locale` (`entry_id`,`locale_code`),
  KEY `idx_sys_localization_translation_locale_status` (`locale_code`,`translation_status`),
  KEY `idx_sys_localization_translation_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_localization_usage_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_line` int DEFAULT NULL,
  `source_text` text COLLATE utf8mb4_unicode_ci,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_usage_ref` (`entry_id`,`source_type`,`source_ref`,`source_line`),
  KEY `idx_sys_localization_usage_ref_entry` (`entry_id`),
  KEY `idx_sys_localization_usage_ref_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT '0',
  `menu_code` varchar(64) NOT NULL,
  `menu_name` varchar(128) NOT NULL,
  `menu_type` varchar(32) NOT NULL,
  `path` varchar(255) DEFAULT NULL,
  `component` varchar(255) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `icon` varchar(64) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `permission_key` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_menu_code` (`menu_code`),
  KEY `idx_sys_menu_status` (`status`,`sort_no`),
  KEY `idx_sys_menu_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `permission_key` varchar(128) NOT NULL,
  `permission_name` varchar(128) NOT NULL,
  `permission_group` varchar(64) DEFAULT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'CORE',
  `plugin_code` varchar(64) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_key` (`permission_key`),
  KEY `idx_sys_permission_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_plugin_definition` (
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
  `schema_mode` varchar(32) NOT NULL DEFAULT 'ISOLATED',
  `supports_hot_disable` tinyint NOT NULL DEFAULT '1',
  `supports_data_purge` tinyint NOT NULL DEFAULT '0',
  `runtime_contributions_json` json DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_definition_code` (`plugin_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_plugin_dependency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `depends_on_plugin_code` varchar(64) NOT NULL,
  `min_version` varchar(32) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_dependency_rel` (`plugin_code`,`depends_on_plugin_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_plugin_menu_rel` (
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
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_menu_rel` (`plugin_code`,`plugin_version`,`menu_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_plugin_permission_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `permission_key` varchar(128) NOT NULL,
  `permission_name` varchar(128) NOT NULL,
  `permission_group` varchar(64) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_permission_rel` (`plugin_code`,`plugin_version`,`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_plugin_runtime_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
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
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_sys_plugin_runtime_log_plugin_created` (`plugin_code`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_plugin_schema_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plugin_code` varchar(64) NOT NULL,
  `plugin_version` varchar(32) NOT NULL,
  `step_name` varchar(128) NOT NULL,
  `direction` varchar(16) NOT NULL,
  `script_path` varchar(512) DEFAULT NULL,
  `execution_status` varchar(32) NOT NULL,
  `detail_message` varchar(1024) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sys_plugin_schema_history_plugin_created` (`plugin_code`,`plugin_version`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_plugin_version` (
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
  `lifecycle_status` varchar(32) NOT NULL DEFAULT 'INSTALLED',
  `schema_status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `is_active` tinyint NOT NULL DEFAULT '0',
  `rollbackable` tinyint NOT NULL DEFAULT '0',
  `metadata_json` json DEFAULT NULL,
  `validation_report_json` json DEFAULT NULL,
  `staged_path` varchar(512) DEFAULT NULL,
  `installed_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_plugin_version_code_version` (`plugin_code`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_code` varchar(64) NOT NULL,
  `role_name` varchar(128) NOT NULL,
  `role_type` varchar(32) NOT NULL DEFAULT 'CUSTOM',
  `default_home_path` varchar(255) NOT NULL DEFAULT '/dashboard/home',
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`),
  KEY `idx_sys_role_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `resource_code` varchar(128) NOT NULL DEFAULT '*',
  `scope_type` varchar(32) NOT NULL DEFAULT 'SELF',
  `custom_dept_ids` varchar(1024) DEFAULT NULL,
  `custom_user_ids` varchar(1024) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_data_scope_resource` (`role_id`,`resource_code`),
  KEY `idx_sys_role_data_scope_role` (`role_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `permission_key` varchar(128) NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission_rel` (`role_id`,`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `word` varchar(128) NOT NULL,
  `normalized_word` varchar(128) NOT NULL,
  `category` varchar(64) DEFAULT NULL,
  `severity` varchar(32) DEFAULT NULL,
  `action` varchar(32) NOT NULL DEFAULT 'BLOCK',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_sensitive_word_normalized` (`normalized_word`,`deleted`),
  KEY `idx_sys_sensitive_word_enabled` (`enabled`,`deleted`),
  KEY `idx_sys_sensitive_word_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_work_order_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(160) NOT NULL,
  `detail_html` mediumtext NOT NULL,
  `priority` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `submitter_id` bigint NOT NULL,
  `submitter_uuid` char(36) DEFAULT NULL,
  `submitter_name` varchar(128) DEFAULT NULL,
  `admin_reply` varchar(4000) DEFAULT NULL,
  `handled_by` bigint DEFAULT NULL,
  `handled_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_sys_work_order_status_updated` (`status`,`deleted`,`updated_at`),
  KEY `idx_sys_work_order_submitter_updated` (`submitter_id`,`submitter_uuid`,`deleted`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` char(36) NOT NULL,
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
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_uuid` (`uuid`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_mobile` (`mobile`),
  KEY `idx_sys_user_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `dept_id` bigint NOT NULL,
  `primary_flag` tinyint NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_department_rel` (`user_id`,`user_uuid`,`dept_id`),
  KEY `idx_sys_user_department_dept` (`dept_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_passkey_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `user_handle` varchar(128) NOT NULL,
  `credential_id` varchar(512) NOT NULL,
  `public_key_cose` text NOT NULL,
  `sign_count` bigint NOT NULL DEFAULT '0',
  `transports` varchar(255) DEFAULT NULL,
  `backup_eligible` tinyint NOT NULL DEFAULT '0',
  `backup_state` tinyint NOT NULL DEFAULT '0',
  `label` varchar(128) NOT NULL DEFAULT '通行密钥',
  `last_used_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_passkey_credential_id` (`credential_id`),
  KEY `idx_passkey_user` (`user_id`,`user_uuid`,`deleted`),
  KEY `idx_passkey_user_handle` (`user_handle`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `role_id` bigint NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role_rel` (`user_id`,`user_uuid`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_wechat_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `openid` varchar(128) NOT NULL,
  `unionid` varchar(128) DEFAULT NULL,
  `scope` varchar(255) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_wechat_openid` (`openid`),
  UNIQUE KEY `uk_sys_user_wechat_unionid` (`unionid`),
  KEY `idx_sys_user_wechat_user` (`user_id`,`user_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_verification_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `factor_code` varchar(32) NOT NULL,
  `factor_name` varchar(64) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT '0',
  `bound` tinyint NOT NULL DEFAULT '0',
  `email_required` tinyint NOT NULL DEFAULT '0',
  `masked_contact` varchar(255) DEFAULT NULL,
  `secret_key` varchar(512) DEFAULT NULL,
  `recovery_codes_json` json DEFAULT NULL,
  `verified_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_verification_binding` (`user_id`,`user_uuid`,`factor_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_verification_challenge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `challenge_id` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `factor_code` varchar(32) NOT NULL,
  `challenge_type` varchar(16) NOT NULL,
  `expires_at` datetime NOT NULL,
  `consumed_flag` tinyint NOT NULL DEFAULT '0',
  `setup_secret` varchar(512) DEFAULT NULL,
  `setup_uri` varchar(512) DEFAULT NULL,
  `recovery_codes_json` json DEFAULT NULL,
  `code_hash` varchar(128) DEFAULT NULL,
  `masked_contact` varchar(255) DEFAULT NULL,
  `debug_code` varchar(32) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_verification_challenge` (`challenge_id`),
  KEY `idx_sys_verification_challenge_user_uuid` (`user_id`,`user_uuid`,`factor_code`,`challenge_type`,`deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_department_closure` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ancestor_id` bigint NOT NULL,
  `descendant_id` bigint NOT NULL,
  `depth` int NOT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_closure` (`ancestor_id`,`descendant_id`),
  KEY `idx_dept_closure_descendant` (`descendant_id`),
  KEY `idx_dept_closure_ancestor_depth` (`ancestor_id`,`depth`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `security_audit_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL,
  `event_type` varchar(128) NOT NULL,
  `severity` varchar(32) NOT NULL,
  `source_ip` varchar(128) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `request_id` varchar(128) DEFAULT NULL,
  `trace_id` varchar(128) DEFAULT NULL,
  `resource_code` varchar(128) DEFAULT NULL,
  `action_code` varchar(64) DEFAULT NULL,
  `target_id` varchar(128) DEFAULT NULL,
  `result` varchar(32) NOT NULL,
  `reason_code` varchar(128) DEFAULT NULL,
  `message` varchar(1000) DEFAULT NULL,
  `metadata_json` longtext,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_security_audit_created_at` (`created_at`),
  KEY `idx_security_audit_event_type_created_at` (`event_type`,`created_at`),
  KEY `idx_security_audit_request_id` (`request_id`),
  KEY `idx_security_audit_source_ip_created_at` (`source_ip`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_code` varchar(64) NOT NULL,
  `team_name` varchar(128) NOT NULL,
  `team_type` varchar(32) NOT NULL DEFAULT 'GENERAL',
  `avatar_url` varchar(512) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `visibility` varchar(32) NOT NULL DEFAULT 'PRIVATE',
  `join_mode` varchar(32) NOT NULL DEFAULT 'INVITE_ONLY',
  `owner_user_id` bigint NOT NULL,
  `owner_user_uuid` char(36) DEFAULT NULL,
  `member_count` int NOT NULL DEFAULT '1',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_code` (`team_code`,`deleted`),
  KEY `idx_team_owner` (`owner_user_id`,`owner_user_uuid`,`deleted`),
  KEY `idx_team_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_team_status` (`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `aiadc_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `locale` varchar(64) NOT NULL DEFAULT 'zh',
  `title` varchar(128) NOT NULL,
  `subtitle` varchar(64) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `icon_key` varchar(64) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT '100',
  `status` varchar(32) NOT NULL DEFAULT 'draft',
  `tags` varchar(1000) DEFAULT NULL,
  `cta_label` varchar(64) DEFAULT NULL,
  `cta_href` varchar(512) DEFAULT NULL,
  `badge_text` varchar(64) DEFAULT NULL,
  `badge_tone` varchar(32) DEFAULT NULL,
  `activity_date` varchar(64) NOT NULL,
  `activity_time` varchar(64) NOT NULL,
  `location` varchar(255) NOT NULL,
  `featured` tinyint NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_activity_code` (`code`,`locale`,`deleted`),
  KEY `idx_aiadc_activity_status` (`status`,`deleted`,`sort`),
  KEY `idx_aiadc_activity_featured` (`featured`,`deleted`,`sort`),
  KEY `idx_aiadc_activity_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `aiadc_competition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` char(36) NOT NULL,
  `competition_no` varchar(32) NOT NULL,
  `code` varchar(64) NOT NULL,
  `locale` varchar(64) NOT NULL DEFAULT 'zh',
  `title` varchar(128) NOT NULL,
  `short_name` varchar(128) DEFAULT NULL,
  `category` varchar(64) NOT NULL,
  `level` varchar(64) DEFAULT NULL,
  `competition_level` varchar(64) DEFAULT NULL,
  `organizer` varchar(128) DEFAULT NULL,
  `organizers_json` text,
  `registration_start` varchar(64) DEFAULT NULL,
  `registration_end` varchar(64) DEFAULT NULL,
  `competition_start` varchar(64) NOT NULL,
  `competition_end` varchar(64) DEFAULT NULL,
  `location` varchar(255) NOT NULL,
  `participation_scope` varchar(255) DEFAULT NULL,
  `participation_requirement` text,
  `schedule_json` text,
  `description` varchar(1000) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `contact_name` varchar(128) DEFAULT NULL,
  `contact_qr_code_url` varchar(512) DEFAULT NULL,
  `homepage_content` mediumtext,
  `tags` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'draft',
  `fee_mode` varchar(16) NOT NULL DEFAULT 'TEAM',
  `entry_fee_minor` bigint NOT NULL DEFAULT '0',
  `currency` varchar(16) NOT NULL DEFAULT 'CNY',
  `featured` tinyint NOT NULL DEFAULT '0',
  `sort` int NOT NULL DEFAULT '100',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_competition_uuid` (`uuid`),
  UNIQUE KEY `uk_aiadc_competition_no` (`competition_no`,`deleted`),
  UNIQUE KEY `uk_aiadc_competition_code` (`code`,`locale`,`deleted`),
  KEY `idx_aiadc_competition_uuid_deleted` (`uuid`,`deleted`),
  KEY `idx_aiadc_competition_category` (`category`,`deleted`,`sort`),
  KEY `idx_aiadc_competition_status` (`status`,`deleted`,`sort`),
  KEY `idx_aiadc_competition_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_aiadc_competition_featured` (`featured`,`deleted`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_registration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_no` varchar(64) NOT NULL,
  `competition_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL,
  `owner_user_uuid` char(36) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
  `fee_mode` varchar(16) NOT NULL DEFAULT 'TEAM',
  `entry_fee_minor` bigint NOT NULL DEFAULT '0',
  `member_count` int NOT NULL DEFAULT '0',
  `payable_amount_minor` bigint NOT NULL DEFAULT '0',
  `currency` varchar(16) NOT NULL DEFAULT 'CNY',
  `payment_order_no` varchar(64) DEFAULT NULL,
  `participant_no` varchar(64) DEFAULT NULL,
  `team_snapshot_json` longtext,
  `project_snapshot_json` longtext,
  `member_snapshot_json` longtext,
  `collection_schema_snapshot_json` longtext,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_registration_no` (`registration_no`,`deleted`),
  UNIQUE KEY `uk_competition_registration_participant` (`participant_no`,`deleted`),
  KEY `idx_competition_registration_owner` (`owner_user_id`,`deleted`,`created_at`),
  KEY `idx_competition_registration_owner_uuid` (`owner_user_uuid`,`deleted`,`created_at`),
  KEY `idx_competition_registration_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_competition_registration_competition` (`competition_id`,`status`,`deleted`),
  KEY `idx_competition_registration_payment` (`payment_order_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_payment_order_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_id` bigint NOT NULL,
  `provider_code` varchar(64) NOT NULL DEFAULT 'alipay',
  `client_ip` varchar(64) DEFAULT NULL,
  `notify_url` varchar(1024) DEFAULT NULL,
  `return_url` varchar(1024) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `claim_token` varchar(64) DEFAULT NULL,
  `claim_expires_at` datetime DEFAULT NULL,
  `process_message` varchar(1024) DEFAULT NULL,
  `owner_user_uuid` char(36) DEFAULT NULL,
  `simulated_role_id` bigint DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_payment_order_task_registration` (`registration_id`,`deleted`),
  KEY `idx_competition_payment_order_task_owner_uuid` (`owner_user_uuid`,`created_at`),
  KEY `idx_competition_payment_order_task_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_competition_payment_order_task_queue` (`deleted`,`status`,`next_retry_at`,`created_at`,`id`),
  KEY `idx_competition_payment_order_task_claim` (`claim_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `certificate_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_code` varchar(64) NOT NULL,
  `template_name` varchar(128) NOT NULL,
  `template_type` varchar(32) NOT NULL DEFAULT 'CERTIFICATE',
  `scene_type` varchar(32) NOT NULL DEFAULT 'COMPETITION_AWARD',
  `description` varchar(1000) DEFAULT NULL,
  `latest_version` int NOT NULL DEFAULT '1',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_template_code` (`template_code`,`deleted`),
  KEY `idx_certificate_template_status` (`status`,`deleted`,`updated_at`),
  KEY `idx_certificate_template_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `certificate_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL,
  `version` int NOT NULL,
  `background_file_id` bigint DEFAULT NULL,
  `background_url` varchar(512) DEFAULT NULL,
  `page_width` int NOT NULL DEFAULT '3508',
  `page_height` int NOT NULL DEFAULT '2480',
  `orientation` varchar(16) NOT NULL DEFAULT 'LANDSCAPE',
  `unit` varchar(16) NOT NULL DEFAULT 'PX',
  `dpi` int NOT NULL DEFAULT '300',
  `canvas_json` longtext NOT NULL,
  `variable_schema_json` longtext,
  `preview_file_id` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_template_version` (`template_id`,`version`,`deleted`),
  KEY `idx_certificate_template_version_status` (`template_id`,`status`,`deleted`),
  KEY `idx_certificate_template_version_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `certificate_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(64) NOT NULL,
  `batch_name` varchar(128) DEFAULT NULL,
  `template_id` bigint NOT NULL,
  `template_version_id` bigint NOT NULL,
  `competition_id` bigint DEFAULT NULL,
  `stage_id` bigint DEFAULT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `source_ref_id` bigint DEFAULT NULL,
  `total_count` int NOT NULL DEFAULT '0',
  `success_count` int NOT NULL DEFAULT '0',
  `failed_count` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `error_message` varchar(1000) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` varchar(64) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_batch_no` (`batch_no`,`deleted`),
  KEY `idx_certificate_batch_template` (`template_id`,`template_version_id`,`deleted`),
  KEY `idx_certificate_batch_status` (`status`,`deleted`,`created_at`),
  KEY `idx_certificate_batch_owner` (`created_by`,`created_by_uuid`,`deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `certificate_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `certificate_no` varchar(64) NOT NULL,
  `verification_code` varchar(32) NOT NULL,
  `public_token` varchar(64) NOT NULL,
  `batch_id` bigint DEFAULT NULL,
  `template_id` bigint NOT NULL,
  `template_version_id` bigint NOT NULL,
  `competition_id` bigint DEFAULT NULL,
  `stage_id` bigint DEFAULT NULL,
  `registration_id` bigint DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `recipient_name` varchar(128) NOT NULL,
  `recipient_type` varchar(32) NOT NULL DEFAULT 'CUSTOM',
  `competition_title` varchar(128) DEFAULT NULL,
  `project_name` varchar(128) DEFAULT NULL,
  `team_name` varchar(128) DEFAULT NULL,
  `award_name` varchar(128) DEFAULT NULL,
  `issue_date` date NOT NULL,
  `expire_date` date DEFAULT NULL,
  `data_json` longtext NOT NULL,
  `certificate_file_id` bigint DEFAULT NULL,
  `certificate_file_url` varchar(512) DEFAULT NULL,
  `preview_image_file_id` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'GENERATED',
  `revoked_reason` varchar(500) DEFAULT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` varchar(64) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_record_no` (`certificate_no`,`deleted`),
  UNIQUE KEY `uk_certificate_record_token` (`public_token`,`deleted`),
  KEY `idx_certificate_record_batch` (`batch_id`,`deleted`),
  KEY `idx_certificate_record_template` (`template_id`,`template_version_id`,`deleted`),
  KEY `idx_certificate_record_status` (`status`,`deleted`,`created_at`),
  KEY `idx_certificate_record_recipient` (`recipient_name`,`deleted`),
  KEY `idx_certificate_record_owner` (`created_by`,`created_by_uuid`,`deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `certificate_verify_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `certificate_id` bigint DEFAULT NULL,
  `certificate_no` varchar(64) DEFAULT NULL,
  `query_type` varchar(32) NOT NULL,
  `query_result` varchar(32) NOT NULL,
  `client_ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_certificate_verify_log_certificate` (`certificate_id`,`created_at`),
  KEY `idx_certificate_verify_log_no` (`certificate_no`,`created_at`),
  KEY `idx_certificate_verify_log_result` (`query_result`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_stage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_id` bigint NOT NULL,
  `stage_code` varchar(32) NOT NULL,
  `stage_name` varchar(128) NOT NULL,
  `material_submit_start` datetime DEFAULT NULL,
  `material_submit_end` datetime DEFAULT NULL,
  `review_start` datetime DEFAULT NULL,
  `review_end` datetime DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `sort` int NOT NULL DEFAULT '100',
  `promotion_rule_type` varchar(16) DEFAULT NULL,
  `promotion_rule_value` decimal(10,2) DEFAULT NULL,
  `promotion_tie_policy` varchar(32) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_stage_code` (`competition_id`,`stage_code`,`deleted`),
  KEY `idx_competition_stage_competition` (`competition_id`,`deleted`,`sort`),
  KEY `idx_competition_stage_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_stage_form` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `form_name` varchar(128) NOT NULL,
  `form_schema_json` longtext NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_stage_form` (`stage_id`,`version`,`deleted`),
  KEY `idx_competition_stage_form_competition` (`competition_id`,`stage_id`,`deleted`),
  KEY `idx_competition_stage_form_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `aiadc_activity_registration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `application_no` varchar(32) NOT NULL,
  `activity_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `mobile` varchar(32) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `organization` varchar(255) DEFAULT NULL,
  `position` varchar(128) DEFAULT NULL,
  `remark` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED',
  `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `owner_user_id` bigint NOT NULL,
  `owner_user_uuid` char(36) NOT NULL,
  `owner_username` varchar(128) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_activity_registration_no` (`application_no`),
  KEY `idx_aiadc_activity_registration_owner` (`owner_user_id`,`deleted`,`submitted_at`),
  KEY `idx_aiadc_activity_registration_activity` (`activity_id`,`deleted`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_stage_review_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `registration_id` bigint NOT NULL,
  `score` decimal(10,2) DEFAULT NULL,
  `decision` varchar(32) NOT NULL DEFAULT 'PENDING',
  `review_comment` varchar(1000) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decided_by_uuid` char(36) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_stage_review_result` (`stage_id`,`registration_id`,`deleted`),
  KEY `idx_competition_stage_review_result_rank` (`competition_id`,`stage_id`,`decision`,`score`,`deleted`),
  KEY `idx_competition_stage_review_result_registration` (`registration_id`,`published_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_config_item_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_code` varchar(64) NOT NULL DEFAULT 'DEFAULT',
  `item_type` varchar(64) NOT NULL,
  `item_key` varchar(128) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content_json` longtext,
  `content_text` longtext,
  `sort_order` int NOT NULL DEFAULT '100',
  `required_flag` tinyint NOT NULL DEFAULT '0',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_config_item_template_key` (`template_code`,`item_type`,`item_key`,`deleted`),
  KEY `idx_competition_config_item_template_lookup` (`template_code`,`enabled`,`deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_config_set` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_uuid` char(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` datetime DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_config_set_version` (`competition_uuid`,`version`,`deleted`),
  KEY `idx_competition_config_set_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_competition_config_set_status` (`competition_uuid`,`status`,`deleted`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_config_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_uuid` char(36) NOT NULL,
  `config_set_id` bigint NOT NULL,
  `item_type` varchar(64) NOT NULL,
  `item_key` varchar(128) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content_json` longtext,
  `content_text` longtext,
  `sort_order` int NOT NULL DEFAULT '100',
  `required_flag` tinyint NOT NULL DEFAULT '0',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_config_item_key` (`config_set_id`,`item_type`,`item_key`,`deleted`),
  KEY `idx_competition_config_item_lookup` (`competition_uuid`,`item_type`,`enabled`,`deleted`,`sort_order`),
  KEY `idx_competition_config_item_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_competition_config_item_set` (`config_set_id`,`deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `competition_config_item_template`
(`template_code`,`item_type`,`item_key`,`title`,`content_json`,`content_text`,`sort_order`,`required_flag`,`enabled`,`deleted`)
VALUES
('DEFAULT','AGREEMENT','commitment','Commitment','{}','Please configure the competition commitment.',10,1,1,0),
('DEFAULT','CONSENT','informed-consent','Informed consent','{}','Please configure the informed consent content.',20,1,1,0),
('DEFAULT','REGISTRATION_FIELD','contact-name','Contact name','{"type":"input","target":"registration"}',NULL,10,1,1,0),
('DEFAULT','REQUIRED_FILE','work-file','Work file','{"accept":"*","maxSizeMb":100,"maxCount":1}',NULL,10,1,1,0),
('DEFAULT','TEAM_SETTINGS','team-size-limits','团队人数限制','{"teamMinMembers":1,"teamMaxMembers":20,"standardField":true}',NULL,0,0,1,0),
('DEFAULT','TEAM_FIELD','teamName','团队名称','{"fieldType":"TEXT","placeholder":"请输入团队名称","validationRule":"NONE","standardField":true}',NULL,10,1,1,0),
('DEFAULT','TEAM_FIELD','avatarUrl','团队头像','{"fieldType":"IMAGE","placeholder":"请上传团队头像","validationRule":"NONE","standardField":true}',NULL,20,0,1,0),
('DEFAULT','TEAM_FIELD','description','团队简介','{"fieldType":"TEXTAREA","placeholder":"请输入团队简介","validationRule":"NONE","standardField":true}',NULL,30,0,1,0),
('DEFAULT','MEMBER_FIELD','memberName','成员姓名','{"fieldType":"TEXT","placeholder":"请输入成员姓名","validationRule":"NONE","standardField":true}',NULL,110,1,1,0),
('DEFAULT','PROJECT_FIELD','title','项目名称','{"fieldType":"TEXT","placeholder":"请输入项目名称","validationRule":"NONE","standardField":true}',NULL,210,1,1,0),
('DEFAULT','PROJECT_FIELD','imageUrl','项目头像','{"fieldType":"IMAGE","placeholder":"请上传项目头像","validationRule":"NONE","standardField":true}',NULL,220,0,1,0),
('DEFAULT','PROJECT_FIELD','description','项目简介','{"fieldType":"TEXTAREA","placeholder":"请输入项目简介","validationRule":"NONE","standardField":true}',NULL,230,0,1,0),
('DEFAULT','PROJECT_FIELD','intellectualPropertyType','知识产权类型','{"fieldType":"SELECT","placeholder":"请选择知识产权类型","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true,"options":"发明专利\\n实用新型专利\\n外观设计专利\\n软件著作权\\n作品著作权\\n商标\\n其他"}',NULL,310,1,1,0),
('DEFAULT','PROJECT_FIELD','intellectualPropertyName','知识产权名称','{"fieldType":"TEXT","placeholder":"请输入知识产权名称","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true}',NULL,320,1,1,0),
('DEFAULT','PROJECT_FIELD','registrationNumber','申请号/登记号','{"fieldType":"TEXT","placeholder":"请输入申请号或登记号","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true}',NULL,330,0,1,0),
('DEFAULT','PROJECT_FIELD','rightsHolder','权利人','{"fieldType":"TEXT","placeholder":"请输入权利人","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true}',NULL,340,1,1,0),
('DEFAULT','PROJECT_FIELD','legalStatus','法律状态','{"fieldType":"SELECT","placeholder":"请选择法律状态","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true,"options":"申请中\\n已受理\\n已授权\\n已登记\\n已失效\\n其他"}',NULL,350,0,1,0),
('DEFAULT','PROJECT_FIELD','grantDate','授权/登记日期','{"fieldType":"DATE","placeholder":"请选择授权或登记日期","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true}',NULL,360,0,1,0),
('DEFAULT','PROJECT_FIELD','distributionRegions','知识产权分布区域','{"fieldType":"MULTI_SELECT","placeholder":"请选择知识产权分布区域","validationRule":"NONE","groupLabel":"知识产权信息","standardField":true,"options":"中国大陆\\n中国香港\\n中国澳门\\n中国台湾\\n海外"}',NULL,370,1,1,0);

CREATE TABLE `competition_config_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_uuid` char(36) NOT NULL,
  `operator_user_id` bigint NOT NULL DEFAULT '0',
  `operator_user_uuid` char(36) DEFAULT NULL,
  `action` varchar(64) NOT NULL,
  `module` varchar(64) NOT NULL,
  `detail_message` varchar(1000) DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_competition_config_audit_competition` (`competition_uuid`,`created_at`,`id`),
  KEY `idx_competition_config_audit_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`),
  KEY `idx_competition_config_audit_operator` (`operator_user_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_submission_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_uuid` char(36) NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `registration_uuid` char(36) DEFAULT NULL,
  `config_version` int NOT NULL DEFAULT '1',
  `snapshot_json` longtext NOT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_competition_submission_snapshot_lookup` (`competition_uuid`,`config_version`,`user_uuid`,`created_at`),
  KEY `idx_competition_submission_snapshot_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `registration_material_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_id` bigint NOT NULL,
  `competition_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `form_version` int NOT NULL DEFAULT '1',
  `submitter_user_id` bigint NOT NULL,
  `submitter_user_uuid` char(36) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED',
  `submitted_at` datetime DEFAULT NULL,
  `locked_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_registration_material_submission` (`registration_id`,`stage_id`,`deleted`),
  KEY `idx_registration_material_submission_competition` (`competition_id`,`stage_id`,`deleted`),
  KEY `idx_registration_material_submission_submitter` (`submitter_user_id`,`submitter_user_uuid`,`deleted`),
  KEY `idx_registration_material_submission_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `registration_material_value` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint NOT NULL,
  `field_key` varchar(128) NOT NULL,
  `field_type` varchar(32) NOT NULL,
  `text_value` longtext,
  `file_id` bigint DEFAULT NULL,
  `json_value` longtext,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_registration_material_value_submission` (`submission_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `registration_material_value_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint NOT NULL,
  `revision_no` int NOT NULL,
  `field_key` varchar(128) NOT NULL,
  `field_type` varchar(32) NOT NULL,
  `text_value` longtext,
  `file_id` bigint DEFAULT NULL,
  `json_value` longtext,
  `changed_by` bigint NOT NULL,
  `changed_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_registration_material_value_revision` (`submission_id`,`revision_no`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `aiadc_expert` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(64) NOT NULL,
  `title` varchar(128) DEFAULT NULL,
  `organization` varchar(128) DEFAULT NULL,
  `position` varchar(128) DEFAULT NULL,
  `expertise` varchar(255) NOT NULL,
  `phone` varchar(64) DEFAULT NULL,
  `mobile` varchar(32) DEFAULT NULL,
  `id_card_number` varchar(32) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `account_status` varchar(32) DEFAULT NULL,
  `initial_password_reset_required` tinyint NOT NULL DEFAULT '0',
  `email` varchar(128) DEFAULT NULL,
  `avatar_url` varchar(512) DEFAULT NULL,
  `bio` varchar(1000) DEFAULT NULL,
  `tags` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'active',
  `sort` int NOT NULL DEFAULT '100',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_expert_code` (`code`,`deleted`),
  KEY `idx_aiadc_expert_user` (`user_id`,`user_uuid`,`deleted`),
  KEY `idx_aiadc_expert_status` (`status`,`deleted`,`sort`),
  KEY `idx_aiadc_expert_name` (`name`,`deleted`),
  KEY `idx_aiadc_expert_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `aiadc_project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `locale` varchar(16) NOT NULL DEFAULT 'zh',
  `title` varchar(128) NOT NULL,
  `category` varchar(64) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `image_url` varchar(512) DEFAULT NULL,
  `owner_name` varchar(128) DEFAULT NULL,
  `rating` varchar(32) NOT NULL DEFAULT 'popular',
  `sort` int NOT NULL DEFAULT '100',
  `status` varchar(32) NOT NULL DEFAULT 'draft',
  `tags` varchar(1000) DEFAULT NULL,
  `cta_label` varchar(64) DEFAULT NULL,
  `cta_href` varchar(512) DEFAULT NULL,
  `featured` tinyint NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_project_code` (`code`,`locale`,`deleted`),
  KEY `idx_aiadc_project_category` (`category`,`deleted`,`sort`),
  KEY `idx_aiadc_project_status` (`status`,`deleted`,`sort`),
  KEY `idx_aiadc_project_featured` (`featured`,`deleted`,`sort`),
  KEY `idx_aiadc_project_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `role` varchar(32) NOT NULL DEFAULT 'MEMBER',
  `member_alias` varchar(128) DEFAULT NULL,
  `member_name` varchar(128) DEFAULT NULL,
  `employee_no` varchar(64) DEFAULT NULL,
  `department_name` varchar(128) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL,
  `extra_values_json` json DEFAULT NULL,
  `member_source` varchar(32) NOT NULL DEFAULT 'REGISTERED',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `invited_by` bigint DEFAULT NULL,
  `invited_by_uuid` char(36) DEFAULT NULL,
  `joined_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_member` (`team_id`,`user_id`,`user_uuid`,`deleted`),
  KEY `idx_team_member_user` (`user_id`,`user_uuid`,`status`,`deleted`),
  KEY `idx_team_member_inviter` (`invited_by`,`invited_by_uuid`,`deleted`),
  KEY `idx_team_member_team` (`team_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team_invite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `invite_code` varchar(64) DEFAULT NULL,
  `invite_token_hash` varchar(128) NOT NULL,
  `invite_type` varchar(32) NOT NULL DEFAULT 'LINK',
  `role_on_join` varchar(32) NOT NULL DEFAULT 'MEMBER',
  `expires_at` datetime DEFAULT NULL,
  `max_uses` int DEFAULT NULL,
  `used_count` int NOT NULL DEFAULT '0',
  `need_approval` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `created_by` bigint NOT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_invite_token` (`invite_token_hash`,`deleted`),
  UNIQUE KEY `uk_team_invite_code` (`invite_code`,`deleted`),
  KEY `idx_team_invite_creator` (`created_by`,`created_by_uuid`,`deleted`),
  KEY `idx_team_invite_team` (`team_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team_join_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) DEFAULT NULL,
  `invite_id` bigint DEFAULT NULL,
  `apply_message` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_by_uuid` char(36) DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `review_message` varchar(1000) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_join_pending` (`team_id`,`user_id`,`user_uuid`,`status`,`deleted`),
  KEY `idx_team_join_team` (`team_id`,`status`,`deleted`),
  KEY `idx_team_join_user` (`user_id`,`user_uuid`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_message_attachment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint unsigned NOT NULL,
  `message_id` bigint unsigned NOT NULL,
  `file_id` bigint unsigned DEFAULT NULL,
  `original_file_name` varchar(255) DEFAULT NULL,
  `file_extension` varchar(32) DEFAULT NULL,
  `mime_type` varchar(255) DEFAULT NULL,
  `file_size_bytes` bigint unsigned DEFAULT NULL,
  `public_url` varchar(512) DEFAULT NULL,
  `preview_url` varchar(512) DEFAULT NULL,
  `download_url` varchar(512) DEFAULT NULL,
  `preview_mode` varchar(32) DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_message_attachment_file` (`message_id`,`file_id`),
  KEY `idx_ai_message_attachment_message` (`message_id`,`is_deleted`),
  KEY `idx_ai_message_attachment_conversation` (`conversation_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_conversation_share` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint unsigned NOT NULL,
  `share_token` varchar(128) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` datetime DEFAULT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `created_by_uuid` varchar(64) NOT NULL DEFAULT '',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_conversation_share_token` (`share_token`),
  KEY `idx_ai_conversation_share_conversation` (`conversation_id`,`is_deleted`),
  KEY `idx_ai_conversation_share_creator` (`created_by`,`created_by_uuid`,`is_deleted`),
  KEY `idx_ai_conversation_share_status` (`status`,`expires_at`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_subject (
    id bigint primary key auto_increment,
    subject_type varchar(32) not null,
    ref_id bigint not null,
    subject_code varchar(128) null,
    display_name varchar(128) null,
    status varchar(32) not null default 'ENABLED',
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_iam_subject_type_ref (subject_type, ref_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_permission (
    id bigint primary key auto_increment,
    permission_key varchar(128) not null,
    resource_code varchar(128) not null,
    action_code varchar(64) not null,
    permission_name varchar(128) not null,
    permission_group varchar(128) null,
    risk_level varchar(32) not null default 'LOW',
    require_confirm tinyint not null default 0,
    require_approval tinyint not null default 0,
    data_scope_required tinyint not null default 0,
    source_type varchar(32) not null default 'SYSTEM',
    plugin_code varchar(128) null,
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_iam_permission_key (permission_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_subject_role (
    id bigint primary key auto_increment,
    subject_id bigint not null,
    role_id bigint not null,
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_iam_subject_role (subject_id, role_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_delegation_grant (
    id bigint primary key auto_increment,
    delegator_subject_id bigint not null,
    delegate_subject_id bigint not null,
    resource_code varchar(128) null,
    action_code varchar(64) null,
    permission_key varchar(128) null,
    tool_code varchar(128) null,
    scope_type varchar(32) not null default 'SELF',
    max_risk_level varchar(32) not null default 'LOW',
    require_confirm tinyint not null default 1,
    require_approval tinyint not null default 0,
    valid_from datetime null,
    expires_at datetime null,
    status varchar(32) not null default 'ENABLED',
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    key idx_delegation_delegate (delegate_subject_id, deleted),
    key idx_delegation_delegator (delegator_subject_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_employee_tool_grant (
    id bigint primary key auto_increment,
    employee_id bigint not null,
    tool_code varchar(128) not null,
    permission_key varchar(128) null,
    resource_code varchar(128) null,
    action_code varchar(64) null,
    permission_mode varchar(32) not null default 'DENY',
    max_risk_level varchar(32) not null default 'LOW',
    require_confirm tinyint not null default 1,
    require_approval tinyint not null default 0,
    data_scope_type varchar(32) not null default 'SELF',
    enabled tinyint not null default 1,
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_ai_employee_tool_grant (employee_id, tool_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_employee_tool_grant_dept (
    id bigint primary key auto_increment,
    grant_id bigint not null,
    dept_id bigint not null,
    created_at datetime default current_timestamp,
    deleted tinyint not null default 0,
    key idx_ai_employee_tool_grant_dept (grant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_employee_tool_grant_user (
    id bigint primary key auto_increment,
    grant_id bigint not null,
    user_id bigint not null,
    created_at datetime default current_timestamp,
    deleted tinyint not null default 0,
    key idx_ai_employee_tool_grant_user (grant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_tool_execution_audit (
    id bigint primary key auto_increment,
    user_id bigint not null,
    employee_id bigint null,
    conversation_id bigint null,
    pending_tool_call_id bigint null,
    tool_code varchar(128) not null,
    permission_key varchar(128) null,
    resource_code varchar(128) null,
    action_code varchar(64) null,
    risk_level varchar(32) not null,
    execution_status varchar(32) not null,
    arguments_hash varchar(128) null,
    result_summary varchar(1000) null,
    error_message varchar(1000) null,
    created_at datetime default current_timestamp,
    key idx_ai_tool_execution_audit_created (created_at),
    key idx_ai_tool_execution_audit_employee (employee_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Consolidated indexes from archived Flyway migrations.
ALTER TABLE `sys_config`
    ADD INDEX `idx_sys_config_scope_key_deleted` (`config_scope`, `config_key`, `deleted`);

ALTER TABLE `sys_config`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_config_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `audit_login_log`
    ADD INDEX `idx_audit_login_user_result_recent` (`user_id`, `login_result`, `created_at`, `id`);
ALTER TABLE `audit_operation_log`
    ADD INDEX `idx_audit_operation_user_recent` (`username`, `created_at`, `id`);
ALTER TABLE `audit_operation_log`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_audit_operation_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_recent` (`publish_status`, `deleted`, `id`);
ALTER TABLE `msg_notice`
    ADD COLUMN `target_user_uuid` varchar(64) DEFAULT NULL,
    ADD INDEX `idx_msg_notice_visible_target_user_uuid_recent` (`publish_status`, `deleted`, `target_user_id`, `target_user_uuid`, `id`);
ALTER TABLE `msg_notice`
    ADD COLUMN `created_by_uuid` varchar(64) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` varchar(64) DEFAULT NULL,
    ADD INDEX `idx_msg_notice_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `msg_delivery_log`
    ADD COLUMN `target_user_uuid` varchar(64) DEFAULT NULL,
    ADD COLUMN `created_by_uuid` varchar(64) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` varchar(64) DEFAULT NULL,
    ADD INDEX `idx_msg_delivery_log_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`),
    ADD INDEX `idx_msg_delivery_log_target_user_uuid` (`target_user_id`, `target_user_uuid`, `created_at`);
ALTER TABLE `msg_notice_read`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_msg_notice_read_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `aiadc_expert`
    ADD COLUMN `user_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_aiadc_expert_user` (`user_id`, `user_uuid`, `deleted`);
ALTER TABLE `sys_work_order_feedback`
    ADD COLUMN `submitter_uuid` char(36) DEFAULT NULL,
    DROP INDEX `idx_sys_work_order_submitter_updated`,
    ADD INDEX `idx_sys_work_order_submitter_updated` (`submitter_id`, `submitter_uuid`, `deleted`, `updated_at`);
ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_target_role_recent` (`publish_status`, `deleted`, `target_role_id`, `id`);
ALTER TABLE `sys_user_role`
    ADD INDEX `idx_sys_user_role_user_deleted` (`user_id`, `user_uuid`, `deleted`, `role_id`);
ALTER TABLE `sys_role_permission`
    ADD INDEX `idx_sys_role_permission_role_deleted_perm` (`role_id`, `deleted`, `permission_key`);
ALTER TABLE `sys_localization_entry`
    ADD INDEX `idx_sys_localization_entry_namespace_deleted_status` (`namespace_id`, `deleted`, `status`, `updated_at`);
ALTER TABLE `sys_localization_translation`
    ADD INDEX `idx_sys_localization_translation_locale_deleted_entry` (`locale_code`, `deleted`, `entry_id`);
ALTER TABLE `sys_localization_namespace`
    ADD INDEX `idx_sys_localization_namespace_deleted_sort` (`deleted`, `sort_no`, `id`);
ALTER TABLE `sys_localization_language`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_localization_language_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_localization_namespace`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_localization_namespace_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_localization_entry`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_localization_entry_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_localization_translation`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_localization_translation_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_localization_usage_ref`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_localization_usage_ref_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_localization_release`
    ADD COLUMN `published_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_localization_release_publisher_uuid` (`published_by`, `published_by_uuid`, `published_at`);
ALTER TABLE `payment_event_outbox`
    ADD COLUMN `user_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_payment_outbox_user_uuid` (`user_uuid`, `created_at`);
ALTER TABLE `payment_event_outbox`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `payment_order`
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `payment_refund`
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `payment_webhook_event`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `payment_event_outbox`
    ADD INDEX `idx_payment_outbox_deleted_status_retry_created` (`deleted`, `status`, `next_retry_at`, `created_at`, `id`);
ALTER TABLE `payment_event_outbox`
    ADD INDEX `idx_payment_outbox_deleted_status` (`deleted`, `status`);
ALTER TABLE `payment_webhook_event`
    ADD INDEX `idx_payment_webhook_event_provider_nonce_deleted_received` (`provider_code`, `nonce`, `deleted`, `received_at`);
ALTER TABLE `payment_webhook_event`
    ADD INDEX `idx_payment_webhook_event_provider_event_deleted_id` (`provider_code`, `event_id`, `deleted`, `id`);
ALTER TABLE `payment_provider_config`
    ADD INDEX `idx_payment_provider_config_provider_deleted_id` (`provider_code`, `deleted`, `id`);
ALTER TABLE `payment_provider_config`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_payment_provider_config_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `payment_event_outbox`
    ADD INDEX `idx_payment_outbox_owner_queue` (`deleted`, `source_type`, `status`, `next_retry_at`, `created_at`, `id`);
ALTER TABLE `payment_event_outbox`
    ADD INDEX `idx_payment_outbox_claim_token` (`claim_token`);
ALTER TABLE `ai_knowledge_base`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_ai_knowledge_base_creator_uuid` (`created_by`, `created_by_uuid`, `create_time`);
ALTER TABLE `ai_knowledge_base_acl`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_ai_knowledge_acl_creator_uuid` (`created_by`, `created_by_uuid`, `create_time`);
ALTER TABLE `ai_knowledge_document`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `simulated_role_id` bigint unsigned DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_ai_knowledge_document_creator_uuid` (`created_by`, `created_by_uuid`, `create_time`);
ALTER TABLE `ai_knowledge_document`
    ADD COLUMN `index_claim_token` varchar(64) DEFAULT NULL,
    ADD COLUMN `index_claim_expires_at` datetime DEFAULT NULL,
    ADD INDEX `idx_ai_knowledge_document_index_claim` (`index_claim_token`);
ALTER TABLE `sys_export_task`
    ADD COLUMN `claimed_by` varchar(128) DEFAULT NULL,
    ADD COLUMN `claim_token` varchar(128) DEFAULT NULL,
    ADD COLUMN `claim_expires_at` datetime DEFAULT NULL,
    ADD INDEX `idx_sys_export_task_claim_token` (`claim_token`);
ALTER TABLE `certificate_batch`
    ADD COLUMN `updated_by_uuid` varchar(64) DEFAULT NULL;
ALTER TABLE `certificate_record`
    ADD COLUMN `updated_by_uuid` varchar(64) DEFAULT NULL;
ALTER TABLE `competition_registration`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_competition_registration_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `team`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_team_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `aiadc_competition`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_aiadc_competition_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `aiadc_activity`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_aiadc_activity_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `aiadc_expert`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_aiadc_expert_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `competition_stage`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_competition_stage_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `competition_stage_form`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_competition_stage_form_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `certificate_template`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_certificate_template_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `certificate_template_version`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_certificate_template_version_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `competition_payment_order_task`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `simulated_role_id` bigint DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_competition_payment_order_task_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `registration_material_submission`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_registration_material_submission_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `team_invite`
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `sys_sensitive_word`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_sensitive_word_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_department`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_department_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_role`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_role_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_role_permission`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `sys_role_data_scope`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `sys_dict_type`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_dict_type_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_dict_item`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_dict_item_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_menu`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_menu_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_permission`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_permission_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_user`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_user_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_user_role`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `sys_user_department`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `sys_user_passkey_credential`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `sys_user_wechat_binding`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `sys_verification_binding`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `sys_verification_challenge`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `sys_work_order_feedback`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL;
ALTER TABLE `aiadc_project`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_aiadc_project_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `competition_config_set`
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_competition_config_set_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `competition_config_item`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_competition_config_item_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_plugin_definition`
    ADD INDEX `idx_sys_plugin_definition_deleted_status_sort_code` (`deleted`, `status`, `sort_no`, `plugin_code`);
ALTER TABLE `sys_plugin_version`
    ADD INDEX `idx_sys_plugin_version_plugin_deleted_status_created` (`plugin_code`, `deleted`, `created_at`);
ALTER TABLE `sys_plugin_version`
    ADD INDEX `idx_sys_plugin_version_plugin_active_deleted` (`plugin_code`, `is_active`, `deleted`);
ALTER TABLE `sys_plugin_runtime_log`
    ADD INDEX `idx_sys_plugin_runtime_log_code_deleted_id` (`plugin_code`, `deleted`, `id`);
ALTER TABLE `sys_plugin_menu_rel`
    ADD INDEX `idx_sys_plugin_menu_rel_code_version_deleted_sort` (`plugin_code`, `plugin_version`, `deleted`, `sort_no`, `id`);
ALTER TABLE `sys_plugin_permission_rel`
    ADD INDEX `idx_sys_plugin_permission_rel_code_version_deleted` (`plugin_code`, `plugin_version`, `deleted`, `id`);
ALTER TABLE `sys_plugin_definition`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_plugin_definition_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_plugin_version`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_plugin_version_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_plugin_dependency`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_plugin_dependency_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_plugin_menu_rel`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_plugin_menu_rel_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_plugin_permission_rel`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_plugin_permission_rel_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_plugin_runtime_log`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_plugin_runtime_log_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `sys_plugin_schema_history`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_sys_plugin_schema_history_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `plugin_event_outbox`
    ADD COLUMN `user_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_plugin_event_outbox_user_uuid` (`user_uuid`, `created_at`);
ALTER TABLE `platform_event_outbox`
    MODIFY COLUMN `created_by` bigint DEFAULT NULL,
    MODIFY COLUMN `updated_by` bigint DEFAULT NULL,
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_platform_event_outbox_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `plugin_event_outbox`
    MODIFY COLUMN `created_by` bigint DEFAULT NULL,
    MODIFY COLUMN `updated_by` bigint DEFAULT NULL,
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_plugin_event_outbox_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `plugin_event_outbox`
    ADD INDEX `idx_plugin_event_outbox_deleted_status_retry_created` (`deleted`, `status`, `next_retry_at`, `created_at`, `id`);
ALTER TABLE `plugin_event_outbox`
    ADD INDEX `idx_plugin_event_outbox_claim_token` (`claim_token`);
ALTER TABLE `msg_notice_read`
    ADD INDEX `idx_msg_notice_read_notice_user_deleted` (`notice_id`, `user_id`, `user_uuid`, `deleted`);
ALTER TABLE `file_object`
    ADD COLUMN `uploaded_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_file_object_uploader` (`uploaded_by`, `uploaded_by_uuid`, `deleted`);
ALTER TABLE `file_object`
    ADD COLUMN `visibility_scope` varchar(32) NOT NULL DEFAULT 'PERSONAL',
    ADD INDEX `idx_file_object_visibility` (`visibility_scope`, `deleted`);
UPDATE `file_object`
SET `visibility_scope` = 'DOWNLOAD_CENTER'
WHERE `bucket` = 'download_center'
  AND `deleted` = 0
  AND (`visibility_scope` IS NULL OR `visibility_scope` = '' OR `visibility_scope` = 'PERSONAL');
ALTER TABLE `file_object`
    ADD INDEX `idx_file_object_deleted_bucket` (`deleted`, `bucket`);
ALTER TABLE `file_object`
    ADD INDEX `idx_file_object_deleted_created_id` (`deleted`, `created_at`, `id`);
ALTER TABLE `file_processing_task`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_file_processing_task_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `file_object`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_file_object_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `file_processing_artifact`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_file_processing_artifact_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `file_storage_space`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD COLUMN `updated_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_file_storage_space_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `file_storage_space`
    ADD INDEX `idx_file_storage_space_deleted_default_id` (`deleted`, `default_flag`, `id`);
ALTER TABLE `competition_config_audit`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_competition_config_audit_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
ALTER TABLE `competition_submission_snapshot`
    ADD COLUMN `created_by_uuid` char(36) DEFAULT NULL,
    ADD INDEX `idx_competition_submission_snapshot_creator_uuid` (`created_by`, `created_by_uuid`, `created_at`);
CREATE INDEX `idx_sensitive_word_enabled`
    ON `sys_sensitive_word` (`enabled`, `deleted`, `normalized_word`);

-- Bootstrap protected administrator.
-- The BCrypt hashes below are for the initial password `123456`.
INSERT INTO `sys_permission` (
    `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    ('audit:login:view', '查看登录日志', 'audit', 'CORE', NULL, 0, 0, 0),
    ('audit:operation:view', '查看操作日志', 'audit', 'CORE', NULL, 0, 0, 0),
    ('audit:view', '查看审计中心', 'audit', 'CORE', NULL, 0, 0, 0),
    ('aiadc:activity:view', '查看活动', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:activity:create', '新建活动', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:activity:update', '编辑活动', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:activity:delete', '删除活动', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:competition:view', '查看赛事', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:competition:create', '新建赛事', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:competition:update', '编辑赛事', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:competition:delete', '删除赛事', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:registration:view', '查看赛事报名', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:registration:create', '创建赛事报名', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:registration:update', '编辑赛事报名', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:registration:pay', '支付报名费用', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:material:view', '查看报名材料', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:material:submit', '提交报名材料', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:stage:view', '查看赛事阶段', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:stage:manage', '管理赛事阶段', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-template:view', '查看证书模板', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-template:create', '新建证书模板', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-template:update', '编辑证书模板', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-template:publish', '发布证书模板', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-template:delete', '删除证书模板', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-batch:view', '查看证书批次', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-batch:create', '生成证书批次', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-batch:download', '下载证书批次', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate:view', '查看证书', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate:download', '下载证书', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate:regenerate', '重新生成证书', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate:revoke', '撤销证书', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('expert:view', '查看专家', 'expert', 'CORE', NULL, 0, 0, 0),
    ('expert:create', '新建专家', 'expert', 'CORE', NULL, 0, 0, 0),
    ('expert:update', '编辑专家', 'expert', 'CORE', NULL, 0, 0, 0),
    ('expert:delete', '删除专家', 'expert', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:view', '查看项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:create', '新建项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:update', '编辑项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:delete', '删除项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('dashboard:view', 'View dashboard', 'dashboard', 'CORE', NULL, 0, 0, 0),
    ('download:center:view', '查看下载中心', 'download', 'CORE', NULL, 0, 0, 0),
    ('download:center:create', '创建下载任务', 'download', 'CORE', NULL, 0, 0, 0),
    ('download:center:delete', '删除下载任务', 'download', 'CORE', NULL, 0, 0, 0),
    ('localization:view', '查看多语言设置', 'localization', 'CORE', NULL, 0, 0, 0),
    ('localization:create', '创建多语言资源', 'localization', 'CORE', NULL, 0, 0, 0),
    ('localization:update', '编辑多语言资源', 'localization', 'CORE', NULL, 0, 0, 0),
    ('localization:delete', '删除多语言资源', 'localization', 'CORE', NULL, 0, 0, 0),
    ('localization:publish', '发布多语言资源', 'localization', 'CORE', NULL, 0, 0, 0),
    ('localization:rollback', '回滚多语言资源', 'localization', 'CORE', NULL, 0, 0, 0),
    ('localization:sync', '同步多语言资源', 'localization', 'CORE', NULL, 0, 0, 0),
    ('ai:view', '查看 AI 能力', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:chat:send', '发送 AI 对话', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:employee:create', '创建数字员工', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:employee:update', '编辑数字员工', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:employee:delete', '删除数字员工', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:employee:status', '启停数字员工', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:employee:skills', '配置数字员工技能', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:llm:create', '创建模型服务', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:llm:update', '编辑模型服务', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:llm:delete', '删除模型服务', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:llm:status', '启停模型服务', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:skill:view', '查看技能列表', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:knowledge:view', '查看知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:knowledge:query', '检索知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:knowledge:create', '创建知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:knowledge:update', '编辑知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:knowledge:delete', '删除知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:knowledge:bind', '绑定知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:knowledge:share', '共享知识库', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:knowledge:document:upload', '上传知识文档', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:knowledge:document:delete', '删除知识文档', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:knowledge:document:index', '重建知识索引', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:tool:view', '查看 AI 工具', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:tool:execute', '执行 AI 工具', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:tool:invoke', '调用 AI 工具', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:tool-policy:view', '查看 AI 工具策略', 'ai', 'CORE', NULL, 0, 0, 0),
    ('ai:tool-policy:manage', '管理 AI 工具策略', 'ai', 'CORE', NULL, 0, 0, 0),
    ('payment:config:test', '测试支付配置', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:config:update', '编辑支付配置', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:config:view', '查看支付配置', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:order:create', '创建支付订单', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:order:view', '查看支付订单', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:refund:create', 'Create refund', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:refund:view', 'View refund', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:view', '访问支付中心', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:webhook:retry', '重试支付回调', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:webhook:view', '查看支付回调', 'payment', 'CORE', NULL, 0, 0, 0),
    ('message:message:view', '查看站内消息', 'message', 'CORE', NULL, 0, 0, 0),
    ('message:message:read', '标记消息已读', 'message', 'CORE', NULL, 0, 0, 0),
    ('message:message:write', '发送站内消息', 'message', 'CORE', NULL, 0, 0, 0),
    ('message:message:retract', '撤回站内消息', 'message', 'CORE', NULL, 0, 0, 0),
    ('plugin:management:view', '查看插件管理', 'plugin', 'CORE', NULL, 0, 0, 0),
    ('plugin:management:upload', '上传插件包', 'plugin', 'CORE', NULL, 0, 0, 0),
    ('plugin:management:install', '安装插件', 'plugin', 'CORE', NULL, 0, 0, 0),
    ('plugin:management:upgrade', '升级插件', 'plugin', 'CORE', NULL, 0, 0, 0),
    ('plugin:management:rollback', '回滚插件', 'plugin', 'CORE', NULL, 0, 0, 0),
    ('plugin:management:enable', '启用插件', 'plugin', 'CORE', NULL, 0, 0, 0),
    ('plugin:management:disable', '停用插件', 'plugin', 'CORE', NULL, 0, 0, 0),
    ('plugin:management:logs', '查看插件日志', 'plugin', 'CORE', NULL, 0, 0, 0),
    ('plugin:sensitive-words:import', 'Import sensitive words', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    ('plugin:sensitive-words:manage', 'Manage sensitive words', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    ('plugin:sensitive-words:view', 'View sensitive words', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    ('plugin:work-order-feedback:create', '提交工单反馈', 'plugin', 'PLUGIN', 'work-order-feedback', 0, 0, 0),
    ('plugin:work-order-feedback:manage', '处理工单反馈', 'plugin', 'PLUGIN', 'work-order-feedback', 0, 0, 0),
    ('plugin:work-order-feedback:view', '查看工单反馈', 'plugin', 'PLUGIN', 'work-order-feedback', 0, 0, 0),
    ('profile:view', '查看个人中心', 'profile', 'CORE', NULL, 0, 0, 0),
    ('system:config:update', '编辑系统配置', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:config:view', '查看系统配置', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:department:create', '新建部门', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:department:delete', '删除部门', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:department:update', '编辑部门', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:department:view', '查看部门', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:dict:create', '新建字典', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:dict:delete', '删除字典', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:dict:update', '编辑字典', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:dict:view', '查看字典', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:file:delete', '删除文件', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:file:manage', '管理文件', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:file:manage:delete', '删除全站文件', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:file:publish', '发布公开文件', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:file:upload', '上传文件', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:file:view', '查看文件', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:menu:create', '新建菜单', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:menu:delete', '删除菜单', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:menu:status', '启停菜单', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:menu:update', '编辑菜单', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:menu:view', '查看菜单', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:monitor:docs:view', '查看接口文档', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:monitor:redis:view', '查看 Redis 监控', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:monitor:service:view', '查看服务监控', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:monitor:view', '查看系统监控', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:notification:view', '查看消息通知', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:notification:write', '发送系统通知', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:online-user:ban', '封禁在线用户', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:online-user:kick', '强退在线用户', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:online-user:view', '查看在线用户', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:create', '新建角色', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:delete', '删除角色', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:grant', '分配角色', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:permissions', '配置角色权限', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:update', '编辑角色', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:view', '查看角色', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:update:check', 'Check system updates', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:update:install', '安装系统更新', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:update:rollback', '回滚系统更新', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:update:view', '查看系统更新', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:create', '新建用户', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:delete', '删除用户', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:export', '导出用户', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:sensitive:view', '查看用户敏感信息', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:status', '启停用户', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:update', '编辑用户', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:view', '查看用户', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:verification:manage', '管理认证设置', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:verification:view', '查看认证设置', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:view', '访问系统管理', 'system', 'CORE', NULL, 0, 0, 0),
    ('team:view', '查看团队', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:create', '新建团队', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:update', '编辑团队', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:delete', '删除团队', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:member:view', '查看团队成员', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:member:invite', 'Invite team member', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:member:remove', '移除团队成员', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:member:role-update', '调整团队成员角色', 'team', 'CORE', NULL, 0, 0, 0),
    ('workflow:view', '查看工作流', 'workflow', 'CORE', NULL, 0, 0, 0),
    ('workflow:config', '配置工作流', 'workflow', 'CORE', NULL, 0, 0, 0),
    ('workflow:approve', '审批工作流', 'workflow', 'CORE', NULL, 0, 0, 0),
    ('user:center:view', '访问用户中心', 'user', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `source_type` = VALUES(`source_type`),
    `plugin_code` = VALUES(`plugin_code`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
    `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
VALUES
    (-955, 0, 'dashboard.home', '首页', 'MENU', '/dashboard/home', '@/pages/dashboard/DashboardHomePage', 'DashboardOutlined', 0, 'dashboard:view', 'ENABLED', 0, 0, 0),
    (-956, -1100, 'files.download-center', '下载中心', 'MENU', '/data-management/download-center', '@/pages/files/DownloadCenter', 'DownloadOutlined', 6, 'download:center:view', 'ENABLED', 0, 0, 0),
    (-1100, 0, 'data.management.root', '数据管理', 'CATALOG', '/data-management', 'redirect:/competitions/management', 'DatabaseOutlined', 3, NULL, 'ENABLED', 0, 0, 0),
    (-1101, -1100, 'data.query-center', '查询中心', 'CATALOG', '/data-management/query-center', 'redirect:/team/search', 'SearchOutlined', 7, NULL, 'ENABLED', 0, 0, 0),
    (-1041, 0, 'activity.root', '活动', 'CATALOG', '/activities', 'redirect:/activities/management', 'CalendarOutlined', 90, NULL, 'DISABLED', 0, 0, 1),
    (-1052, -1100, 'activity.activities', '活动管理', 'MENU', '/activities/management', '@/pages/activity', 'CalendarOutlined', 2, 'aiadc:activity:view', 'ENABLED', 0, 0, 0),
    (-1053, -1101, 'activity.search', '活动查询', 'MENU', '/activities/search', '@/pages/activity', 'SearchOutlined', 3, 'aiadc:activity:view', 'ENABLED', 0, 0, 0),
    (-1043, -1052, 'activity.activities.create', '新增活动', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:activity:create', 'ENABLED', 0, 0, 0),
    (-1044, -1052, 'activity.activities.update', '编辑活动', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:activity:update', 'ENABLED', 0, 0, 0),
    (-1045, -1052, 'activity.activities.delete', '删除活动', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:activity:delete', 'ENABLED', 0, 0, 0),
    (-1069, 0, 'registration.root', '报名', 'CATALOG', '/registration', 'redirect:/competitions/register', 'FormOutlined', 4, NULL, 'ENABLED', 0, 0, 0),
    (-1070, 0, 'competition.root', '赛事', 'CATALOG', '/competitions', 'redirect:/competitions/register', 'TrophyOutlined', 5, NULL, 'ENABLED', 0, 0, 0),
    (-1071, -1100, 'competition.management', '赛事管理', 'MENU', '/competitions/management', '@/pages/competition', 'TrophyOutlined', 1, 'aiadc:competition:view', 'ENABLED', 0, 0, 0),
    (-1075, -1069, 'competition.registration', '赛事报名', 'MENU', '/competitions/register', '@/pages/competition', 'FormOutlined', 1, NULL, 'ENABLED', 0, 0, 0),
    (-1076, -1069, 'activity.registration', '活动报名', 'MENU', '/activities/register', '@/pages/competition', 'CalendarOutlined', 2, NULL, 'ENABLED', 0, 0, 0),
    (-1077, -1070, 'expert.application', '专家申请', 'MENU', '/competitions/expert-apply', '@/pages/competition', 'SolutionOutlined', 3, NULL, 'ENABLED', 0, 0, 0),
    (-1091, 0, 'project.root', '项目', 'CATALOG', '/projects', 'redirect:/projects/management', 'ProjectOutlined', 92, NULL, 'DISABLED', 0, 0, 1),
    (-1092, -1100, 'project.management', '项目管理', 'MENU', '/projects/management', '@/pages/project', 'ProjectOutlined', 3, 'aiadc:project:view', 'ENABLED', 0, 0, 0),
    (-1096, -1101, 'project.search', '项目查询', 'MENU', '/projects/search', '@/pages/project', 'SearchOutlined', 2, 'aiadc:project:view', 'ENABLED', 0, 0, 0),
    (-1093, -1092, 'project.management.create', '新增项目', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:project:create', 'ENABLED', 0, 0, 0),
    (-1094, -1092, 'project.management.update', '编辑项目', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:project:update', 'ENABLED', 0, 0, 0),
    (-1095, -1092, 'project.management.delete', '删除项目', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:project:delete', 'ENABLED', 0, 0, 0),
    (-1110, -1100, 'payment.management', '支付管理', 'MENU', '/payments/management', '@/pages/payment', 'CreditCardOutlined', 5, 'payment:order:view', 'ENABLED', 0, 0, 0),
    (-1111, -1101, 'payment.status', 'Payment status query', 'MENU', '/payments/status', '@/pages/payment', 'SearchOutlined', 4, 'payment:order:view', 'ENABLED', 0, 0, 0),
    (-1079, 0, 'certificate.root', '证书管理', 'CATALOG', '/certificates', 'redirect:/certificates/templates', 'FileProtectOutlined', 6, NULL, 'ENABLED', 0, 0, 0),
    (-1080, -1079, 'certificate.templates', '证书模板', 'MENU', '/certificates/templates', '@/pages/certificates/TemplatesPage', 'FileProtectOutlined', 1, 'aiadc:certificate-template:view', 'ENABLED', 0, 0, 0),
    (-1081, -1079, 'certificate.generate', '证书生成', 'MENU', '/certificates/generate', '@/pages/certificates/GeneratePage', 'FileDoneOutlined', 2, 'aiadc:certificate-batch:create', 'ENABLED', 0, 0, 0),
    (-1082, -1079, 'certificate.records', '证书记录', 'MENU', '/certificates/records', '@/pages/certificates/RecordsPage', 'AuditOutlined', 3, 'aiadc:certificate:view', 'ENABLED', 0, 0, 0),
    (-1072, -1071, 'competition.management.create', '新增赛事', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:competition:create', 'ENABLED', 0, 0, 0),
    (-1073, -1071, 'competition.management.update', '编辑赛事', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:competition:update', 'ENABLED', 0, 0, 0),
    (-1074, -1071, 'competition.management.delete', '删除赛事', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:competition:delete', 'ENABLED', 0, 0, 0),
    (-1083, -1080, 'certificate.templates.create', 'Create certificate template', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:certificate-template:create', 'ENABLED', 0, 0, 0),
    (-1084, -1080, 'certificate.templates.update', 'Update certificate template', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:certificate-template:update', 'ENABLED', 0, 0, 0),
    (-1085, -1080, 'certificate.templates.publish', 'Publish certificate template', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:certificate-template:publish', 'ENABLED', 0, 0, 0),
    (-1086, -1080, 'certificate.templates.delete', 'Archive certificate template', 'BUTTON', NULL, NULL, NULL, 4, 'aiadc:certificate-template:delete', 'ENABLED', 0, 0, 0),
    (-1087, -1081, 'certificate.generate.create', 'Generate certificates', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:certificate-batch:create', 'ENABLED', 0, 0, 0),
    (-1088, -1082, 'certificate.records.download', 'Download certificate', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:certificate:download', 'ENABLED', 0, 0, 0),
    (-1089, -1082, 'certificate.records.regenerate', 'Regenerate certificate', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:certificate:regenerate', 'ENABLED', 0, 0, 0),
    (-1090, -1082, 'certificate.records.revoke', 'Revoke certificate', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:certificate:revoke', 'ENABLED', 0, 0, 0),
    (-1060, 0, 'expert.root', '专家库', 'CATALOG', '/experts', 'redirect:/experts/management', 'SolutionOutlined', 7, NULL, 'ENABLED', 0, 0, 0),
    (-1061, -1060, 'expert.management', '专家管理', 'MENU', '/experts/management', '@/pages/expert', 'SolutionOutlined', 1, 'expert:view', 'ENABLED', 0, 0, 0),
    (-1065, -1060, 'expert.query', '专家查询', 'MENU', '/experts/query', '@/pages/expert', 'SearchOutlined', 2, 'expert:view', 'ENABLED', 0, 0, 0),
    (-1062, -1061, 'expert.management.create', '创建专家', 'BUTTON', NULL, NULL, NULL, 1, 'expert:create', 'ENABLED', 0, 0, 0),
    (-1063, -1061, 'expert.management.update', '编辑专家', 'BUTTON', NULL, NULL, NULL, 2, 'expert:update', 'ENABLED', 0, 0, 0),
    (-1064, -1061, 'expert.management.delete', '删除专家', 'BUTTON', NULL, NULL, NULL, 3, 'expert:delete', 'ENABLED', 0, 0, 0),
    (-957, 0, 'team.root', '团队', 'CATALOG', '/team', 'redirect:/team/management', 'TeamOutlined', 93, 'team:view', 'DISABLED', 0, 0, 1),
    (-1040, -1100, 'team.management', '团队管理', 'MENU', '/team/management', '@/pages/team', 'TeamOutlined', 4, 'team:view', 'ENABLED', 0, 0, 0),
    (-1050, -1101, 'team.search', '团队查询', 'MENU', '/team/search', '@/pages/team', 'SearchOutlined', 1, 'team:view', 'ENABLED', 0, 0, 0),
    (-958, -1040, 'team.create', '创建团队', 'BUTTON', NULL, NULL, NULL, 1, 'team:create', 'ENABLED', 0, 0, 0),
    (-959, -1040, 'team.update', '编辑团队', 'BUTTON', NULL, NULL, NULL, 2, 'team:update', 'ENABLED', 0, 0, 0),
    (-960, -1040, 'team.delete', '删除团队', 'BUTTON', NULL, NULL, NULL, 3, 'team:delete', 'ENABLED', 0, 0, 0),
    (-961, -1040, 'team.member.view', '查看成员', 'BUTTON', NULL, NULL, NULL, 4, 'team:member:view', 'ENABLED', 0, 0, 0),
    (-962, -1040, 'team.member.invite', 'Invite member', 'BUTTON', NULL, NULL, NULL, 5, 'team:member:invite', 'ENABLED', 0, 0, 0),
    (-963, -1040, 'team.member.remove', '移除成员', 'BUTTON', NULL, NULL, NULL, 6, 'team:member:remove', 'ENABLED', 0, 0, 0),
    (-964, -1040, 'team.member.role-update', '更新成员角色', 'BUTTON', NULL, NULL, NULL, 7, 'team:member:role-update', 'ENABLED', 0, 0, 0),
    (-950, 0, 'user.center.root', '用户中心', 'CATALOG', '/user-center', '@/layouts/SettingsLayout', 'TeamOutlined', 18, 'user:center:view', 'ENABLED', 0, 0, 0),
    (-951, -950, 'system.users', '用户管理', 'MENU', '/user-center/users', '@/pages/system/users', 'TeamOutlined', 21, 'system:user:view', 'ENABLED', 0, 0, 0),
    (-965, -951, 'system.users.create', '创建用户', 'BUTTON', NULL, NULL, NULL, 1, 'system:user:create', 'ENABLED', 0, 0, 0),
    (-966, -951, 'system.users.update', '编辑用户', 'BUTTON', NULL, NULL, NULL, 2, 'system:user:update', 'ENABLED', 0, 0, 0),
    (-967, -951, 'system.users.delete', '删除用户', 'BUTTON', NULL, NULL, NULL, 3, 'system:user:delete', 'ENABLED', 0, 0, 0),
    (-968, -951, 'system.users.export', '导出用户', 'BUTTON', NULL, NULL, NULL, 4, 'system:user:export', 'ENABLED', 0, 0, 0),
    (-954, -950, 'system.departments', '组织部门', 'MENU', '/user-center/departments', '@/pages/system/departments', 'ApartmentOutlined', 22, 'system:department:view', 'ENABLED', 0, 0, 0),
    (-952, -950, 'system.online-users', '在线用户', 'MENU', '/user-center/online-users', '@/pages/system/online-users', 'UserSwitchOutlined', 23, 'system:online-user:view', 'ENABLED', 0, 0, 0),
    (-953, -950, 'system.roles', '角色管理', 'MENU', '/user-center/roles', '@/pages/system/roles', 'SafetyOutlined', 24, 'system:role:view', 'ENABLED', 0, 0, 0),
    (-969, -953, 'system.roles.create', '创建角色', 'BUTTON', NULL, NULL, NULL, 1, 'system:role:create', 'ENABLED', 0, 0, 0),
    (-970, -953, 'system.roles.update', '编辑角色', 'BUTTON', NULL, NULL, NULL, 2, 'system:role:update', 'ENABLED', 0, 0, 0),
    (-971, -953, 'system.roles.delete', '删除角色', 'BUTTON', NULL, NULL, NULL, 3, 'system:role:delete', 'ENABLED', 0, 0, 0),
    (-972, -953, 'system.roles.grant', '授权角色', 'BUTTON', NULL, NULL, NULL, 4, 'system:role:grant', 'ENABLED', 0, 0, 0),
    (-940, 0, 'user.center.personal', '个人中心', 'CATALOG', '/user-center/personal-center', '@/layouts/SettingsLayout', 'IdcardOutlined', 19, 'profile:view', 'ENABLED', 0, 0, 0),
    (-941, -940, 'profile.center', '个人资料', 'MENU', '/user-center/personal-center/profile', '@/pages/profile/Center', 'UserOutlined', 1, 'profile:view', 'ENABLED', 0, 0, 0),
    (-942, -940, 'files.my', '我的文件', 'MENU', '/user-center/personal-center/files', '@/pages/files/Center', 'FileOutlined', 2, 'system:file:view', 'ENABLED', 0, 0, 0),
    (-1000, 0, 'settings.root', '系统设置', 'CATALOG', '/settings', '@/layouts/SettingsLayout', 'SettingOutlined', 20, 'system:view', 'ENABLED', 0, 0, 0),
    (-1001, -1000, 'settings.menus', '菜单管理', 'MENU', '/settings/menus', '@/pages/settings/menus', 'AppstoreOutlined', 2, 'system:menu:view', 'ENABLED', 0, 0, 0),
    (-1020, -1001, 'settings.menus.create', '创建菜单', 'BUTTON', NULL, NULL, NULL, 1, 'system:menu:create', 'ENABLED', 0, 0, 0),
    (-1021, -1001, 'settings.menus.update', '编辑菜单', 'BUTTON', NULL, NULL, NULL, 2, 'system:menu:update', 'ENABLED', 0, 0, 0),
    (-1022, -1001, 'settings.menus.delete', '删除菜单', 'BUTTON', NULL, NULL, NULL, 3, 'system:menu:delete', 'ENABLED', 0, 0, 0),
    (-1002, -1000, 'settings.dicts', '字典管理', 'MENU', '/settings/dicts', '@/pages/settings/dicts', 'DatabaseOutlined', 3, 'system:dict:view', 'ENABLED', 0, 0, 0),
    (-1023, -1002, 'settings.dicts.create', '创建字典', 'BUTTON', NULL, NULL, NULL, 1, 'system:dict:create', 'ENABLED', 0, 0, 0),
    (-1024, -1002, 'settings.dicts.update', '编辑字典', 'BUTTON', NULL, NULL, NULL, 2, 'system:dict:update', 'ENABLED', 0, 0, 0),
    (-1025, -1002, 'settings.dicts.delete', '删除字典', 'BUTTON', NULL, NULL, NULL, 3, 'system:dict:delete', 'ENABLED', 0, 0, 0),
    (-1003, -1000, 'settings.profile-fields', '字段管理', 'MENU', '/settings/profile-fields', '@/pages/settings/profile-fields', 'FormOutlined', 4, 'system:config:view', 'ENABLED', 0, 0, 0),
    (-1004, -1000, 'settings.personalization', '个性化设置', 'MENU', '/settings/personalization', '@/pages/settings/personalization', 'SkinOutlined', 5, 'system:config:view', 'ENABLED', 0, 0, 0),
    (-1005, -1000, 'settings.security', '安全设置', 'MENU', '/settings/security', '@/pages/settings/security', 'SafetyOutlined', 6, 'system:config:view', 'ENABLED', 0, 0, 0),
    (-1006, -1000, 'settings.verification', '验证管理', 'MENU', '/settings/verification', '@/pages/settings/verification', 'SafetyOutlined', 7, 'system:verification:view', 'ENABLED', 0, 0, 0),
    (-1007, -1000, 'settings.payment', '支付设置', 'MENU', '/settings/payment', '@/pages/settings/payment', 'CreditCardOutlined', 8, 'payment:view', 'ENABLED', 0, 0, 0),
    (-1012, -1000, 'settings.files', '全站文件管理', 'MENU', '/settings/files/all', '@/pages/settings/files/Center', 'FolderOpenOutlined', 9, 'system:file:manage', 'ENABLED', 0, 0, 0),
    (-1008, -1000, 'settings.notifications', '通知中心', 'MENU', '/settings/notifications', '@/pages/settings/notifications/index', 'NotificationOutlined', 9, 'system:notification:view', 'ENABLED', 0, 0, 0),
    (-1015, -1000, 'settings.monitoring', '系统监控', 'MENU', '/settings/monitoring', '@/pages/settings/monitoring/index', 'FundOutlined', 10, 'system:monitor:view', 'ENABLED', 0, 0, 0),
    (-1013, -1000, 'settings.monitoring.api-docs', '接口文档', 'MENU', '/settings/api-docs', '@/pages/settings/monitoring/ApiDocs', 'FileTextOutlined', 11, 'system:monitor:docs:view', 'ENABLED', 0, 0, 0),
    (-1014, -1000, 'settings.monitoring.audit', '审计中心', 'MENU', '/settings/audit', '@/pages/settings/monitoring/Audit', 'AuditOutlined', 12, 'audit:view', 'ENABLED', 0, 0, 0),
    (-1009, -1000, 'settings.plugins', '插件管理中心', 'MENU', '/settings/plugins', '@/pages/settings/plugins', 'ApiOutlined', 10, 'plugin:management:view', 'ENABLED', 0, 0, 0),
    (-1011, -1000, 'localization.root', 'Localization center', 'MENU', '/settings/localization', '@/pages/settings/localization', 'TranslationOutlined', 29, 'localization:view', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `parent_id` = VALUES(`parent_id`),
    `menu_name` = VALUES(`menu_name`),
    `menu_type` = VALUES(`menu_type`),
    `path` = VALUES(`path`),
    `component` = VALUES(`component`),
    `icon` = VALUES(`icon`),
    `sort_no` = VALUES(`sort_no`),
    `permission_key` = VALUES(`permission_key`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = VALUES(`deleted`);

INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `role_type`, `default_home_path`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 'ADMIN', 'Administrator', 'SYSTEM', '/dashboard/home', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `role_type` = VALUES(`role_type`),
    `default_home_path` = VALUES(`default_home_path`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `role_type`, `default_home_path`, `created_by`, `updated_by`, `deleted`)
VALUES (1002, 'commonuser', 'Common User', 'BUSINESS', '/dashboard/home', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `role_type` = VALUES(`role_type`),
    `default_home_path` = VALUES(`default_home_path`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_data_scope` (`role_id`, `resource_code`, `scope_type`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, '*', 'ALL', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `scope_type` = VALUES(`scope_type`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_data_scope` (`role_id`, `resource_code`, `scope_type`, `created_by`, `updated_by`, `deleted`)
VALUES (1002, '*', 'SELF', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `scope_type` = VALUES(`scope_type`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

DELETE FROM `sys_role_permission`
WHERE `role_id` = 1002
  AND `permission_key` NOT IN (
      'dashboard:view',
      'profile:view',
      'system:file:view',
      'system:file:upload',
      'aiadc:registration:view',
      'aiadc:registration:create',
      'aiadc:registration:update',
      'aiadc:registration:pay',
      'aiadc:activity:create',
      'aiadc:material:view',
      'aiadc:material:submit',
      'aiadc:stage:view'
  );

DELETE FROM `sys_role_permission`
WHERE `role_id` = 1001 AND `permission_key` = '*';

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, p.`permission_key`, 0, 0, 0
FROM `sys_permission` p
WHERE p.`deleted` = 0
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1002, p.`permission_key`, 0, 0, 0
FROM `sys_permission` p
WHERE p.`deleted` = 0
  AND p.`permission_key` IN (
      'dashboard:view',
      'profile:view',
      'system:file:view',
      'system:file:upload',
      'aiadc:registration:view',
      'aiadc:registration:create',
      'aiadc:registration:update',
      'aiadc:registration:pay',
      'aiadc:activity:create',
      'aiadc:material:view',
      'aiadc:material:submit',
      'aiadc:stage:view'
  )
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1003, p.`permission_key`, 0, 0, 0
FROM `sys_permission` p
WHERE p.`deleted` = 0
  AND p.`permission_key` IN (
      'dashboard:view',
      'expert:view'
  )
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_config` (
    `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`,
    `created_by`, `updated_by`, `deleted`
)
VALUES ('auth.default-registration-role-code', 'Default registration role', 'commonuser', 'PLATFORM', 1, 'Default role code assigned to newly registered users', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `config_name` = VALUES(`config_name`),
    `config_value` = VALUES(`config_value`),
    `config_scope` = VALUES(`config_scope`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES
    ('IAM', 'permission-snapshot', 1, 'sql-bootstrap', NOW()),
    ('platform', 'runtime-appearance', 1, 'sql-bootstrap', NOW())
ON DUPLICATE KEY UPDATE
    `version` = GREATEST(`version`, VALUES(`version`)),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);

INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('sys_user_gender', '用户性别', 'ENABLED', 1, '系统字典：用户性别', 0, 0, 0),
    ('sys_user_status', 'User status', 'ENABLED', 1, 'System dictionary: user status', 0, 0, 0),
    ('sys_common_status', 'Common status', 'ENABLED', 1, 'System dictionary: common status', 0, 0, 0),
    ('sys_yes_no', '是否', 'ENABLED', 1, 'System dictionary: yes/no', 0, 0, 0),
    ('sys_role_type', '角色类型', 'ENABLED', 1, 'System dictionary: role type', 0, 0, 0),
    ('sys_menu_type', '菜单类型', 'ENABLED', 1, 'System dictionary: menu type', 0, 0, 0),
    ('sys_data_scope_type', '数据范围类型', 'ENABLED', 1, 'System dictionary: data scope type', 0, 0, 0),
    ('team_member_role', '团队成员角色', 'ENABLED', 1, '团队模块字典', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `dict_name` = VALUES(`dict_name`),
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('team_type', '团队类型', 'ENABLED', 1, '团队模块字典', 0, 0, 0),
    ('team_visibility', 'Team visibility', 'ENABLED', 1, '团队模块字典', 0, 0, 0),
    ('team_join_mode', '团队加入方式', 'ENABLED', 1, '团队模块字典', 0, 0, 0),
    ('project_team_member_role', '项目团队成员角色', 'ENABLED', 1, 'Project dictionary: team member role', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `dict_name` = VALUES(`dict_name`),
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `role_type`, `default_home_path`, `created_by`, `updated_by`, `deleted`)
VALUES (1003, 'EXPERT', 'Expert', 'BUSINESS', '/dashboard/home', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `role_type` = VALUES(`role_type`),
    `default_home_path` = VALUES(`default_home_path`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('aiadc_expert_title', '专家头衔', 'ENABLED', 1, '专家库字典：专家头衔', 0, 0, 0),
    ('aiadc_expert_position', '专家职务', 'ENABLED', 1, '专家库字典：专家职务', 0, 0, 0),
    ('aiadc_expert_expertise', '专家专业领域', 'ENABLED', 1, '专家库字典：专业领域', 0, 0, 0),
    ('aiadc_expert_tag', '专家标签', 'ENABLED', 1, '专家库字典：专家标签', 0, 0, 0),
    ('aiadc_expert_status', '专家状态', 'ENABLED', 1, 'Expert status and default order', 0, 0, 0),
    ('aiadc_expert_initial_status', '专家申请初始状态', 'ENABLED', 1, 'Initial status for expert applications', 0, 0, 0),
    ('aiadc_expert_approval_status', '专家审批状态', 'ENABLED', 1, 'Expert approval lifecycle and initial order', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `dict_name` = VALUES(`dict_name`),
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('aiadc_competition_category', '竞赛类别', 'ENABLED', 1, 'Competition dictionary: category', 0, 0, 0),
    ('aiadc_competition_level', '竞赛级别', 'ENABLED', 1, 'Competition dictionary: level', 0, 0, 0),
    ('aiadc_activity_category', '活动分类', 'ENABLED', 1, 'Competition dictionary: activity category', 0, 0, 0),
    ('aiadc_activity_locale', '活动语言', 'ENABLED', 1, 'Activity locale and default order', 0, 0, 0),
    ('aiadc_activity_status', '活动状态', 'ENABLED', 1, 'Activity status and default order', 0, 0, 0),
    ('aiadc_activity_public_status', '活动公开状态', 'ENABLED', 1, 'Activity statuses visible to public queries', 0, 0, 0),
    ('aiadc_project_locale', '项目语言', 'ENABLED', 1, 'Project locale and default order', 0, 0, 0),
    ('aiadc_project_status', '项目状态', 'ENABLED', 1, 'Project status and default order', 0, 0, 0),
    ('aiadc_project_rating', '项目评级', 'ENABLED', 1, 'Project rating and default order', 0, 0, 0),
    ('aiadc_project_filter_all', '项目全部筛选标记', 'ENABLED', 1, 'Project query wildcard value', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `dict_name` = VALUES(`dict_name`),
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, '教授', '教授', 10, 'ENABLED', '专家头衔', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_title' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ASSOCIATE_PROFESSOR', 'Associate Professor', 20, 'ENABLED', 'Expert title', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_title' AND `deleted` = 0
UNION ALL
SELECT `id`, 'RESEARCHER', 'Researcher', 30, 'ENABLED', 'Expert title', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_title' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SENIOR_ENGINEER', 'Senior Engineer', 40, 'ENABLED', 'Expert title', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_title' AND `deleted` = 0
UNION ALL
SELECT `id`, '行业专家', '行业专家', 50, 'ENABLED', '专家头衔', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_title' AND `deleted` = 0
UNION ALL
SELECT `id`, '主任', '主任', 10, 'ENABLED', '专家职务', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_position' AND `deleted` = 0
UNION ALL
SELECT `id`, '院长', '院长', 20, 'ENABLED', '专家职务', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_position' AND `deleted` = 0
UNION ALL
SELECT `id`, '总工程师', '总工程师', 30, 'ENABLED', '专家职务', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_position' AND `deleted` = 0
UNION ALL
SELECT `id`, '技术负责人', '技术负责人', 40, 'ENABLED', '专家职务', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_position' AND `deleted` = 0
UNION ALL
SELECT `id`, 'INVESTMENT_PARTNER', 'Investment Partner', 50, 'ENABLED', 'Expert position', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_position' AND `deleted` = 0
UNION ALL
SELECT `id`, '人工智能', '人工智能', 10, 'ENABLED', '专家专业领域', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_expertise' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SMART_MANUFACTURING', 'Smart Manufacturing', 20, 'ENABLED', 'Expert expertise', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_expertise' AND `deleted` = 0
UNION ALL
SELECT `id`, '产业投资', '产业投资', 30, 'ENABLED', '专家专业领域', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_expertise' AND `deleted` = 0
UNION ALL
SELECT `id`, '数字经济', '数字经济', 40, 'ENABLED', '专家专业领域', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_expertise' AND `deleted` = 0
UNION ALL
SELECT `id`, '科技成果转化', '科技成果转化', 50, 'ENABLED', '专家专业领域', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_expertise' AND `deleted` = 0
UNION ALL
SELECT `id`, '评审专家', '评审专家', 10, 'ENABLED', '专家标签', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_tag' AND `deleted` = 0
UNION ALL
SELECT `id`, '导师', '导师', 20, 'ENABLED', '专家标签', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_tag' AND `deleted` = 0
UNION ALL
SELECT `id`, '产业资源', '产业资源', 30, 'ENABLED', '专家标签', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_tag' AND `deleted` = 0
UNION ALL
SELECT `id`, 'FINANCING', 'Financing', 40, 'ENABLED', 'Expert tag', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_tag' AND `deleted` = 0
UNION ALL
SELECT `id`, 'TECHNICAL_CONSULTANT', 'Technical Consultant', 50, 'ENABLED', 'Expert tag', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_tag' AND `deleted` = 0
UNION ALL
SELECT `id`, 'active', '启用', 10, 'ENABLED', '专家默认状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'inactive', '停用', 20, 'ENABLED', '专家状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'inactive', '停用', 10, 'ENABLED', '专家申请初始状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_initial_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'PENDING', '待处理', 10, 'ENABLED', '专家审批初始状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_approval_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'RUNNING', '审批中', 20, 'ENABLED', '专家审批状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_approval_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'APPROVED', '已通过', 30, 'ENABLED', '专家审批状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_approval_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'REJECTED', '已拒绝', 40, 'ENABLED', '专家审批状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_approval_status' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'INNOVATION', '创新赛', 10, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'APPLICATION', '应用赛', 20, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SPECIAL', '专项赛', 30, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'OTHER', '其他', 40, 'ENABLED', '竞赛类别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SCHOOL', '校级', 10, 'ENABLED', '竞赛级别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_level' AND `deleted` = 0
UNION ALL
SELECT `id`, 'PROVINCE', '省级', 20, 'ENABLED', '竞赛级别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_level' AND `deleted` = 0
UNION ALL
SELECT `id`, 'NATIONAL', '国家级', 30, 'ENABLED', '竞赛级别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_level' AND `deleted` = 0
UNION ALL
SELECT `id`, 'INTERNATIONAL', '国际级', 40, 'ENABLED', '竞赛级别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_level' AND `deleted` = 0
UNION ALL
SELECT `id`, '路演活动', '路演活动', 10, 'ENABLED', '活动分类', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_category' AND `deleted` = 0
UNION ALL
SELECT `id`, '创业沙龙', '创业沙龙', 20, 'ENABLED', '活动分类', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_category' AND `deleted` = 0
UNION ALL
SELECT `id`, '政策宣讲', '政策宣讲', 30, 'ENABLED', '活动分类', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_category' AND `deleted` = 0
UNION ALL
SELECT `id`, '培训活动', '培训活动', 40, 'ENABLED', '活动分类', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_category' AND `deleted` = 0
UNION ALL
SELECT `id`, '其他', '其他', 50, 'ENABLED', '活动分类', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'zh', '中文', 10, 'ENABLED', '活动默认语言', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_locale' AND `deleted` = 0
UNION ALL
SELECT `id`, 'en', 'English', 20, 'ENABLED', 'Activity locale', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_locale' AND `deleted` = 0
UNION ALL
SELECT `id`, 'draft', '草稿', 10, 'ENABLED', '活动默认状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'published', '已发布', 20, 'ENABLED', '活动状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'published', '已发布', 10, 'ENABLED', '公开查询可见状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_public_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'zh', '中文', 10, 'ENABLED', '项目默认语言', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_locale' AND `deleted` = 0
UNION ALL
SELECT `id`, 'en', 'English', 20, 'ENABLED', 'Project locale', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_locale' AND `deleted` = 0
UNION ALL
SELECT `id`, 'draft', '草稿', 10, 'ENABLED', '项目默认状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'published', '已发布', 20, 'ENABLED', '项目状态', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'popular', '热门', 10, 'ENABLED', '项目默认评级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_rating' AND `deleted` = 0
UNION ALL
SELECT `id`, 'excellent', '优秀', 20, 'ENABLED', '项目评级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_rating' AND `deleted` = 0
UNION ALL
SELECT `id`, 'new', '最新', 30, 'ENABLED', '项目评级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_rating' AND `deleted` = 0
UNION ALL
SELECT `id`, 'all', '全部', 10, 'ENABLED', '项目查询全部筛选标记', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_project_filter_all' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'GENERAL', '通用团队', 10, 'ENABLED', '团队类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'DEV', 'Development Team', 20, 'ENABLED', 'Team type', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'COMPETITION', '竞赛团队', 30, 'ENABLED', '团队类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'CLUB', '社团组织', 40, 'ENABLED', '团队类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'OTHER', '其他', 50, 'ENABLED', '团队类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'PRIVATE', 'Private', 10, 'ENABLED', 'Team visibility', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_visibility' AND `deleted` = 0
UNION ALL
SELECT `id`, 'PUBLIC', 'Public', 20, 'ENABLED', 'Team visibility', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_visibility' AND `deleted` = 0
UNION ALL
SELECT `id`, 'INVITE_ONLY', 'Invite Only', 10, 'ENABLED', 'Team join mode', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_join_mode' AND `deleted` = 0
UNION ALL
SELECT `id`, 'APPLY', '申请加入', 20, 'ENABLED', '团队加入方式', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_join_mode' AND `deleted` = 0
UNION ALL
SELECT `id`, 'OPEN', 'Open', 30, 'ENABLED', 'Team join mode', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_join_mode' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ADMIN', 'Owner', 10, 'ENABLED', 'Project team member role', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'project_team_member_role' AND `deleted` = 0
UNION ALL
SELECT `id`, 'MEMBER', '组员', 20, 'ENABLED', '项目团队成员角色', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'project_team_member_role' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'MALE', 'Male', 10, 'ENABLED', 'User gender', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_user_gender' AND `deleted` = 0
UNION ALL
SELECT `id`, 'FEMALE', 'Female', 20, 'ENABLED', 'User gender', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_user_gender' AND `deleted` = 0
UNION ALL
SELECT `id`, 'OTHER', '其他', 30, 'ENABLED', '用户性别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_user_gender' AND `deleted` = 0
UNION ALL
SELECT `id`, 'UNKNOWN', '未知', 40, 'ENABLED', '用户性别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_user_gender' AND `deleted` = 0
UNION ALL
SELECT `id`, 'DISABLED', 'Disabled', 20, 'ENABLED', 'User status', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_user_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'LOCKED', 'Locked', 30, 'ENABLED', 'User status', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_user_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ENABLED', 'Enabled', 10, 'ENABLED', 'User status', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_user_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ENABLED', 'Enabled', 10, 'ENABLED', 'Common status', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_common_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'DISABLED', 'Disabled', 20, 'ENABLED', 'Common status', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_common_status' AND `deleted` = 0
UNION ALL
SELECT `id`, 'YES', 'Yes', 10, 'ENABLED', 'Yes or no', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_yes_no' AND `deleted` = 0
UNION ALL
SELECT `id`, 'NO', 'No', 20, 'ENABLED', 'Yes or no', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_yes_no' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SYSTEM', '系统角色', 10, 'ENABLED', '角色类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_role_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'CUSTOM', 'Custom Role', 20, 'ENABLED', 'Role type', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_role_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'CATALOG', '目录', 10, 'ENABLED', '菜单类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'MENU', '菜单', 20, 'ENABLED', '菜单类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'BUTTON', '按钮', 30, 'ENABLED', '菜单类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'LINK', '外链', 40, 'ENABLED', '菜单类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ALL', '全部数据', 10, 'ENABLED', '数据范围类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'DEPT_AND_CHILD', '本部门及下级', 20, 'ENABLED', '数据范围类型', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'DEPT', 'Department', 30, 'ENABLED', 'Data scope type', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SELF', 'Self', 40, 'ENABLED', 'Data scope type', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'CUSTOM', 'Custom', 50, 'ENABLED', 'Data scope type', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'OWNER', 'Owner', 10, 'ENABLED', 'Team member role', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_member_role' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ADMIN', 'Admin', 20, 'ENABLED', 'Team member role', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_member_role' AND `deleted` = 0
UNION ALL
SELECT `id`, 'MANAGER', 'Manager', 30, 'ENABLED', 'Team member role', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_member_role' AND `deleted` = 0
UNION ALL
SELECT `id`, 'MEMBER', '成员', 40, 'ENABLED', '团队成员角色', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_member_role' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user` (`id`, `uuid`, `username`, `nickname`, `real_name`, `password_hash`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, CAST(FLOOR(100000000000000000 + RAND() * 900000000000000000) AS CHAR), 'admin', 'Administrator', 'Administrator', '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `uuid` = IF(`uuid` REGEXP '^[0-9]+$', `uuid`, VALUES(`uuid`)),
    `nickname` = VALUES(`nickname`),
    `real_name` = VALUES(`real_name`),
    `password_hash` = VALUES(`password_hash`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user_role` (`user_id`, `user_uuid`, `role_id`, `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`)
VALUES (1001, (SELECT `uuid` FROM `sys_user` WHERE `id` = 1001), 1001, 0, '00000000-0000-0000-0000-000000000000', 0, '00000000-0000-0000-0000-000000000000', 0)
ON DUPLICATE KEY UPDATE
    `user_uuid` = VALUES(`user_uuid`),
    `updated_by` = VALUES(`updated_by`),
    `updated_by_uuid` = VALUES(`updated_by_uuid`),
    `deleted` = 0;

INSERT INTO `sys_config` (
    `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    ('branding.website-name', '站点名称', 'Lumira', 'PLATFORM', 0, 'Website name shown in the console and browser title', 0, 0, 0),
    ('branding.website-favicon-url', '站点图标地址', '', 'PLATFORM', 0, '浏览器标签页 icon 地址', 0, 0, 0),
    ('branding.website-logo-url', '站点 Logo 地址', '', 'PLATFORM', 0, '控制台左上角品牌 Logo 地址', 0, 0, 0),
    ('branding.login-background-url', '登录页背景图地址', '', 'PLATFORM', 0, '登录页背景图地址', 0, 0, 0),
    ('branding.github-link-enabled', 'GitHub link enabled', 'true', 'PLATFORM', 0, '是否显示顶部 GitHub 图标', 0, 0, 0),
    ('branding.github-link-url', 'GitHub 链接', '', 'PLATFORM', 0, '顶部 GitHub 图标跳转地址', 0, 0, 0),
    ('branding.help-link-enabled', 'Help link enabled', 'true', 'PLATFORM', 0, '是否显示顶部帮助图标', 0, 0, 0),
    ('branding.help-link-url', '帮助链接', '', 'PLATFORM', 0, '顶部帮助图标跳转地址', 0, 0, 0),
    ('branding.company-name', '公司名称', 'Lumira', 'PLATFORM', 0, '页脚版权主体名称', 0, 0, 0),
    ('branding.copyright-start-year', '版权起始年份', CAST(YEAR(CURRENT_DATE()) AS CHAR), 'PLATFORM', 0, '页脚版权起始年份', 0, 0, 0),
    ('branding.footer-icp', '页脚备案', '', 'PLATFORM', 0, '页脚备案信息', 0, 0, 0),
    ('branding.footer-police-beian', '页脚公安备案', '', 'PLATFORM', 0, '页脚公安备案信息', 0, 0, 0),
    ('branding.footer-copyright', '页脚版权声明', CONCAT('Copyright ', YEAR(CURRENT_DATE()), ' Lumira All Rights Reserved'), 'PLATFORM', 0, '页脚版权声明', 0, 0, 0),
    ('agreement.user-agreement-markdown', '用户协议', '', 'PLATFORM', 0, '用户协议 Markdown', 0, 0, 0),
    ('agreement.privacy-agreement-markdown', '隐私协议', '', 'PLATFORM', 0, '隐私协议 Markdown', 0, 0, 0),
    ('account.activation.url', '账户激活地址', 'http://localhost:8000/account-activation', 'PLATFORM', 1, '前端账户激活页面地址', 0, 0, 0),
    ('watermark.enabled', 'Watermark enabled', 'false', 'PLATFORM', 0, 'Global watermark enabled flag', 0, 0, 0),
    ('watermark.mode', '水印模式', 'TEXT', 'PLATFORM', 0, 'TEXT/IMAGE', 0, 0, 0),
    ('watermark.text-lines', '水印文本', '', 'PLATFORM', 0, '多行文本水印', 0, 0, 0),
    ('watermark.image-url', '水印图片', '', 'PLATFORM', 0, '图片水印 URL', 0, 0, 0),
    ('watermark.font-color', '字体颜色', 'rgba(0,0,0,0.15)', 'PLATFORM', 0, '字体颜色', 0, 0, 0),
    ('watermark.font-size', '字体大小', '14', 'PLATFORM', 0, '字体大小', 0, 0, 0),
    ('watermark.font-weight', '字体粗细', 'normal', 'PLATFORM', 0, '字体粗细', 0, 0, 0),
    ('watermark.rotate', '旋转角度', '-22', 'PLATFORM', 0, '旋转角度', 0, 0, 0),
    ('watermark.gap-x', '横向间距', '100', 'PLATFORM', 0, '横向间距', 0, 0, 0),
    ('watermark.gap-y', '纵向间距', '100', 'PLATFORM', 0, '纵向间距', 0, 0, 0),
    ('watermark.offset-x', '横向偏移', '0', 'PLATFORM', 0, '横向偏移', 0, 0, 0),
    ('watermark.offset-y', '纵向偏移', '0', 'PLATFORM', 0, '纵向偏移', 0, 0, 0),
    ('watermark.z-index', '层级', '9', 'PLATFORM', 0, 'z-index', 0, 0, 0),
    ('watermark.opacity', 'Watermark opacity', '0.15', 'PLATFORM', 0, 'Watermark opacity', 0, 0, 0),
    ('floating-window.api-docs-qr-enabled', 'API docs QR enabled', 'false', 'PLATFORM', 0, '是否在全局悬浮窗展示接口文档二维码入口', 0, 0, 0),
    ('floating-window.api-docs-qr-title', 'API docs QR title', '', 'PLATFORM', 0, 'API docs QR dialog title', 0, 0, 0),
    ('floating-window.api-docs-qr-image-url', 'API docs QR image', '', 'PLATFORM', 0, 'API docs floating entry QR image URL', 0, 0, 0),
    ('smtp.enabled', 'SMTP 邮箱通知启用', 'false', 'PLATFORM', 0, '是否启用邮箱通知渠道', 0, 0, 0),
    ('smtp.host', 'SMTP 主机', '', 'PLATFORM', 0, '邮件服务器地址', 0, 0, 0),
    ('smtp.port', 'SMTP 端口', '25', 'PLATFORM', 0, 'SMTP server port', 0, 0, 0),
    ('smtp.username', 'SMTP username', '', 'PLATFORM', 0, 'SMTP login username', 0, 0, 0),
    ('smtp.password', 'SMTP 密码', '', 'PLATFORM', 0, 'SMTP 登录密码', 0, 0, 0),
    ('smtp.from', '发件人地址', '', 'PLATFORM', 0, 'SMTP default sender', 0, 0, 0),
    ('smtp.auth-enabled', 'SMTP 认证', 'true', 'PLATFORM', 0, '是否启用 SMTP AUTH', 0, 0, 0),
    ('smtp.starttls-enabled', 'SMTP STARTTLS', 'true', 'PLATFORM', 0, '是否启用 STARTTLS', 0, 0, 0),
    ('smtp.ssl-enabled', 'SMTP SSL', 'false', 'PLATFORM', 0, '是否启用 SSL', 0, 0, 0),
    ('notification.wechat-official.enabled', '微信公众号通知启用', 'false', 'PLATFORM', 0, '是否启用微信公众号或服务号模板消息通知', 0, 0, 0),
    ('notification.wechat-official.app-id', '微信公众号 AppID', '', 'PLATFORM', 0, '微信公众号或服务号 AppID', 0, 0, 0),
    ('notification.wechat-official.app-secret', '微信公众号 AppSecret', '', 'PLATFORM', 0, '微信公众号或服务号 AppSecret', 0, 0, 0),
    ('notification.wechat-official.template-id', '微信公众号模板 ID', '', 'PLATFORM', 0, '用于系统通知的公众号模板消息 ID', 0, 0, 0),
    ('notification.wechat-official.detail-url', '微信公众号通知详情链接', '', 'PLATFORM', 0, 'System URL opened after template message click; can be empty', 0, 0, 0),
    ('verification.totp.enabled', '2FA 启用', 'true', 'PLATFORM', 0, '是否启用 2FA 登录方式', 0, 0, 0),
    ('verification.password-login.enabled', '密码登录', 'true', 'PLATFORM', 0, '是否启用账号密码登录', 0, 0, 0),
    ('verification.email-login.enabled', 'Email code login enabled', 'false', 'PLATFORM', 0, 'Whether email code login is enabled', 0, 0, 0),
    ('verification.login-mode.order', '登录方式排序', 'password,sms,email,wechat,passkey', 'PLATFORM', 0, '登录页分段控制器展示顺序', 0, 0, 0),
    ('verification.sms.enabled', 'SMS verification enabled', 'false', 'PLATFORM', 0, 'Whether SMS verification service is enabled', 0, 0, 0),
    ('verification.sms.provider', '短信验证码服务商', 'aliyun', 'PLATFORM', 0, '短信验证码服务提供方', 0, 0, 0),
    ('verification.sms.sign-name', '短信签名', '', 'PLATFORM', 0, 'SMS verification sign name', 0, 0, 0),
    ('verification.sms.template-code', '短信模板编码', '', 'PLATFORM', 0, 'SMS verification template code', 0, 0, 0),
    ('verification.sms.access-key-id', '短信 Access Key ID', '', 'PLATFORM', 0, '短信验证码访问密钥 ID', 0, 0, 0),
    ('verification.sms.access-key-secret', '短信 Access Key Secret', '', 'PLATFORM', 0, '短信验证码访问密钥 Secret', 0, 0, 0),
    ('verification.sms.endpoint', '短信服务地址', '', 'PLATFORM', 0, 'SMS verification endpoint', 0, 0, 0),
    ('verification.sms.region', '短信服务地域', '', 'PLATFORM', 0, 'SMS verification region', 0, 0, 0),
    ('verification.wechat-login.enabled', '微信登录启用', 'false', 'PLATFORM', 0, '是否启用微信扫码登录', 0, 0, 0),
    ('verification.wechat-login.app-id', '微信 AppID', '', 'PLATFORM', 0, '微信开放平台网站应用 AppID', 0, 0, 0),
    ('verification.wechat-login.app-secret', '微信 AppSecret', '', 'PLATFORM', 0, '微信开放平台网站应用 AppSecret', 0, 0, 0),
    ('verification.wechat-login.redirect-uri', '微信登录回调地址', '', 'PLATFORM', 0, '微信开放平台授权回调地址', 0, 0, 0),
    ('verification.wechat-login.state-expire-minutes', '微信登录状态有效期', '10', 'PLATFORM', 0, '微信登录 state 缓存有效期，单位分钟', 0, 0, 0),
    ('verification.passkey.enabled', '通行密钥启用', 'false', 'PLATFORM', 0, '是否启用通行密钥登录', 0, 0, 0),
    ('verification.passkey.passwordless-enabled', 'Passkey passwordless enabled', 'false', 'PLATFORM', 0, '是否允许发现式凭据无账号登录', 0, 0, 0),
    ('verification.passkey.self-binding-enabled', '通行密钥自助绑定', 'false', 'PLATFORM', 0, '是否允许用户在个人中心自助绑定通行密钥', 0, 0, 0),
    ('verification.passkey.rp-id', '通行密钥 RP ID', '', 'PLATFORM', 0, 'WebAuthn RP ID', 0, 0, 0),
    ('verification.passkey.rp-name', '通行密钥 RP 名称', '', 'PLATFORM', 0, 'WebAuthn RP 显示名称', 0, 0, 0),
    ('verification.passkey.allowed-origins', '通行密钥允许 Origin', '', 'PLATFORM', 0, 'WebAuthn 允许的前端 Origin', 0, 0, 0),
    ('verification.passkey.challenge-ttl-seconds', '通行密钥 Challenge TTL', '120', 'PLATFORM', 0, 'WebAuthn challenge TTL seconds', 0, 0, 0),
    ('security.idle-timeout-seconds', '空闲超时时间', '1800', 'PLATFORM', 1, 'Session idle timeout seconds', 0, 0, 0),
    ('security.access-token-expire-seconds', 'Access Token 过期时间', '1800', 'PLATFORM', 1, 'Access token TTL seconds', 0, 0, 0),
    ('security.refresh-token-expire-seconds', 'Refresh Token 刷新时限', '604800', 'PLATFORM', 1, 'Refresh token TTL seconds', 0, 0, 0),
    ('security.allow-multi-device-login', 'Multi-device login', '1', 'PLATFORM', 1, 'Whether the same account can be online on multiple devices', 0, 0, 0),
    ('security.captcha-enabled', 'Captcha enabled', '0', 'PLATFORM', 1, '是否开启登录时的人机验证码', 0, 0, 0),
    ('security.captcha-type', 'Captcha type', 'IMAGE', 'PLATFORM', 1, '验证码类型：IMAGE/SLIDER', 0, 0, 0),
    ('security.login-defense-window-minutes', '登录防御统计窗口', '5', 'PLATFORM', 1, 'Login defense statistics window in minutes', 0, 0, 0),
    ('security.login-max-validation-attempts', 'Max validation attempts', '100', 'PLATFORM', 1, 'Maximum verification or login validation attempts in the window', 0, 0, 0),
    ('security.login-max-failure-count', 'Max login failure count', '10', 'PLATFORM', 1, 'Maximum login failures allowed in the statistics window', 0, 0, 0),
    ('security.verification-code-expire-seconds', '验证码有效期', '300', 'PLATFORM', 1, '短信/邮箱验证码的有效秒数', 0, 0, 0),
    ('security.verification-code-cooldown-seconds', 'Verification code cooldown', '60', 'PLATFORM', 1, '同一账号同一验证码渠道再次发送前需要等待的秒数', 0, 0, 0),
    ('security.password-min-length', 'Password min length', '6', 'PLATFORM', 1, '用户密码允许的最少字符数', 0, 0, 0),
    ('security.password-require-uppercase', '密码必须包含大写字母', '0', 'PLATFORM', 1, '强制密码包含 A-Z', 0, 0, 0),
    ('security.password-require-lowercase', '密码必须包含小写字母', '0', 'PLATFORM', 1, '强制密码包含 a-z', 0, 0, 0),
    ('security.password-require-special-character', '密码必须包含特殊字符', '0', 'PLATFORM', 1, '强制密码包含特殊字符', 0, 0, 0),
    ('security.password-allow-consecutive-characters', '允许连续字符', '1', 'PLATFORM', 1, 'Whether consecutive password characters are allowed', 0, 0, 0),
    ('profile.field.system.overrides', 'System profile field metadata overrides', '[]', 'PLATFORM', 0, 'Stores editable labels, descriptions, placeholders, and groups for built-in profile fields', 0, 0, 0),
    ('profile.field.custom.definitions', 'Custom profile field definitions', '[]', 'PLATFORM', 0, 'Custom profile field definitions', 0, 0, 0),
    ('profile.field.avatar.visible', 'Avatar visible', 'true', 'PLATFORM', 0, 'Profile avatar visible flag', 0, 0, 0),
    ('profile.field.avatar.weight', '头像评分权重', '10', 'PLATFORM', 0, '个人中心头像字段评分权重', 0, 0, 0),
    ('profile.field.avatar.required', '头像 required', 'false', 'PLATFORM', 0, 'Profile avatar required flag', 0, 0, 0),
    ('profile.field.avatar.sort', '头像 sort', '10', 'PLATFORM', 0, '个人中心头像字段排序', 0, 0, 0),
    ('profile.field.real-name.visible', 'Real name visible', 'true', 'PLATFORM', 0, 'Profile real name visible flag', 0, 0, 0),
    ('profile.field.real-name.weight', '姓名评分权重', '15', 'PLATFORM', 0, '个人中心姓名字段评分权重', 0, 0, 0),
    ('profile.field.real-name.required', '姓名 required', 'false', 'PLATFORM', 0, 'Profile real name required flag', 0, 0, 0),
    ('profile.field.real-name.sort', '姓名 sort', '20', 'PLATFORM', 0, '个人中心姓名字段排序', 0, 0, 0),
    ('profile.field.mobile.visible', 'Mobile visible', 'true', 'PLATFORM', 0, 'Profile mobile visible flag', 0, 0, 0),
    ('profile.field.mobile.weight', 'Mobile weight', '15', 'PLATFORM', 0, 'Profile mobile field score weight', 0, 0, 0),
    ('profile.field.mobile.required', 'Mobile required', 'false', 'PLATFORM', 0, 'Profile mobile required flag', 0, 0, 0),
    ('profile.field.mobile.sort', 'Mobile sort', '30', 'PLATFORM', 0, 'Profile mobile field sort order', 0, 0, 0),
    ('profile.field.email.visible', 'Email visible', 'true', 'PLATFORM', 0, 'Profile email visible flag', 0, 0, 0),
    ('profile.field.email.weight', '邮箱评分权重', '15', 'PLATFORM', 0, '个人中心邮箱字段评分权重', 0, 0, 0),
    ('profile.field.email.required', '邮箱 required', 'false', 'PLATFORM', 0, 'Profile email required flag', 0, 0, 0),
    ('profile.field.email.sort', '邮箱 sort', '40', 'PLATFORM', 0, '个人中心邮箱字段排序', 0, 0, 0),
    ('profile.field.birth-month.visible', 'Birth month visible', 'true', 'PLATFORM', 0, 'Profile birth month visible flag', 0, 0, 0),
    ('profile.field.birth-month.weight', '出生年月评分权重', '10', 'PLATFORM', 0, '个人中心出生年月字段评分权重', 0, 0, 0),
    ('profile.field.birth-month.required', '出生年月 required', 'false', 'PLATFORM', 0, 'Profile birth month required flag', 0, 0, 0),
    ('profile.field.birth-month.sort', '出生年月 sort', '50', 'PLATFORM', 0, '个人中心出生年月字段排序', 0, 0, 0),
    ('profile.field.gender.visible', 'Gender visible', 'true', 'PLATFORM', 0, 'Profile gender visible flag', 0, 0, 0),
    ('profile.field.gender.weight', '性别评分权重', '10', 'PLATFORM', 0, '个人中心性别字段评分权重', 0, 0, 0),
    ('profile.field.gender.required', '性别 required', 'false', 'PLATFORM', 0, 'Profile gender required flag', 0, 0, 0),
    ('profile.field.gender.sort', '性别 sort', '60', 'PLATFORM', 0, '个人中心性别字段排序', 0, 0, 0),
    ('profile.field.region.visible', 'Region visible', 'true', 'PLATFORM', 0, 'Profile region visible flag', 0, 0, 0),
    ('profile.field.region.weight', 'Region weight', '10', 'PLATFORM', 0, 'Profile region field score weight', 0, 0, 0),
    ('profile.field.region.required', 'Region required', 'false', 'PLATFORM', 0, 'Profile region required flag', 0, 0, 0),
    ('profile.field.region.sort', 'Region sort', '70', 'PLATFORM', 0, 'Profile region field sort order', 0, 0, 0),
    ('profile.field.id-card-number.visible', 'ID card number visible', 'true', 'PLATFORM', 0, 'Profile ID card number visible flag', 0, 0, 0),
    ('profile.field.id-card-number.weight', 'ID card number weight', '5', 'PLATFORM', 0, 'Profile ID card number score weight', 0, 0, 0),
    ('profile.field.id-card-number.required', 'ID card number required', 'false', 'PLATFORM', 0, 'Profile ID card number required flag', 0, 0, 0),
    ('profile.field.id-card-number.sort', 'ID card number sort', '80', 'PLATFORM', 0, 'Profile ID card number field sort order', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `config_key` = VALUES(`config_key`);

INSERT INTO `iam_user` (`id`, `user_no`, `display_name`, `status`, `user_type`, `source`, `deleted`)
VALUES (1001, 'admin', 'Administrator', 'ENABLED', 'SYSTEM', 'BOOTSTRAP_SQL', 0)
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `status` = VALUES(`status`),
    `user_type` = VALUES(`user_type`),
    `source` = VALUES(`source`),
    `deleted` = 0;

INSERT INTO `iam_user_identity` (`user_id`, `user_uuid`, `identity_type`, `identifier`, `identifier_normalized`, `verified`, `primary_identity`, `status`, `deleted`)
VALUES (1001, (SELECT `uuid` FROM `sys_user` WHERE `id` = 1001), 'USERNAME', 'admin', 'admin', 1, 1, 'ENABLED', 0)
ON DUPLICATE KEY UPDATE
    `user_id` = VALUES(`user_id`),
    `user_uuid` = VALUES(`user_uuid`),
    `identifier` = VALUES(`identifier`),
    `verified` = VALUES(`verified`),
    `primary_identity` = VALUES(`primary_identity`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `iam_user_credential` (`user_id`, `user_uuid`, `credential_type`, `credential_secret`, `algorithm`, `version`, `status`, `deleted`)
VALUES (1001, (SELECT `uuid` FROM `sys_user` WHERE `id` = 1001), 'PASSWORD', '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te', 'BCRYPT', 1, 'ENABLED', 0)
ON DUPLICATE KEY UPDATE
    `user_uuid` = VALUES(`user_uuid`),
    `credential_secret` = VALUES(`credential_secret`),
    `algorithm` = VALUES(`algorithm`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `iam_user_profile` (`user_id`, `user_uuid`, `nickname`, `real_name`, `locale`, `deleted`)
VALUES (1001, (SELECT `uuid` FROM `sys_user` WHERE `id` = 1001), 'Administrator', 'Administrator', 'zh-CN', 0)
ON DUPLICATE KEY UPDATE
    `user_uuid` = VALUES(`user_uuid`),
    `nickname` = VALUES(`nickname`),
    `real_name` = VALUES(`real_name`),
    `locale` = VALUES(`locale`),
    `deleted` = 0;

INSERT INTO `iam_subject` (`subject_type`, `ref_id`, `subject_code`, `display_name`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES ('USER', 1001, 'admin', 'Administrator', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `subject_code` = VALUES(`subject_code`),
    `display_name` = VALUES(`display_name`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `iam_subject_role` (`subject_id`, `role_id`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 1001, 0, 0, 0
FROM `iam_subject`
WHERE `subject_type` = 'USER' AND `ref_id` = 1001 AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user` (`id`, `uuid`, `username`, `nickname`, `real_name`, `password_hash`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1002, CAST(FLOOR(100000000000000000 + RAND() * 900000000000000000) AS CHAR), 'user', 'Common User', 'Common User', '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `uuid` = IF(`uuid` REGEXP '^[0-9]+$', `uuid`, VALUES(`uuid`)),
    `nickname` = VALUES(`nickname`),
    `real_name` = VALUES(`real_name`),
    `password_hash` = VALUES(`password_hash`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user_role` (`user_id`, `user_uuid`, `role_id`, `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`)
VALUES (1002, (SELECT `uuid` FROM `sys_user` WHERE `id` = 1002), 1002, 0, '00000000-0000-0000-0000-000000000000', 0, '00000000-0000-0000-0000-000000000000', 0)
ON DUPLICATE KEY UPDATE
    `user_uuid` = VALUES(`user_uuid`),
    `updated_by` = VALUES(`updated_by`),
    `updated_by_uuid` = VALUES(`updated_by_uuid`),
    `deleted` = 0;

INSERT INTO `iam_user` (`id`, `user_no`, `display_name`, `status`, `user_type`, `source`, `deleted`)
VALUES (1002, 'user', 'Common User', 'ENABLED', 'REGISTERED', 'BOOTSTRAP_SQL', 0)
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `status` = VALUES(`status`),
    `user_type` = VALUES(`user_type`),
    `source` = VALUES(`source`),
    `deleted` = 0;

INSERT INTO `iam_user_identity` (`user_id`, `user_uuid`, `identity_type`, `identifier`, `identifier_normalized`, `verified`, `primary_identity`, `status`, `deleted`)
VALUES (1002, (SELECT `uuid` FROM `sys_user` WHERE `id` = 1002), 'USERNAME', 'user', 'user', 1, 1, 'ENABLED', 0)
ON DUPLICATE KEY UPDATE
    `user_id` = VALUES(`user_id`),
    `user_uuid` = VALUES(`user_uuid`),
    `identifier` = VALUES(`identifier`),
    `verified` = VALUES(`verified`),
    `primary_identity` = VALUES(`primary_identity`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `iam_user_credential` (`user_id`, `user_uuid`, `credential_type`, `credential_secret`, `algorithm`, `version`, `status`, `deleted`)
VALUES (1002, (SELECT `uuid` FROM `sys_user` WHERE `id` = 1002), 'PASSWORD', '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te', 'BCRYPT', 1, 'ENABLED', 0)
ON DUPLICATE KEY UPDATE
    `user_uuid` = VALUES(`user_uuid`),
    `credential_secret` = VALUES(`credential_secret`),
    `algorithm` = VALUES(`algorithm`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `iam_user_profile` (`user_id`, `user_uuid`, `nickname`, `real_name`, `locale`, `deleted`)
VALUES (1002, (SELECT `uuid` FROM `sys_user` WHERE `id` = 1002), 'Common User', 'Common User', 'zh-CN', 0)
ON DUPLICATE KEY UPDATE
    `user_uuid` = VALUES(`user_uuid`),
    `nickname` = VALUES(`nickname`),
    `real_name` = VALUES(`real_name`),
    `locale` = VALUES(`locale`),
    `deleted` = 0;

INSERT INTO `iam_subject` (`subject_type`, `ref_id`, `subject_code`, `display_name`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES ('USER', 1002, 'user', 'Common User', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `subject_code` = VALUES(`subject_code`),
    `display_name` = VALUES(`display_name`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `iam_subject_role` (`subject_id`, `role_id`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 1002, 0, 0, 0
FROM `iam_subject`
WHERE `subject_type` = 'USER' AND `ref_id` = 1002 AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

-- Built-in plugin catalog. Enable/disable state is global in sys_plugin_definition and sys_plugin_version.
INSERT INTO `sys_plugin_definition` (
    `plugin_code`, `plugin_name`, `plugin_type`, `description`, `author`, `plugin_api_version`,
    `builtin_flag`, `status`, `sort_no`, `schema_mode`, `supports_hot_disable`, `supports_data_purge`,
    `runtime_contributions_json`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'sensitive-words', 'Sensitive Words Plugin', 'SECURITY', 'Provides sensitive word dictionary maintenance, request content blocking, and import capabilities.',
    'Lumira', '1.0', 1, 'ENABLED', 10, 'SHARED', 1, 0,
    JSON_ARRAY('routes', 'menus', 'permissions', 'importers', 'interceptors'), 0, 0, 0
)
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
    `deleted` = 0;

INSERT INTO `sys_plugin_version` (
    `plugin_code`, `version`, `min_platform_version`, `install_status`, `load_status`, `health_status`,
    `lifecycle_status`, `schema_status`, `is_active`, `rollbackable`, `metadata_json`, `validation_report_json`,
    `installed_at`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'sensitive-words', '1.0.0', '1.0.0', 'INSTALLED', 'LOADED', 'HEALTHY',
    'INSTALLED', 'READY', 1, 0,
    JSON_OBJECT(
        'pluginCode', 'sensitive-words',
        'pluginName', 'Sensitive Words Plugin',
        'version', '1.0.0',
        'kind', 'SECURITY',
        'builtin', true
    ),
    JSON_OBJECT('builtin', true, 'status', 'VERIFIED'), CURRENT_TIMESTAMP, 0, 0, 0
)
ON DUPLICATE KEY UPDATE
    `min_platform_version` = VALUES(`min_platform_version`),
    `install_status` = VALUES(`install_status`),
    `load_status` = VALUES(`load_status`),
    `health_status` = VALUES(`health_status`),
    `schema_status` = VALUES(`schema_status`),
    `is_active` = VALUES(`is_active`),
    `metadata_json` = VALUES(`metadata_json`),
    `validation_report_json` = VALUES(`validation_report_json`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_plugin_menu_rel` (
    `plugin_code`, `plugin_version`, `menu_code`, `menu_name`, `route_path`, `icon`,
    `permission_key`, `parent_menu_code`, `sort_no`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'sensitive-words', '1.0.0', 'plugin.sensitive-words', 'Sensitive Words Management', '/plugins/sensitive-words', 'SafetyOutlined',
    'plugin:sensitive-words:view', 'settings.plugins', 10, 0, 0, 0
)
ON DUPLICATE KEY UPDATE
    `menu_name` = VALUES(`menu_name`),
    `route_path` = VALUES(`route_path`),
    `icon` = VALUES(`icon`),
    `permission_key` = VALUES(`permission_key`),
    `parent_menu_code` = VALUES(`parent_menu_code`),
    `sort_no` = VALUES(`sort_no`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_plugin_permission_rel` (
    `plugin_code`, `plugin_version`, `permission_key`, `permission_name`, `permission_group`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    ('sensitive-words', '1.0.0', 'plugin:sensitive-words:view', 'View sensitive words plugin', 'sensitive-words', 0, 0, 0),
    ('sensitive-words', '1.0.0', 'plugin:sensitive-words:manage', 'Manage sensitive words plugin', 'sensitive-words', 0, 0, 0),
    ('sensitive-words', '1.0.0', 'plugin:sensitive-words:import', 'Import sensitive words', 'sensitive-words', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_plugin_definition` (
    `plugin_code`, `plugin_name`, `plugin_type`, `description`, `author`, `plugin_api_version`,
    `builtin_flag`, `status`, `sort_no`, `schema_mode`, `supports_hot_disable`, `supports_data_purge`,
    `runtime_contributions_json`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'work-order-feedback', 'Work Order Feedback', 'BUSINESS', 'Allows users to submit rich-text feedback and administrators to follow up.',
    'Lumira', '1.0', 1, 'ENABLED', 20, 'SHARED', 1, 0,
    JSON_ARRAY('routes', 'menus', 'permissions', 'rich-text-upload'), 0, 0, 0
)
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
    `deleted` = 0;

INSERT INTO `sys_plugin_version` (
    `plugin_code`, `version`, `min_platform_version`, `install_status`, `load_status`, `health_status`,
    `lifecycle_status`, `schema_status`, `is_active`, `rollbackable`, `metadata_json`, `validation_report_json`,
    `installed_at`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'work-order-feedback', '1.0.0', '1.0.0', 'INSTALLED', 'LOADED', 'HEALTHY',
    'INSTALLED', 'READY', 1, 0,
    JSON_OBJECT(
        'pluginCode', 'work-order-feedback',
        'pluginName', '工单反馈',
        'version', '1.0.0',
        'kind', 'BUSINESS',
        'builtin', true
    ),
    JSON_OBJECT('builtin', true, 'status', 'VERIFIED'), CURRENT_TIMESTAMP, 0, 0, 0
)
ON DUPLICATE KEY UPDATE
    `min_platform_version` = VALUES(`min_platform_version`),
    `install_status` = VALUES(`install_status`),
    `load_status` = VALUES(`load_status`),
    `health_status` = VALUES(`health_status`),
    `schema_status` = VALUES(`schema_status`),
    `is_active` = VALUES(`is_active`),
    `metadata_json` = VALUES(`metadata_json`),
    `validation_report_json` = VALUES(`validation_report_json`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_plugin_menu_rel` (
    `plugin_code`, `plugin_version`, `menu_code`, `menu_name`, `route_path`, `icon`,
    `permission_key`, `parent_menu_code`, `sort_no`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'work-order-feedback', '1.0.0', 'plugin.work-order-feedback', '工单反馈', '/plugins/work-order-feedback', 'CustomerServiceOutlined',
    'plugin:work-order-feedback:view', 'settings.plugins', 20, 0, 0, 0
)
ON DUPLICATE KEY UPDATE
    `menu_name` = VALUES(`menu_name`),
    `route_path` = VALUES(`route_path`),
    `icon` = VALUES(`icon`),
    `permission_key` = VALUES(`permission_key`),
    `parent_menu_code` = VALUES(`parent_menu_code`),
    `sort_no` = VALUES(`sort_no`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_plugin_permission_rel` (
    `plugin_code`, `plugin_version`, `permission_key`, `permission_name`, `permission_group`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    ('work-order-feedback', '1.0.0', 'plugin:work-order-feedback:view', '查看工单反馈', 'work-order-feedback', 0, 0, 0),
    ('work-order-feedback', '1.0.0', 'plugin:work-order-feedback:create', '提交工单反馈', 'work-order-feedback', 0, 0, 0),
    ('work-order-feedback', '1.0.0', 'plugin:work-order-feedback:manage', '处理工单反馈', 'work-order-feedback', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

-- Final super administrator permission sync.
-- Keep ADMIN mapped to every active permission after all core and built-in plugin seeds.
INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, p.`permission_key`, 0, 0, 0
FROM `sys_permission` p
WHERE p.`deleted` = 0
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES ('IAM', 'permission-snapshot', 2, 'admin-permission-final-sync', NOW())
ON DUPLICATE KEY UPDATE
    `version` = `version` + 1,
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);

-- XXL-JOB scheduler tables. Keep this schema aligned with xuxueli/xxl-job-admin:3.4.0.
CREATE TABLE `xxl_job_group` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `app_name` varchar(64) NOT NULL,
  `title` varchar(64) NOT NULL,
  `address_type` tinyint(4) NOT NULL DEFAULT '0',
  `address_list` text,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_registry` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `registry_group` varchar(50) NOT NULL,
  `registry_key` varchar(255) NOT NULL,
  `registry_value` varchar(255) NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_g_k_v` (`registry_group`,`registry_key`,`registry_value`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_info` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `job_group` int(11) NOT NULL,
  `job_desc` varchar(255) NOT NULL,
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `author` varchar(64) DEFAULT NULL,
  `alarm_email` varchar(255) DEFAULT NULL,
  `schedule_type` varchar(50) NOT NULL DEFAULT 'NONE',
  `schedule_conf` varchar(128) DEFAULT NULL,
  `misfire_strategy` varchar(50) NOT NULL DEFAULT 'DO_NOTHING',
  `executor_route_strategy` varchar(50) DEFAULT NULL,
  `executor_handler` varchar(255) DEFAULT NULL,
  `executor_param` text DEFAULT NULL,
  `executor_block_strategy` varchar(50) DEFAULT NULL,
  `executor_timeout` int(11) NOT NULL DEFAULT '0',
  `executor_fail_retry_count` int(11) NOT NULL DEFAULT '0',
  `glue_type` varchar(50) NOT NULL,
  `glue_source` mediumtext,
  `glue_remark` varchar(128) DEFAULT NULL,
  `glue_updatetime` datetime DEFAULT NULL,
  `child_jobid` varchar(255) DEFAULT NULL,
  `trigger_status` tinyint(4) NOT NULL DEFAULT '0',
  `trigger_last_time` bigint(13) NOT NULL DEFAULT '0',
  `trigger_next_time` bigint(13) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_logglue` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `job_id` int(11) NOT NULL,
  `glue_type` varchar(50) DEFAULT NULL,
  `glue_source` mediumtext,
  `glue_remark` varchar(128) NOT NULL,
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `job_group` int(11) NOT NULL,
  `job_id` int(11) NOT NULL,
  `executor_address` varchar(255) DEFAULT NULL,
  `executor_handler` varchar(255) DEFAULT NULL,
  `executor_param` text DEFAULT NULL,
  `executor_sharding_param` varchar(20) DEFAULT NULL,
  `executor_fail_retry_count` int(11) NOT NULL DEFAULT '0',
  `trigger_time` datetime DEFAULT NULL,
  `trigger_code` int(11) NOT NULL,
  `trigger_msg` text,
  `handle_time` datetime DEFAULT NULL,
  `handle_code` int(11) NOT NULL,
  `handle_msg` text,
  `alarm_status` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `I_trigger_time` (`trigger_time`),
  KEY `I_handle_code` (`handle_code`),
  KEY `I_jobgroup` (`job_group`),
  KEY `I_jobid` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_log_report` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `trigger_day` datetime DEFAULT NULL,
  `running_count` int(11) NOT NULL DEFAULT '0',
  `suc_count` int(11) NOT NULL DEFAULT '0',
  `fail_count` int(11) NOT NULL DEFAULT '0',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_trigger_day` (`trigger_day`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_lock` (
  `lock_name` varchar(50) NOT NULL,
  PRIMARY KEY (`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(100) NOT NULL,
  `token` varchar(100) DEFAULT NULL,
  `role` tinyint(4) NOT NULL,
  `permission` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_username` (`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `xxl_job_group` (`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
VALUES (1, 'lumira-server', 'Lumira Server Executor', 0, NULL, NOW());

INSERT INTO `xxl_job_user` (`id`, `username`, `password`, `role`, `permission`)
VALUES (1, 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 1, NULL);

INSERT INTO `xxl_job_lock` (`lock_name`) VALUES ('schedule_lock');

INSERT INTO `ai_employee` (
  `username`, `nickname`, `position`, `avatar_key`, `description`, `greeting`, `system_prompt`,
  `default_llm_service_id`, `enabled`, `sort_order`, `is_deleted`, `create_time`, `update_time`
) VALUES (
  'ai-assistant', 'AI Assistant', 'General Chat', NULL,
  'Default assistant for general AI conversations.', 'Hello, I am AI Assistant. How can I help?',
  'You are the general AI assistant for this enterprise platform. Help clearly and concisely, answer in the user''s language, and do not claim access to a specific digital employee''s private skills or knowledge unless one is selected.',
  NULL, 1, 100000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON DUPLICATE KEY UPDATE `is_deleted` = 0;

SET FOREIGN_KEY_CHECKS = 1;





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

-- Workflow persistence is database-owned. Application startup must not create
-- these tables or seed workflow records.
CREATE TABLE IF NOT EXISTS `workflow_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_type` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `version_no` int NOT NULL DEFAULT 1,
  `created_by` bigint DEFAULT 0,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT 0,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_definition_business` (`business_type`,`deleted`),
  KEY `idx_workflow_definition_status` (`status`,`deleted`),
  KEY `idx_workflow_definition_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `workflow_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `definition_id` bigint NOT NULL,
  `node_key` varchar(64) NOT NULL,
  `node_type` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL,
  `position_x` int DEFAULT 0,
  `position_y` int DEFAULT 0,
  `assignment_type` varchar(32) DEFAULT NULL,
  `approver_user_ids_json` json DEFAULT NULL,
  `approver_role_ids_json` json DEFAULT NULL,
  `approval_mode` varchar(16) NOT NULL DEFAULT 'ALL',
  `config_json` json DEFAULT NULL,
  `created_by` bigint DEFAULT 0,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT 0,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_node_key` (`definition_id`,`node_key`,`deleted`),
  KEY `idx_workflow_node_definition` (`definition_id`,`deleted`),
  KEY `idx_workflow_node_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `workflow_edge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `definition_id` bigint NOT NULL,
  `edge_key` varchar(64) NOT NULL,
  `source_node_key` varchar(64) NOT NULL,
  `target_node_key` varchar(64) NOT NULL,
  `condition_expression` varchar(255) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT 100,
  `config_json` json DEFAULT NULL,
  `created_by` bigint DEFAULT 0,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT 0,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_edge_key` (`definition_id`,`edge_key`,`deleted`),
  KEY `idx_workflow_edge_source` (`definition_id`,`source_node_key`,`deleted`),
  KEY `idx_workflow_edge_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `workflow_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `definition_id` bigint NOT NULL,
  `definition_version_no` int NOT NULL,
  `business_type` varchar(64) NOT NULL,
  `business_id` bigint NOT NULL,
  `business_uuid` varchar(64) DEFAULT NULL,
  `business_title` varchar(255) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `current_node_key` varchar(64) DEFAULT NULL,
  `snapshot_json` json NOT NULL,
  `variables_json` json DEFAULT NULL,
  `applicant_user_id` bigint DEFAULT NULL,
  `applicant_user_uuid` varchar(64) DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT 0,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT 0,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_workflow_instance_business` (`business_type`,`business_id`,`deleted`),
  KEY `idx_workflow_instance_status` (`status`,`deleted`,`updated_at`),
  KEY `idx_workflow_instance_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `workflow_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NOT NULL,
  `node_key` varchar(64) NOT NULL,
  `node_name` varchar(128) NOT NULL,
  `approval_mode` varchar(16) NOT NULL DEFAULT 'ALL',
  `status` varchar(32) NOT NULL,
  `approver_user_id` bigint DEFAULT NULL,
  `approver_user_uuid` varchar(64) DEFAULT NULL,
  `approver_role_id` bigint DEFAULT NULL,
  `completed_by` bigint DEFAULT NULL,
  `completed_by_uuid` varchar(64) DEFAULT NULL,
  `completed_by_name` varchar(128) DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `comment` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT 0,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT 0,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_workflow_task_user_uuid` (`approver_user_id`,`approver_user_uuid`,`status`,`deleted`,`created_at`),
  KEY `idx_workflow_task_role` (`approver_role_id`,`status`,`deleted`,`created_at`),
  KEY `idx_workflow_task_instance` (`instance_id`,`node_key`,`status`,`deleted`),
  KEY `idx_workflow_task_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `workflow_action_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NOT NULL,
  `task_id` bigint DEFAULT NULL,
  `action_type` varchar(32) NOT NULL,
  `node_key` varchar(64) DEFAULT NULL,
  `node_name` varchar(128) DEFAULT NULL,
  `operator_user_id` bigint DEFAULT NULL,
  `operator_user_uuid` varchar(64) DEFAULT NULL,
  `operator_username` varchar(128) DEFAULT NULL,
  `comment` varchar(500) DEFAULT NULL,
  `created_by` bigint DEFAULT 0,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT 0,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_workflow_action_instance` (`instance_id`,`created_at`,`id`),
  KEY `idx_workflow_action_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_account_activation_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token_hash` char(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `user_uuid` varchar(64) DEFAULT NULL,
  `expert_id` bigint DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `consumed_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT 0,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT 0,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_activation_token_hash` (`token_hash`),
  KEY `idx_account_activation_user_uuid` (`user_id`,`user_uuid`,`consumed_at`,`deleted`),
  KEY `idx_account_activation_expires` (`expires_at`,`consumed_at`,`deleted`),
  KEY `idx_account_activation_creator_uuid` (`created_by`,`created_by_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sys_user_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_uuid` char(36) NOT NULL,
  `draft_key` varchar(128) NOT NULL,
  `payload_json` json NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_draft_owner_key` (`user_id`,`user_uuid`,`draft_key`),
  KEY `idx_sys_user_draft_updated` (`user_id`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `aiadc_expert`
  ADD COLUMN `approval_status` varchar(32) NOT NULL DEFAULT 'APPROVED' AFTER `status`,
  ADD COLUMN `approval_instance_id` bigint DEFAULT NULL AFTER `approval_status`,
  ADD COLUMN `approved_by` bigint DEFAULT NULL AFTER `approval_instance_id`,
  ADD COLUMN `approved_at` datetime DEFAULT NULL AFTER `approved_by`,
  ADD INDEX `idx_aiadc_expert_approval` (`approval_status`,`deleted`,`updated_at`);

INSERT INTO `workflow_definition` (
  `business_type`, `name`, `status`, `version_no`, `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
SELECT 'EXPERT_APPLICATION', '专家申请审批', 'ACTIVE', 1,
       0, '00000000-0000-0000-0000-000000000000', 0, '00000000-0000-0000-0000-000000000000', 0
WHERE NOT EXISTS (
  SELECT 1 FROM `workflow_definition` WHERE `business_type` = 'EXPERT_APPLICATION' AND `deleted` = 0
);

INSERT INTO `workflow_node` (
  `definition_id`, `node_key`, `node_type`, `name`, `position_x`, `position_y`, `assignment_type`,
  `approver_user_ids_json`, `approver_role_ids_json`, `approval_mode`, `config_json`,
  `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
SELECT definition_record.`id`, seed.`node_key`, seed.`node_type`, seed.`name`, seed.`position_x`, seed.`position_y`, seed.`assignment_type`,
       JSON_ARRAY(), CASE WHEN seed.`node_key` = 'review' THEN JSON_ARRAY(COALESCE(admin_role.`id`, 1001)) ELSE JSON_ARRAY() END,
       'ALL', JSON_OBJECT(), 0, '00000000-0000-0000-0000-000000000000', 0, '00000000-0000-0000-0000-000000000000', 0
FROM `workflow_definition` definition_record
JOIN (
  SELECT 'start' AS `node_key`, 'START' AS `node_type`, '开始' AS `name`, 80 AS `position_x`, 120 AS `position_y`, NULL AS `assignment_type`
  UNION ALL SELECT 'review', 'APPROVAL', '管理员审批', 320, 120, 'ROLE'
  UNION ALL SELECT 'end', 'END', '结束', 580, 120, NULL
) seed
LEFT JOIN `sys_role` admin_role ON admin_role.`role_code` = 'ADMIN' AND admin_role.`deleted` = 0
WHERE definition_record.`business_type` = 'EXPERT_APPLICATION' AND definition_record.`deleted` = 0
ON DUPLICATE KEY UPDATE `updated_at` = `workflow_node`.`updated_at`;

INSERT INTO `workflow_edge` (
  `definition_id`, `edge_key`, `source_node_key`, `target_node_key`, `condition_expression`, `sort_order`, `config_json`,
  `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
SELECT definition_record.`id`, seed.`edge_key`, seed.`source_key`, seed.`target_key`, NULL, 10, JSON_OBJECT(),
       0, '00000000-0000-0000-0000-000000000000', 0, '00000000-0000-0000-0000-000000000000', 0
FROM `workflow_definition` definition_record
JOIN (
  SELECT 'start-review' AS `edge_key`, 'start' AS `source_key`, 'review' AS `target_key`
  UNION ALL SELECT 'review-end', 'review', 'end'
) seed
WHERE definition_record.`business_type` = 'EXPERT_APPLICATION' AND definition_record.`deleted` = 0
ON DUPLICATE KEY UPDATE `updated_at` = `workflow_edge`.`updated_at`;
-- Sensitive-word behavior values are database-managed business configuration.
INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('sys_sensitive_word_action', '敏感词动作', 'ENABLED', 1, 'Sensitive word action and default order', 0, 0, 0),
    ('sys_sensitive_word_blocking_action', '敏感词阻断动作', 'ENABLED', 1, 'Action treated as blocking', 0, 0, 0),
    ('sys_sensitive_word_default_category', '敏感词默认分类', 'ENABLED', 1, 'Default category for manual entries', 0, 0, 0),
    ('sys_sensitive_word_import_category', '敏感词导入分类', 'ENABLED', 1, 'Category for imported entries', 0, 0, 0),
    ('sys_sensitive_word_default_severity', '敏感词默认级别', 'ENABLED', 1, 'Default severity for sensitive words', 0, 0, 0),
    ('sys_sensitive_word_severity', '敏感词级别优先级', 'ENABLED', 1, 'Database-owned matching priority', 0, 0, 0)
ON DUPLICATE KEY UPDATE `status`=VALUES(`status`), `is_system`=VALUES(`is_system`), `remark`=VALUES(`remark`), `deleted`=0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'BLOCK', '阻断', 10, 'ENABLED', '默认敏感词动作', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_action' AND `deleted`=0
UNION ALL SELECT `id`, 'LOG_ONLY', '仅记录', 20, 'ENABLED', '敏感词动作', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_action' AND `deleted`=0
UNION ALL SELECT `id`, 'BLOCK', '阻断', 10, 'ENABLED', '产生阻断结果的动作', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_blocking_action' AND `deleted`=0
UNION ALL SELECT `id`, 'DEFAULT', '默认', 10, 'ENABLED', '手工敏感词默认分类', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_default_category' AND `deleted`=0
UNION ALL SELECT `id`, 'IMPORTED', '导入', 10, 'ENABLED', '导入敏感词分类', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_import_category' AND `deleted`=0
UNION ALL SELECT `id`, 'MEDIUM', '中', 10, 'ENABLED', '敏感词默认级别', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_default_severity' AND `deleted`=0
UNION ALL SELECT `id`, 'LOW', '低', 10, 'ENABLED', '匹配优先级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_severity' AND `deleted`=0
UNION ALL SELECT `id`, 'MEDIUM', '中', 20, 'ENABLED', '匹配优先级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_severity' AND `deleted`=0
UNION ALL SELECT `id`, 'HIGH', '高', 30, 'ENABLED', '匹配优先级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_severity' AND `deleted`=0
UNION ALL SELECT `id`, 'CRITICAL', '严重', 40, 'ENABLED', '匹配优先级', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='sys_sensitive_word_severity' AND `deleted`=0
ON DUPLICATE KEY UPDATE `item_label`=VALUES(`item_label`), `sort_no`=VALUES(`sort_no`), `status`=VALUES(`status`), `remark`=VALUES(`remark`), `deleted`=0;

-- Built-in storage spaces are persisted configuration, not application-code defaults.
INSERT INTO `file_storage_space` (
    `title`, `storage_key`, `provider`, `root_path`, `bucket_name`, `endpoint`, `region`,
    `access_key_id`, `access_key_secret`, `rename_strategy`, `max_file_size_mb`, `allowed_mime_types`,
    `default_flag`, `retain_file_on_record_delete`, `anonymous_access_allowed`, `status`,
    `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
VALUES
    ('用户上传文件', 'local', 'LOCAL', 'storage/uploads/', '', '', '', '', NULL, 'APPEND_RANDOM_ID', 20, '*', 1, 0, 1, 'ENABLED', 1, '00000000-0000-0000-0000-000000000000', 1, '00000000-0000-0000-0000-000000000000', 0),
    ('下载中心', 'download_center', 'LOCAL', 'storage/uploads/download_center/', '', '', '', '', NULL, 'APPEND_RANDOM_ID', 100, '*', 0, 0, 1, 'ENABLED', 1, '00000000-0000-0000-0000-000000000000', 1, '00000000-0000-0000-0000-000000000000', 0),
    ('AI 聊天附件', 'ai_chat', 'LOCAL', 'storage/uploads/ai_chat/', '', '', '', '', NULL, 'APPEND_RANDOM_ID', 20, '*', 0, 0, 0, 'ENABLED', 1, '00000000-0000-0000-0000-000000000000', 1, '00000000-0000-0000-0000-000000000000', 0),
    ('头像文件', 'avatar', 'LOCAL', 'storage/uploads/avatar/', '', '', '', '', NULL, 'APPEND_RANDOM_ID', 10, '*', 0, 0, 1, 'ENABLED', 1, '00000000-0000-0000-0000-000000000000', 1, '00000000-0000-0000-0000-000000000000', 0),
    ('Support feedback images', 'support_feedback', 'LOCAL', 'storage/uploads/support_feedback/', '', '', '', '', NULL, 'APPEND_RANDOM_ID', 20, '*', 0, 0, 1, 'ENABLED', 1, '00000000-0000-0000-0000-000000000000', 1, '00000000-0000-0000-0000-000000000000', 0)
ON DUPLICATE KEY UPDATE
    `title`=VALUES(`title`), `provider`=VALUES(`provider`), `root_path`=VALUES(`root_path`),
    `rename_strategy`=VALUES(`rename_strategy`), `max_file_size_mb`=VALUES(`max_file_size_mb`),
    `allowed_mime_types`=VALUES(`allowed_mime_types`), `default_flag`=VALUES(`default_flag`),
    `retain_file_on_record_delete`=VALUES(`retain_file_on_record_delete`),
    `anonymous_access_allowed`=VALUES(`anonymous_access_allowed`), `status`=VALUES(`status`), `deleted`=0;


-- Database-owned file service providers, strategies, preview rules, and runtime defaults.
INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('file_storage_provider', '文件存储提供商', 'ENABLED', 1, 'File service storage providers', 0, 0, 0),
    ('file_rename_strategy', '文件重命名策略', 'ENABLED', 1, 'File service rename strategies', 0, 0, 0),
    ('file_storage_status', '文件存储状态', 'ENABLED', 1, 'File service storage statuses', 0, 0, 0),
    ('file_preview_extension', '文件预览扩展名规则', 'ENABLED', 1, 'item_value=extension, item_label=preview mode', 0, 0, 0),
    ('file_preview_content_type', '文件预览 MIME 规则', 'ENABLED', 1, 'item_value=MIME, item_label=preview mode, remark=EXACT/PREFIX', 0, 0, 0),
    ('file_runtime_default', '文件服务运行默认值', 'ENABLED', 1, 'item_value=setting key, item_label=setting value', 0, 0, 0)
ON DUPLICATE KEY UPDATE `dict_name`=VALUES(`dict_name`), `status`='ENABLED', `is_system`=1,
    `remark`=VALUES(`remark`), `deleted`=0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'LOCAL', 'Local storage', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_storage_provider'
UNION ALL SELECT `id`, 'ALIYUN_OSS', '阿里云 OSS', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_storage_provider'
UNION ALL SELECT `id`, 'TENCENT_COS', '腾讯云 COS', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_storage_provider'
UNION ALL SELECT `id`, 'APPEND_RANDOM_ID', '追加随机标识', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_rename_strategy'
UNION ALL SELECT `id`, 'RANDOM_STRING', '随机字符串', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_rename_strategy'
UNION ALL SELECT `id`, 'KEEP_ORIGINAL', '保留原名', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_rename_strategy'
UNION ALL SELECT `id`, 'ENABLED', '启用', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_storage_status'
UNION ALL SELECT `id`, 'DISABLED', '停用', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_storage_status'
UNION ALL SELECT `id`, 'png', 'IMAGE', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'jpg', 'IMAGE', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'jpeg', 'IMAGE', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'gif', 'IMAGE', 40, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'bmp', 'IMAGE', 50, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'ico', 'IMAGE', 60, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'pdf', 'PDF', 70, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'txt', 'TEXT', 80, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'md', 'TEXT', 90, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'csv', 'TEXT', 100, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'json', 'TEXT', 110, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'xml', 'TEXT', 120, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_extension'
UNION ALL SELECT `id`, 'image/', 'IMAGE', 10, 'ENABLED', 'PREFIX', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_content_type'
UNION ALL SELECT `id`, 'application/pdf', 'PDF', 20, 'ENABLED', 'EXACT', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_content_type'
UNION ALL SELECT `id`, 'text/', 'TEXT', 30, 'ENABLED', 'PREFIX', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_preview_content_type'
UNION ALL SELECT `id`, 'STORAGE_PROVIDER', 'LOCAL', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'STORAGE_KEY', 'local', 15, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'ROOT_PATH', 'storage/uploads/', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'RENAME_STRATEGY', 'APPEND_RANDOM_ID', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'MAX_FILE_SIZE_MB', '20', 40, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'ALLOWED_MIME_TYPES', '*', 50, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'DOCUMENT_CATEGORY', '我的文件', 60, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'IMAGE_CATEGORY', '图片', 70, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'UNSUPPORTED_PREVIEW_MODE', 'UNSUPPORTED', 80, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
UNION ALL SELECT `id`, 'STORAGE_STATUS', 'ENABLED', 90, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='file_runtime_default'
ON DUPLICATE KEY UPDATE `item_label`=VALUES(`item_label`), `sort_no`=VALUES(`sort_no`),
    `status`='ENABLED', `remark`=VALUES(`remark`), `deleted`=0;


-- Database-owned work-order status, priority, terminal-state, and upload defaults.
INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('work_order_feedback_status', '工单反馈状态', 'ENABLED', 1, 'remark=TERMINAL marks handled states', 0, 0, 0),
    ('work_order_feedback_priority', '工单反馈优先级', 'ENABLED', 1, 'Work order feedback priorities', 0, 0, 0),
    ('work_order_feedback_default', '工单反馈默认配置', 'ENABLED', 1, 'item_value=setting key, item_label=setting value', 0, 0, 0)
ON DUPLICATE KEY UPDATE `dict_name`=VALUES(`dict_name`), `status`='ENABLED', `is_system`=1,
    `remark`=VALUES(`remark`), `deleted`=0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'OPEN', '待处理', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_status'
UNION ALL SELECT `id`, 'PROCESSING', '处理中', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_status'
UNION ALL SELECT `id`, 'RESOLVED', '已解决', 30, 'ENABLED', 'TERMINAL', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_status'
UNION ALL SELECT `id`, 'CLOSED', '已关闭', 40, 'ENABLED', 'TERMINAL', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_status'
UNION ALL SELECT `id`, 'LOW', '低', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_priority'
UNION ALL SELECT `id`, 'NORMAL', '普通', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_priority'
UNION ALL SELECT `id`, 'HIGH', '高', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_priority'
UNION ALL SELECT `id`, 'URGENT', '紧急', 40, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_priority'
UNION ALL SELECT `id`, 'INITIAL_STATUS', 'OPEN', 10, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_default'
UNION ALL SELECT `id`, 'DEFAULT_PRIORITY', 'NORMAL', 20, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_default'
UNION ALL SELECT `id`, 'UPLOAD_BUCKET', 'support_feedback', 30, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_default'
UNION ALL SELECT `id`, 'IMAGE_CATEGORY', '工单反馈', 40, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_default'
UNION ALL SELECT `id`, 'IMAGE_REMARK', '工单反馈富文本图片', 50, 'ENABLED', NULL, 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code`='work_order_feedback_default'
ON DUPLICATE KEY UPDATE `item_label`=VALUES(`item_label`), `sort_no`=VALUES(`sort_no`),
    `status`='ENABLED', `remark`=VALUES(`remark`), `deleted`=0;
+

-- Database-owned profile and team-member field metadata.
CREATE TABLE IF NOT EXISTS `sys_profile_field_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `page_key` varchar(64) NOT NULL,
  `field_key` varchar(64) NOT NULL,
  `field_label` varchar(128) NOT NULL,
  `field_description` varchar(512) DEFAULT NULL,
  `group_key` varchar(64) NOT NULL,
  `group_label` varchar(128) NOT NULL,
  `visible_config_key` varchar(128) NOT NULL,
  `weight_config_key` varchar(128) NOT NULL,
  `default_visible` tinyint NOT NULL DEFAULT 1,
  `default_weight` int NOT NULL DEFAULT 0,
  `field_type` varchar(32) NOT NULL,
  `required_flag` tinyint NOT NULL DEFAULT 0,
  `placeholder` varchar(255) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT 0,
  `updated_by` bigint DEFAULT 0,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_profile_field_page_key` (`page_key`,`field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `sys_dict_type` (`dict_code`,`dict_name`,`status`,`is_system`,`remark`,`created_by`,`updated_by`,`deleted`)
VALUES ('profile_settings_page_key','资料字段页面','ENABLED',1,'Profile settings supported pages',0,0,0),
       ('profile_custom_field_type','资料自定义字段类型','ENABLED',1,'Profile settings custom field types',0,0,0)
ON DUPLICATE KEY UPDATE `dict_name`=VALUES(`dict_name`),`status`='ENABLED',`is_system`=1,`remark`=VALUES(`remark`),`deleted`=0;

INSERT INTO `sys_dict_item` (`dict_type_id`,`item_value`,`item_label`,`sort_no`,`status`,`created_by`,`updated_by`,`deleted`)
SELECT `id`,'PROFILE','个人资料',10,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_settings_page_key'
UNION ALL SELECT `id`,'TEAM_MEMBER','团队成员',20,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_settings_page_key'
UNION ALL SELECT `id`,'TEXT','文本',10,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_custom_field_type'
UNION ALL SELECT `id`,'NUMBER','数字',20,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_custom_field_type'
UNION ALL SELECT `id`,'DATE','日期',30,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_custom_field_type'
UNION ALL SELECT `id`,'SELECT','下拉选择',40,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_custom_field_type'
UNION ALL SELECT `id`,'TEXTAREA','多行文本',50,'ENABLED',0,0,0 FROM `sys_dict_type` WHERE `dict_code`='profile_custom_field_type'
ON DUPLICATE KEY UPDATE `item_label`=VALUES(`item_label`),`sort_no`=VALUES(`sort_no`),`status`='ENABLED',`deleted`=0;

INSERT INTO `sys_profile_field_definition` (`page_key`,`field_key`,`field_label`,`field_description`,`group_key`,`group_label`,`visible_config_key`,`weight_config_key`,`default_visible`,`default_weight`,`field_type`,`required_flag`,`placeholder`,`sort_no`,`status`,`created_by`,`updated_by`,`deleted`)
VALUES
('PROFILE','avatarUrl','Avatar','Controls whether profile avatar upload and preview are shown','basic','Basic profile','profile.field.avatar.visible','profile.field.avatar.weight',1,10,'IMAGE',0,NULL,10,'ENABLED',0,0,0),
('PROFILE','realName','Real name','Controls whether the real-name profile field is shown','basic','Basic profile','profile.field.real-name.visible','profile.field.real-name.weight',1,15,'TEXT',0,'Enter real name',20,'ENABLED',0,0,0),
('PROFILE','mobile','Mobile','Controls whether the mobile profile field is shown','contact','Contact','profile.field.mobile.visible','profile.field.mobile.weight',1,15,'MOBILE',0,'Enter mobile number',30,'ENABLED',0,0,0),
('PROFILE','email','Email','Controls whether the email profile field is shown','contact','Contact','profile.field.email.visible','profile.field.email.weight',1,15,'EMAIL',0,'Enter email address',40,'ENABLED',0,0,0),
('PROFILE','birthMonth','Birth month','Controls whether the birth-month profile field is shown','basic','Basic profile','profile.field.birth-month.visible','profile.field.birth-month.weight',1,10,'MONTH',0,'Select birth month',50,'ENABLED',0,0,0),
('PROFILE','gender','Gender','Controls whether the gender profile field is shown','basic','Basic profile','profile.field.gender.visible','profile.field.gender.weight',1,10,'SELECT',0,'Select gender',60,'ENABLED',0,0,0),
('PROFILE','region','Region','Controls whether the region profile field is shown','basic','Basic profile','profile.field.region.visible','profile.field.region.weight',1,10,'TEXT',0,'Enter region',70,'ENABLED',0,0,0),
('PROFILE','idCardNumber','ID card number','Controls whether the ID-card profile field is shown','identity','Identity','profile.field.id-card-number.visible','profile.field.id-card-number.weight',1,5,'ID_CARD',0,'Enter ID card number',80,'ENABLED',0,0,0),
('TEAM_MEMBER','memberName','Member name','Team member name','teamMember','Team member','team.member.field.member-name.visible','team.member.field.member-name.weight',1,10,'TEXT',1,'Enter member name',10,'ENABLED',0,0,0),
('TEAM_MEMBER','employeeNo','Employee number','Team member employee or student number','teamMember','Team member','team.member.field.employee-no.visible','team.member.field.employee-no.weight',1,5,'TEXT',0,'Enter employee or student number',20,'ENABLED',0,0,0),
('TEAM_MEMBER','departmentName','Department','Team member department','teamMember','Team member','team.member.field.department-name.visible','team.member.field.department-name.weight',1,5,'TEXT',0,'Enter department',30,'ENABLED',0,0,0),
('TEAM_MEMBER','role','Role','Team member role','teamMember','Team member','team.member.field.role.visible','team.member.field.role.weight',1,5,'SELECT',0,'Select role',40,'ENABLED',0,0,0),
('TEAM_MEMBER','remark','Remark','Team member remark','teamMember','Team member','team.member.field.remark.visible','team.member.field.remark.weight',1,5,'TEXTAREA',0,'Enter remark',50,'ENABLED',0,0,0)
ON DUPLICATE KEY UPDATE `field_label`=VALUES(`field_label`),`field_description`=VALUES(`field_description`),`group_key`=VALUES(`group_key`),`group_label`=VALUES(`group_label`),`visible_config_key`=VALUES(`visible_config_key`),`weight_config_key`=VALUES(`weight_config_key`),`default_visible`=VALUES(`default_visible`),`default_weight`=VALUES(`default_weight`),`field_type`=VALUES(`field_type`),`required_flag`=VALUES(`required_flag`),`placeholder`=VALUES(`placeholder`),`sort_no`=VALUES(`sort_no`),`status`='ENABLED',`deleted`=0;
+

-- Database-owned platform setting groups and defaults.
CREATE TABLE IF NOT EXISTS `sys_platform_setting_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_code` varchar(64) NOT NULL,
  `config_key` varchar(128) NOT NULL,
  `config_name` varchar(128) NOT NULL DEFAULT '',
  `remark` varchar(512) DEFAULT NULL,
  `default_value` text,
  `reset_value` text,
  `sort_no` int NOT NULL DEFAULT 0,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT 0,
  `updated_by` bigint DEFAULT 0,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_setting_config_key` (`config_key`),
  KEY `idx_platform_setting_group` (`group_code`,`status`,`deleted`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `sys_platform_setting_definition` (`group_code`,`config_key`,`default_value`,`sort_no`,`status`,`created_by`,`updated_by`,`deleted`)
VALUES
    ('BRANDING','branding.website-name','Lumira',10,'ENABLED',0,0,0),
    ('BRANDING','branding.website-favicon-url','',20,'ENABLED',0,0,0),
    ('BRANDING','branding.website-logo-url','',30,'ENABLED',0,0,0),
    ('BRANDING','branding.login-background-url','',40,'ENABLED',0,0,0),
    ('BRANDING','branding.github-link-enabled','true',50,'ENABLED',0,0,0),
    ('BRANDING','branding.github-link-url','',60,'ENABLED',0,0,0),
    ('BRANDING','branding.help-link-enabled','true',70,'ENABLED',0,0,0),
    ('BRANDING','branding.help-link-url','',80,'ENABLED',0,0,0),
    ('BRANDING','branding.company-name','',90,'ENABLED',0,0,0),
    ('BRANDING','branding.copyright-start-year','',100,'ENABLED',0,0,0),
    ('BRANDING','branding.footer-icp','',110,'ENABLED',0,0,0),
    ('BRANDING','branding.footer-police-beian','',120,'ENABLED',0,0,0),
    ('BRANDING','branding.footer-copyright','',130,'ENABLED',0,0,0),
    ('AGREEMENT','agreement.user-agreement-markdown','',10,'ENABLED',0,0,0),
    ('AGREEMENT','agreement.privacy-agreement-markdown','',20,'ENABLED',0,0,0),
    ('SMTP','smtp.enabled','true',10,'ENABLED',0,0,0),
    ('SMTP','smtp.host','',20,'ENABLED',0,0,0),
    ('SMTP','smtp.port','25',30,'ENABLED',0,0,0),
    ('SMTP','smtp.username','',40,'ENABLED',0,0,0),
    ('SMTP','smtp.password','',50,'ENABLED',0,0,0),
    ('SMTP','smtp.from','',60,'ENABLED',0,0,0),
    ('SMTP','smtp.auth-enabled','true',70,'ENABLED',0,0,0),
    ('SMTP','smtp.starttls-enabled','true',80,'ENABLED',0,0,0),
    ('SMTP','smtp.ssl-enabled','false',90,'ENABLED',0,0,0),
    ('SMTP','smtp.test-subject','SMTP test email',100,'ENABLED',0,0,0),
    ('SMTP','smtp.test-content','This is a test email sent from the system SMTP settings.',110,'ENABLED',0,0,0),
    ('SMTP','smtp.connection-timeout-ms','5000',120,'ENABLED',0,0,0),
    ('SMTP','smtp.read-timeout-ms','5000',130,'ENABLED',0,0,0),
    ('SMTP','smtp.write-timeout-ms','5000',140,'ENABLED',0,0,0),
    ('WECHAT_OFFICIAL','notification.wechat-official.enabled','false',10,'ENABLED',0,0,0),
    ('WECHAT_OFFICIAL','notification.wechat-official.app-id','',20,'ENABLED',0,0,0),
    ('WECHAT_OFFICIAL','notification.wechat-official.app-secret','',30,'ENABLED',0,0,0),
    ('WECHAT_OFFICIAL','notification.wechat-official.template-id','',40,'ENABLED',0,0,0),
    ('WECHAT_OFFICIAL','notification.wechat-official.detail-url','',50,'ENABLED',0,0,0),
    ('WATERMARK','watermark.enabled','false',10,'ENABLED',0,0,0),
    ('WATERMARK','watermark.mode','TEXT',20,'ENABLED',0,0,0),
    ('WATERMARK','watermark.text-lines','',30,'ENABLED',0,0,0),
    ('WATERMARK','watermark.image-url','',40,'ENABLED',0,0,0),
    ('WATERMARK','watermark.font-color','rgba(0,0,0,0.15)',50,'ENABLED',0,0,0),
    ('WATERMARK','watermark.font-size','14',60,'ENABLED',0,0,0),
    ('WATERMARK','watermark.font-weight','normal',70,'ENABLED',0,0,0),
    ('WATERMARK','watermark.rotate','-22',80,'ENABLED',0,0,0),
    ('WATERMARK','watermark.gap-x','100',90,'ENABLED',0,0,0),
    ('WATERMARK','watermark.gap-y','100',100,'ENABLED',0,0,0),
    ('WATERMARK','watermark.offset-x','0',110,'ENABLED',0,0,0),
    ('WATERMARK','watermark.offset-y','0',120,'ENABLED',0,0,0),
    ('WATERMARK','watermark.z-index','9',130,'ENABLED',0,0,0),
    ('WATERMARK','watermark.opacity','0.15',140,'ENABLED',0,0,0),
    ('FLOATING_WINDOW','floating-window.api-docs-qr-enabled','false',10,'ENABLED',0,0,0),
    ('FLOATING_WINDOW','floating-window.api-docs-qr-title','',20,'ENABLED',0,0,0),
    ('FLOATING_WINDOW','floating-window.api-docs-qr-image-url','',30,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-width','3508',10,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-height','2480',20,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-orientation','LANDSCAPE',30,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-unit','PX',40,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-dpi','300',50,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-json','{"page":{"width":3508,"height":2480,"dpi":300,"orientation":"LANDSCAPE"},"elements":[{"id":"el_name","type":"text","fieldKey":"recipientName","x":1200,"y":920,"width":1100,"height":120,"fontFamily":"Microsoft YaHei","fontSize":72,"fontWeight":"bold","color":"#222222","textAlign":"center","placeholder":"${recipientName}"},{"id":"el_award","type":"text","fieldKey":"awardName","x":1200,"y":1200,"width":1100,"height":100,"fontFamily":"Microsoft YaHei","fontSize":56,"fontWeight":"normal","color":"#222222","textAlign":"center","placeholder":"${awardName}"},{"id":"el_qr","type":"qrcode","fieldKey":"verificationUrl","x":2920,"y":1900,"width":220,"height":220}]}',60,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.canvas.default-variable-schema-json','{"variables":[{"key":"recipientName","label":"Recipient","type":"text","required":true},{"key":"competitionTitle","label":"Competition","type":"text","required":true},{"key":"projectName","label":"Project","type":"text","required":false},{"key":"teamName","label":"Team","type":"text","required":false},{"key":"awardName","label":"Award","type":"text","required":true},{"key":"certificateNo","label":"Certificate No","type":"text","required":true},{"key":"issueDate","label":"Issue Date","type":"date","required":true},{"key":"verificationUrl","label":"Verification URL","type":"qrcode","required":true}]}',70,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.public.organizer','Lumira',80,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.template-statuses','DRAFT,PUBLISHED,ARCHIVED',90,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.scene-types','COMPETITION_AWARD,PARTICIPATION,CUSTOM',100,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.source-types','MANUAL,IMPORT,REGISTRATION,AWARD_RESULT',110,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.recipient-types','USER,TEAM,PROJECT,CUSTOM',120,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.record-statuses','ISSUED,REVOKED',130,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.default-scene-type','COMPETITION_AWARD',140,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.default-source-type','MANUAL',150,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.rule.default-recipient-type','CUSTOM',160,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.number.template-prefix','CTPL-',170,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.number.batch-prefix','CB-',180,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.number.certificate-prefix','CERT-',190,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.number.timestamp-format','yyyyMMddHHmmssSSS',200,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.number.verification-code-length','6',210,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.preview.batch-no','PREVIEW',220,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.preview.batch-name','Preview',230,'ENABLED',0,0,0),
    ('CERTIFICATE','certificate.preview.status','PREVIEW',240,'ENABLED',0,0,0)
ON DUPLICATE KEY UPDATE `group_code`=VALUES(`group_code`),`default_value`=VALUES(`default_value`),
    `sort_no`=VALUES(`sort_no`),`status`='ENABLED',`deleted`=0;

UPDATE `sys_platform_setting_definition`
SET `config_name` = `config_key`
WHERE (`config_name` IS NULL OR `config_name` = '') AND `deleted`=0;

UPDATE `sys_platform_setting_definition`
SET `reset_value` = CASE `config_key`
    WHEN 'smtp.enabled' THEN 'false' WHEN 'smtp.host' THEN '' WHEN 'smtp.port' THEN '25'
    WHEN 'smtp.username' THEN '' WHEN 'smtp.password' THEN '' WHEN 'smtp.from' THEN ''
    WHEN 'smtp.auth-enabled' THEN 'true' WHEN 'smtp.starttls-enabled' THEN 'true'
    WHEN 'smtp.ssl-enabled' THEN 'false' ELSE `reset_value` END
WHERE `group_code`='SMTP' AND `deleted`=0;
