-- Restore routable built-in pages after database-backed navigation became
-- authoritative. Keep the repair idempotent for existing installations.

UPDATE `sys_menu`
SET `permission_key` = CASE `menu_code`
        WHEN 'competition.registration' THEN 'aiadc:registration:view'
        WHEN 'activity.registration' THEN 'aiadc:activity:create'
    END,
    `status` = 'ENABLED',
    `updated_by` = 0,
    `deleted` = 0
WHERE `menu_code` IN ('competition.registration', 'activity.registration');

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
    `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    -1115, 0, 'workflow.root', '工作流', 'CATALOG', '/workflows',
    'redirect:/workflows/tasks', 'BranchesOutlined', 9, NULL, 'ENABLED', 0, 0, 0
)
ON DUPLICATE KEY UPDATE
    `parent_id` = 0,
    `menu_name` = VALUES(`menu_name`),
    `menu_type` = VALUES(`menu_type`),
    `path` = VALUES(`path`),
    `component` = VALUES(`component`),
    `icon` = VALUES(`icon`),
    `sort_no` = VALUES(`sort_no`),
    `permission_key` = VALUES(`permission_key`),
    `status` = 'ENABLED',
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
    `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT -1116, workflow_root.`id`, 'workflow.tasks', '我的审批', 'MENU',
       '/workflows/tasks', '@/pages/workflow/WorkflowTasksPage', 'AuditOutlined',
       1, 'workflow:approve', 'ENABLED', 0, 0, 0
FROM `sys_menu` workflow_root
WHERE workflow_root.`menu_code` = 'workflow.root'
  AND workflow_root.`deleted` = 0
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
    `status` = 'ENABLED',
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
    `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT -1117, workflow_root.`id`, 'workflow.config', '工作流配置', 'MENU',
       '/workflows/config', '@/pages/workflow/WorkflowConfigPage', 'BranchesOutlined',
       2, 'workflow:config', 'ENABLED', 0, 0, 0
FROM `sys_menu` workflow_root
WHERE workflow_root.`menu_code` = 'workflow.root'
  AND workflow_root.`deleted` = 0
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
    `status` = 'ENABLED',
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

-- Role editing previously removed permissions that were not represented by
-- the page tree. Repair the built-in administrator from the active catalog.
INSERT INTO `sys_role_permission` (
    `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`
)
SELECT administrator_role.`id`, permission_row.`permission_key`, 0, 0, 0
FROM `sys_role` administrator_role
JOIN `sys_permission` permission_row
  ON permission_row.`deleted` = 0
WHERE administrator_role.`deleted` = 0
  AND LOWER(administrator_role.`role_code`) = 'admin'
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES
    ('platform', 'menu-tree', 1, 'migration:V202608050002:admin-route-navigation', NOW()),
    ('IAM', 'permission-snapshot', 1, 'migration:V202608050002:admin-route-navigation', NOW())
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
