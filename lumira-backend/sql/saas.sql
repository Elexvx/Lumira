-- Lumira consolidated database initialization script.
-- Generated from all service migration modules while Flyway is disabled before first production launch.
-- Includes minimum bootstrap data required by infrastructure components such as XXL-JOB Admin.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `ai_conversation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
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
  UNIQUE KEY `uk_ai_conversation_code` (`tenant_id`,`conversation_code`),
  KEY `idx_ai_conversation_owner` (`tenant_id`,`owner_user_id`,`is_pinned`,`latest_message_at`,`is_deleted`),
  KEY `idx_ai_conversation_employee` (`tenant_id`,`employee_id`,`latest_message_at`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_employee` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
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
  UNIQUE KEY `uk_ai_employee_username` (`tenant_id`,`username`,`is_deleted`),
  KEY `idx_ai_employee_enabled_sort` (`tenant_id`,`enabled`,`sort_order`,`id`,`is_deleted`),
  KEY `idx_ai_employee_llm` (`tenant_id`,`default_llm_service_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_employee_knowledge_base` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `employee_id` bigint unsigned NOT NULL,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_knowledge_base_rel` (`tenant_id`,`employee_id`,`knowledge_base_id`),
  KEY `idx_ai_employee_knowledge_base_employee` (`tenant_id`,`employee_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_employee_skill` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `skill_code` varchar(128) NOT NULL,
  `permission_mode` varchar(32) NOT NULL DEFAULT 'deny',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_skill` (`tenant_id`,`employee_id`,`skill_code`,`is_deleted`),
  KEY `idx_ai_employee_skill_code` (`tenant_id`,`skill_code`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_base` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
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
  UNIQUE KEY `uk_ai_knowledge_base_code` (`tenant_id`,`kb_code`),
  UNIQUE KEY `uk_ai_knowledge_base_owner_name` (`tenant_id`,`owner_user_id`,`name`,`is_deleted`),
  KEY `idx_ai_knowledge_base_tenant_status` (`tenant_id`,`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_owner` (`tenant_id`,`owner_user_id`,`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_access` (`tenant_id`,`owner_user_id`,`visibility_scope`,`status`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_base_acl` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
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
  UNIQUE KEY `uk_ai_knowledge_acl_subject` (`tenant_id`,`knowledge_base_id`,`subject_type`,`subject_id`,`permission`,`is_deleted`),
  KEY `idx_ai_knowledge_acl_subject` (`tenant_id`,`subject_type`,`subject_id`,`permission`,`is_deleted`),
  KEY `idx_ai_knowledge_acl_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_chunk` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
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
  UNIQUE KEY `uk_ai_knowledge_chunk_index` (`tenant_id`,`document_id`,`chunk_index`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_document` (`tenant_id`,`document_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_vector` (`tenant_id`,`knowledge_base_id`,`is_deleted`,`embedding_model`,`update_time`),
  KEY `idx_ai_knowledge_chunk_acl` (`tenant_id`,`knowledge_base_id`,`document_id`,`is_deleted`,`update_time`,`id`),
  FULLTEXT KEY `ft_ai_knowledge_chunk_search_text` (`search_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_base_stats` (
  `tenant_id` bigint unsigned NOT NULL,
  `knowledge_base_id` bigint unsigned NOT NULL,
  `document_count` bigint unsigned NOT NULL DEFAULT '0',
  `chunk_count` bigint unsigned NOT NULL DEFAULT '0',
  `vector_indexed_chunk_count` bigint unsigned NOT NULL DEFAULT '0',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`tenant_id`,`knowledge_base_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_knowledge_document` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
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
  KEY `idx_ai_knowledge_document_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_document_file` (`tenant_id`,`file_id`),
  KEY `idx_ai_knowledge_document_index_retry` (`status`,`is_deleted`,`index_next_retry_at`,`update_time`,`id`),
  KEY `idx_ai_knowledge_document_status` (`tenant_id`,`knowledge_base_id`,`status`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_llm_model` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `llm_service_id` bigint unsigned NOT NULL,
  `model_code` varchar(128) NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_llm_model_code` (`tenant_id`,`llm_service_id`,`model_code`,`is_deleted`),
  KEY `idx_ai_llm_model_service` (`tenant_id`,`llm_service_id`,`enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_llm_service` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
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
  UNIQUE KEY `uk_ai_llm_service_code` (`tenant_id`,`code`,`is_deleted`),
  KEY `idx_ai_llm_service_provider` (`tenant_id`,`provider`,`enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned NOT NULL,
  `role` varchar(32) NOT NULL,
  `content` longtext NOT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_conversation` (`tenant_id`,`conversation_id`,`create_time`,`is_deleted`)
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
  `tenant_id` bigint unsigned NOT NULL,
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
  KEY `idx_ai_tool_audit_tenant_created` (`tenant_id`,`create_time`),
  KEY `idx_ai_tool_audit_employee` (`tenant_id`,`employee_id`,`create_time`),
  KEY `idx_ai_tool_audit_skill` (`tenant_id`,`skill_code`,`result_status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_tool_call_plan` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
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
  KEY `idx_ai_tool_plan_owner` (`tenant_id`,`owner_user_id`,`status`,`expires_at`),
  KEY `idx_ai_tool_plan_conversation` (`tenant_id`,`conversation_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_tool_policy` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
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
  KEY `idx_ai_tool_policy_tenant_enabled` (`tenant_id`,`enabled`,`is_deleted`),
  KEY `idx_ai_tool_policy_tool` (`tenant_id`,`tool_code`,`enabled`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
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
  KEY `idx_audit_login_tenant_created` (`tenant_id`,`created_at`),
  KEY `idx_audit_login_user_created` (`tenant_id`,`user_id`,`created_at`),
  KEY `idx_audit_login_result_created` (`tenant_id`,`login_result`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
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
  KEY `idx_audit_operation_tenant_created` (`tenant_id`,`created_at`),
  KEY `idx_audit_operation_user_created` (`tenant_id`,`user_id`,`created_at`),
  KEY `idx_audit_operation_module_created` (`tenant_id`,`module_name`,`created_at`),
  KEY `idx_audit_operation_result_created` (`tenant_id`,`result_status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ddd_read_model_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `context_name` varchar(64) NOT NULL,
  `scope` varchar(128) NOT NULL,
  `version` bigint NOT NULL DEFAULT '1',
  `last_event_key` varchar(255) DEFAULT NULL,
  `rebuilt_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ddd_read_model_version_scope` (`tenant_id`,`context_name`,`scope`),
  KEY `idx_ddd_read_model_version_context` (`context_name`,`updated_at`),
  KEY `idx_ddd_read_model_version_event_key` (`last_event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `file_object` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_file_object_key` (`tenant_id`,`object_key`),
  KEY `idx_file_object_department` (`tenant_id`,`department_id`,`deleted`),
  KEY `idx_file_object_visibility` (`tenant_id`,`visibility_scope`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file_processing_artifact` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_file_processing_artifact` (`tenant_id`,`file_id`,`artifact_type`),
  KEY `idx_file_processing_artifact_file` (`tenant_id`,`file_id`,`deleted`),
  KEY `idx_file_processing_artifact_type` (`tenant_id`,`artifact_type`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file_processing_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_file_processing_task_file_type` (`tenant_id`,`file_id`,`task_type`),
  KEY `idx_file_processing_task_status_retry` (`status`,`next_retry_at`,`priority`,`created_at`),
  KEY `idx_file_processing_task_file` (`tenant_id`,`file_id`,`deleted`),
  KEY `idx_file_processing_task_queue` (`deleted`,`status`,`next_retry_at`,`priority`,`created_at`,`id`),
  KEY `idx_file_processing_task_tenant_created` (`tenant_id`,`deleted`,`status`,`created_at`,`id`),
  KEY `idx_file_processing_batch_claim` (`deleted`,`status`,`next_retry_at`,`priority`,`created_at`,`id`),
  KEY `idx_file_processing_claim_token` (`claim_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file_storage_space` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_file_storage_space_key` (`tenant_id`,`storage_key`),
  KEY `idx_file_storage_space_default` (`tenant_id`,`default_flag`,`deleted`)
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
  `tenant_id` bigint NOT NULL,
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
  KEY `idx_msg_delivery_log_tenant_channel_created` (`tenant_id`,`channel`,`created_at`),
  KEY `idx_msg_delivery_log_tenant_status_created` (`tenant_id`,`send_status`,`created_at`),
  KEY `idx_msg_delivery_log_notice` (`tenant_id`,`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_notice` (
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

CREATE TABLE `msg_notice_read` (
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

CREATE TABLE `payment_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `source_type` varchar(64) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `event_key` varchar(128) NOT NULL,
  `payload_json` longtext NOT NULL,
  `status` varchar(32) NOT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error_message` varchar(512) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_outbox_event` (`tenant_id`,`source_type`,`event_type`,`event_key`),
  KEY `idx_payment_outbox_status` (`tenant_id`,`status`,`next_retry_at`),
  KEY `idx_payment_outbox_created_at` (`tenant_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_payment_order_tenant_order_no` (`tenant_id`,`order_no`),
  UNIQUE KEY `uk_payment_order_tenant_idempotency_key` (`tenant_id`,`idempotency_key`),
  KEY `idx_payment_order_tenant_status` (`tenant_id`,`status`),
  KEY `idx_payment_order_provider` (`tenant_id`,`provider_code`,`provider_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_provider_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_payment_provider_config_tenant_provider` (`tenant_id`,`provider_code`),
  KEY `idx_payment_provider_config_tenant_deleted` (`tenant_id`,`deleted`),
  KEY `idx_payment_provider_config_provider` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_payment_refund_tenant_refund_no` (`tenant_id`,`refund_no`),
  UNIQUE KEY `uk_payment_refund_tenant_idempotency_key` (`tenant_id`,`idempotency_key`),
  KEY `idx_payment_refund_tenant_status` (`tenant_id`,`status`),
  KEY `idx_payment_refund_order_no` (`tenant_id`,`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_webhook_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_payment_webhook_event_tenant_provider_event` (`tenant_id`,`provider_code`,`event_id`),
  KEY `idx_payment_webhook_event_nonce` (`tenant_id`,`provider_code`,`nonce`),
  KEY `idx_payment_webhook_event_status` (`tenant_id`,`processed`,`retry_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `platform_event_outbox` (
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
  KEY `idx_platform_event_outbox_tenant_status` (`tenant_id`,`dispatch_status`),
  KEY `idx_platform_event_outbox_retry` (`dispatch_status`,`next_retry_at`),
  KEY `idx_platform_event_outbox_created_at` (`created_at`),
  KEY `idx_platform_event_outbox_event_key` (`event_key`),
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
  `tenant_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `event_type` varchar(128) NOT NULL,
  `event_key` varchar(191) NOT NULL,
  `payload_json` json NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime DEFAULT NULL,
  `last_error_message` varchar(512) DEFAULT NULL,
  `created_by` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_event_outbox_event` (`tenant_id`,`event_type`,`event_key`),
  KEY `idx_plugin_event_outbox_status` (`tenant_id`,`status`,`next_retry_at`),
  KEY `idx_plugin_event_outbox_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sys_config` (
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

CREATE TABLE `sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_sys_department_code` (`tenant_id`,`dept_code`),
  KEY `idx_sys_department_parent` (`tenant_id`,`parent_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_dict_item` (
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

CREATE TABLE `sys_dict_type` (
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

CREATE TABLE `sys_export_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  KEY `idx_sys_export_task_tenant_creator` (`tenant_id`,`created_by`,`created_at`),
  KEY `idx_sys_export_task_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE `sys_permission` (
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

CREATE TABLE `sys_plugin_tenant` (
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
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_sys_role_code` (`tenant_id`,`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_sys_role_data_scope_resource` (`tenant_id`,`role_id`,`resource_code`),
  KEY `idx_sys_role_data_scope_role` (`tenant_id`,`role_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_permission` (
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

CREATE TABLE `sys_sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_sys_sensitive_word_tenant_normalized` (`tenant_id`,`normalized_word`,`deleted`),
  KEY `idx_sys_sensitive_word_tenant_enabled` (`tenant_id`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_tenant` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_code` varchar(64) NOT NULL,
  `tenant_name` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `remark` varchar(512) DEFAULT NULL,
  `created_by` bigint unsigned DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint unsigned DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_tenant_code` (`tenant_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user` (
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

CREATE TABLE `sys_user_department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `dept_id` bigint NOT NULL,
  `primary_flag` tinyint NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_department_rel` (`tenant_id`,`user_id`,`dept_id`),
  KEY `idx_sys_user_department_dept` (`tenant_id`,`dept_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_passkey_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_passkey_credential_id` (`credential_id`),
  KEY `idx_passkey_user` (`tenant_id`,`user_id`,`deleted`),
  KEY `idx_passkey_user_handle` (`user_handle`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_role` (
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

CREATE TABLE `sys_user_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_tenant_rel` (`tenant_id`,`user_id`),
  KEY `idx_sys_user_tenant_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_tenant_profile` (
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
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_sys_verification_binding` (`tenant_id`,`user_id`,`factor_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_verification_challenge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `challenge_id` varchar(64) NOT NULL,
  `tenant_id` bigint NOT NULL,
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
  `tenant_id` bigint NOT NULL,
  `ancestor_id` bigint NOT NULL,
  `descendant_id` bigint NOT NULL,
  `depth` int NOT NULL,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_closure` (`tenant_id`,`ancestor_id`,`descendant_id`),
  KEY `idx_dept_closure_descendant` (`tenant_id`,`descendant_id`),
  KEY `idx_dept_closure_ancestor_depth` (`tenant_id`,`ancestor_id`,`depth`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `security_audit_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
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
  KEY `idx_security_audit_tenant_created_at` (`tenant_id`,`created_at`),
  KEY `idx_security_audit_request_id` (`request_id`),
  KEY `idx_security_audit_source_ip_created_at` (`source_ip`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_team_code` (`tenant_id`,`team_code`,`deleted`),
  KEY `idx_team_owner` (`tenant_id`,`owner_user_id`,`deleted`),
  KEY `idx_team_status` (`tenant_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role` varchar(32) NOT NULL DEFAULT 'MEMBER',
  `member_alias` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `invited_by` bigint DEFAULT NULL,
  `joined_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_member` (`team_id`,`user_id`,`deleted`),
  KEY `idx_team_member_user` (`tenant_id`,`user_id`,`status`,`deleted`),
  KEY `idx_team_member_team` (`tenant_id`,`team_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team_invite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  UNIQUE KEY `uk_team_invite_code` (`tenant_id`,`invite_code`,`deleted`),
  KEY `idx_team_invite_team` (`tenant_id`,`team_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `team_join_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
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
  KEY `idx_team_join_team` (`tenant_id`,`team_id`,`status`,`deleted`),
  KEY `idx_team_join_user` (`tenant_id`,`user_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_message_attachment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
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
  KEY `idx_ai_message_attachment_message` (`tenant_id`,`message_id`,`is_deleted`),
  KEY `idx_ai_message_attachment_conversation` (`tenant_id`,`conversation_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_subject (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
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
    unique key uk_iam_subject_tenant_type_ref (tenant_id, subject_type, ref_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_permission (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
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
    unique key uk_iam_permission_key (tenant_id, permission_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_subject_role (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    subject_id bigint not null,
    role_id bigint not null,
    created_by bigint default 0,
    created_at datetime default current_timestamp,
    updated_by bigint default 0,
    updated_at datetime default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    unique key uk_iam_subject_role (tenant_id, subject_id, role_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_delegation_grant (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
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
    key idx_delegation_delegate (tenant_id, delegate_subject_id, deleted),
    key idx_delegation_delegator (tenant_id, delegator_subject_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_employee_tool_grant (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
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
    unique key uk_ai_employee_tool_grant (tenant_id, employee_id, tool_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_employee_tool_grant_dept (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    grant_id bigint not null,
    dept_id bigint not null,
    created_at datetime default current_timestamp,
    deleted tinyint not null default 0,
    key idx_ai_employee_tool_grant_dept (tenant_id, grant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_employee_tool_grant_user (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
    grant_id bigint not null,
    user_id bigint not null,
    created_at datetime default current_timestamp,
    deleted tinyint not null default 0,
    key idx_ai_employee_tool_grant_user (tenant_id, grant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_tool_execution_audit (
    id bigint primary key auto_increment,
    tenant_id bigint not null,
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
    key idx_ai_tool_execution_audit_tenant_created (tenant_id, created_at),
    key idx_ai_tool_execution_audit_employee (tenant_id, employee_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Consolidated indexes from archived Flyway migrations.
ALTER TABLE `sys_config`
    ADD INDEX `idx_sys_config_scope_key_tenant_deleted` (`config_scope`, `config_key`, `tenant_id`, `deleted`);
ALTER TABLE `sys_plugin_tenant`
    ADD INDEX `idx_sys_plugin_tenant_current` (`tenant_id`, `enabled`, `deleted`, `plugin_code`, `plugin_version`);
ALTER TABLE `audit_login_log`
    ADD INDEX `idx_audit_login_user_result_recent` (`tenant_id`, `user_id`, `login_result`, `created_at`, `id`);
ALTER TABLE `audit_operation_log`
    ADD INDEX `idx_audit_operation_tenant_user_recent` (`tenant_id`, `username`, `created_at`, `id`);
ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_recent` (`tenant_id`, `publish_status`, `deleted`, `id`);
ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_target_user_recent` (`tenant_id`, `publish_status`, `deleted`, `target_user_id`, `id`);
ALTER TABLE `msg_notice`
    ADD INDEX `idx_msg_notice_visible_target_role_recent` (`tenant_id`, `publish_status`, `deleted`, `target_role_id`, `id`);
ALTER TABLE `sys_user_role`
    ADD INDEX `idx_sys_user_role_tenant_user_deleted` (`tenant_id`, `user_id`, `deleted`, `role_id`);
ALTER TABLE `sys_role_permission`
    ADD INDEX `idx_sys_role_permission_tenant_role_deleted_perm` (`tenant_id`, `role_id`, `deleted`, `permission_key`);
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
    ADD INDEX `idx_payment_webhook_event_tenant_provider_nonce_deleted_received` (`tenant_id`, `provider_code`, `nonce`, `deleted`, `received_at`);
ALTER TABLE `payment_webhook_event`
    ADD INDEX `idx_payment_webhook_event_tenant_provider_event_deleted_id` (`tenant_id`, `provider_code`, `event_id`, `deleted`, `id`);
ALTER TABLE `payment_provider_config`
    ADD INDEX `idx_payment_provider_config_tenant_provider_deleted_id` (`tenant_id`, `provider_code`, `deleted`, `id`);
ALTER TABLE `payment_event_outbox`
    ADD INDEX `idx_payment_outbox_owner_queue` (`deleted`, `source_type`, `status`, `next_retry_at`, `created_at`, `id`);
ALTER TABLE `sys_plugin_definition`
    ADD INDEX `idx_sys_plugin_definition_deleted_status_sort_code` (`deleted`, `status`, `sort_no`, `plugin_code`);
ALTER TABLE `sys_plugin_version`
    ADD INDEX `idx_sys_plugin_version_plugin_deleted_status_created` (`plugin_code`, `deleted`, `created_at`);
ALTER TABLE `sys_plugin_tenant`
    ADD INDEX `idx_sys_plugin_tenant_tenant_deleted_enabled_code` (`tenant_id`, `deleted`, `enabled`, `plugin_code`);
ALTER TABLE `sys_plugin_tenant`
    ADD INDEX `idx_sys_plugin_tenant_code_deleted_enabled` (`plugin_code`, `deleted`, `enabled`);
ALTER TABLE `sys_plugin_runtime_log`
    ADD INDEX `idx_sys_plugin_runtime_log_code_deleted_id` (`plugin_code`, `deleted`, `id`);
ALTER TABLE `sys_plugin_menu_rel`
    ADD INDEX `idx_sys_plugin_menu_rel_code_version_deleted_sort` (`plugin_code`, `plugin_version`, `deleted`, `sort_no`, `id`);
ALTER TABLE `sys_plugin_permission_rel`
    ADD INDEX `idx_sys_plugin_permission_rel_code_version_deleted` (`plugin_code`, `plugin_version`, `deleted`, `id`);
ALTER TABLE `plugin_event_outbox`
    ADD INDEX `idx_plugin_event_outbox_deleted_status_retry_created` (`deleted`, `status`, `next_retry_at`, `created_at`, `id`);
ALTER TABLE `msg_notice_read`
    ADD INDEX `idx_msg_notice_read_tenant_notice_user_deleted` (`tenant_id`, `notice_id`, `user_id`, `deleted`);
ALTER TABLE `file_object`
    ADD INDEX `idx_file_object_tenant_deleted_bucket` (`tenant_id`, `deleted`, `bucket`);
ALTER TABLE `file_object`
    ADD INDEX `idx_file_object_tenant_deleted_created_id` (`tenant_id`, `deleted`, `created_at`, `id`);
ALTER TABLE `file_storage_space`
    ADD INDEX `idx_file_storage_space_tenant_deleted_default_id` (`tenant_id`, `deleted`, `default_flag`, `id`);
CREATE INDEX `idx_sensitive_word_tenant_enabled`
    ON `sys_sensitive_word` (`tenant_id`, `enabled`, `deleted`, `normalized_word`);
CREATE INDEX `idx_ai_knowledge_base_acl_subject`
    ON `ai_knowledge_base_acl` (`tenant_id`, `knowledge_base_id`, `subject_type`, `subject_id`, `permission`, `is_deleted`);
CREATE INDEX `idx_ai_tool_policy_runtime`
    ON `ai_tool_policy` (`tenant_id`, `enabled`, `is_deleted`, `tool_code`, `action_type`, `risk_level`);

-- Bootstrap platform tenant and protected administrator.
-- The BCrypt hashes below are for the initial password `123456`.
INSERT INTO `sys_tenant` (`id`, `tenant_code`, `tenant_name`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 'platform', 'Lumira Platform', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `tenant_name` = VALUES(`tenant_name`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_permission` (
    `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    (1001, 'ai:chat:send', 'ai:chat:send', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:employee:create', 'ai:employee:create', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:employee:delete', 'ai:employee:delete', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:employee:skills', 'ai:employee:skills', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:employee:status', 'ai:employee:status', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:employee:update', 'ai:employee:update', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:bind', 'ai:knowledge:bind', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:create', 'ai:knowledge:create', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:delete', 'ai:knowledge:delete', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:document:delete', 'ai:knowledge:document:delete', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:document:index', 'ai:knowledge:document:index', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:document:upload', 'ai:knowledge:document:upload', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:query', 'ai:knowledge:query', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:share', 'ai:knowledge:share', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:update', 'ai:knowledge:update', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:knowledge:view', 'ai:knowledge:view', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:llm:create', 'ai:llm:create', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:llm:delete', 'ai:llm:delete', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:llm:status', 'ai:llm:status', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:llm:update', 'ai:llm:update', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:tool:execute', 'ai:tool:execute', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:tool:invoke', 'ai:tool:invoke', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:tool:view', 'ai:tool:view', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'ai:view', 'ai:view', 'ai', 'CORE', NULL, 0, 0, 0),
    (1001, 'audit:login:view', 'audit:login:view', 'audit', 'CORE', NULL, 0, 0, 0),
    (1001, 'audit:operation:view', 'audit:operation:view', 'audit', 'CORE', NULL, 0, 0, 0),
    (1001, 'audit:view', 'audit:view', 'audit', 'CORE', NULL, 0, 0, 0),
    (1001, 'dashboard:view', 'dashboard:view', 'dashboard', 'CORE', NULL, 0, 0, 0),
    (1001, 'download:center:view', 'download:center:view', 'download', 'CORE', NULL, 0, 0, 0),
    (1001, 'localization:view', 'localization:view', 'localization', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:config:test', 'payment:config:test', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:config:update', 'payment:config:update', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:config:view', 'payment:config:view', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:order:create', 'payment:order:create', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:order:view', 'payment:order:view', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:refund:create', 'payment:refund:create', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:refund:view', 'payment:refund:view', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:view', 'payment:view', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:webhook:retry', 'payment:webhook:retry', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'payment:webhook:view', 'payment:webhook:view', 'payment', 'CORE', NULL, 0, 0, 0),
    (1001, 'plugin:management:view', 'plugin:management:view', 'plugin', 'CORE', NULL, 0, 0, 0),
    (1001, 'plugin:sensitive-words:import', 'plugin:sensitive-words:import', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    (1001, 'plugin:sensitive-words:manage', 'plugin:sensitive-words:manage', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    (1001, 'plugin:sensitive-words:view', 'plugin:sensitive-words:view', 'plugin', 'PLUGIN', 'sensitive-words', 0, 0, 0),
    (1001, 'profile:view', 'profile:view', 'profile', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:config:update', 'system:config:update', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:config:view', 'system:config:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:department:create', 'system:department:create', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:department:delete', 'system:department:delete', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:department:update', 'system:department:update', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:department:view', 'system:department:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:dict:create', 'system:dict:create', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:dict:delete', 'system:dict:delete', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:dict:update', 'system:dict:update', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:dict:view', 'system:dict:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:file:delete', 'system:file:delete', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:file:manage', 'system:file:manage', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:file:upload', 'system:file:upload', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:file:view', 'system:file:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:menu:create', 'system:menu:create', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:menu:delete', 'system:menu:delete', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:menu:status', 'system:menu:status', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:menu:update', 'system:menu:update', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:menu:view', 'system:menu:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:monitor:docs:view', 'system:monitor:docs:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:monitor:redis:view', 'system:monitor:redis:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:monitor:service:view', 'system:monitor:service:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:monitor:view', 'system:monitor:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:notification:view', 'system:notification:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:online-user:ban', 'system:online-user:ban', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:online-user:kick', 'system:online-user:kick', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:online-user:view', 'system:online-user:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:create', 'system:role:create', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:delete', 'system:role:delete', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:grant', 'system:role:grant', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:permissions', 'system:role:permissions', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:update', 'system:role:update', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:role:view', 'system:role:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:update:check', 'system:update:check', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:update:install', 'system:update:install', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:update:rollback', 'system:update:rollback', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:update:view', 'system:update:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:create', 'system:user:create', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:delete', 'system:user:delete', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:export', 'system:user:export', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:sensitive:view', 'system:user:sensitive:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:status', 'system:user:status', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:update', 'system:user:update', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:user:view', 'system:user:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:verification:manage', 'system:verification:manage', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:verification:view', 'system:verification:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'system:view', 'system:view', 'system', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:view', 'team:view', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:create', 'team:create', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:update', 'team:update', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:delete', 'team:delete', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:member:view', 'team:member:view', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:member:invite', 'team:member:invite', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:member:remove', 'team:member:remove', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'team:member:role-update', 'team:member:role-update', 'team', 'CORE', NULL, 0, 0, 0),
    (1001, 'user:center:view', 'user:center:view', 'user', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `source_type` = VALUES(`source_type`),
    `plugin_code` = VALUES(`plugin_code`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `default_home_path`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 1001, 'ADMIN', 'Administrator', 'SYSTEM', '/dashboard/home', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `role_type` = VALUES(`role_type`),
    `default_home_path` = VALUES(`default_home_path`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `default_home_path`, `created_by`, `updated_by`, `deleted`)
VALUES (1002, 1001, 'commonuser', 'Common User', 'BUSINESS', '/dashboard/home', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `role_type` = VALUES(`role_type`),
    `default_home_path` = VALUES(`default_home_path`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_data_scope` (`tenant_id`, `role_id`, `resource_code`, `scope_type`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 1001, '*', 'ALL', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `scope_type` = VALUES(`scope_type`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_data_scope` (`tenant_id`, `role_id`, `resource_code`, `scope_type`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 1002, '*', 'SELF', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `scope_type` = VALUES(`scope_type`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

DELETE FROM `sys_role_permission`
WHERE `tenant_id` = 1001 AND `role_id` = 1001 AND `permission_key` = '*';

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 1001, p.`permission_key`, 0, 0, 0
FROM `sys_permission` p
WHERE p.`tenant_id` = 1001 AND p.`deleted` = 0
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 1002, p.`permission_key`, 0, 0, 0
FROM `sys_permission` p
WHERE p.`tenant_id` = 1001
  AND p.`deleted` = 0
  AND p.`permission_key` IN (
      'dashboard:view',
      'download:center:view',
      'user:center:view',
      'profile:view',
      'system:file:view',
      'system:file:upload',
      'ai:view',
      'ai:chat:send',
      'ai:knowledge:view',
      'team:view'
  )
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_config` (
    `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`,
    `created_by`, `updated_by`, `deleted`
)
VALUES (1001, 'auth.default-registration-role-code', 'Default registration role', 'commonuser', 'PLATFORM', 1, 'Default role code assigned to newly registered users', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `config_name` = VALUES(`config_name`),
    `config_value` = VALUES(`config_value`),
    `config_scope` = VALUES(`config_scope`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_type` (`tenant_id`, `dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    (1001, 'sys_user_gender', '用户性别', 'ENABLED', 1, '系统字典：用户性别', 0, 0, 0),
    (1001, 'sys_user_status', '用户状态', 'ENABLED', 1, '系统字典：用户状态', 0, 0, 0),
    (1001, 'sys_common_status', '通用状态', 'ENABLED', 1, '系统字典：通用状态', 0, 0, 0),
    (1001, 'sys_yes_no', '是否', 'ENABLED', 1, '系统字典：是否', 0, 0, 0),
    (1001, 'sys_role_type', '角色类型', 'ENABLED', 1, '系统字典：角色类型', 0, 0, 0),
    (1001, 'sys_menu_type', '菜单类型', 'ENABLED', 1, '系统字典：菜单类型', 0, 0, 0),
    (1001, 'sys_data_scope_type', '数据范围类型', 'ENABLED', 1, '系统字典：数据范围类型', 0, 0, 0),
    (1001, 'team_member_role', '团队成员角色', 'ENABLED', 1, '团队模块字典', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `dict_name` = VALUES(`dict_name`),
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_type` (`tenant_id`, `dict_code`, `dict_name`, `status`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
    (1001, 'team_type', '团队类型', 'ENABLED', 1, '团队模块字典', 0, 0, 0),
    (1001, 'team_visibility', '团队可见性', 'ENABLED', 1, '团队模块字典', 0, 0, 0),
    (1001, 'team_join_mode', '团队加入方式', 'ENABLED', 1, '团队模块字典', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `dict_name` = VALUES(`dict_name`),
    `status` = VALUES(`status`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item` (`tenant_id`, `dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, `id`, 'GENERAL', '通用团队', 10, 'ENABLED', '团队类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'DEV', '开发团队', 20, 'ENABLED', '团队类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'COMPETITION', '竞赛团队', 30, 'ENABLED', '团队类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'CLUB', '社团组织', 40, 'ENABLED', '团队类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'OTHER', '其他', 50, 'ENABLED', '团队类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'PRIVATE', '私有', 10, 'ENABLED', '团队可见性', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_visibility' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'PUBLIC', '公开', 20, 'ENABLED', '团队可见性', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_visibility' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'INVITE_ONLY', '仅邀请', 10, 'ENABLED', '团队加入方式', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_join_mode' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'APPLY', '申请加入', 20, 'ENABLED', '团队加入方式', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_join_mode' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'OPEN', '开放加入', 30, 'ENABLED', '团队加入方式', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_join_mode' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_dict_item` (`tenant_id`, `dict_type_id`, `item_value`, `item_label`, `sort_no`, `status`, `remark`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, `id`, 'MALE', '男', 10, 'ENABLED', '用户性别', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_user_gender' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'FEMALE', '女', 20, 'ENABLED', '用户性别', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_user_gender' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'OTHER', '其他', 30, 'ENABLED', '用户性别', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_user_gender' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'UNKNOWN', '未知', 40, 'ENABLED', '用户性别', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_user_gender' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'ENABLED', '启用', 10, 'ENABLED', '用户状态', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_user_status' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'DISABLED', '禁用', 20, 'ENABLED', '用户状态', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_user_status' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'LOCKED', '锁定', 30, 'ENABLED', '用户状态', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_user_status' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'ENABLED', '启用', 10, 'ENABLED', '通用状态', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_common_status' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'DISABLED', '停用', 20, 'ENABLED', '通用状态', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_common_status' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'YES', '是', 10, 'ENABLED', '是否', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_yes_no' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'NO', '否', 20, 'ENABLED', '是否', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_yes_no' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'SYSTEM', '系统角色', 10, 'ENABLED', '角色类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_role_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'CUSTOM', '自定义角色', 20, 'ENABLED', '角色类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_role_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'CATALOG', '目录', 10, 'ENABLED', '菜单类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'MENU', '菜单', 20, 'ENABLED', '菜单类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'BUTTON', '按钮', 30, 'ENABLED', '菜单类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'LINK', '外链', 40, 'ENABLED', '菜单类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_menu_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'ALL', '全部数据', 10, 'ENABLED', '数据范围类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'DEPT_AND_CHILD', '本部门及下级', 20, 'ENABLED', '数据范围类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'DEPT', '本部门', 30, 'ENABLED', '数据范围类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'SELF', '仅本人', 40, 'ENABLED', '数据范围类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'CUSTOM', '自定义', 50, 'ENABLED', '数据范围类型', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'sys_data_scope_type' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'OWNER', '所有者', 10, 'ENABLED', '团队成员角色', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_member_role' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'ADMIN', '管理员', 20, 'ENABLED', '团队成员角色', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_member_role' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'MANAGER', '协作者', 30, 'ENABLED', '团队成员角色', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_member_role' AND `deleted` = 0
UNION ALL
SELECT 1001, `id`, 'MEMBER', '成员', 40, 'ENABLED', '团队成员角色', 0, 0, 0 FROM `sys_dict_type` WHERE `tenant_id` = 1001 AND `dict_code` = 'team_member_role' AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `item_label` = VALUES(`item_label`),
    `sort_no` = VALUES(`sort_no`),
    `status` = VALUES(`status`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user` (`id`, `username`, `nickname`, `real_name`, `password_hash`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 'admin', 'Administrator', 'Administrator', '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `nickname` = VALUES(`nickname`),
    `real_name` = VALUES(`real_name`),
    `password_hash` = VALUES(`password_hash`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user_tenant` (`tenant_id`, `user_id`, `is_default`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 1001, 1, 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `is_default` = VALUES(`is_default`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user_tenant_profile` (`tenant_id`, `user_id`, `display_name`, `locale`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 1001, 'Administrator', 'zh-CN', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `locale` = VALUES(`locale`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user_role` (`tenant_id`, `user_id`, `role_id`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 1001, 1001, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_config` (
    `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    (1001, 'branding.website-name', '站点名称', '宏翔商道', 'PLATFORM', 0, '控制台顶部与浏览器标题展示名称', 0, 0, 0),
    (1001, 'branding.website-favicon-url', '站点图标地址', '', 'PLATFORM', 0, '浏览器标签页 icon 地址', 0, 0, 0),
    (1001, 'branding.website-logo-url', '站点 Logo 地址', '', 'PLATFORM', 0, '控制台左上角品牌 Logo 地址', 0, 0, 0),
    (1001, 'branding.login-background-url', '登录页背景图地址', '', 'PLATFORM', 0, '登录页背景图地址', 0, 0, 0),
    (1001, 'branding.github-link-enabled', 'GitHub 链接开关', 'true', 'PLATFORM', 0, '是否显示顶部 GitHub 图标', 0, 0, 0),
    (1001, 'branding.github-link-url', 'GitHub 链接', '', 'PLATFORM', 0, '顶部 GitHub 图标跳转地址', 0, 0, 0),
    (1001, 'branding.help-link-enabled', '帮助链接开关', 'true', 'PLATFORM', 0, '是否显示顶部帮助图标', 0, 0, 0),
    (1001, 'branding.help-link-url', '帮助链接', '', 'PLATFORM', 0, '顶部帮助图标跳转地址', 0, 0, 0),
    (1001, 'branding.company-name', '公司名称', '宏翔商道', 'PLATFORM', 0, '页脚版权主体名称', 0, 0, 0),
    (1001, 'branding.copyright-start-year', '版权起始年份', CAST(YEAR(CURRENT_DATE()) AS CHAR), 'PLATFORM', 0, '页脚版权起始年份', 0, 0, 0),
    (1001, 'branding.footer-icp', '页脚备案', '', 'PLATFORM', 0, '页脚备案信息', 0, 0, 0),
    (1001, 'branding.footer-police-beian', '页脚公安备案', '', 'PLATFORM', 0, '页脚公安备案信息', 0, 0, 0),
    (1001, 'branding.footer-copyright', '页脚版权声明', CONCAT('Copyright ', YEAR(CURRENT_DATE()), ' 宏翔商道 All Rights Reserved'), 'PLATFORM', 0, '页脚版权声明', 0, 0, 0),
    (1001, 'agreement.user-agreement-markdown', '用户协议', '', 'PLATFORM', 0, '用户协议 Markdown', 0, 0, 0),
    (1001, 'agreement.privacy-agreement-markdown', '隐私协议', '', 'PLATFORM', 0, '隐私协议 Markdown', 0, 0, 0),
    (1001, 'watermark.enabled', '水印开关', 'false', 'PLATFORM', 0, '全局水印开关', 0, 0, 0),
    (1001, 'watermark.mode', '水印模式', 'TEXT', 'PLATFORM', 0, 'TEXT/IMAGE', 0, 0, 0),
    (1001, 'watermark.text-lines', '水印文本', '', 'PLATFORM', 0, '多行文本水印', 0, 0, 0),
    (1001, 'watermark.image-url', '水印图片', '', 'PLATFORM', 0, '图片水印 URL', 0, 0, 0),
    (1001, 'watermark.font-color', '字体颜色', 'rgba(0,0,0,0.15)', 'PLATFORM', 0, '字体颜色', 0, 0, 0),
    (1001, 'watermark.font-size', '字体大小', '14', 'PLATFORM', 0, '字体大小', 0, 0, 0),
    (1001, 'watermark.font-weight', '字体粗细', 'normal', 'PLATFORM', 0, '字体粗细', 0, 0, 0),
    (1001, 'watermark.rotate', '旋转角度', '-22', 'PLATFORM', 0, '旋转角度', 0, 0, 0),
    (1001, 'watermark.gap-x', '横向间距', '100', 'PLATFORM', 0, '横向间距', 0, 0, 0),
    (1001, 'watermark.gap-y', '纵向间距', '100', 'PLATFORM', 0, '纵向间距', 0, 0, 0),
    (1001, 'watermark.offset-x', '横向偏移', '0', 'PLATFORM', 0, '横向偏移', 0, 0, 0),
    (1001, 'watermark.offset-y', '纵向偏移', '0', 'PLATFORM', 0, '纵向偏移', 0, 0, 0),
    (1001, 'watermark.z-index', '层级', '9', 'PLATFORM', 0, 'z-index', 0, 0, 0),
    (1001, 'watermark.opacity', '透明度', '0.15', 'PLATFORM', 0, '透明度', 0, 0, 0),
    (1001, 'floating-window.api-docs-qr-enabled', '接口文档二维码开关', 'false', 'PLATFORM', 0, '是否在全局悬浮窗展示接口文档二维码入口', 0, 0, 0),
    (1001, 'floating-window.api-docs-qr-title', '接口文档二维码标题', '', 'PLATFORM', 0, '接口文档二维码弹层标题', 0, 0, 0),
    (1001, 'floating-window.api-docs-qr-image-url', '接口文档二维码图片', '', 'PLATFORM', 0, '接口文档悬浮入口展开后展示的二维码图片', 0, 0, 0),
    (1001, 'smtp.enabled', 'SMTP 邮箱通知启用', 'false', 'PLATFORM', 0, '是否启用邮箱通知渠道', 0, 0, 0),
    (1001, 'smtp.host', 'SMTP 主机', '', 'PLATFORM', 0, '邮件服务器地址', 0, 0, 0),
    (1001, 'smtp.port', 'SMTP 端口', '25', 'PLATFORM', 0, '邮件服务器端口', 0, 0, 0),
    (1001, 'smtp.username', 'SMTP 用户名', '', 'PLATFORM', 0, 'SMTP 登录用户名', 0, 0, 0),
    (1001, 'smtp.password', 'SMTP 密码', '', 'PLATFORM', 0, 'SMTP 登录密码', 0, 0, 0),
    (1001, 'smtp.from', '发件人地址', '', 'PLATFORM', 0, 'SMTP 默认发件人', 0, 0, 0),
    (1001, 'smtp.auth-enabled', 'SMTP 认证', 'true', 'PLATFORM', 0, '是否启用 SMTP AUTH', 0, 0, 0),
    (1001, 'smtp.starttls-enabled', 'SMTP STARTTLS', 'true', 'PLATFORM', 0, '是否启用 STARTTLS', 0, 0, 0),
    (1001, 'smtp.ssl-enabled', 'SMTP SSL', 'false', 'PLATFORM', 0, '是否启用 SSL', 0, 0, 0),
    (1001, 'notification.wechat-official.enabled', '微信公众号通知启用', 'false', 'PLATFORM', 0, '是否启用微信公众号/服务号模板消息通知', 0, 0, 0),
    (1001, 'notification.wechat-official.app-id', '微信公众号 AppID', '', 'PLATFORM', 0, '微信公众号或服务号 AppID', 0, 0, 0),
    (1001, 'notification.wechat-official.app-secret', '微信公众号 AppSecret', '', 'PLATFORM', 0, '微信公众号或服务号 AppSecret', 0, 0, 0),
    (1001, 'notification.wechat-official.template-id', '微信公众号模板 ID', '', 'PLATFORM', 0, '用于系统通知的公众号模板消息 ID', 0, 0, 0),
    (1001, 'notification.wechat-official.detail-url', '微信公众号通知详情链接', '', 'PLATFORM', 0, '模板消息点击后打开的系统链接，可留空', 0, 0, 0),
    (1001, 'verification.totp.enabled', '2FA 启用', 'true', 'PLATFORM', 0, '是否启用 2FA 登录方式', 0, 0, 0),
    (1001, 'verification.password-login.enabled', '密码登录', 'true', 'PLATFORM', 0, '是否启用账号密码登录', 0, 0, 0),
    (1001, 'verification.email-login.enabled', '邮箱验证码登录', 'false', 'PLATFORM', 0, '是否启用邮箱验证码登录', 0, 0, 0),
    (1001, 'verification.login-mode.order', '登录方式排序', 'password,sms,email,wechat,passkey', 'PLATFORM', 0, '登录页分段控制器展示顺序', 0, 0, 0),
    (1001, 'verification.sms.enabled', '短信验证码启用', 'false', 'PLATFORM', 0, '是否启用短信验证码服务', 0, 0, 0),
    (1001, 'verification.sms.provider', '短信验证码服务商', 'aliyun', 'PLATFORM', 0, '短信验证码服务提供方', 0, 0, 0),
    (1001, 'verification.sms.sign-name', '短信签名', '', 'PLATFORM', 0, '短信验证码签名', 0, 0, 0),
    (1001, 'verification.sms.template-code', '短信模板编码', '', 'PLATFORM', 0, '短信验证码模板编码', 0, 0, 0),
    (1001, 'verification.sms.access-key-id', '短信 Access Key ID', '', 'PLATFORM', 0, '短信验证码访问密钥 ID', 0, 0, 0),
    (1001, 'verification.sms.access-key-secret', '短信 Access Key Secret', '', 'PLATFORM', 0, '短信验证码访问密钥 Secret', 0, 0, 0),
    (1001, 'verification.sms.endpoint', '短信服务地址', '', 'PLATFORM', 0, '短信验证码服务端点', 0, 0, 0),
    (1001, 'verification.sms.region', '短信服务地域', '', 'PLATFORM', 0, '短信验证码服务地域', 0, 0, 0),
    (1001, 'verification.wechat-login.enabled', '微信登录启用', 'false', 'PLATFORM', 0, '是否启用微信扫码登录', 0, 0, 0),
    (1001, 'verification.wechat-login.app-id', '微信 AppID', '', 'PLATFORM', 0, '微信开放平台网站应用 AppID', 0, 0, 0),
    (1001, 'verification.wechat-login.app-secret', '微信 AppSecret', '', 'PLATFORM', 0, '微信开放平台网站应用 AppSecret', 0, 0, 0),
    (1001, 'verification.wechat-login.redirect-uri', '微信登录回调地址', '', 'PLATFORM', 0, '微信开放平台授权回调地址', 0, 0, 0),
    (1001, 'verification.wechat-login.state-expire-minutes', '微信登录状态有效期', '10', 'PLATFORM', 0, '微信登录 state 缓存有效期，单位分钟', 0, 0, 0),
    (1001, 'verification.passkey.enabled', '通行密钥启用', 'false', 'PLATFORM', 0, '是否启用通行密钥登录', 0, 0, 0),
    (1001, 'verification.passkey.passwordless-enabled', '通行密钥无账号登录', 'false', 'PLATFORM', 0, '是否允许发现式凭据无账号登录', 0, 0, 0),
    (1001, 'verification.passkey.self-binding-enabled', '通行密钥自助绑定', 'false', 'PLATFORM', 0, '是否允许用户在个人中心自助绑定通行密钥', 0, 0, 0),
    (1001, 'verification.passkey.rp-id', '通行密钥 RP ID', '', 'PLATFORM', 0, 'WebAuthn RP ID', 0, 0, 0),
    (1001, 'verification.passkey.rp-name', '通行密钥 RP 名称', '', 'PLATFORM', 0, 'WebAuthn RP 显示名称', 0, 0, 0),
    (1001, 'verification.passkey.allowed-origins', '通行密钥允许 Origin', '', 'PLATFORM', 0, 'WebAuthn 允许的前端 Origin', 0, 0, 0),
    (1001, 'verification.passkey.challenge-ttl-seconds', '通行密钥 Challenge TTL', '120', 'PLATFORM', 0, 'WebAuthn challenge 有效期秒数', 0, 0, 0),
    (1001, 'security.idle-timeout-seconds', '空闲超时时间', '1800', 'PLATFORM', 1, '会话在无操作状态下允许保持的秒数', 0, 0, 0),
    (1001, 'security.access-token-expire-seconds', 'Access Token 过期时间', '1800', 'PLATFORM', 1, 'Access Token 的有效秒数', 0, 0, 0),
    (1001, 'security.refresh-token-expire-seconds', 'Refresh Token 刷新时限', '604800', 'PLATFORM', 1, 'Refresh Token 的有效秒数', 0, 0, 0),
    (1001, 'security.allow-multi-device-login', '多设备登录', '1', 'PLATFORM', 1, '是否允许同一账号在多个设备同时在线', 0, 0, 0),
    (1001, 'security.captcha-enabled', '验证码开关', '0', 'PLATFORM', 1, '是否开启登录时的人机验证码', 0, 0, 0),
    (1001, 'security.captcha-type', '验证码类型', 'IMAGE', 'PLATFORM', 1, '验证码类型：IMAGE/SLIDER', 0, 0, 0),
    (1001, 'security.login-defense-window-minutes', '登录防御统计窗口', '5', 'PLATFORM', 1, '统计登录尝试与错误次数的时间窗口，单位分钟', 0, 0, 0),
    (1001, 'security.login-max-validation-attempts', '最大验证次数', '100', 'PLATFORM', 1, '统计窗口内允许的最大验证码/登录验证尝试次数', 0, 0, 0),
    (1001, 'security.login-max-failure-count', '最大错误次数', '10', 'PLATFORM', 1, '统计窗口内允许的最大登录失败次数', 0, 0, 0),
    (1001, 'security.verification-code-expire-seconds', '验证码有效期', '300', 'PLATFORM', 1, '短信/邮箱验证码的有效秒数', 0, 0, 0),
    (1001, 'security.verification-code-cooldown-seconds', '验证码重发冷却', '60', 'PLATFORM', 1, '同一账号同一验证码渠道再次发送前需要等待的秒数', 0, 0, 0),
    (1001, 'security.password-min-length', '密码最短长度', '6', 'PLATFORM', 1, '用户密码允许的最少字符数', 0, 0, 0),
    (1001, 'security.password-require-uppercase', '密码必须包含大写字母', '0', 'PLATFORM', 1, '强制密码包含 A-Z', 0, 0, 0),
    (1001, 'security.password-require-lowercase', '密码必须包含小写字母', '0', 'PLATFORM', 1, '强制密码包含 a-z', 0, 0, 0),
    (1001, 'security.password-require-special-character', '密码必须包含特殊字符', '0', 'PLATFORM', 1, '强制密码包含特殊字符', 0, 0, 0),
    (1001, 'security.password-allow-consecutive-characters', '允许连续字符', '1', 'PLATFORM', 1, '是否允许密码中出现连续字符', 0, 0, 0),
    (1001, 'profile.field.system.overrides', 'System profile field metadata overrides', '[]', 'PLATFORM', 0, 'Stores editable labels, descriptions, placeholders, and groups for built-in profile fields', 0, 0, 0),
    (1001, 'profile.field.custom.definitions', '自定义资料字段定义', '[]', 'PLATFORM', 0, '保存个人中心可扩展的自定义资料字段定义', 0, 0, 0),
    (1001, 'profile.field.avatar.visible', '头像显示开关', 'true', 'PLATFORM', 0, '个人中心头像字段显示开关', 0, 0, 0),
    (1001, 'profile.field.avatar.weight', '头像评分权重', '10', 'PLATFORM', 0, '个人中心头像字段评分权重', 0, 0, 0),
    (1001, 'profile.field.avatar.required', '头像 required', 'false', 'PLATFORM', 0, '个人中心头像字段必填开关', 0, 0, 0),
    (1001, 'profile.field.avatar.sort', '头像 sort', '10', 'PLATFORM', 0, '个人中心头像字段排序', 0, 0, 0),
    (1001, 'profile.field.real-name.visible', '姓名显示开关', 'true', 'PLATFORM', 0, '个人中心姓名字段显示开关', 0, 0, 0),
    (1001, 'profile.field.real-name.weight', '姓名评分权重', '15', 'PLATFORM', 0, '个人中心姓名字段评分权重', 0, 0, 0),
    (1001, 'profile.field.real-name.required', '姓名 required', 'false', 'PLATFORM', 0, '个人中心姓名字段必填开关', 0, 0, 0),
    (1001, 'profile.field.real-name.sort', '姓名 sort', '20', 'PLATFORM', 0, '个人中心姓名字段排序', 0, 0, 0),
    (1001, 'profile.field.mobile.visible', '手机号显示开关', 'true', 'PLATFORM', 0, '个人中心手机号字段显示开关', 0, 0, 0),
    (1001, 'profile.field.mobile.weight', '手机号评分权重', '15', 'PLATFORM', 0, '个人中心手机号字段评分权重', 0, 0, 0),
    (1001, 'profile.field.mobile.required', '手机号 required', 'false', 'PLATFORM', 0, '个人中心手机号字段必填开关', 0, 0, 0),
    (1001, 'profile.field.mobile.sort', '手机号 sort', '30', 'PLATFORM', 0, '个人中心手机号字段排序', 0, 0, 0),
    (1001, 'profile.field.email.visible', '邮箱显示开关', 'true', 'PLATFORM', 0, '个人中心邮箱字段显示开关', 0, 0, 0),
    (1001, 'profile.field.email.weight', '邮箱评分权重', '15', 'PLATFORM', 0, '个人中心邮箱字段评分权重', 0, 0, 0),
    (1001, 'profile.field.email.required', '邮箱 required', 'false', 'PLATFORM', 0, '个人中心邮箱字段必填开关', 0, 0, 0),
    (1001, 'profile.field.email.sort', '邮箱 sort', '40', 'PLATFORM', 0, '个人中心邮箱字段排序', 0, 0, 0),
    (1001, 'profile.field.birth-month.visible', '出生年月显示开关', 'true', 'PLATFORM', 0, '个人中心出生年月字段显示开关', 0, 0, 0),
    (1001, 'profile.field.birth-month.weight', '出生年月评分权重', '10', 'PLATFORM', 0, '个人中心出生年月字段评分权重', 0, 0, 0),
    (1001, 'profile.field.birth-month.required', '出生年月 required', 'false', 'PLATFORM', 0, '个人中心出生年月字段必填开关', 0, 0, 0),
    (1001, 'profile.field.birth-month.sort', '出生年月 sort', '50', 'PLATFORM', 0, '个人中心出生年月字段排序', 0, 0, 0),
    (1001, 'profile.field.gender.visible', '性别显示开关', 'true', 'PLATFORM', 0, '个人中心性别字段显示开关', 0, 0, 0),
    (1001, 'profile.field.gender.weight', '性别评分权重', '10', 'PLATFORM', 0, '个人中心性别字段评分权重', 0, 0, 0),
    (1001, 'profile.field.gender.required', '性别 required', 'false', 'PLATFORM', 0, '个人中心性别字段必填开关', 0, 0, 0),
    (1001, 'profile.field.gender.sort', '性别 sort', '60', 'PLATFORM', 0, '个人中心性别字段排序', 0, 0, 0),
    (1001, 'profile.field.region.visible', '所在地区显示开关', 'true', 'PLATFORM', 0, '个人中心所在地区字段显示开关', 0, 0, 0),
    (1001, 'profile.field.region.weight', '所在地区评分权重', '10', 'PLATFORM', 0, '个人中心所在地区字段评分权重', 0, 0, 0),
    (1001, 'profile.field.region.required', '所在地区 required', 'false', 'PLATFORM', 0, '个人中心所在地区字段必填开关', 0, 0, 0),
    (1001, 'profile.field.region.sort', '所在地区 sort', '70', 'PLATFORM', 0, '个人中心所在地区字段排序', 0, 0, 0),
    (1001, 'profile.field.id-card-number.visible', '身份证号码显示开关', 'true', 'PLATFORM', 0, '个人中心身份证号码字段显示开关', 0, 0, 0),
    (1001, 'profile.field.id-card-number.weight', '身份证号码评分权重', '5', 'PLATFORM', 0, '个人中心身份证号码字段评分权重', 0, 0, 0),
    (1001, 'profile.field.id-card-number.required', '身份证号码 required', 'false', 'PLATFORM', 0, '个人中心身份证号码字段必填开关', 0, 0, 0),
    (1001, 'profile.field.id-card-number.sort', '身份证号码 sort', '80', 'PLATFORM', 0, '个人中心身份证号码字段排序', 0, 0, 0)
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

INSERT INTO `iam_subject` (`tenant_id`, `subject_type`, `ref_id`, `subject_code`, `display_name`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 'USER', 1001, 'admin', 'Administrator', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `subject_code` = VALUES(`subject_code`),
    `display_name` = VALUES(`display_name`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `iam_subject_role` (`tenant_id`, `subject_id`, `role_id`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, `id`, 1001, 0, 0, 0
FROM `iam_subject`
WHERE `tenant_id` = 1001 AND `subject_type` = 'USER' AND `ref_id` = 1001 AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user` (`id`, `username`, `nickname`, `real_name`, `password_hash`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1002, 'user', 'Common User', 'Common User', '$2a$10$VBwFJkc.aR1ML.qIKi1Lb.st90B.SS4RrIuwQ3LY/y.VG9/oUU8te', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `nickname` = VALUES(`nickname`),
    `real_name` = VALUES(`real_name`),
    `password_hash` = VALUES(`password_hash`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user_tenant` (`tenant_id`, `user_id`, `is_default`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 1002, 1, 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `is_default` = VALUES(`is_default`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user_tenant_profile` (`tenant_id`, `user_id`, `display_name`, `locale`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 1002, 'Common User', 'zh-CN', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `locale` = VALUES(`locale`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_user_role` (`tenant_id`, `user_id`, `role_id`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 1002, 1002, 0, 0, 0)
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

INSERT INTO `iam_subject` (`tenant_id`, `subject_type`, `ref_id`, `subject_code`, `display_name`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 'USER', 1002, 'user', 'Common User', 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `subject_code` = VALUES(`subject_code`),
    `display_name` = VALUES(`display_name`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `iam_subject_role` (`tenant_id`, `subject_id`, `role_id`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, `id`, 1002, 0, 0, 0
FROM `iam_subject`
WHERE `tenant_id` = 1001 AND `subject_type` = 'USER' AND `ref_id` = 1002 AND `deleted` = 0
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

-- Built-in plugin catalog. Tenant enable/disable state is stored in sys_plugin_tenant.
INSERT INTO `sys_plugin_definition` (
    `plugin_code`, `plugin_name`, `plugin_type`, `description`, `author`, `plugin_api_version`,
    `builtin_flag`, `status`, `sort_no`, `schema_mode`, `supports_hot_disable`, `supports_data_purge`,
    `runtime_contributions_json`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'sensitive-words', '敏感词拦截插件', 'SECURITY', '提供敏感词词库维护、请求内容拦截和导入能力。',
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
        'pluginName', '敏感词拦截插件',
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
    'sensitive-words', '1.0.0', 'plugin.sensitive-words', '敏感词管理', '/plugins/sensitive-words', 'SafetyOutlined',
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
    ('sensitive-words', '1.0.0', 'plugin:sensitive-words:view', '查看敏感词插件', 'sensitive-words', 0, 0, 0),
    ('sensitive-words', '1.0.0', 'plugin:sensitive-words:manage', '管理敏感词插件', 'sensitive-words', 0, 0, 0),
    ('sensitive-words', '1.0.0', 'plugin:sensitive-words:import', '导入敏感词', 'sensitive-words', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

-- XXL-JOB scheduler tables. Keep this schema aligned with xuxueli/xxl-job-admin:3.4.0.
CREATE TABLE `xxl_job_group` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `app_name` varchar(64) NOT NULL COMMENT '执行器AppName',
  `title` varchar(64) NOT NULL COMMENT '执行器名称',
  `address_type` tinyint(4) NOT NULL DEFAULT '0' COMMENT '执行器地址类型：0=自动注册、1=手动录入',
  `address_list` text COMMENT '执行器地址列表，多地址逗号分隔',
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
  `job_group` int(11) NOT NULL COMMENT '执行器主键ID',
  `job_desc` varchar(255) NOT NULL,
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `author` varchar(64) DEFAULT NULL COMMENT '作者',
  `alarm_email` varchar(255) DEFAULT NULL COMMENT '报警邮件',
  `schedule_type` varchar(50) NOT NULL DEFAULT 'NONE' COMMENT '调度类型',
  `schedule_conf` varchar(128) DEFAULT NULL COMMENT '调度配置，值含义取决于调度类型',
  `misfire_strategy` varchar(50) NOT NULL DEFAULT 'DO_NOTHING' COMMENT '调度过期策略',
  `executor_route_strategy` varchar(50) DEFAULT NULL COMMENT '执行器路由策略',
  `executor_handler` varchar(255) DEFAULT NULL COMMENT '任务handler',
  `executor_param` text DEFAULT NULL COMMENT '任务参数',
  `executor_block_strategy` varchar(50) DEFAULT NULL COMMENT '阻塞处理策略',
  `executor_timeout` int(11) NOT NULL DEFAULT '0' COMMENT '任务执行超时时间，单位秒',
  `executor_fail_retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `glue_type` varchar(50) NOT NULL COMMENT 'GLUE类型',
  `glue_source` mediumtext COMMENT 'GLUE源代码',
  `glue_remark` varchar(128) DEFAULT NULL COMMENT 'GLUE备注',
  `glue_updatetime` datetime DEFAULT NULL COMMENT 'GLUE更新时间',
  `child_jobid` varchar(255) DEFAULT NULL COMMENT '子任务ID，多个逗号分隔',
  `trigger_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '调度状态：0-停止，1-运行',
  `trigger_last_time` bigint(13) NOT NULL DEFAULT '0' COMMENT '上次调度时间',
  `trigger_next_time` bigint(13) NOT NULL DEFAULT '0' COMMENT '下次调度时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_logglue` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `job_id` int(11) NOT NULL COMMENT '任务，主键ID',
  `glue_type` varchar(50) DEFAULT NULL COMMENT 'GLUE类型',
  `glue_source` mediumtext COMMENT 'GLUE源代码',
  `glue_remark` varchar(128) NOT NULL COMMENT 'GLUE备注',
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `job_group` int(11) NOT NULL COMMENT '执行器主键ID',
  `job_id` int(11) NOT NULL COMMENT '任务，主键ID',
  `executor_address` varchar(255) DEFAULT NULL COMMENT '执行器地址，本次执行的地址',
  `executor_handler` varchar(255) DEFAULT NULL COMMENT '任务handler',
  `executor_param` text DEFAULT NULL COMMENT '任务参数',
  `executor_sharding_param` varchar(20) DEFAULT NULL COMMENT '任务分片参数，格式如 1/2',
  `executor_fail_retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `trigger_time` datetime DEFAULT NULL COMMENT '调度-时间',
  `trigger_code` int(11) NOT NULL COMMENT '调度-结果',
  `trigger_msg` text COMMENT '调度-日志',
  `handle_time` datetime DEFAULT NULL COMMENT '执行-时间',
  `handle_code` int(11) NOT NULL COMMENT '执行-状态',
  `handle_msg` text COMMENT '执行-日志',
  `alarm_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '告警状态：0-默认、1-无需告警、2-告警成功、3-告警失败',
  PRIMARY KEY (`id`),
  KEY `I_trigger_time` (`trigger_time`),
  KEY `I_handle_code` (`handle_code`),
  KEY `I_jobgroup` (`job_group`),
  KEY `I_jobid` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_log_report` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `trigger_day` datetime DEFAULT NULL COMMENT '调度-时间',
  `running_count` int(11) NOT NULL DEFAULT '0' COMMENT '运行中-日志数量',
  `suc_count` int(11) NOT NULL DEFAULT '0' COMMENT '执行成功-日志数量',
  `fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '执行失败-日志数量',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_trigger_day` (`trigger_day`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_lock` (
  `lock_name` varchar(50) NOT NULL COMMENT '锁名称',
  PRIMARY KEY (`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `xxl_job_user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '账号',
  `password` varchar(100) NOT NULL COMMENT '密码加密信息',
  `token` varchar(100) DEFAULT NULL COMMENT '登录token',
  `role` tinyint(4) NOT NULL COMMENT '角色：0-普通用户、1-管理员',
  `permission` varchar(255) DEFAULT NULL COMMENT '权限：执行器ID列表，多个逗号分割',
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_username` (`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `xxl_job_group` (`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
VALUES (1, 'lumira-server', 'Lumira 后端执行器', 0, NULL, NOW());

INSERT INTO `xxl_job_user` (`id`, `username`, `password`, `role`, `permission`)
VALUES (1, 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 1, NULL);

INSERT INTO `xxl_job_lock` (`lock_name`) VALUES ('schedule_lock');

SET FOREIGN_KEY_CHECKS = 1;
