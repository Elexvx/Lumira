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
VALUES
  (1001, 'system:update:view', '查看平台更新', 'system', 'CORE', NULL, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0),
  (1001, 'system:update:check', '检查平台更新', 'system', 'CORE', NULL, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0)
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
VALUES
  (1001, 2001, 'system:update:view', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0),
  (1001, 2001, 'system:update:check', 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0)
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;
