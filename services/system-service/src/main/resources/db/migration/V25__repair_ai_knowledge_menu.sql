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
 AND p.deleted = 0
WHERE r.tenant_id = 1001
  AND upper(r.role_code) = 'ADMIN'
  AND r.deleted = 0
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_menu` (`tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`)
SELECT parent.tenant_id, parent.id, 'settings.ai-knowledge', '知识库', 'MENU', '/settings/ai-knowledge', '@/pages/settings/ai-knowledge', 'FileSearchOutlined', 25, 'ai:knowledge:view', 'ENABLED', 0, 0, 0
FROM `sys_menu` parent
WHERE parent.tenant_id = 1001
  AND parent.menu_code = 'settings.root'
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
