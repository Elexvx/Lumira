INSERT INTO `sys_permission` (
  `tenant_id`,
  `permission_key`,
  `permission_name`,
  `permission_group`,
  `source_type`,
  `plugin_code`,
  `created_by`,
  `created_at`,
  `updated_by`,
  `updated_at`,
  `deleted`
)
VALUES (
  1001,
  'ai:view',
  '查看数字员工',
  'ai',
  'CORE',
  NULL,
  0,
  CURRENT_TIMESTAMP,
  0,
  CURRENT_TIMESTAMP,
  0
)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `source_type` = VALUES(`source_type`),
  `plugin_code` = VALUES(`plugin_code`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_role_permission` (
  `tenant_id`,
  `role_id`,
  `permission_key`,
  `created_by`,
  `created_at`,
  `updated_by`,
  `updated_at`,
  `deleted`
)
VALUES (
  1001,
  2001,
  'ai:view',
  0,
  CURRENT_TIMESTAMP,
  0,
  CURRENT_TIMESTAMP,
  0
)
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_menu` (
  `tenant_id`,
  `parent_id`,
  `menu_code`,
  `menu_name`,
  `menu_type`,
  `path`,
  `component`,
  `created_by`,
  `created_at`,
  `updated_by`,
  `updated_at`,
  `deleted`,
  `icon`,
  `sort_no`,
  `permission_key`,
  `status`
)
SELECT
  1001,
  `settings_root`.`id`,
  'settings.ai-employees',
  '数字员工',
  'MENU',
  '/settings/ai-employees',
  '@/pages/settings/ai-employees',
  0,
  CURRENT_TIMESTAMP,
  0,
  CURRENT_TIMESTAMP,
  0,
  'RobotOutlined',
  24,
  'ai:view',
  'ENABLED'
FROM (
  SELECT `id`
  FROM `sys_menu`
  WHERE `tenant_id` = 1001
    AND `menu_code` = 'settings.root'
  LIMIT 1
) AS `settings_root`
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `menu_name` = VALUES(`menu_name`),
  `menu_type` = VALUES(`menu_type`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0,
  `icon` = VALUES(`icon`),
  `sort_no` = VALUES(`sort_no`),
  `permission_key` = VALUES(`permission_key`),
  `status` = VALUES(`status`);
