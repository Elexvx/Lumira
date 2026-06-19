-- Consolidated ai-service baseline. Future schema changes should start at V2.

CREATE TABLE IF NOT EXISTS `ai_llm_service` (
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

CREATE TABLE IF NOT EXISTS `ai_llm_model` (
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

CREATE TABLE IF NOT EXISTS `ai_employee` (
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

CREATE TABLE IF NOT EXISTS `ai_skill` (
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

CREATE TABLE IF NOT EXISTS `ai_employee_skill` (
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

CREATE TABLE IF NOT EXISTS `ai_conversation` (
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

CREATE TABLE IF NOT EXISTS `ai_message` (
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

CREATE TABLE IF NOT EXISTS `ai_message_attachment` (
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

CREATE TABLE IF NOT EXISTS `ai_tool_audit_log` (
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

CREATE TABLE IF NOT EXISTS `ai_tool_policy` (
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

CREATE TABLE IF NOT EXISTS `ai_tool_call_plan` (
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

CREATE TABLE IF NOT EXISTS `ai_knowledge_base` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `kb_code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `visibility_scope` varchar(32) NOT NULL DEFAULT 'PERSONAL',
  `owner_user_id` bigint unsigned NOT NULL DEFAULT '0',
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_base_code` (`tenant_id`,`kb_code`),
  UNIQUE KEY `uk_ai_knowledge_base_owner_name` (`tenant_id`,`owner_user_id`,`name`,`is_deleted`),
  KEY `idx_ai_knowledge_base_tenant_status` (`tenant_id`,`status`,`is_deleted`),
  KEY `idx_ai_knowledge_base_owner` (`tenant_id`,`owner_user_id`,`status`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_knowledge_document` (
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
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_knowledge_document_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_document_file` (`tenant_id`,`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_knowledge_chunk` (
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
  `vector_indexed_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_chunk_index` (`tenant_id`,`document_id`,`chunk_index`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_document` (`tenant_id`,`document_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_vector` (`tenant_id`,`knowledge_base_id`,`is_deleted`,`embedding_model`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_employee_knowledge_base` (
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

CREATE TABLE IF NOT EXISTS `ai_knowledge_base_acl` (
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

INSERT INTO `ai_skill` (`skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `enabled`, `is_deleted`)
SELECT 'system.permission.snapshot','读取当前权限上下文','system','返回当前登录用户、租户、角色、部门和权限集合，供 AI 判断可访问边界。','LOW',1,0,1,0
WHERE NOT EXISTS (SELECT 1 FROM `ai_skill` WHERE `skill_code` = 'system.permission.snapshot' AND `is_deleted` = 0);

INSERT INTO `ai_skill` (`skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `enabled`, `is_deleted`)
SELECT 'system.menu.list','读取系统菜单与模块入口','system','按当前账号权限读取系统菜单、路由、权限键和状态，供 AI 理解平台能力地图。','LOW',1,0,1,0
WHERE NOT EXISTS (SELECT 1 FROM `ai_skill` WHERE `skill_code` = 'system.menu.list' AND `is_deleted` = 0);

INSERT INTO `ai_skill` (`skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `enabled`, `is_deleted`)
SELECT 'system.config.read','读取非敏感系统配置','system','按配置键读取非敏感平台配置；敏感配置会被拒绝。','MEDIUM',1,0,1,0
WHERE NOT EXISTS (SELECT 1 FROM `ai_skill` WHERE `skill_code` = 'system.config.read' AND `is_deleted` = 0);

INSERT INTO `ai_skill` (`skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `enabled`, `is_deleted`)
SELECT 'system.user.search','检索系统用户','system','按关键词和状态检索当前租户用户，返回脱敏后的基础资料。','MEDIUM',1,0,1,0
WHERE NOT EXISTS (SELECT 1 FROM `ai_skill` WHERE `skill_code` = 'system.user.search' AND `is_deleted` = 0);

INSERT INTO `ai_skill` (`skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `enabled`, `is_deleted`)
SELECT 'file.object.search','检索文件对象','file','按关键词、类型和状态检索文件中心对象。','MEDIUM',1,0,1,0
WHERE NOT EXISTS (SELECT 1 FROM `ai_skill` WHERE `skill_code` = 'file.object.search' AND `is_deleted` = 0);

INSERT INTO `ai_skill` (`skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `enabled`, `is_deleted`)
SELECT 'audit.ai_call.search','检索 AI 工具审计','audit','按数字员工、技能编码和结果状态检索 AI 调用审计日志。','MEDIUM',1,0,1,0
WHERE NOT EXISTS (SELECT 1 FROM `ai_skill` WHERE `skill_code` = 'audit.ai_call.search' AND `is_deleted` = 0);

INSERT INTO `ai_tool_policy` (`tenant_id`, `policy_name`, `tool_code`, `action_type`, `risk_level`, `match_type`, `match_value`, `verdict`, `message`, `enabled`, `is_deleted`)
SELECT 1001, '禁止读取或修改密钥类配置', '*', NULL, NULL, 'KEYWORD', 'password,secret,token,credential,private,api_key,密钥,密码,令牌', 'DENY', '命中平台防护规则：敏感密钥、密码或令牌不允许由 AI 工具读取或修改。', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `ai_tool_policy` WHERE tenant_id = 1001 AND policy_name = '禁止读取或修改密钥类配置' AND is_deleted = 0);
