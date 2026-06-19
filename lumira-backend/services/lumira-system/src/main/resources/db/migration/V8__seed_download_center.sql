INSERT INTO `sys_permission` (
  `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `updated_by`, `deleted`
) VALUES (
  1001, 'download:center:view', '查看下载中心', 'download', 'CORE', NULL, 0, 0, 0
) ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `source_type` = VALUES(`source_type`),
  `plugin_code` = VALUES(`plugin_code`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_menu` (
  `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `created_by`, `updated_by`, `deleted`, `icon`, `sort_no`, `permission_key`, `status`
) VALUES (
  1001, 0, 'files.download-center', '下载中心', 'MENU', '/download-center', '@/pages/files/DownloadCenter', 0, 0, 0, 'DownloadOutlined', 1, 'download:center:view', 'ENABLED'
) ON DUPLICATE KEY UPDATE
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

INSERT IGNORE INTO `sys_role_permission` (
  `tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`
) VALUES
  (1001, 2001, 'download:center:view', 0, 0, 0),
  (1001, 2003, 'download:center:view', 1001, 1001, 0);
