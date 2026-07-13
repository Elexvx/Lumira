-- Existing-database migration for workflow permissions.
-- Fresh databases receive the same records from sql/saas.sql.
INSERT INTO `sys_permission` (
    `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
    `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
VALUES
    ('workflow:view', '查看工作流', 'workflow', 'CORE', NULL, 0, '00000000-0000-0000-0000-000000000000', 0, '00000000-0000-0000-0000-000000000000', 0),
    ('workflow:config', '配置工作流', 'workflow', 'CORE', NULL, 0, '00000000-0000-0000-0000-000000000000', 0, '00000000-0000-0000-0000-000000000000', 0),
    ('workflow:approve', '审批工作流', 'workflow', 'CORE', NULL, 0, '00000000-0000-0000-0000-000000000000', 0, '00000000-0000-0000-0000-000000000000', 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `source_type` = VALUES(`source_type`),
    `plugin_code` = VALUES(`plugin_code`),
    `updated_by` = VALUES(`updated_by`),
    `updated_by_uuid` = VALUES(`updated_by_uuid`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (
    `role_id`, `permission_key`, `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
SELECT
    role_record.`id`, permission_record.`permission_key`,
    0, '00000000-0000-0000-0000-000000000000',
    0, '00000000-0000-0000-0000-000000000000', 0
FROM `sys_role` role_record
JOIN `sys_permission` permission_record
  ON permission_record.`permission_key` IN ('workflow:view', 'workflow:config', 'workflow:approve')
 AND permission_record.`deleted` = 0
WHERE role_record.`role_code` = 'ADMIN'
  AND role_record.`deleted` = 0
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `updated_by_uuid` = VALUES(`updated_by_uuid`),
    `deleted` = 0;
