-- Restore AIADC project management navigation and role permissions on existing role-only databases.
-- Safe to run repeatedly.

INSERT INTO `sys_permission` (
    `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    ('aiadc:project:view', '查看项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:create', '新建项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:update', '编辑项目', 'aiadc', 'CORE', NULL, 0, 0, 0),
    ('aiadc:project:delete', '删除项目', 'aiadc', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `permission_name` = VALUES(`permission_name`),
    `permission_group` = VALUES(`permission_group`),
    `source_type` = VALUES(`source_type`),
    `plugin_code` = VALUES(`plugin_code`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
    `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
VALUES
    (-1100, 0, 'data.management.root', '数据管理', 'CATALOG', '/data-management', 'redirect:/competitions/management', 'DatabaseOutlined', 3, NULL, 'ENABLED', 0, 0, 0),
    (-956, -1100, 'files.download-center', '下载中心', 'MENU', '/data-management/download-center', '@/pages/files/DownloadCenter', 'DownloadOutlined', 6, 'download:center:view', 'ENABLED', 0, 0, 0),
    (-1101, -1100, 'data.query-center', '查询中心', 'CATALOG', '/data-management/query-center', 'redirect:/team/search', 'SearchOutlined', 7, NULL, 'ENABLED', 0, 0, 0),
    (-1070, 0, 'competition.root', '赛事', 'CATALOG', '/competitions', 'redirect:/competitions/management', 'TrophyOutlined', 91, NULL, 'DISABLED', 0, 0, 1),
    (-1071, -1100, 'competition.management', '赛事管理', 'MENU', '/competitions/management', '@/pages/competition', 'TrophyOutlined', 1, 'aiadc:competition:view', 'ENABLED', 0, 0, 0),
    (-1075, -1070, 'competition.registration', '赛事报名', 'MENU', '/competitions/register', '@/pages/competition', 'FormOutlined', 2, 'aiadc:registration:create', 'DISABLED', 0, 0, 1),
    (-1091, 0, 'project.root', '项目', 'CATALOG', '/projects', 'redirect:/projects/management', 'ProjectOutlined', 92, NULL, 'DISABLED', 0, 0, 1),
    (-1092, -1100, 'project.management', '项目管理', 'MENU', '/projects/management', '@/pages/project', 'ProjectOutlined', 3, 'aiadc:project:view', 'ENABLED', 0, 0, 0),
    (-1096, -1101, 'project.search', '项目查询', 'MENU', '/projects/search', '@/pages/project', 'SearchOutlined', 2, 'aiadc:project:view', 'ENABLED', 0, 0, 0),
    (-1093, -1092, 'project.management.create', '新增项目', 'BUTTON', NULL, NULL, NULL, 1, 'aiadc:project:create', 'ENABLED', 0, 0, 0),
    (-1094, -1092, 'project.management.update', '编辑项目', 'BUTTON', NULL, NULL, NULL, 2, 'aiadc:project:update', 'ENABLED', 0, 0, 0),
    (-1095, -1092, 'project.management.delete', '删除项目', 'BUTTON', NULL, NULL, NULL, 3, 'aiadc:project:delete', 'ENABLED', 0, 0, 0)
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
    `deleted` = VALUES(`deleted`);

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT r.`id`, p.`permission_key`, 0, 0, 0
FROM `sys_role` r
JOIN `sys_permission` p ON p.`deleted` = 0
WHERE r.`deleted` = 0
  AND r.`role_code` = 'ADMIN'
  AND p.`permission_key` LIKE 'aiadc:project:%'
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT r.`id`, p.`permission_key`, 0, 0, 0
FROM `sys_role` r
JOIN `sys_permission` p ON p.`deleted` = 0
WHERE r.`deleted` = 0
  AND r.`role_code` = 'commonuser'
  AND p.`permission_key` IN ('aiadc:project:view', 'aiadc:project:create')
ON DUPLICATE KEY UPDATE
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
SELECT 'IAM', 'permission-snapshot', 1, 'repair.project-management-menu', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM `ddd_read_model_version`
    WHERE `context_name` = 'IAM' AND `scope` = 'permission-snapshot'
);

UPDATE `ddd_read_model_version`
SET `version` = `version` + 1,
    `last_event_key` = 'repair.project-management-menu',
    `rebuilt_at` = CURRENT_TIMESTAMP
WHERE `context_name` = 'IAM' AND `scope` = 'permission-snapshot';
