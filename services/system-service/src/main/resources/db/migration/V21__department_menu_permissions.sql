INSERT INTO `sys_permission` (
  `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
  `created_by`, `updated_by`, `deleted`
) VALUES
  (1001, 'system:department:view', '查看组织部门', 'system', 'CORE', NULL, 0, 0, 0),
  (1001, 'system:department:create', '创建组织部门', 'system', 'CORE', NULL, 0, 0, 0),
  (1001, 'system:department:update', '编辑组织部门', 'system', 'CORE', NULL, 0, 0, 0),
  (1001, 'system:department:delete', '删除组织部门', 'system', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `source_type` = VALUES(`source_type`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_menu` (
  `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`,
  `created_by`, `updated_by`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`
)
SELECT
  parent.tenant_id,
  parent.id,
  'system.departments',
  '组织部门',
  'MENU',
  '/user-center/departments',
  '@/pages/system/departments',
  0,
  0,
  0,
  'ApartmentOutlined',
  22,
  'system:department:view',
  'ENABLED'
FROM `sys_menu` parent
WHERE parent.tenant_id = 1001
  AND parent.menu_code = 'user.center.root'
  AND parent.deleted = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `menu_name` = VALUES(`menu_name`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `sort_no` = VALUES(`sort_no`),
  `permission_key` = VALUES(`permission_key`),
  `status` = VALUES(`status`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT r.tenant_id, r.id, p.permission_key, 0, 0, 0
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.tenant_id = r.tenant_id
 AND p.permission_key IN (
      'system:department:view',
      'system:department:create',
      'system:department:update',
      'system:department:delete'
 )
 AND p.deleted = 0
WHERE r.tenant_id = 1001
  AND upper(r.role_code) = 'ADMIN'
  AND r.deleted = 0
ON DUPLICATE KEY UPDATE
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;
