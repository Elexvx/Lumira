-- Lumira consolidated database initialization script.
-- Generated from all service migration modules while Flyway is disabled before first production launch.
-- Includes minimum bootstrap data required by infrastructure components such as XXL-JOB Admin.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `ai_conversation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `employee_id` bigint unsigned NOT NULL,
  `owner_user_id` bigint unsigned NOT NULL,
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
  KEY `idx_ai_conversation_owner` (`owner_user_id`,`is_pinned`,`latest_message_at`,`is_deleted`),
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
  `document_count` bigint NOT NULL DEFAULT '0',
  `chunk_count` bigint NOT NULL DEFAULT '0',
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_base_code` (`kb_code`),
  UNIQUE KEY `uk_ai_knowledge_base_owner_name` (`owner_user_id`,`name`,`is_deleted`),
  KEY `idx_ai_knowledge_base_status` (`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_owner` (`owner_user_id`,`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_access` (`owner_user_id`,`visibility_scope`,`status`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_base_acl` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `subject_type` varchar(32) NOT NULL,
  `subject_id` bigint unsigned NOT NULL,
  `permission` varchar(32) NOT NULL,
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_acl_subject` (`knowledge_base_id`,`subject_type`,`subject_id`,`permission`,`is_deleted`),
  KEY `idx_ai_knowledge_acl_subject` (`subject_type`,`subject_id`,`permission`,`is_deleted`),
  KEY `idx_ai_knowledge_acl_base` (`knowledge_base_id`,`is_deleted`)
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
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_knowledge_document_base` (`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_document_file` (`file_id`),
  KEY `idx_ai_knowledge_document_index_retry` (`status`,`is_deleted`,`index_next_retry_at`,`update_time`,`id`),
  KEY `idx_ai_knowledge_document_status` (`knowledge_base_id`,`status`,`is_deleted`)
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
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_skill_code` (`skill_code`,`is_deleted`),
  KEY `idx_ai_skill_category_enabled` (`category`,`enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_tool_audit_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `skill_code` varchar(128) NOT NULL,
  `tool_name` varchar(128) DEFAULT NULL,
  `permission_mode` varchar(32) DEFAULT NULL,
  `confirm_required` tinyint unsigned NOT NULL DEFAULT '0',
  `confirm_result` tinyint unsigned DEFAULT NULL,
  `supervisor_verdict` varchar(32) DEFAULT NULL,
  `supervisor_message` varchar(1024) DEFAULT NULL,
  `policy_match` varchar(1024) DEFAULT NULL,
  `confirmed_by` bigint unsigned DEFAULT NULL,
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
  KEY `idx_ai_tool_audit_employee` (`employee_id`,`create_time`),
  KEY `idx_ai_tool_audit_skill` (`skill_code`,`result_status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_tool_call_plan` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `owner_user_id` bigint unsigned NOT NULL,
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
  `confirmed_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_plan_owner` (`owner_user_id`,`status`,`expires_at`),
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_audit_operation_created` (`created_at`),
  KEY `idx_audit_operation_user_created` (`user_id`,`created_at`),
  KEY `idx_audit_operation_user_uuid_created` (`user_uuid`,`created_at`),
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_file_object_key` (`object_key`),
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
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_processing_artifact` (`file_id`,`artifact_type`),
  KEY `idx_file_processing_artifact_file` (`file_id`,`deleted`),
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
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_processing_task_file_type` (`file_id`,`task_type`),
  KEY `idx_file_processing_task_status_retry` (`status`,`next_retry_at`,`priority`,`created_at`),
  KEY `idx_file_processing_task_file` (`file_id`,`deleted`),
  KEY `idx_file_processing_task_queue` (`deleted`,`status`,`next_retry_at`,`priority`,`created_at`,`id`),
  KEY `idx_file_processing_task_created` (`deleted`,`status`,`created_at`,`id`),
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_storage_space_key` (`storage_key`),
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
  UNIQUE KEY `uk_iam_credential_user_type` (`user_id`,`credential_type`,`version`),
  KEY `idx_iam_credential_type_status` (`credential_type`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_iam_device_user_device` (`user_id`,`device_id`),
  KEY `idx_iam_device_user_active` (`user_id`,`last_active_at`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_source` varchar(64) NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `detail_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_iam_event_user_created` (`user_id`,`created_at`),
  KEY `idx_iam_event_type_created` (`event_type`,`created_at`),
  KEY `idx_iam_event_ip_created` (`ip`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
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
  KEY `idx_iam_identity_user` (`user_id`,`identity_type`,`deleted`),
  KEY `idx_iam_identity_last_used` (`last_used_at`,`id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_iam_profile_user` (`user_id`),
  KEY `idx_iam_profile_real_name` (`real_name`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `iam_user_security_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_iam_security_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_delivery_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_id` bigint DEFAULT NULL,
  `channel` varchar(32) NOT NULL,
  `target_scope` varchar(32) NOT NULL,
  `target_user_id` bigint DEFAULT NULL,
  `target_user_name` varchar(64) DEFAULT NULL,
  `target_email` varchar(128) DEFAULT NULL,
  `title` varchar(128) NOT NULL,
  `content` text NOT NULL,
  `send_status` varchar(32) NOT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msg_delivery_log_channel_created` (`channel`,`created_at`),
  KEY `idx_msg_delivery_log_status_created` (`send_status`,`created_at`),
  KEY `idx_msg_delivery_log_notice` (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
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
  KEY `idx_msg_notice_type_status_created` (`notice_type`,`publish_status`,`created_at`),
  KEY `idx_msg_notice_target_created` (`target_user_id`,`created_at`),
  KEY `idx_msg_notice_target_role_created` (`target_role_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_notice_read` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `read_at` datetime NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_notice_read` (`notice_id`,`user_id`),
  KEY `idx_msg_notice_read_user_created` (`user_id`,`read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_outbox_event` (`source_type`,`event_type`,`event_key`),
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
  `payment_url` varchar(1024) DEFAULT NULL,
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_order_order_no` (`order_no`),
  UNIQUE KEY `uk_payment_order_idempotency_key` (`idempotency_key`),
  KEY `idx_payment_order_status` (`status`),
  KEY `idx_payment_order_provider` (`provider_code`,`provider_order_no`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_refund_refund_no` (`refund_no`),
  UNIQUE KEY `uk_payment_refund_idempotency_key` (`idempotency_key`),
  KEY `idx_payment_refund_status` (`status`),
  KEY `idx_payment_refund_order_no` (`order_no`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_platform_event_outbox_retry` (`dispatch_status`,`next_retry_at`),
  KEY `idx_platform_event_outbox_created_at` (`created_at`),
  KEY `idx_platform_event_outbox_event_key` (`event_key`),
  KEY `idx_platform_event_outbox_user_uuid` (`user_uuid`,`created_at`),
  KEY `idx_platform_event_outbox_owner_queue` (`source_type`,`created_at`,`id`,`dispatch_status`,`next_retry_at`,`deleted`),
  KEY `idx_platform_event_outbox_batch_claim` (`source_type`,`deleted`,`dispatch_status`,`next_retry_at`,`created_at`,`id`),
  KEY `idx_platform_event_outbox_claim_token` (`claim_token`)
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
  `created_by_name` varchar(128) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_platform_update_task_created_at` (`created_at`),
  KEY `idx_platform_update_task_status` (`status`),
  KEY `idx_platform_update_task_updater_task_id` (`updater_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plugin_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_event_outbox_event` (`event_type`,`event_key`),
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `dept_code` varchar(64) NOT NULL,
  `dept_name` varchar(128) NOT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_department_code` (`dept_code`),
  KEY `idx_sys_department_parent` (`parent_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_dict_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
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
  UNIQUE KEY `uk_sys_dict_item_value` (`dict_type_id`,`item_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
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
  UNIQUE KEY `uk_sys_dict_type_code` (`dict_code`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_sys_export_task_creator` (`created_by`,`created_at`),
  KEY `idx_sys_export_task_status` (`status`,`created_at`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_entry_namespace_key` (`namespace_id`,`message_key`),
  KEY `idx_sys_localization_entry_status` (`status`,`updated_at`),
  KEY `idx_sys_localization_entry_source` (`source_type`,`source_ref`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_language_locale` (`locale_code`),
  KEY `idx_sys_localization_language_status` (`status`,`sort_no`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_namespace_code` (`namespace_code`),
  KEY `idx_sys_localization_namespace_status` (`status`,`sort_no`)
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
  `published_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_release_locale_version` (`locale_code`,`release_version`),
  KEY `idx_sys_localization_release_locale_active` (`locale_code`,`active_flag`,`release_version`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_translation_entry_locale` (`entry_id`,`locale_code`),
  KEY `idx_sys_localization_translation_locale_status` (`locale_code`,`translation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_localization_usage_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entry_id` bigint NOT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UI',
  `source_ref` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_line` int DEFAULT NULL,
  `source_text` text COLLATE utf8mb4_unicode_ci,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_localization_usage_ref` (`entry_id`,`source_type`,`source_ref`,`source_line`),
  KEY `idx_sys_localization_usage_ref_entry` (`entry_id`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `icon` varchar(64) DEFAULT NULL,
  `sort_no` int NOT NULL DEFAULT '0',
  `permission_key` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_menu_code` (`menu_code`),
  KEY `idx_sys_menu_status` (`status`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
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
  UNIQUE KEY `uk_sys_permission_key` (`permission_key`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `resource_code` varchar(128) NOT NULL DEFAULT '*',
  `scope_type` varchar(32) NOT NULL DEFAULT 'SELF',
  `custom_dept_ids` varchar(1024) DEFAULT NULL,
  `custom_user_ids` varchar(1024) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_sensitive_word_normalized` (`normalized_word`,`deleted`),
  KEY `idx_sys_sensitive_word_enabled` (`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_work_order_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(160) NOT NULL,
  `detail_html` mediumtext NOT NULL,
  `priority` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `submitter_id` bigint NOT NULL,
  `submitter_name` varchar(128) DEFAULT NULL,
  `admin_reply` varchar(4000) DEFAULT NULL,
  `handled_by` bigint DEFAULT NULL,
  `handled_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_sys_work_order_status_updated` (`status`,`deleted`,`updated_at`),
  KEY `idx_sys_work_order_submitter_updated` (`submitter_id`,`deleted`,`updated_at`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_uuid` (`uuid`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `dept_id` bigint NOT NULL,
  `primary_flag` tinyint NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_department_rel` (`user_id`,`dept_id`),
  KEY `idx_sys_user_department_dept` (`dept_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_passkey_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `user_handle` varchar(128) NOT NULL,
  `credential_id` varchar(512) NOT NULL,
  `public_key_cose` text NOT NULL,
  `sign_count` bigint NOT NULL DEFAULT '0',
  `transports` varchar(255) DEFAULT NULL,
  `backup_eligible` tinyint NOT NULL DEFAULT '0',
  `backup_state` tinyint NOT NULL DEFAULT '0',
  `label` varchar(128) NOT NULL DEFAULT '閫氳瀵嗛挜',
  `last_used_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_passkey_credential_id` (`credential_id`),
  KEY `idx_passkey_user` (`user_id`,`deleted`),
  KEY `idx_passkey_user_handle` (`user_handle`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role_rel` (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_wechat_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `openid` varchar(128) NOT NULL,
  `unionid` varchar(128) DEFAULT NULL,
  `scope` varchar(255) DEFAULT NULL,
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_wechat_openid` (`openid`),
  UNIQUE KEY `uk_sys_user_wechat_unionid` (`unionid`),
  KEY `idx_sys_user_wechat_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_verification_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_verification_binding` (`user_id`,`factor_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_verification_challenge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `challenge_id` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_verification_challenge` (`challenge_id`)
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
  `member_count` int NOT NULL DEFAULT '1',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_code` (`team_code`,`deleted`),
  KEY `idx_team_owner` (`owner_user_id`,`deleted`),
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_activity_code` (`code`,`locale`,`deleted`),
  KEY `idx_aiadc_activity_status` (`status`,`deleted`,`sort`),
  KEY `idx_aiadc_activity_featured` (`featured`,`deleted`,`sort`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_competition_uuid` (`uuid`),
  UNIQUE KEY `uk_aiadc_competition_no` (`competition_no`,`deleted`),
  UNIQUE KEY `uk_aiadc_competition_code` (`code`,`locale`,`deleted`),
  KEY `idx_aiadc_competition_uuid_deleted` (`uuid`,`deleted`),
  KEY `idx_aiadc_competition_category` (`category`,`deleted`,`sort`),
  KEY `idx_aiadc_competition_status` (`status`,`deleted`,`sort`),
  KEY `idx_aiadc_competition_featured` (`featured`,`deleted`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_registration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_no` varchar(64) NOT NULL,
  `competition_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL,
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
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_registration_no` (`registration_no`,`deleted`),
  UNIQUE KEY `uk_competition_registration_participant` (`participant_no`,`deleted`),
  KEY `idx_competition_registration_owner` (`owner_user_id`,`deleted`,`created_at`),
  KEY `idx_competition_registration_competition` (`competition_id`,`status`,`deleted`),
  KEY `idx_competition_registration_payment` (`payment_order_no`,`deleted`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_template_code` (`template_code`,`deleted`),
  KEY `idx_certificate_template_status` (`status`,`deleted`,`updated_at`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_template_version` (`template_id`,`version`,`deleted`),
  KEY `idx_certificate_template_version_status` (`template_id`,`status`,`deleted`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_batch_no` (`batch_no`,`deleted`),
  KEY `idx_certificate_batch_template` (`template_id`,`template_version_id`,`deleted`),
  KEY `idx_certificate_batch_status` (`status`,`deleted`,`created_at`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_certificate_record_no` (`certificate_no`,`deleted`),
  UNIQUE KEY `uk_certificate_record_token` (`public_token`,`deleted`),
  KEY `idx_certificate_record_batch` (`batch_id`,`deleted`),
  KEY `idx_certificate_record_template` (`template_id`,`template_version_id`,`deleted`),
  KEY `idx_certificate_record_status` (`status`,`deleted`,`created_at`),
  KEY `idx_certificate_record_recipient` (`recipient_name`,`deleted`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_stage_code` (`competition_id`,`stage_code`,`deleted`),
  KEY `idx_competition_stage_competition` (`competition_id`,`deleted`,`sort`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_stage_form` (`stage_id`,`version`,`deleted`),
  KEY `idx_competition_stage_form_competition` (`competition_id`,`stage_id`,`deleted`)
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
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_config_set_version` (`competition_uuid`,`version`,`deleted`),
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_config_item_key` (`config_set_id`,`item_type`,`item_key`,`deleted`),
  KEY `idx_competition_config_item_lookup` (`competition_uuid`,`item_type`,`enabled`,`deleted`,`sort_order`),
  KEY `idx_competition_config_item_set` (`config_set_id`,`deleted`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition_config_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_uuid` char(36) NOT NULL,
  `operator_user_id` bigint NOT NULL DEFAULT '0',
  `operator_user_uuid` char(36) DEFAULT NULL,
  `action` varchar(64) NOT NULL,
  `module` varchar(64) NOT NULL,
  `detail_message` varchar(1000) DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_competition_config_audit_competition` (`competition_uuid`,`created_at`,`id`),
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_competition_submission_snapshot_lookup` (`competition_uuid`,`config_version`,`user_uuid`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `registration_material_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_id` bigint NOT NULL,
  `competition_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `form_version` int NOT NULL DEFAULT '1',
  `submitter_user_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'SUBMITTED',
  `submitted_at` datetime DEFAULT NULL,
  `locked_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_registration_material_submission` (`registration_id`,`stage_id`,`deleted`),
  KEY `idx_registration_material_submission_competition` (`competition_id`,`stage_id`,`deleted`)
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
  `account_status` varchar(32) DEFAULT NULL,
  `initial_password_reset_required` tinyint NOT NULL DEFAULT '0',
  `email` varchar(128) DEFAULT NULL,
  `avatar_url` varchar(512) DEFAULT NULL,
  `bio` varchar(1000) DEFAULT NULL,
  `tags` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'active',
  `sort` int NOT NULL DEFAULT '100',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_expert_code` (`code`,`deleted`),
  KEY `idx_aiadc_expert_status` (`status`,`deleted`,`sort`),
  KEY `idx_aiadc_expert_name` (`name`,`deleted`)
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aiadc_project_code` (`code`,`locale`,`deleted`),
  KEY `idx_aiadc_project_category` (`category`,`deleted`,`sort`),
  KEY `idx_aiadc_project_status` (`status`,`deleted`,`sort`),
  KEY `idx_aiadc_project_featured` (`featured`,`deleted`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
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
  `joined_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_member` (`team_id`,`user_id`,`deleted`),
  KEY `idx_team_member_user` (`user_id`,`status`,`deleted`),
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_invite_token` (`invite_token_hash`,`deleted`),
  UNIQUE KEY `uk_team_invite_code` (`invite_code`,`deleted`),
  KEY `idx_team_invite_team` (`team_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team_join_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `invite_id` bigint DEFAULT NULL,
  `apply_message` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `review_message` varchar(1000) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_join_pending` (`team_id`,`user_id`,`status`,`deleted`),
  KEY `idx_team_join_team` (`team_id`,`status`,`deleted`),
  KEY `idx_team_join_user` (`user_id`,`status`,`deleted`)
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
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_conversation_share_token` (`share_token`),
  KEY `idx_ai_conversation_share_conversation` (`conversation_id`,`is_deleted`),
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
ALTER TABLE `audit_login_log`
    ADD INDEX `idx_audit_login_user_result_recent` (`user_id`, `login_result`, `created_at`, `id`);
ALTER TABLE `audit_operation_log`
    ADD INDEX `idx_audit_operation_user_recent` (`username`, `created_at`, `id`);
ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_recent` (`publish_status`, `deleted`, `id`);
ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_target_user_recent` (`publish_status`, `deleted`, `target_user_id`, `id`);
ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_target_role_recent` (`publish_status`, `deleted`, `target_role_id`, `id`);
ALTER TABLE `sys_user_role`
    ADD INDEX `idx_sys_user_role_user_deleted` (`user_id`, `deleted`, `role_id`);
ALTER TABLE `sys_role_permission`
    ADD INDEX `idx_sys_role_permission_role_deleted_perm` (`role_id`, `deleted`, `permission_key`);
ALTER TABLE `sys_localization_entry`
    ADD INDEX `idx_sys_localization_entry_namespace_deleted_status` (`namespace_id`, `deleted`, `status`, `updated_at`);
ALTER TABLE `sys_localization_translation`
    ADD INDEX `idx_sys_localization_translation_locale_deleted_entry` (`locale_code`, `deleted`, `entry_id`);
ALTER TABLE `sys_localization_namespace`
    ADD INDEX `idx_sys_localization_namespace_deleted_sort` (`deleted`, `sort_no`, `id`);
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
ALTER TABLE `payment_event_outbox`
    ADD INDEX `idx_payment_outbox_owner_queue` (`deleted`, `source_type`, `status`, `next_retry_at`, `created_at`, `id`);
ALTER TABLE `payment_event_outbox`
    ADD INDEX `idx_payment_outbox_claim_token` (`claim_token`);
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
ALTER TABLE `plugin_event_outbox`
    ADD INDEX `idx_plugin_event_outbox_deleted_status_retry_created` (`deleted`, `status`, `next_retry_at`, `created_at`, `id`);
ALTER TABLE `plugin_event_outbox`
    ADD INDEX `idx_plugin_event_outbox_claim_token` (`claim_token`);
ALTER TABLE `msg_notice_read`
    ADD INDEX `idx_msg_notice_read_notice_user_deleted` (`notice_id`, `user_id`, `deleted`);
ALTER TABLE `file_object`
    ADD INDEX `idx_file_object_deleted_bucket` (`deleted`, `bucket`);
ALTER TABLE `file_object`
    ADD INDEX `idx_file_object_deleted_created_id` (`deleted`, `created_at`, `id`);
ALTER TABLE `file_storage_space`
    ADD INDEX `idx_file_storage_space_deleted_default_id` (`deleted`, `default_flag`, `id`);
CREATE INDEX `idx_sensitive_word_enabled`
    ON `sys_sensitive_word` (`enabled`, `deleted`, `normalized_word`);

-- Bootstrap protected administrator.
-- The BCrypt hashes below are for the initial password `123456`.
INSERT INTO `sys_permission` (
    `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    ('audit:login:view', '鏌ョ湅鐧诲綍鏃ュ織', 'audit', 'CORE', NULL, 0, 0, 0),
    ('audit:operation:view', '鏌ョ湅鎿嶄綔鏃ュ織', 'audit', 'CORE', NULL, 0, 0, 0),
    ('audit:view', '鏌ョ湅瀹¤涓績', 'audit', 'CORE', NULL, 0, 0, 0),
    ('aiadc:activity:view', '鏌ョ湅娲诲姩', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:activity:create', '鏂板缓娲诲姩', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:activity:update', '缂栬緫娲诲姩', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:activity:delete', '鍒犻櫎娲诲姩', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:competition:view', '鏌ョ湅璧涗簨', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:competition:create', '鏂板缓璧涗簨', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:competition:update', '缂栬緫璧涗簨', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:competition:delete', '鍒犻櫎璧涗簨', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:registration:view', '鏌ョ湅璧涗簨鎶ュ悕', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:registration:create', '鍒涘缓璧涗簨鎶ュ悕', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:registration:update', '缂栬緫璧涗簨鎶ュ悕', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:registration:pay', '鏀粯鎶ュ悕璐圭敤', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:material:view', '鏌ョ湅鎶ュ悕鏉愭枡', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:material:submit', '鎻愪氦鎶ュ悕鏉愭枡', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:stage:view', '鏌ョ湅璧涗簨闃舵', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:stage:manage', '绠＄悊璧涗簨闃舵', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-template:view', '鏌ョ湅璇佷功妯℃澘', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-template:create', '鏂板缓璇佷功妯℃澘', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-template:update', '缂栬緫璇佷功妯℃澘', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-template:publish', '鍙戝竷璇佷功妯℃澘', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-template:delete', '鍒犻櫎璇佷功妯℃澘', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-batch:view', '鏌ョ湅璇佷功鎵规', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-batch:create', '鐢熸垚璇佷功鎵规', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate-batch:download', '涓嬭浇璇佷功鎵规', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate:view', '鏌ョ湅璇佷功', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate:download', '涓嬭浇璇佷功', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate:regenerate', '閲嶆柊鐢熸垚璇佷功', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:certificate:revoke', '鎾ら攢璇佷功', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('expert:view', '鏌ョ湅涓撳', 'expert', 'CORE', NULL, 0, 0, 0),
    ('expert:create', '鏂板缓涓撳', 'expert', 'CORE', NULL, 0, 0, 0),
    ('expert:update', '缂栬緫涓撳', 'expert', 'CORE', NULL, 0, 0, 0),
    ('expert:delete', '鍒犻櫎涓撳', 'expert', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:view', '鏌ョ湅椤圭洰', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:create', '鏂板缓椤圭洰', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:update', '缂栬緫椤圭洰', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:delete', '鍒犻櫎椤圭洰', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('dashboard:view', 'View dashboard', 'dashboard', 'CORE', NULL, 0, 0, 0),
    ('download:center:view', '鏌ョ湅涓嬭浇涓績', 'download', 'CORE', NULL, 0, 0, 0),
    ('localization:view', '鏌ョ湅澶氳瑷€璁剧疆', 'localization', 'CORE', NULL, 0, 0, 0),
    ('payment:config:test', '娴嬭瘯鏀粯閰嶇疆', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:config:update', '缂栬緫鏀粯閰嶇疆', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:config:view', '鏌ョ湅鏀粯閰嶇疆', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:order:create', '鍒涘缓鏀粯璁㈠崟', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:order:view', '鏌ョ湅鏀粯璁㈠崟', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:refund:create', 'Create refund', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:refund:view', 'View refund', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:view', '璁块棶鏀粯涓績', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:webhook:retry', '閲嶈瘯鏀粯鍥炶皟', 'payment', 'CORE', NULL, 0, 0, 0),
    ('payment:webhook:view', '鏌ョ湅鏀粯鍥炶皟', 'payment', 'CORE', NULL, 0, 0, 0),
    ('plugin:management:view', '鏌ョ湅鎻掍欢绠＄悊', 'plugin', 'CORE', NULL, 0, 0, 0),
    ('plugin:sensitive-words:import', 'Import sensitive words', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    ('plugin:sensitive-words:manage', 'Manage sensitive words', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    ('plugin:sensitive-words:view', 'View sensitive words', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    ('plugin:work-order-feedback:create', '鎻愪氦宸ュ崟鍙嶉', 'plugin', 'PLUGIN', 'work-order-feedback', 0, 0, 0),
    ('plugin:work-order-feedback:manage', '澶勭悊宸ュ崟鍙嶉', 'plugin', 'PLUGIN', 'work-order-feedback', 0, 0, 0),
    ('plugin:work-order-feedback:view', '鏌ョ湅宸ュ崟鍙嶉', 'plugin', 'PLUGIN', 'work-order-feedback', 0, 0, 0),
    ('profile:view', '鏌ョ湅涓汉涓績', 'profile', 'CORE', NULL, 0, 0, 0),
    ('system:config:update', '缂栬緫绯荤粺閰嶇疆', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:config:view', '鏌ョ湅绯荤粺閰嶇疆', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:department:create', '鏂板缓閮ㄩ棬', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:department:delete', '鍒犻櫎閮ㄩ棬', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:department:update', '缂栬緫閮ㄩ棬', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:department:view', '鏌ョ湅閮ㄩ棬', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:dict:create', '鏂板缓瀛楀吀', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:dict:delete', '鍒犻櫎瀛楀吀', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:dict:update', '缂栬緫瀛楀吀', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:dict:view', '鏌ョ湅瀛楀吀', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:file:delete', '鍒犻櫎鏂囦欢', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:file:manage', '绠＄悊鏂囦欢', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:file:upload', '涓婁紶鏂囦欢', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:file:view', '鏌ョ湅鏂囦欢', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:menu:create', '鏂板缓鑿滃崟', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:menu:delete', '鍒犻櫎鑿滃崟', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:menu:status', '鍚仠鑿滃崟', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:menu:update', '缂栬緫鑿滃崟', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:menu:view', '鏌ョ湅鑿滃崟', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:monitor:docs:view', '鏌ョ湅鎺ュ彛鏂囨。', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:monitor:redis:view', '鏌ョ湅 Redis 鐩戞帶', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:monitor:service:view', '鏌ョ湅鏈嶅姟鐩戞帶', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:monitor:view', '鏌ョ湅绯荤粺鐩戞帶', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:notification:view', '鏌ョ湅娑堟伅閫氱煡', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:online-user:ban', '灏佺鍦ㄧ嚎鐢ㄦ埛', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:online-user:kick', '寮洪€€鍦ㄧ嚎鐢ㄦ埛', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:online-user:view', '鏌ョ湅鍦ㄧ嚎鐢ㄦ埛', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:create', '鏂板缓瑙掕壊', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:delete', '鍒犻櫎瑙掕壊', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:grant', '鍒嗛厤瑙掕壊', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:permissions', '閰嶇疆瑙掕壊鏉冮檺', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:update', '缂栬緫瑙掕壊', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:role:view', '鏌ョ湅瑙掕壊', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:update:check', 'Check system updates', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:update:install', '瀹夎绯荤粺鏇存柊', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:update:rollback', '鍥炴粴绯荤粺鏇存柊', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:update:view', '鏌ョ湅绯荤粺鏇存柊', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:create', '鏂板缓鐢ㄦ埛', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:delete', '鍒犻櫎鐢ㄦ埛', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:export', '瀵煎嚭鐢ㄦ埛', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:sensitive:view', '鏌ョ湅鐢ㄦ埛鏁忔劅淇℃伅', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:status', '鍚仠鐢ㄦ埛', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:update', '缂栬緫鐢ㄦ埛', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:user:view', '鏌ョ湅鐢ㄦ埛', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:verification:manage', '绠＄悊璁よ瘉璁剧疆', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:verification:view', '鏌ョ湅璁よ瘉璁剧疆', 'system', 'CORE', NULL, 0, 0, 0),
    ('system:view', '璁块棶绯荤粺绠＄悊', 'system', 'CORE', NULL, 0, 0, 0),
    ('team:view', '鏌ョ湅鍥㈤槦', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:create', '鏂板缓鍥㈤槦', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:update', '缂栬緫鍥㈤槦', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:delete', '鍒犻櫎鍥㈤槦', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:member:view', '鏌ョ湅鍥㈤槦鎴愬憳', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:member:invite', 'Invite team member', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:member:remove', '绉婚櫎鍥㈤槦鎴愬憳', 'team', 'CORE', NULL, 0, 0, 0),
    ('team:member:role-update', '璋冩暣鍥㈤槦鎴愬憳瑙掕壊', 'team', 'CORE', NULL, 0, 0, 0),
    ('user:center:view', '璁块棶鐢ㄦ埛涓績', 'user', 'CORE', NULL, 0, 0, 0)
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
    (-955, 0, 'dashboard.home', '棣栭〉', 'MENU', '/dashboard/home', '@/pages/dashboard/DashboardHomePage', 'DashboardOutlined', 0, 'dashboard:view', 'ENABLED', 0, 0, 0),
    (-956, -1100, 'files.download-center', '涓嬭浇涓績', 'MENU', '/data-management/download-center', '@/pages/files/DownloadCenter', 'DownloadOutlined', 6, 'download:center:view', 'ENABLED', 0, 0, 0),
    (-1100, 0, 'data.management.root', '鏁版嵁绠＄悊', 'CATALOG', '/data-management', 'redirect:/competitions/management', 'DatabaseOutlined', 3, NULL, 'ENABLED', 0, 0, 0),
    (-1101, -1100, 'data.query-center', '鏌ヨ涓績', 'CATALOG', '/data-management/query-center', 'redirect:/team/search', 'SearchOutlined', 7, NULL, 'ENABLED', 0, 0, 0),
    (-1041, 0, 'activity.root', '娲诲姩', 'CATALOG', '/activities', 'redirect:/activities/management', 'CalendarOutlined', 90, NULL, 'DISABLED', 0, 0, 1),
    (-1052, -1100, 'activity.activities', '娲诲姩绠＄悊', 'MENU', '/activities/management', '@/pages/activity', 'CalendarOutlined', 2, 'aiadc:activity:view', 'ENABLED', 0, 0, 0),
    (-1053, -1101, 'activity.search', '娲诲姩鏌ヨ', 'MENU', '/activities/search', '@/pages/activity', 'SearchOutlined', 3, 'aiadc:activity:view', 'ENABLED', 0, 0, 0),
    (-1043, -1052, 'activity.activities.create', '鏂板娲诲姩', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:activity:create', 'ENABLED', 0, 0, 0),
    (-1044, -1052, 'activity.activities.update', '缂栬緫娲诲姩', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:activity:update', 'ENABLED', 0, 0, 0),
    (-1045, -1052, 'activity.activities.delete', '鍒犻櫎娲诲姩', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:activity:delete', 'ENABLED', 0, 0, 0),
    (-1070, 0, 'competition.root', '璧涗簨', 'CATALOG', '/competitions', 'redirect:/competitions/register', 'TrophyOutlined', 4, NULL, 'ENABLED', 0, 0, 0),
    (-1071, -1100, 'competition.management', '璧涗簨绠＄悊', 'MENU', '/competitions/management', '@/pages/competition', 'TrophyOutlined', 1, 'aiadc:competition:view', 'ENABLED', 0, 0, 0),
    (-1075, -1070, 'competition.registration', '璧涗簨鎶ュ悕', 'MENU', '/competitions/register', '@/pages/competition', 'FormOutlined', 1, NULL, 'ENABLED', 0, 0, 0),
    (-1076, -1041, 'activity.registration', '活动报名', 'MENU', '/activities/register', '@/pages/competition', 'CalendarOutlined', 1, NULL, 'ENABLED', 0, 0, 0),
    (-1077, -1070, 'expert.application', '涓撳鐢宠', 'MENU', '/competitions/expert-apply', '@/pages/competition', 'SolutionOutlined', 3, NULL, 'ENABLED', 0, 0, 0),
    (-1091, 0, 'project.root', '椤圭洰', 'CATALOG', '/projects', 'redirect:/projects/management', 'ProjectOutlined', 92, NULL, 'DISABLED', 0, 0, 1),
    (-1092, -1100, 'project.management', '椤圭洰绠＄悊', 'MENU', '/projects/management', '@/pages/project', 'ProjectOutlined', 3, 'aiadc:project:view', 'ENABLED', 0, 0, 0),
    (-1096, -1101, 'project.search', '椤圭洰鏌ヨ', 'MENU', '/projects/search', '@/pages/project', 'SearchOutlined', 2, 'aiadc:project:view', 'ENABLED', 0, 0, 0),
    (-1093, -1092, 'project.management.create', '鏂板椤圭洰', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:project:create', 'ENABLED', 0, 0, 0),
    (-1094, -1092, 'project.management.update', '缂栬緫椤圭洰', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:project:update', 'ENABLED', 0, 0, 0),
    (-1095, -1092, 'project.management.delete', '鍒犻櫎椤圭洰', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:project:delete', 'ENABLED', 0, 0, 0),
    (-1110, -1100, 'payment.management', '鏀粯绠＄悊', 'MENU', '/payments/management', '@/pages/payment', 'CreditCardOutlined', 5, 'payment:order:view', 'ENABLED', 0, 0, 0),
    (-1111, -1101, 'payment.status', 'Payment status query', 'MENU', '/payments/status', '@/pages/payment', 'SearchOutlined', 4, 'payment:order:view', 'ENABLED', 0, 0, 0),
    (-1079, 0, 'certificate.root', '璇佷功绠＄悊', 'CATALOG', '/certificates', 'redirect:/certificates/templates', 'FileProtectOutlined', 5, NULL, 'ENABLED', 0, 0, 0),
    (-1080, -1079, 'certificate.templates', '璇佷功妯℃澘', 'MENU', '/certificates/templates', '@/pages/certificates/TemplatesPage', 'FileProtectOutlined', 1, 'aiadc:certificate-template:view', 'ENABLED', 0, 0, 0),
    (-1081, -1079, 'certificate.generate', '璇佷功鐢熸垚', 'MENU', '/certificates/generate', '@/pages/certificates/GeneratePage', 'FileDoneOutlined', 2, 'aiadc:certificate-batch:create', 'ENABLED', 0, 0, 0),
    (-1082, -1079, 'certificate.records', '璇佷功璁板綍', 'MENU', '/certificates/records', '@/pages/certificates/RecordsPage', 'AuditOutlined', 3, 'aiadc:certificate:view', 'ENABLED', 0, 0, 0),
    (-1072, -1071, 'competition.management.create', '鏂板璧涗簨', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:competition:create', 'ENABLED', 0, 0, 0),
    (-1073, -1071, 'competition.management.update', '缂栬緫璧涗簨', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:competition:update', 'ENABLED', 0, 0, 0),
    (-1074, -1071, 'competition.management.delete', '鍒犻櫎璧涗簨', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:competition:delete', 'ENABLED', 0, 0, 0),
    (-1083, -1080, 'certificate.templates.create', 'Create certificate template', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:certificate-template:create', 'ENABLED', 0, 0, 0),
    (-1084, -1080, 'certificate.templates.update', 'Update certificate template', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:certificate-template:update', 'ENABLED', 0, 0, 0),
    (-1085, -1080, 'certificate.templates.publish', 'Publish certificate template', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:certificate-template:publish', 'ENABLED', 0, 0, 0),
    (-1086, -1080, 'certificate.templates.delete', 'Archive certificate template', 'BUTTON', NULL, NULL, NULL, 4, 'aiadc:certificate-template:delete', 'ENABLED', 0, 0, 0),
    (-1087, -1081, 'certificate.generate.create', 'Generate certificates', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:certificate-batch:create', 'ENABLED', 0, 0, 0),
    (-1088, -1082, 'certificate.records.download', 'Download certificate', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:certificate:download', 'ENABLED', 0, 0, 0),
    (-1089, -1082, 'certificate.records.regenerate', 'Regenerate certificate', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:certificate:regenerate', 'ENABLED', 0, 0, 0),
    (-1090, -1082, 'certificate.records.revoke', 'Revoke certificate', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:certificate:revoke', 'ENABLED', 0, 0, 0),
    (-1060, 0, 'expert.root', 'Expert library', 'CATALOG', '/experts', 'redirect:/experts/management', 'SolutionOutlined', 6, NULL, 'ENABLED', 0, 0, 0),
    (-1061, -1060, 'expert.management', '涓撳绠＄悊', 'MENU', '/experts/management', '@/pages/expert', 'SolutionOutlined', 1, 'expert:view', 'ENABLED', 0, 0, 0),
    (-1065, -1060, 'expert.query', '涓撳鏌ヨ', 'MENU', '/experts/query', '@/pages/expert', 'SearchOutlined', 2, 'expert:view', 'ENABLED', 0, 0, 0),
    (-1062, -1061, 'expert.management.create', '鍒涘缓涓撳', 'BUTTON', NULL, NULL, NULL, 1, 'expert:create', 'ENABLED', 0, 0, 0),
    (-1063, -1061, 'expert.management.update', '缂栬緫涓撳', 'BUTTON', NULL, NULL, NULL, 2, 'expert:update', 'ENABLED', 0, 0, 0),
    (-1064, -1061, 'expert.management.delete', '鍒犻櫎涓撳', 'BUTTON', NULL, NULL, NULL, 3, 'expert:delete', 'ENABLED', 0, 0, 0),
    (-957, 0, 'team.root', '鍥㈤槦', 'CATALOG', '/team', 'redirect:/team/management', 'TeamOutlined', 93, 'team:view', 'DISABLED', 0, 0, 1),
    (-1040, -1100, 'team.management', '鍥㈤槦绠＄悊', 'MENU', '/team/management', '@/pages/team', 'TeamOutlined', 4, 'team:view', 'ENABLED', 0, 0, 0),
    (-1050, -1101, 'team.search', '鍥㈤槦鏌ヨ', 'MENU', '/team/search', '@/pages/team', 'SearchOutlined', 1, 'team:view', 'ENABLED', 0, 0, 0),
    (-958, -1040, 'team.create', '鍒涘缓鍥㈤槦', 'BUTTON', NULL, NULL, NULL, 1, 'team:create', 'ENABLED', 0, 0, 0),
    (-959, -1040, 'team.update', '缂栬緫鍥㈤槦', 'BUTTON', NULL, NULL, NULL, 2, 'team:update', 'ENABLED', 0, 0, 0),
    (-960, -1040, 'team.delete', '鍒犻櫎鍥㈤槦', 'BUTTON', NULL, NULL, NULL, 3, 'team:delete', 'ENABLED', 0, 0, 0),
    (-961, -1040, 'team.member.view', '鏌ョ湅鎴愬憳', 'BUTTON', NULL, NULL, NULL, 4, 'team:member:view', 'ENABLED', 0, 0, 0),
    (-962, -1040, 'team.member.invite', 'Invite member', 'BUTTON', NULL, NULL, NULL, 5, 'team:member:invite', 'ENABLED', 0, 0, 0),
    (-963, -1040, 'team.member.remove', '绉婚櫎鎴愬憳', 'BUTTON', NULL, NULL, NULL, 6, 'team:member:remove', 'ENABLED', 0, 0, 0),
    (-964, -1040, 'team.member.role-update', '鏇存柊鎴愬憳瑙掕壊', 'BUTTON', NULL, NULL, NULL, 7, 'team:member:role-update', 'ENABLED', 0, 0, 0),
    (-950, 0, 'user.center.root', '鐢ㄦ埛涓績', 'CATALOG', '/user-center', '@/layouts/SettingsLayout', 'TeamOutlined', 18, 'user:center:view', 'ENABLED', 0, 0, 0),
    (-951, -950, 'system.users', '鐢ㄦ埛绠＄悊', 'MENU', '/user-center/users', '@/pages/system/users', 'TeamOutlined', 21, 'system:user:view', 'ENABLED', 0, 0, 0),
    (-965, -951, 'system.users.create', '鍒涘缓鐢ㄦ埛', 'BUTTON', NULL, NULL, NULL, 1, 'system:user:create', 'ENABLED', 0, 0, 0),
    (-966, -951, 'system.users.update', '缂栬緫鐢ㄦ埛', 'BUTTON', NULL, NULL, NULL, 2, 'system:user:update', 'ENABLED', 0, 0, 0),
    (-967, -951, 'system.users.delete', '鍒犻櫎鐢ㄦ埛', 'BUTTON', NULL, NULL, NULL, 3, 'system:user:delete', 'ENABLED', 0, 0, 0),
    (-968, -951, 'system.users.export', '瀵煎嚭鐢ㄦ埛', 'BUTTON', NULL, NULL, NULL, 4, 'system:user:export', 'ENABLED', 0, 0, 0),
    (-954, -950, 'system.departments', '缁勭粐閮ㄩ棬', 'MENU', '/user-center/departments', '@/pages/system/departments', 'ApartmentOutlined', 22, 'system:department:view', 'ENABLED', 0, 0, 0),
    (-952, -950, 'system.online-users', '鍦ㄧ嚎鐢ㄦ埛', 'MENU', '/user-center/online-users', '@/pages/system/online-users', 'UserSwitchOutlined', 23, 'system:online-user:view', 'ENABLED', 0, 0, 0),
    (-953, -950, 'system.roles', '瑙掕壊绠＄悊', 'MENU', '/user-center/roles', '@/pages/system/roles', 'SafetyOutlined', 24, 'system:role:view', 'ENABLED', 0, 0, 0),
    (-969, -953, 'system.roles.create', '鍒涘缓瑙掕壊', 'BUTTON', NULL, NULL, NULL, 1, 'system:role:create', 'ENABLED', 0, 0, 0),
    (-970, -953, 'system.roles.update', '缂栬緫瑙掕壊', 'BUTTON', NULL, NULL, NULL, 2, 'system:role:update', 'ENABLED', 0, 0, 0),
    (-971, -953, 'system.roles.delete', '鍒犻櫎瑙掕壊', 'BUTTON', NULL, NULL, NULL, 3, 'system:role:delete', 'ENABLED', 0, 0, 0),
    (-972, -953, 'system.roles.grant', '鎺堟潈瑙掕壊', 'BUTTON', NULL, NULL, NULL, 4, 'system:role:grant', 'ENABLED', 0, 0, 0),
    (-940, 0, 'user.center.personal', '涓汉涓績', 'CATALOG', '/user-center/personal-center', '@/layouts/SettingsLayout', 'IdcardOutlined', 19, 'profile:view', 'ENABLED', 0, 0, 0),
    (-941, -940, 'profile.center', '涓汉璧勬枡', 'MENU', '/user-center/personal-center/profile', '@/pages/profile/Center', 'UserOutlined', 1, 'profile:view', 'ENABLED', 0, 0, 0),
    (-942, -940, 'files.my', '鎴戠殑鏂囦欢', 'MENU', '/user-center/personal-center/files', '@/pages/files/Center', 'FileOutlined', 2, 'system:file:view', 'ENABLED', 0, 0, 0),
    (-1000, 0, 'settings.root', '绯荤粺璁剧疆', 'CATALOG', '/settings', '@/layouts/SettingsLayout', 'SettingOutlined', 20, 'system:view', 'ENABLED', 0, 0, 0),
    (-1001, -1000, 'settings.menus', '鑿滃崟绠＄悊', 'MENU', '/settings/menus', '@/pages/settings/menus', 'AppstoreOutlined', 2, 'system:menu:view', 'ENABLED', 0, 0, 0),
    (-1020, -1001, 'settings.menus.create', '鍒涘缓鑿滃崟', 'BUTTON', NULL, NULL, NULL, 1, 'system:menu:create', 'ENABLED', 0, 0, 0),
    (-1021, -1001, 'settings.menus.update', '缂栬緫鑿滃崟', 'BUTTON', NULL, NULL, NULL, 2, 'system:menu:update', 'ENABLED', 0, 0, 0),
    (-1022, -1001, 'settings.menus.delete', '鍒犻櫎鑿滃崟', 'BUTTON', NULL, NULL, NULL, 3, 'system:menu:delete', 'ENABLED', 0, 0, 0),
    (-1002, -1000, 'settings.dicts', '瀛楀吀绠＄悊', 'MENU', '/settings/dicts', '@/pages/settings/dicts', 'DatabaseOutlined', 3, 'system:dict:view', 'ENABLED', 0, 0, 0),
    (-1023, -1002, 'settings.dicts.create', '鍒涘缓瀛楀吀', 'BUTTON', NULL, NULL, NULL, 1, 'system:dict:create', 'ENABLED', 0, 0, 0),
    (-1024, -1002, 'settings.dicts.update', '缂栬緫瀛楀吀', 'BUTTON', NULL, NULL, NULL, 2, 'system:dict:update', 'ENABLED', 0, 0, 0),
    (-1025, -1002, 'settings.dicts.delete', '鍒犻櫎瀛楀吀', 'BUTTON', NULL, NULL, NULL, 3, 'system:dict:delete', 'ENABLED', 0, 0, 0),
    (-1003, -1000, 'settings.profile-fields', '瀛楁绠＄悊', 'MENU', '/settings/profile-fields', '@/pages/settings/profile-fields', 'FormOutlined', 4, 'system:config:view', 'ENABLED', 0, 0, 0),
    (-1004, -1000, 'settings.personalization', '涓€у寲璁剧疆', 'MENU', '/settings/personalization', '@/pages/settings/personalization', 'SkinOutlined', 5, 'system:config:view', 'ENABLED', 0, 0, 0),
    (-1005, -1000, 'settings.security', '瀹夊叏璁剧疆', 'MENU', '/settings/security', '@/pages/settings/security', 'SafetyOutlined', 6, 'system:config:view', 'ENABLED', 0, 0, 0),
    (-1006, -1000, 'settings.verification', '楠岃瘉绠＄悊', 'MENU', '/settings/verification', '@/pages/settings/verification', 'SafetyOutlined', 7, 'system:verification:view', 'ENABLED', 0, 0, 0),
    (-1007, -1000, 'settings.payment', '鏀粯璁剧疆', 'MENU', '/settings/payment', '@/pages/settings/payment', 'CreditCardOutlined', 8, 'payment:view', 'ENABLED', 0, 0, 0),
    (-1012, -1000, 'settings.files', '鍏ㄧ珯鏂囦欢绠＄悊', 'MENU', '/settings/files/all', '@/pages/settings/files/Center', 'FolderOpenOutlined', 9, 'system:file:manage', 'ENABLED', 0, 0, 0),
    (-1008, -1000, 'settings.notifications', '閫氱煡涓績', 'MENU', '/settings/notifications', '@/pages/settings/notifications/index', 'NotificationOutlined', 9, 'system:notification:view', 'ENABLED', 0, 0, 0),
    (-1015, -1000, 'settings.monitoring', '绯荤粺鐩戞帶', 'MENU', '/settings/monitoring', '@/pages/settings/monitoring/index', 'FundOutlined', 10, 'system:monitor:view', 'ENABLED', 0, 0, 0),
    (-1013, -1000, 'settings.monitoring.api-docs', '鎺ュ彛鏂囨。', 'MENU', '/settings/api-docs', '@/pages/settings/monitoring/ApiDocs', 'FileTextOutlined', 11, 'system:monitor:docs:view', 'ENABLED', 0, 0, 0),
    (-1014, -1000, 'settings.monitoring.audit', '瀹¤涓績', 'MENU', '/settings/audit', '@/pages/settings/monitoring/Audit', 'AuditOutlined', 12, 'audit:view', 'ENABLED', 0, 0, 0),
    (-1009, -1000, 'settings.plugins', '鎻掍欢绠＄悊涓績', 'MENU', '/settings/plugins', '@/pages/settings/plugins', 'ApiOutlined', 10, 'plugin:management:view', 'ENABLED', 0, 0, 0),
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
      'download:center:view',
      'user:center:view',
      'profile:view',
      'system:file:view',
      'system:file:upload',
      'aiadc:activity:view',
      'aiadc:competition:view',
      'aiadc:registration:view',
      'aiadc:registration:create',
      'aiadc:registration:update',
      'aiadc:registration:pay',
      'aiadc:material:view',
      'aiadc:material:submit',
      'aiadc:stage:view',
      'expert:view',
      'expert:create',
      'expert:update',
      'expert:delete',
      'aiadc:project:view',
      'aiadc:project:create',
      'team:view',
      'team:create',
      'team:member:view'
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
    ('sys_user_gender', '鐢ㄦ埛鎬у埆', 'ENABLED', 1, '绯荤粺瀛楀吀锛氱敤鎴锋€у埆', 0, 0, 0),
    ('sys_user_status', 'User status', 'ENABLED', 1, 'System dictionary: user status', 0, 0, 0),
    ('sys_common_status', 'Common status', 'ENABLED', 1, 'System dictionary: common status', 0, 0, 0),
    ('sys_yes_no', '鏄惁', 'ENABLED', 1, 'System dictionary: yes/no', 0, 0, 0),
    ('sys_role_type', '瑙掕壊绫诲瀷', 'ENABLED', 1, 'System dictionary: role type', 0, 0, 0),
    ('sys_menu_type', '鑿滃崟绫诲瀷', 'ENABLED', 1, 'System dictionary: menu type', 0, 0, 0),
    ('sys_data_scope_type', '鏁版嵁鑼冨洿绫诲瀷', 'ENABLED', 1, 'System dictionary: data scope type', 0, 0, 0),
    ('team_member_role', '鍥㈤槦鎴愬憳瑙掕壊', 'ENABLED', 1, '鍥㈤槦妯″潡瀛楀吀', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `dict_name` = VALUES(`dict_name`),
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('team_type', '鍥㈤槦绫诲瀷', 'ENABLED', 1, '鍥㈤槦妯″潡瀛楀吀', 0, 0, 0),
    ('team_visibility', 'Team visibility', 'ENABLED', 1, '鍥㈤槦妯″潡瀛楀吀', 0, 0, 0),
    ('team_join_mode', '鍥㈤槦鍔犲叆鏂瑰紡', 'ENABLED', 1, '鍥㈤槦妯″潡瀛楀吀', 0, 0, 0),
    ('project_team_member_role', '椤圭洰鍥㈤槦鎴愬憳瑙掕壊', 'ENABLED', 1, 'Project dictionary: team member role', 0, 0, 0)
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
    ('aiadc_expert_title', '涓撳澶磋', 'ENABLED', 1, '涓撳搴撳瓧鍏革細涓撳澶磋', 0, 0, 0),
    ('aiadc_expert_position', '涓撳鑱屽姟', 'ENABLED', 1, '涓撳搴撳瓧鍏革細涓撳鑱屽姟', 0, 0, 0),
    ('aiadc_expert_expertise', '涓撳涓撲笟棰嗗煙', 'ENABLED', 1, '涓撳搴撳瓧鍏革細涓撲笟棰嗗煙', 0, 0, 0),
    ('aiadc_expert_tag', '涓撳鏍囩', 'ENABLED', 1, '涓撳搴撳瓧鍏革細涓撳鏍囩', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `dict_name` = VALUES(`dict_name`),
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_type` (`dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    ('aiadc_competition_category', '绔炶禌绫诲埆', 'ENABLED', 1, 'Competition dictionary: category', 0, 0, 0),
    ('aiadc_competition_level', '绔炶禌绾у埆', 'ENABLED', 1, 'Competition dictionary: level', 0, 0, 0),
    ('aiadc_activity_category', '娲诲姩鍒嗙被', 'ENABLED', 1, 'Competition dictionary: activity category', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `dict_name` = VALUES(`dict_name`),
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, '鏁欐巿', '鏁欐巿', 10, 'ENABLED', '涓撳澶磋', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_title' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ASSOCIATE_PROFESSOR', 'Associate Professor', 20, 'ENABLED', 'Expert title', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_title' AND `deleted` = 0
UNION ALL
SELECT `id`, 'RESEARCHER', 'Researcher', 30, 'ENABLED', 'Expert title', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_title' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SENIOR_ENGINEER', 'Senior Engineer', 40, 'ENABLED', 'Expert title', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_title' AND `deleted` = 0
UNION ALL
SELECT `id`, '琛屼笟涓撳', '琛屼笟涓撳', 50, 'ENABLED', '涓撳澶磋', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_title' AND `deleted` = 0
UNION ALL
SELECT `id`, '涓讳换', '涓讳换', 10, 'ENABLED', '涓撳鑱屽姟', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_position' AND `deleted` = 0
UNION ALL
SELECT `id`, '闄㈤暱', '闄㈤暱', 20, 'ENABLED', '涓撳鑱屽姟', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_position' AND `deleted` = 0
UNION ALL
SELECT `id`, '鎬诲伐绋嬪笀', '鎬诲伐绋嬪笀', 30, 'ENABLED', '涓撳鑱屽姟', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_position' AND `deleted` = 0
UNION ALL
SELECT `id`, '鎶€鏈礋璐ｄ汉', '鎶€鏈礋璐ｄ汉', 40, 'ENABLED', '涓撳鑱屽姟', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_position' AND `deleted` = 0
UNION ALL
SELECT `id`, 'INVESTMENT_PARTNER', 'Investment Partner', 50, 'ENABLED', 'Expert position', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_position' AND `deleted` = 0
UNION ALL
SELECT `id`, '浜哄伐鏅鸿兘', '浜哄伐鏅鸿兘', 10, 'ENABLED', '涓撳涓撲笟棰嗗煙', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_expertise' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SMART_MANUFACTURING', 'Smart Manufacturing', 20, 'ENABLED', 'Expert expertise', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_expertise' AND `deleted` = 0
UNION ALL
SELECT `id`, '浜т笟鎶曡祫', '浜т笟鎶曡祫', 30, 'ENABLED', '涓撳涓撲笟棰嗗煙', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_expertise' AND `deleted` = 0
UNION ALL
SELECT `id`, '鏁板瓧缁忔祹', '鏁板瓧缁忔祹', 40, 'ENABLED', '涓撳涓撲笟棰嗗煙', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_expertise' AND `deleted` = 0
UNION ALL
SELECT `id`, '绉戞妧鎴愭灉杞寲', '绉戞妧鎴愭灉杞寲', 50, 'ENABLED', '涓撳涓撲笟棰嗗煙', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_expertise' AND `deleted` = 0
UNION ALL
SELECT `id`, '璇勫涓撳', '璇勫涓撳', 10, 'ENABLED', '涓撳鏍囩', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_tag' AND `deleted` = 0
UNION ALL
SELECT `id`, '瀵煎笀', '瀵煎笀', 20, 'ENABLED', '涓撳鏍囩', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_tag' AND `deleted` = 0
UNION ALL
SELECT `id`, '浜т笟璧勬簮', '浜т笟璧勬簮', 30, 'ENABLED', '涓撳鏍囩', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_tag' AND `deleted` = 0
UNION ALL
SELECT `id`, 'FINANCING', 'Financing', 40, 'ENABLED', 'Expert tag', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_tag' AND `deleted` = 0
UNION ALL
SELECT `id`, 'TECHNICAL_CONSULTANT', 'Technical Consultant', 50, 'ENABLED', 'Expert tag', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_expert_tag' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'INNOVATION', 'Innovation', 10, 'ENABLED', 'Competition category', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'APPLICATION', 'Application', 20, 'ENABLED', 'Competition category', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SPECIAL', 'Special', 30, 'ENABLED', 'Competition category', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'OTHER', '鍏朵粬', 40, 'ENABLED', '绔炶禌绫诲埆', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_category' AND `deleted` = 0
UNION ALL
SELECT `id`, 'SCHOOL', '鏍＄骇', 10, 'ENABLED', '绔炶禌绾у埆', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_level' AND `deleted` = 0
UNION ALL
SELECT `id`, 'PROVINCE', '鐪佺骇', 20, 'ENABLED', '绔炶禌绾у埆', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_level' AND `deleted` = 0
UNION ALL
SELECT `id`, 'NATIONAL', 'National', 30, 'ENABLED', 'Competition level', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_level' AND `deleted` = 0
UNION ALL
SELECT `id`, 'INTERNATIONAL', 'International', 40, 'ENABLED', 'Competition level', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_competition_level' AND `deleted` = 0
UNION ALL
SELECT `id`, '璺紨娲诲姩', '璺紨娲诲姩', 10, 'ENABLED', '娲诲姩鍒嗙被', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_category' AND `deleted` = 0
UNION ALL
SELECT `id`, '鍒涗笟娌欓緳', '鍒涗笟娌欓緳', 20, 'ENABLED', '娲诲姩鍒嗙被', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_category' AND `deleted` = 0
UNION ALL
SELECT `id`, '鏀跨瓥瀹ｈ', '鏀跨瓥瀹ｈ', 30, 'ENABLED', '娲诲姩鍒嗙被', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_category' AND `deleted` = 0
UNION ALL
SELECT `id`, '鍩硅娲诲姩', '鍩硅娲诲姩', 40, 'ENABLED', '娲诲姩鍒嗙被', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_category' AND `deleted` = 0
UNION ALL
SELECT `id`, '鍏朵粬', '鍏朵粬', 50, 'ENABLED', '娲诲姩鍒嗙被', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'aiadc_activity_category' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item` (`dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT `id`, 'GENERAL', '閫氱敤鍥㈤槦', 10, 'ENABLED', '鍥㈤槦绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'DEV', 'Development Team', 20, 'ENABLED', 'Team type', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'COMPETITION', '绔炶禌鍥㈤槦', 30, 'ENABLED', '鍥㈤槦绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'CLUB', '绀惧洟缁勭粐', 40, 'ENABLED', '鍥㈤槦绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'OTHER', '鍏朵粬', 50, 'ENABLED', '鍥㈤槦绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'PRIVATE', 'Private', 10, 'ENABLED', 'Team visibility', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_visibility' AND `deleted` = 0
UNION ALL
SELECT `id`, 'PUBLIC', 'Public', 20, 'ENABLED', 'Team visibility', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_visibility' AND `deleted` = 0
UNION ALL
SELECT `id`, 'INVITE_ONLY', 'Invite Only', 10, 'ENABLED', 'Team join mode', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_join_mode' AND `deleted` = 0
UNION ALL
SELECT `id`, 'APPLY', '鐢宠鍔犲叆', 20, 'ENABLED', '鍥㈤槦鍔犲叆鏂瑰紡', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_join_mode' AND `deleted` = 0
UNION ALL
SELECT `id`, 'OPEN', 'Open', 30, 'ENABLED', 'Team join mode', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_join_mode' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ADMIN', 'Owner', 10, 'ENABLED', 'Project team member role', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'project_team_member_role' AND `deleted` = 0
UNION ALL
SELECT `id`, 'MEMBER', '缁勫憳', 20, 'ENABLED', '椤圭洰鍥㈤槦鎴愬憳瑙掕壊', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'project_team_member_role' AND `deleted` = 0
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
SELECT `id`, 'OTHER', '鍏朵粬', 30, 'ENABLED', '鐢ㄦ埛鎬у埆', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_user_gender' AND `deleted` = 0
UNION ALL
SELECT `id`, 'UNKNOWN', '鏈煡', 40, 'ENABLED', '鐢ㄦ埛鎬у埆', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_user_gender' AND `deleted` = 0
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
SELECT `id`, 'SYSTEM', '绯荤粺瑙掕壊', 10, 'ENABLED', '瑙掕壊绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_role_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'CUSTOM', 'Custom Role', 20, 'ENABLED', 'Role type', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_role_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'CATALOG', '鐩綍', 10, 'ENABLED', '鑿滃崟绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'MENU', '鑿滃崟', 20, 'ENABLED', '鑿滃崟绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'BUTTON', '鎸夐挳', 30, 'ENABLED', '鑿滃崟绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'LINK', '澶栭摼', 40, 'ENABLED', '鑿滃崟绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'ALL', '鍏ㄩ儴鏁版嵁', 10, 'ENABLED', '鏁版嵁鑼冨洿绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT `id`, 'DEPT_AND_CHILD', '鏈儴闂ㄥ強涓嬬骇', 20, 'ENABLED', '鏁版嵁鑼冨洿绫诲瀷', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
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
SELECT `id`, 'MEMBER', '鎴愬憳', 40, 'ENABLED', '鍥㈤槦鎴愬憳瑙掕壊', 0, 0, 0 FROM `sys_dict_type` WHERE `dict_code` = 'team_member_role' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user` (`id`, `uuid`, `username`, `nickname`, `real_name`, `password_hash`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, '00000000-0000-0000-0000-000000001001', 'admin', 'Administrator', 'Administrator', '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `uuid` = VALUES(`uuid`),
    `nickname` = VALUES(`nickname`),
    `real_name` = VALUES(`real_name`),
    `password_hash` = VALUES(`password_hash`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user_role` (`user_id`, `role_id`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 1001, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_config` (
    `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    ('branding.website-name', '绔欑偣鍚嶇О', 'Lumira', 'PLATFORM', 0, 'Website name shown in the console and browser title', 0, 0, 0),
    ('branding.website-favicon-url', '绔欑偣鍥炬爣鍦板潃', '', 'PLATFORM', 0, '娴忚鍣ㄦ爣绛鹃〉 icon 鍦板潃', 0, 0, 0),
    ('branding.website-logo-url', '绔欑偣 Logo 鍦板潃', '', 'PLATFORM', 0, '鎺у埗鍙板乏涓婅鍝佺墝 Logo 鍦板潃', 0, 0, 0),
    ('branding.login-background-url', '鐧诲綍椤佃儗鏅浘鍦板潃', '', 'PLATFORM', 0, '鐧诲綍椤佃儗鏅浘鍦板潃', 0, 0, 0),
    ('branding.github-link-enabled', 'GitHub link enabled', 'true', 'PLATFORM', 0, '鏄惁鏄剧ず椤堕儴 GitHub 鍥炬爣', 0, 0, 0),
    ('branding.github-link-url', 'GitHub 閾炬帴', '', 'PLATFORM', 0, '椤堕儴 GitHub 鍥炬爣璺宠浆鍦板潃', 0, 0, 0),
    ('branding.help-link-enabled', 'Help link enabled', 'true', 'PLATFORM', 0, '鏄惁鏄剧ず椤堕儴甯姪鍥炬爣', 0, 0, 0),
    ('branding.help-link-url', '甯姪閾炬帴', '', 'PLATFORM', 0, '椤堕儴甯姪鍥炬爣璺宠浆鍦板潃', 0, 0, 0),
    ('branding.company-name', '鍏徃鍚嶇О', 'Lumira', 'PLATFORM', 0, '椤佃剼鐗堟潈涓讳綋鍚嶇О', 0, 0, 0),
    ('branding.copyright-start-year', '鐗堟潈璧峰骞翠唤', CAST(YEAR(CURRENT_DATE()) AS CHAR), 'PLATFORM', 0, '椤佃剼鐗堟潈璧峰骞翠唤', 0, 0, 0),
    ('branding.footer-icp', '椤佃剼澶囨', '', 'PLATFORM', 0, '椤佃剼澶囨淇℃伅', 0, 0, 0),
    ('branding.footer-police-beian', '椤佃剼鍏畨澶囨', '', 'PLATFORM', 0, '椤佃剼鍏畨澶囨淇℃伅', 0, 0, 0),
    ('branding.footer-copyright', '椤佃剼鐗堟潈澹版槑', CONCAT('Copyright ', YEAR(CURRENT_DATE()), ' Lumira All Rights Reserved'), 'PLATFORM', 0, '椤佃剼鐗堟潈澹版槑', 0, 0, 0),
    ('agreement.user-agreement-markdown', '鐢ㄦ埛鍗忚', '', 'PLATFORM', 0, '鐢ㄦ埛鍗忚 Markdown', 0, 0, 0),
    ('agreement.privacy-agreement-markdown', '闅愮鍗忚', '', 'PLATFORM', 0, '闅愮鍗忚 Markdown', 0, 0, 0),
    ('watermark.enabled', 'Watermark enabled', 'false', 'PLATFORM', 0, 'Global watermark enabled flag', 0, 0, 0),
    ('watermark.mode', '姘村嵃妯″紡', 'TEXT', 'PLATFORM', 0, 'TEXT/IMAGE', 0, 0, 0),
    ('watermark.text-lines', '姘村嵃鏂囨湰', '', 'PLATFORM', 0, '澶氳鏂囨湰姘村嵃', 0, 0, 0),
    ('watermark.image-url', '姘村嵃鍥剧墖', '', 'PLATFORM', 0, '鍥剧墖姘村嵃 URL', 0, 0, 0),
    ('watermark.font-color', '瀛椾綋棰滆壊', 'rgba(0,0,0,0.15)', 'PLATFORM', 0, '瀛椾綋棰滆壊', 0, 0, 0),
    ('watermark.font-size', '瀛椾綋澶у皬', '14', 'PLATFORM', 0, '瀛椾綋澶у皬', 0, 0, 0),
    ('watermark.font-weight', '瀛椾綋绮楃粏', 'normal', 'PLATFORM', 0, '瀛椾綋绮楃粏', 0, 0, 0),
    ('watermark.rotate', '鏃嬭浆瑙掑害', '-22', 'PLATFORM', 0, '鏃嬭浆瑙掑害', 0, 0, 0),
    ('watermark.gap-x', '妯悜闂磋窛', '100', 'PLATFORM', 0, '妯悜闂磋窛', 0, 0, 0),
    ('watermark.gap-y', '绾靛悜闂磋窛', '100', 'PLATFORM', 0, '绾靛悜闂磋窛', 0, 0, 0),
    ('watermark.offset-x', '妯悜鍋忕Щ', '0', 'PLATFORM', 0, '妯悜鍋忕Щ', 0, 0, 0),
    ('watermark.offset-y', '绾靛悜鍋忕Щ', '0', 'PLATFORM', 0, '绾靛悜鍋忕Щ', 0, 0, 0),
    ('watermark.z-index', '灞傜骇', '9', 'PLATFORM', 0, 'z-index', 0, 0, 0),
    ('watermark.opacity', 'Watermark opacity', '0.15', 'PLATFORM', 0, 'Watermark opacity', 0, 0, 0),
    ('floating-window.api-docs-qr-enabled', 'API docs QR enabled', 'false', 'PLATFORM', 0, '鏄惁鍦ㄥ叏灞€鎮诞绐楀睍绀烘帴鍙ｆ枃妗ｄ簩缁寸爜鍏ュ彛', 0, 0, 0),
    ('floating-window.api-docs-qr-title', 'API docs QR title', '', 'PLATFORM', 0, 'API docs QR dialog title', 0, 0, 0),
    ('floating-window.api-docs-qr-image-url', 'API docs QR image', '', 'PLATFORM', 0, 'API docs floating entry QR image URL', 0, 0, 0),
    ('smtp.enabled', 'SMTP 閭閫氱煡鍚敤', 'false', 'PLATFORM', 0, '鏄惁鍚敤閭閫氱煡娓犻亾', 0, 0, 0),
    ('smtp.host', 'SMTP 涓绘満', '', 'PLATFORM', 0, '閭欢鏈嶅姟鍣ㄥ湴鍧€', 0, 0, 0),
    ('smtp.port', 'SMTP 绔彛', '25', 'PLATFORM', 0, 'SMTP server port', 0, 0, 0),
    ('smtp.username', 'SMTP username', '', 'PLATFORM', 0, 'SMTP login username', 0, 0, 0),
    ('smtp.password', 'SMTP 瀵嗙爜', '', 'PLATFORM', 0, 'SMTP 鐧诲綍瀵嗙爜', 0, 0, 0),
    ('smtp.from', '鍙戜欢浜哄湴鍧€', '', 'PLATFORM', 0, 'SMTP default sender', 0, 0, 0),
    ('smtp.auth-enabled', 'SMTP 璁よ瘉', 'true', 'PLATFORM', 0, '鏄惁鍚敤 SMTP AUTH', 0, 0, 0),
    ('smtp.starttls-enabled', 'SMTP STARTTLS', 'true', 'PLATFORM', 0, '鏄惁鍚敤 STARTTLS', 0, 0, 0),
    ('smtp.ssl-enabled', 'SMTP SSL', 'false', 'PLATFORM', 0, '鏄惁鍚敤 SSL', 0, 0, 0),
    ('notification.wechat-official.enabled', '寰俊鍏紬鍙烽€氱煡鍚敤', 'false', 'PLATFORM', 0, '鏄惁鍚敤寰俊鍏紬鍙?鏈嶅姟鍙锋ā鏉挎秷鎭€氱煡', 0, 0, 0),
    ('notification.wechat-official.app-id', '寰俊鍏紬鍙?AppID', '', 'PLATFORM', 0, '寰俊鍏紬鍙锋垨鏈嶅姟鍙?AppID', 0, 0, 0),
    ('notification.wechat-official.app-secret', '寰俊鍏紬鍙?AppSecret', '', 'PLATFORM', 0, '寰俊鍏紬鍙锋垨鏈嶅姟鍙?AppSecret', 0, 0, 0),
    ('notification.wechat-official.template-id', '寰俊鍏紬鍙锋ā鏉?ID', '', 'PLATFORM', 0, '鐢ㄤ簬绯荤粺閫氱煡鐨勫叕浼楀彿妯℃澘娑堟伅 ID', 0, 0, 0),
    ('notification.wechat-official.detail-url', '寰俊鍏紬鍙烽€氱煡璇︽儏閾炬帴', '', 'PLATFORM', 0, 'System URL opened after template message click; can be empty', 0, 0, 0),
    ('verification.totp.enabled', '2FA 鍚敤', 'true', 'PLATFORM', 0, '鏄惁鍚敤 2FA 鐧诲綍鏂瑰紡', 0, 0, 0),
    ('verification.password-login.enabled', '瀵嗙爜鐧诲綍', 'true', 'PLATFORM', 0, '鏄惁鍚敤璐﹀彿瀵嗙爜鐧诲綍', 0, 0, 0),
    ('verification.email-login.enabled', 'Email code login enabled', 'false', 'PLATFORM', 0, 'Whether email code login is enabled', 0, 0, 0),
    ('verification.login-mode.order', '鐧诲綍鏂瑰紡鎺掑簭', 'password,sms,email,wechat,passkey', 'PLATFORM', 0, '鐧诲綍椤靛垎娈垫帶鍒跺櫒灞曠ず椤哄簭', 0, 0, 0),
    ('verification.sms.enabled', 'SMS verification enabled', 'false', 'PLATFORM', 0, 'Whether SMS verification service is enabled', 0, 0, 0),
    ('verification.sms.provider', '鐭俊楠岃瘉鐮佹湇鍔″晢', 'aliyun', 'PLATFORM', 0, '鐭俊楠岃瘉鐮佹湇鍔℃彁渚涙柟', 0, 0, 0),
    ('verification.sms.sign-name', '鐭俊绛惧悕', '', 'PLATFORM', 0, 'SMS verification sign name', 0, 0, 0),
    ('verification.sms.template-code', '鐭俊妯℃澘缂栫爜', '', 'PLATFORM', 0, 'SMS verification template code', 0, 0, 0),
    ('verification.sms.access-key-id', '鐭俊 Access Key ID', '', 'PLATFORM', 0, '鐭俊楠岃瘉鐮佽闂瘑閽?ID', 0, 0, 0),
    ('verification.sms.access-key-secret', '鐭俊 Access Key Secret', '', 'PLATFORM', 0, '鐭俊楠岃瘉鐮佽闂瘑閽?Secret', 0, 0, 0),
    ('verification.sms.endpoint', '鐭俊鏈嶅姟鍦板潃', '', 'PLATFORM', 0, 'SMS verification endpoint', 0, 0, 0),
    ('verification.sms.region', '鐭俊鏈嶅姟鍦板煙', '', 'PLATFORM', 0, 'SMS verification region', 0, 0, 0),
    ('verification.wechat-login.enabled', '寰俊鐧诲綍鍚敤', 'false', 'PLATFORM', 0, '鏄惁鍚敤寰俊鎵爜鐧诲綍', 0, 0, 0),
    ('verification.wechat-login.app-id', '寰俊 AppID', '', 'PLATFORM', 0, '寰俊寮€鏀惧钩鍙扮綉绔欏簲鐢?AppID', 0, 0, 0),
    ('verification.wechat-login.app-secret', '寰俊 AppSecret', '', 'PLATFORM', 0, '寰俊寮€鏀惧钩鍙扮綉绔欏簲鐢?AppSecret', 0, 0, 0),
    ('verification.wechat-login.redirect-uri', '寰俊鐧诲綍鍥炶皟鍦板潃', '', 'PLATFORM', 0, '寰俊寮€鏀惧钩鍙版巿鏉冨洖璋冨湴鍧€', 0, 0, 0),
    ('verification.wechat-login.state-expire-minutes', '寰俊鐧诲綍鐘舵€佹湁鏁堟湡', '10', 'PLATFORM', 0, '寰俊鐧诲綍 state 缂撳瓨鏈夋晥鏈燂紝鍗曚綅鍒嗛挓', 0, 0, 0),
    ('verification.passkey.enabled', '閫氳瀵嗛挜鍚敤', 'false', 'PLATFORM', 0, '鏄惁鍚敤閫氳瀵嗛挜鐧诲綍', 0, 0, 0),
    ('verification.passkey.passwordless-enabled', 'Passkey passwordless enabled', 'false', 'PLATFORM', 0, '鏄惁鍏佽鍙戠幇寮忓嚟鎹棤璐﹀彿鐧诲綍', 0, 0, 0),
    ('verification.passkey.self-binding-enabled', '閫氳瀵嗛挜鑷姪缁戝畾', 'false', 'PLATFORM', 0, '鏄惁鍏佽鐢ㄦ埛鍦ㄤ釜浜轰腑蹇冭嚜鍔╃粦瀹氶€氳瀵嗛挜', 0, 0, 0),
    ('verification.passkey.rp-id', '閫氳瀵嗛挜 RP ID', '', 'PLATFORM', 0, 'WebAuthn RP ID', 0, 0, 0),
    ('verification.passkey.rp-name', '閫氳瀵嗛挜 RP 鍚嶇О', '', 'PLATFORM', 0, 'WebAuthn RP 鏄剧ず鍚嶇О', 0, 0, 0),
    ('verification.passkey.allowed-origins', '閫氳瀵嗛挜鍏佽 Origin', '', 'PLATFORM', 0, 'WebAuthn 鍏佽鐨勫墠绔?Origin', 0, 0, 0),
    ('verification.passkey.challenge-ttl-seconds', '閫氳瀵嗛挜 Challenge TTL', '120', 'PLATFORM', 0, 'WebAuthn challenge TTL seconds', 0, 0, 0),
    ('security.idle-timeout-seconds', '绌洪棽瓒呮椂鏃堕棿', '1800', 'PLATFORM', 1, 'Session idle timeout seconds', 0, 0, 0),
    ('security.access-token-expire-seconds', 'Access Token 杩囨湡鏃堕棿', '1800', 'PLATFORM', 1, 'Access token TTL seconds', 0, 0, 0),
    ('security.refresh-token-expire-seconds', 'Refresh Token 鍒锋柊鏃堕檺', '604800', 'PLATFORM', 1, 'Refresh token TTL seconds', 0, 0, 0),
    ('security.allow-multi-device-login', 'Multi-device login', '1', 'PLATFORM', 1, 'Whether the same account can be online on multiple devices', 0, 0, 0),
    ('security.captcha-enabled', 'Captcha enabled', '0', 'PLATFORM', 1, '鏄惁寮€鍚櫥褰曟椂鐨勪汉鏈洪獙璇佺爜', 0, 0, 0),
    ('security.captcha-type', 'Captcha type', 'IMAGE', 'PLATFORM', 1, '楠岃瘉鐮佺被鍨嬶細IMAGE/SLIDER', 0, 0, 0),
    ('security.login-defense-window-minutes', '鐧诲綍闃插尽缁熻绐楀彛', '5', 'PLATFORM', 1, 'Login defense statistics window in minutes', 0, 0, 0),
    ('security.login-max-validation-attempts', 'Max validation attempts', '100', 'PLATFORM', 1, 'Maximum verification or login validation attempts in the window', 0, 0, 0),
    ('security.login-max-failure-count', 'Max login failure count', '10', 'PLATFORM', 1, 'Maximum login failures allowed in the statistics window', 0, 0, 0),
    ('security.verification-code-expire-seconds', '楠岃瘉鐮佹湁鏁堟湡', '300', 'PLATFORM', 1, '鐭俊/閭楠岃瘉鐮佺殑鏈夋晥绉掓暟', 0, 0, 0),
    ('security.verification-code-cooldown-seconds', 'Verification code cooldown', '60', 'PLATFORM', 1, '鍚屼竴璐﹀彿鍚屼竴楠岃瘉鐮佹笭閬撳啀娆″彂閫佸墠闇€瑕佺瓑寰呯殑绉掓暟', 0, 0, 0),
    ('security.password-min-length', 'Password min length', '6', 'PLATFORM', 1, '鐢ㄦ埛瀵嗙爜鍏佽鐨勬渶灏戝瓧绗︽暟', 0, 0, 0),
    ('security.password-require-uppercase', '瀵嗙爜蹇呴』鍖呭惈澶у啓瀛楁瘝', '0', 'PLATFORM', 1, '寮哄埗瀵嗙爜鍖呭惈 A-Z', 0, 0, 0),
    ('security.password-require-lowercase', '瀵嗙爜蹇呴』鍖呭惈灏忓啓瀛楁瘝', '0', 'PLATFORM', 1, '寮哄埗瀵嗙爜鍖呭惈 a-z', 0, 0, 0),
    ('security.password-require-special-character', '瀵嗙爜蹇呴』鍖呭惈鐗规畩瀛楃', '0', 'PLATFORM', 1, '寮哄埗瀵嗙爜鍖呭惈鐗规畩瀛楃', 0, 0, 0),
    ('security.password-allow-consecutive-characters', '鍏佽杩炵画瀛楃', '1', 'PLATFORM', 1, 'Whether consecutive password characters are allowed', 0, 0, 0),
    ('profile.field.system.overrides', 'System profile field metadata overrides', '[]', 'PLATFORM', 0, 'Stores editable labels, descriptions, placeholders, and groups for built-in profile fields', 0, 0, 0),
    ('profile.field.custom.definitions', 'Custom profile field definitions', '[]', 'PLATFORM', 0, 'Custom profile field definitions', 0, 0, 0),
    ('profile.field.avatar.visible', 'Avatar visible', 'true', 'PLATFORM', 0, 'Profile avatar visible flag', 0, 0, 0),
    ('profile.field.avatar.weight', '澶村儚璇勫垎鏉冮噸', '10', 'PLATFORM', 0, '涓汉涓績澶村儚瀛楁璇勫垎鏉冮噸', 0, 0, 0),
    ('profile.field.avatar.required', '澶村儚 required', 'false', 'PLATFORM', 0, 'Profile avatar required flag', 0, 0, 0),
    ('profile.field.avatar.sort', '澶村儚 sort', '10', 'PLATFORM', 0, '涓汉涓績澶村儚瀛楁鎺掑簭', 0, 0, 0),
    ('profile.field.real-name.visible', 'Real name visible', 'true', 'PLATFORM', 0, 'Profile real name visible flag', 0, 0, 0),
    ('profile.field.real-name.weight', '濮撳悕璇勫垎鏉冮噸', '15', 'PLATFORM', 0, '涓汉涓績濮撳悕瀛楁璇勫垎鏉冮噸', 0, 0, 0),
    ('profile.field.real-name.required', '濮撳悕 required', 'false', 'PLATFORM', 0, 'Profile real name required flag', 0, 0, 0),
    ('profile.field.real-name.sort', '濮撳悕 sort', '20', 'PLATFORM', 0, '涓汉涓績濮撳悕瀛楁鎺掑簭', 0, 0, 0),
    ('profile.field.mobile.visible', 'Mobile visible', 'true', 'PLATFORM', 0, 'Profile mobile visible flag', 0, 0, 0),
    ('profile.field.mobile.weight', 'Mobile weight', '15', 'PLATFORM', 0, 'Profile mobile field score weight', 0, 0, 0),
    ('profile.field.mobile.required', 'Mobile required', 'false', 'PLATFORM', 0, 'Profile mobile required flag', 0, 0, 0),
    ('profile.field.mobile.sort', 'Mobile sort', '30', 'PLATFORM', 0, 'Profile mobile field sort order', 0, 0, 0),
    ('profile.field.email.visible', 'Email visible', 'true', 'PLATFORM', 0, 'Profile email visible flag', 0, 0, 0),
    ('profile.field.email.weight', '閭璇勫垎鏉冮噸', '15', 'PLATFORM', 0, '涓汉涓績閭瀛楁璇勫垎鏉冮噸', 0, 0, 0),
    ('profile.field.email.required', '閭 required', 'false', 'PLATFORM', 0, 'Profile email required flag', 0, 0, 0),
    ('profile.field.email.sort', '閭 sort', '40', 'PLATFORM', 0, '涓汉涓績閭瀛楁鎺掑簭', 0, 0, 0),
    ('profile.field.birth-month.visible', 'Birth month visible', 'true', 'PLATFORM', 0, 'Profile birth month visible flag', 0, 0, 0),
    ('profile.field.birth-month.weight', '鍑虹敓骞存湀璇勫垎鏉冮噸', '10', 'PLATFORM', 0, '涓汉涓績鍑虹敓骞存湀瀛楁璇勫垎鏉冮噸', 0, 0, 0),
    ('profile.field.birth-month.required', '鍑虹敓骞存湀 required', 'false', 'PLATFORM', 0, 'Profile birth month required flag', 0, 0, 0),
    ('profile.field.birth-month.sort', '鍑虹敓骞存湀 sort', '50', 'PLATFORM', 0, '涓汉涓績鍑虹敓骞存湀瀛楁鎺掑簭', 0, 0, 0),
    ('profile.field.gender.visible', 'Gender visible', 'true', 'PLATFORM', 0, 'Profile gender visible flag', 0, 0, 0),
    ('profile.field.gender.weight', '鎬у埆璇勫垎鏉冮噸', '10', 'PLATFORM', 0, '涓汉涓績鎬у埆瀛楁璇勫垎鏉冮噸', 0, 0, 0),
    ('profile.field.gender.required', '鎬у埆 required', 'false', 'PLATFORM', 0, 'Profile gender required flag', 0, 0, 0),
    ('profile.field.gender.sort', '鎬у埆 sort', '60', 'PLATFORM', 0, '涓汉涓績鎬у埆瀛楁鎺掑簭', 0, 0, 0),
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

INSERT INTO `iam_user_identity` (`user_id`, `identity_type`, `identifier`, `identifier_normalized`, `verified`, `primary_identity`, `status`, `deleted`)
VALUES (1001, 'USERNAME', 'admin', 'admin', 1, 1, 'ENABLED', 0)
ON DUPLICATE KEY UPDATE
    `user_id` = VALUES(`user_id`),
    `identifier` = VALUES(`identifier`),
    `verified` = VALUES(`verified`),
    `primary_identity` = VALUES(`primary_identity`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `iam_user_credential` (`user_id`, `credential_type`, `credential_secret`, `algorithm`, `version`, `status`, `deleted`)
VALUES (1001, 'PASSWORD', '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te', 'BCRYPT', 1, 'ENABLED', 0)
ON DUPLICATE KEY UPDATE
    `credential_secret` = VALUES(`credential_secret`),
    `algorithm` = VALUES(`algorithm`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `iam_user_profile` (`user_id`, `nickname`, `real_name`, `locale`, `deleted`)
VALUES (1001, 'Administrator', 'Administrator', 'zh-CN', 0)
ON DUPLICATE KEY UPDATE
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
VALUES (1002, '00000000-0000-0000-0000-000000001002', 'user', 'Common User', 'Common User', '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `uuid` = VALUES(`uuid`),
    `nickname` = VALUES(`nickname`),
    `real_name` = VALUES(`real_name`),
    `password_hash` = VALUES(`password_hash`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user_role` (`user_id`, `role_id`, `created_by`, `updated_by`, `deleted`)
VALUES (1002, 1002, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `iam_user` (`id`, `user_no`, `display_name`, `status`, `user_type`, `source`, `deleted`)
VALUES (1002, 'user', 'Common User', 'ENABLED', 'REGISTERED', 'BOOTSTRAP_SQL', 0)
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `status` = VALUES(`status`),
    `user_type` = VALUES(`user_type`),
    `source` = VALUES(`source`),
    `deleted` = 0;

INSERT INTO `iam_user_identity` (`user_id`, `identity_type`, `identifier`, `identifier_normalized`, `verified`, `primary_identity`, `status`, `deleted`)
VALUES (1002, 'USERNAME', 'user', 'user', 1, 1, 'ENABLED', 0)
ON DUPLICATE KEY UPDATE
    `user_id` = VALUES(`user_id`),
    `identifier` = VALUES(`identifier`),
    `verified` = VALUES(`verified`),
    `primary_identity` = VALUES(`primary_identity`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `iam_user_credential` (`user_id`, `credential_type`, `credential_secret`, `algorithm`, `version`, `status`, `deleted`)
VALUES (1002, 'PASSWORD', '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te', 'BCRYPT', 1, 'ENABLED', 0)
ON DUPLICATE KEY UPDATE
    `credential_secret` = VALUES(`credential_secret`),
    `algorithm` = VALUES(`algorithm`),
    `status` = VALUES(`status`),
    `deleted` = 0;

INSERT INTO `iam_user_profile` (`user_id`, `nickname`, `real_name`, `locale`, `deleted`)
VALUES (1002, 'Common User', 'Common User', 'zh-CN', 0)
ON DUPLICATE KEY UPDATE
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
        'pluginName', '宸ュ崟鍙嶉',
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
    'work-order-feedback', '1.0.0', 'plugin.work-order-feedback', '宸ュ崟鍙嶉', '/plugins/work-order-feedback', 'CustomerServiceOutlined',
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
    ('work-order-feedback', '1.0.0', 'plugin:work-order-feedback:view', '鏌ョ湅宸ュ崟鍙嶉', 'work-order-feedback', 0, 0, 0),
    ('work-order-feedback', '1.0.0', 'plugin:work-order-feedback:create', '鎻愪氦宸ュ崟鍙嶉', 'work-order-feedback', 0, 0, 0),
    ('work-order-feedback', '1.0.0', 'plugin:work-order-feedback:manage', '澶勭悊宸ュ崟鍙嶉', 'work-order-feedback', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

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

SET FOREIGN_KEY_CHECKS = 1;





