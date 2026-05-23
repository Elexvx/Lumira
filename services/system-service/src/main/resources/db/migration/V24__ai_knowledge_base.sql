CREATE TABLE IF NOT EXISTS `ai_knowledge_base` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `kb_code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'ENABLED',
  `visibility_scope` varchar(32) NOT NULL DEFAULT 'TENANT',
  `created_by` bigint unsigned NOT NULL DEFAULT '0',
  `updated_by` bigint unsigned NOT NULL DEFAULT '0',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_base_code` (`tenant_id`,`kb_code`),
  UNIQUE KEY `uk_ai_knowledge_base_name` (`tenant_id`,`name`,`is_deleted`),
  KEY `idx_ai_knowledge_base_tenant_status` (`tenant_id`,`status`,`is_deleted`)
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
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_knowledge_chunk_index` (`tenant_id`,`document_id`,`chunk_index`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_base` (`tenant_id`,`knowledge_base_id`,`is_deleted`),
  KEY `idx_ai_knowledge_chunk_document` (`tenant_id`,`document_id`,`is_deleted`)
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

CREATE TABLE IF NOT EXISTS `ai_knowledge_retrieval_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint unsigned NOT NULL,
  `employee_id` bigint unsigned DEFAULT NULL,
  `conversation_id` bigint unsigned DEFAULT NULL,
  `query_text` varchar(1024) NOT NULL,
  `matched_chunk_ids` varchar(1024) DEFAULT NULL,
  `result_count` int unsigned NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_knowledge_retrieval_log_tenant_created` (`tenant_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `sys_permission` (`tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `updated_by`, `deleted`)
VALUES
  (1001, 'ai:knowledge:view', '查看知识库', 'ai', 'CORE', NULL, 0, 0, 0),
  (1001, 'ai:knowledge:create', '创建知识库', 'ai', 'CORE', NULL, 0, 0, 0),
  (1001, 'ai:knowledge:update', '编辑知识库', 'ai', 'CORE', NULL, 0, 0, 0),
  (1001, 'ai:knowledge:delete', '删除知识库', 'ai', 'CORE', NULL, 0, 0, 0),
  (1001, 'ai:knowledge:document:upload', '上传知识库文档', 'ai', 'CORE', NULL, 0, 0, 0),
  (1001, 'ai:knowledge:document:index', '重建知识库索引', 'ai', 'CORE', NULL, 0, 0, 0),
  (1001, 'ai:knowledge:document:delete', '删除知识库文档', 'ai', 'CORE', NULL, 0, 0, 0),
  (1001, 'ai:knowledge:bind', '绑定数字员工知识库', 'ai', 'CORE', NULL, 0, 0, 0),
  (1001, 'ai:knowledge:query', '检索知识库', 'ai', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = VALUES(`deleted`);

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, 2001, p.permission_key, 0, 0, 0
FROM `sys_permission` p
WHERE p.tenant_id = 1001
  AND p.permission_key IN (
    'ai:knowledge:view',
    'ai:knowledge:create',
    'ai:knowledge:update',
    'ai:knowledge:delete',
    'ai:knowledge:document:upload',
    'ai:knowledge:document:index',
    'ai:knowledge:document:delete',
    'ai:knowledge:bind',
    'ai:knowledge:query'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM `sys_role_permission` rp
    WHERE rp.tenant_id = 1001
      AND rp.role_id = 2001
      AND rp.permission_key = p.permission_key
      AND rp.deleted = 0
  );

INSERT INTO `sys_menu` (`tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`)
SELECT 1001, m.id, 'settings.ai-knowledge', '知识库', 'MENU', '/settings/ai-knowledge', '@/pages/settings/ai-knowledge', 'FileSearchOutlined', 25, 'ai:knowledge:view', 'ENABLED', 0, 0, 0
FROM `sys_menu` m
WHERE m.tenant_id = 1001
  AND m.menu_code = 'settings.root'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_menu` existing
    WHERE existing.tenant_id = 1001
      AND existing.menu_code = 'settings.ai-knowledge'
  )
LIMIT 1;
