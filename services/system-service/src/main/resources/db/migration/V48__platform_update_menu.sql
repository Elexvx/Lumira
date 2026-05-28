INSERT INTO `sys_menu` (
  `id`,
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
VALUES
  (3048, 1001, 3002, 'settings.monitoring.update', '平台更新', 'MENU', '/settings/monitoring/update', 'redirect:/settings/monitoring?tab=update', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0, 'CloudSyncOutlined', 10, 'system:update:view', 'ENABLED')
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
