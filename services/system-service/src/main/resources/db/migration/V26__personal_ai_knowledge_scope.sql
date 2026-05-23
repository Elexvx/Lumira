ALTER TABLE `ai_knowledge_base`
  ADD COLUMN `owner_user_id` bigint unsigned NOT NULL DEFAULT '0' AFTER `visibility_scope`,
  ADD KEY `idx_ai_knowledge_base_owner` (`tenant_id`,`owner_user_id`,`status`,`is_deleted`);

UPDATE `ai_knowledge_base`
SET `owner_user_id` = `created_by`
WHERE `owner_user_id` = 0;

UPDATE `ai_knowledge_base`
SET `visibility_scope` = 'PERSONAL'
WHERE `visibility_scope` IN ('PRIVATE', 'TENANT');

ALTER TABLE `ai_knowledge_base`
  DROP INDEX `uk_ai_knowledge_base_name`,
  ADD UNIQUE KEY `uk_ai_knowledge_base_owner_name` (`tenant_id`,`owner_user_id`,`name`,`is_deleted`);

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

INSERT INTO `sys_permission` (`tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `updated_by`, `deleted`)
VALUES
  (1001, 'ai:knowledge:share', '共享知识库', 'ai', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `source_type` = VALUES(`source_type`),
  `plugin_code` = VALUES(`plugin_code`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT r.tenant_id, r.id, p.permission_key, 0, 0, 0
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.tenant_id = r.tenant_id
 AND p.permission_key = 'ai:knowledge:share'
 AND p.deleted = 0
WHERE r.tenant_id = 1001
  AND upper(r.role_code) = 'ADMIN'
  AND r.deleted = 0
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_menu` (`tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 0, 'ai.root', 'AI', 'CATALOG', '/ai', 'redirect:/ai/assistant', 'RobotOutlined', 2, NULL, 'ENABLED', 0, 0, 0)
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
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_menu` (`tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`)
SELECT parent.tenant_id, parent.id, 'ai.assistant', 'AI 助手', 'MENU', '/ai/assistant', '@/pages/ai/Assistant', 'RobotOutlined', 1, 'ai:chat:send', 'ENABLED', 0, 0, 0
FROM `sys_menu` parent
WHERE parent.tenant_id = 1001
  AND parent.menu_code = 'ai.root'
  AND parent.deleted = 0
LIMIT 1
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
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_menu` (`tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`)
SELECT parent.tenant_id, parent.id, 'ai.knowledge', '知识库', 'MENU', '/ai/knowledge', '@/pages/ai/knowledge', 'FileSearchOutlined', 2, 'ai:knowledge:view', 'ENABLED', 0, 0, 0
FROM `sys_menu` parent
WHERE parent.tenant_id = 1001
  AND parent.menu_code = 'ai.root'
  AND parent.deleted = 0
LIMIT 1
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
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

UPDATE `sys_menu`
SET `deleted` = 1,
    `status` = 'DISABLED',
    `updated_at` = CURRENT_TIMESTAMP
WHERE `tenant_id` = 1001
  AND `menu_code` = 'settings.ai-knowledge'
  AND `deleted` = 0;
