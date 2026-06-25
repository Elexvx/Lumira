INSERT INTO `sys_permission` (`tenant_id`, `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`, `created_by`, `updated_by`, `deleted`)
VALUES
    (1001, 'aiadc:project:view', '查看项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:project:create', '新建项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:project:update', '编辑项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    (1001, 'aiadc:project:delete', '删除项目', 'aiadc', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `source_type` = VALUES(`source_type`),
    `plugin_code` = VALUES(`plugin_code`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`)
VALUES
    (-1091, 1001, 0, 'project.root', '项目', 'CATALOG', '/projects', 'redirect:/projects/management', 'ProjectOutlined', 5, NULL, 'ENABLED', 0, 0, 0),
    (-1092, 1001, -1091, 'project.management', '项目管理', 'MENU', '/projects/management', '@/pages/project', 'ProjectOutlined', 1, 'aiadc:project:view', 'ENABLED', 0, 0, 0),
    (-1093, 1001, -1092, 'project.management.create', '新增项目', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:project:create', 'ENABLED', 0, 0, 0),
    (-1094, 1001, -1092, 'project.management.update', '编辑项目', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:project:update', 'ENABLED', 0, 0, 0),
    (-1095, 1001, -1092, 'project.management.delete', '删除项目', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:project:delete', 'ENABLED', 0, 0, 0)
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
    `deleted` = 0;

INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `role_type`, `default_home_path`, `created_by`, `updated_by`, `deleted`)
VALUES
    (1002, 1001, 'commonuser', 'Common User', 'BUSINESS', '/dashboard/home', 0, 0, 0),
    (1003, 1001, 'EXPERT', 'Expert', 'BUSINESS', '/dashboard/home', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `role_type` = VALUES(`role_type`),
    `default_home_path` = VALUES(`default_home_path`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT r.`tenant_id`, r.`id`, p.`permission_key`, 0, 0, 0
FROM `sys_role` r
JOIN `sys_permission` p ON p.`tenant_id` = r.`tenant_id` AND p.`deleted` = 0
WHERE r.`tenant_id` = 1001
  AND r.`role_code` = 'ADMIN'
  AND r.`deleted` = 0
  AND p.`permission_key` LIKE 'aiadc:project:%'
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`tenant_id`, `role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT r.`tenant_id`, r.`id`, p.`permission_key`, 0, 0, 0
FROM `sys_role` r
JOIN `sys_permission` p ON p.`tenant_id` = r.`tenant_id` AND p.`deleted` = 0
WHERE r.`tenant_id` = 1001
  AND r.`role_code` = 'commonuser'
  AND r.`deleted` = 0
  AND p.`permission_key` IN ('aiadc:project:view', 'aiadc:project:create')
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;
