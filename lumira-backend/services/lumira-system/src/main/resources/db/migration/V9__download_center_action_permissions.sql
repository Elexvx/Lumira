INSERT INTO `sys_permission` (
  `tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `updated_by`, `deleted`
) VALUES
  (1001, 'download:center:create', '上传下载中心文件', 'download', 'CORE', NULL, 0, 0, 0),
  (1001, 'download:center:update', '编辑下载中心文件', 'download', 'CORE', NULL, 0, 0, 0),
  (1001, 'download:center:delete', '删除下载中心文件', 'download', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `source_type` = VALUES(`source_type`),
  `plugin_code` = VALUES(`plugin_code`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT IGNORE INTO `sys_role_permission` (
  `tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`
) VALUES
  (1001, 2001, 'download:center:create', 0, 0, 0),
  (1001, 2001, 'download:center:update', 0, 0, 0),
  (1001, 2001, 'download:center:delete', 0, 0, 0);
