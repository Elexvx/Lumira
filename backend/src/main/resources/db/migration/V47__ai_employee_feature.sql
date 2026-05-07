SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `ai_employee` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `username` varchar(64) NOT NULL,
  `nickname` varchar(64) NOT NULL,
  `position` varchar(128) DEFAULT NULL,
  `avatar_key` varchar(128) DEFAULT NULL,
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
  UNIQUE KEY `uk_ai_employee_tenant_username` (`tenant_id`,`username`),
  KEY `idx_ai_employee_tenant_deleted` (`tenant_id`,`is_deleted`),
  KEY `idx_ai_employee_tenant_llm` (`tenant_id`,`default_llm_service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_llm_service` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `provider` varchar(64) NOT NULL,
  `code` varchar(64) NOT NULL,
  `title` varchar(128) NOT NULL,
  `base_url` varchar(512) DEFAULT NULL,
  `api_key_encrypted` text,
  `default_model` varchar(128) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `timeout_ms` int NOT NULL DEFAULT '60000',
  `temperature` decimal(4,2) DEFAULT '0.70',
  `max_tokens` int DEFAULT '2048',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_llm_service_tenant_code` (`tenant_id`,`code`),
  KEY `idx_ai_llm_service_tenant_deleted` (`tenant_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_llm_model` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `llm_service_id` bigint unsigned NOT NULL,
  `model_code` varchar(128) NOT NULL,
  `model_name` varchar(128) DEFAULT NULL,
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_llm_model_service_code` (`tenant_id`,`llm_service_id`,`model_code`),
  KEY `idx_ai_llm_model_tenant_deleted` (`tenant_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_skill` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `skill_code` varchar(128) NOT NULL,
  `skill_name` varchar(128) NOT NULL,
  `category` varchar(64) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `risk_level` varchar(32) DEFAULT NULL,
  `read_only` tinyint unsigned NOT NULL DEFAULT '0',
  `need_confirm` tinyint unsigned NOT NULL DEFAULT '0',
  `enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_skill_code` (`skill_code`),
  KEY `idx_ai_skill_category_enabled` (`category`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_employee_skill` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `employee_id` bigint unsigned NOT NULL,
  `skill_code` varchar(128) NOT NULL,
  `permission_mode` varchar(32) NOT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_employee_skill_rel` (`tenant_id`,`employee_id`,`skill_code`),
  KEY `idx_ai_employee_skill_tenant_employee` (`tenant_id`,`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `employee_id` bigint unsigned NOT NULL,
  `conversation_code` varchar(64) NOT NULL,
  `title` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE',
  `latest_message_at` datetime DEFAULT NULL,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_conversation_code` (`tenant_id`,`conversation_code`),
  KEY `idx_ai_conversation_tenant_employee` (`tenant_id`,`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_message` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned NOT NULL,
  `role` varchar(32) NOT NULL,
  `content` longtext,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_tenant_conversation` (`tenant_id`,`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_tool_audit_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `skill_code` varchar(128) DEFAULT NULL,
  `tool_name` varchar(128) DEFAULT NULL,
  `permission_mode` varchar(32) DEFAULT NULL,
  `confirm_required` tinyint unsigned NOT NULL DEFAULT '0',
  `confirm_result` tinyint unsigned NOT NULL DEFAULT '0',
  `result_status` varchar(32) NOT NULL,
  `detail_message` varchar(512) DEFAULT NULL,
  `request_payload_json` longtext,
  `response_payload_json` longtext,
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_tool_audit_log_tenant_employee_created` (`tenant_id`,`employee_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `ai_skill` (`id`, `skill_code`, `skill_name`, `category`, `description`, `risk_level`, `read_only`, `need_confirm`, `enabled`, `is_deleted`, `create_time`, `update_time`) VALUES
  (1, 'knowledge.search', '知识检索', 'COMMON', '从已授权知识库中查找相关内容并返回参考结论。', 'LOW', 1, 0, 1, 0, NOW(), NOW()),
  (2, 'content.summary', '内容总结', 'COMMON', '对文本、列表或对话内容进行结构化摘要。', 'LOW', 1, 0, 1, 0, NOW(), NOW()),
  (3, 'customer.reply', '客户回复', 'CUSTOM', '基于上下文生成面向客户的正式回复话术。', 'MEDIUM', 0, 0, 1, 0, NOW(), NOW()),
  (4, 'data.query', '数据查询', 'CUSTOM', '访问已授权业务数据并生成查询结果说明。', 'MEDIUM', 0, 0, 1, 0, NOW(), NOW()),
  (5, 'data.export', '数据导出', 'CUSTOM', '将查询结果导出为可下载的结构化数据。', 'HIGH', 0, 1, 1, 0, NOW(), NOW()),
  (6, 'workflow.request', '流程发起', 'CUSTOM', '发起审批、流转或待办类业务流程。', 'HIGH', 0, 1, 1, 0, NOW(), NOW());

INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
  (156, 1001, 'ai:view', '查看数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (157, 1001, 'ai:employee:create', '创建数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (158, 1001, 'ai:employee:update', '编辑数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (159, 1001, 'ai:employee:delete', '删除数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (160, 1001, 'ai:employee:status', '启停数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (161, 1001, 'ai:employee:skills', '配置数字员工技能', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (162, 1001, 'ai:llm:create', '创建 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (163, 1001, 'ai:llm:update', '编辑 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (164, 1001, 'ai:llm:delete', '删除 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (165, 1001, 'ai:llm:status', '启停 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (166, 1001, 'ai:skill:view', '查看技能列表', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (167, 1001, 'ai:chat:send', '发送 AI 对话', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0);

INSERT IGNORE INTO `sys_permission` (`id`, `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
  (168, 1002, 'ai:view', '查看数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (169, 1002, 'ai:employee:create', '创建数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (170, 1002, 'ai:employee:update', '编辑数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (171, 1002, 'ai:employee:delete', '删除数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (172, 1002, 'ai:employee:status', '启停数字员工', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (173, 1002, 'ai:employee:skills', '配置数字员工技能', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (174, 1002, 'ai:llm:create', '创建 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (175, 1002, 'ai:llm:update', '编辑 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (176, 1002, 'ai:llm:delete', '删除 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (177, 1002, 'ai:llm:status', '启停 LLM 服务', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (178, 1002, 'ai:skill:view', '查看技能列表', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0),
  (179, 1002, 'ai:chat:send', '发送 AI 对话', 'ai', 'CORE', NULL, 0, NOW(), 0, NOW(), 0);

INSERT IGNORE INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`) VALUES
  (3024, 1001, 3002, 'settings.ai-employees', '数字员工', 'MENU', '/settings/ai-employees', '@/pages/settings/ai-employees', 0, NOW(), 0, NOW(), 0, 'RobotOutlined', 24, 'ai:view', 'ENABLED'),
  (4024, 1002, 4002, 'settings.ai-employees', '数字员工', 'MENU', '/settings/ai-employees', '@/pages/settings/ai-employees', 0, NOW(), 0, NOW(), 0, 'RobotOutlined', 24, 'ai:view', 'ENABLED');

INSERT IGNORE INTO `sys_role_permission` (`id`, `tenant_id`, `role_id`, `permission_key`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
  (189, 1001, 2001, 'ai:view', 0, NOW(), 0, NOW(), 0),
  (190, 1001, 2001, 'ai:employee:create', 0, NOW(), 0, NOW(), 0),
  (191, 1001, 2001, 'ai:employee:update', 0, NOW(), 0, NOW(), 0),
  (192, 1001, 2001, 'ai:employee:delete', 0, NOW(), 0, NOW(), 0),
  (193, 1001, 2001, 'ai:employee:status', 0, NOW(), 0, NOW(), 0),
  (194, 1001, 2001, 'ai:employee:skills', 0, NOW(), 0, NOW(), 0),
  (195, 1001, 2001, 'ai:llm:create', 0, NOW(), 0, NOW(), 0),
  (196, 1001, 2001, 'ai:llm:update', 0, NOW(), 0, NOW(), 0),
  (197, 1001, 2001, 'ai:llm:delete', 0, NOW(), 0, NOW(), 0),
  (198, 1001, 2001, 'ai:llm:status', 0, NOW(), 0, NOW(), 0),
  (199, 1001, 2001, 'ai:skill:view', 0, NOW(), 0, NOW(), 0),
  (200, 1001, 2001, 'ai:chat:send', 0, NOW(), 0, NOW(), 0),
  (201, 1002, 2002, 'ai:view', 0, NOW(), 0, NOW(), 0),
  (202, 1002, 2002, 'ai:employee:create', 0, NOW(), 0, NOW(), 0),
  (203, 1002, 2002, 'ai:employee:update', 0, NOW(), 0, NOW(), 0),
  (204, 1002, 2002, 'ai:employee:delete', 0, NOW(), 0, NOW(), 0),
  (205, 1002, 2002, 'ai:employee:status', 0, NOW(), 0, NOW(), 0),
  (206, 1002, 2002, 'ai:employee:skills', 0, NOW(), 0, NOW(), 0),
  (207, 1002, 2002, 'ai:llm:create', 0, NOW(), 0, NOW(), 0),
  (208, 1002, 2002, 'ai:llm:update', 0, NOW(), 0, NOW(), 0),
  (209, 1002, 2002, 'ai:llm:delete', 0, NOW(), 0, NOW(), 0),
  (210, 1002, 2002, 'ai:llm:status', 0, NOW(), 0, NOW(), 0),
  (211, 1002, 2002, 'ai:skill:view', 0, NOW(), 0, NOW(), 0),
  (212, 1002, 2002, 'ai:chat:send', 0, NOW(), 0, NOW(), 0);

SET FOREIGN_KEY_CHECKS = 1;
