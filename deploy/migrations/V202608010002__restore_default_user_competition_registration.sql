-- Restore the competition-registration entry and permissions for the role
-- assigned to newly registered users. Existing custom roles remain unchanged.
INSERT INTO `sys_permission` (
  `permission_key`, `permission_name`, `permission_group`, `source_type`, `plugin_code`,
  `created_by`, `updated_by`, `deleted`
)
VALUES
  ('aiadc:registration:view', '查看赛事报名', 'aiadc', 'CORE', NULL, 0, 0, 0),
  ('aiadc:registration:create', '创建赛事报名', 'aiadc', 'CORE', NULL, 0, 0, 0),
  ('aiadc:registration:update', '编辑赛事报名', 'aiadc', 'CORE', NULL, 0, 0, 0),
  ('aiadc:registration:pay', '支付报名费用', 'aiadc', 'CORE', NULL, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `permission_group` = VALUES(`permission_group`),
  `source_type` = VALUES(`source_type`),
  `plugin_code` = VALUES(`plugin_code`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT role_row.`id`, permission_row.`permission_key`, 0, 0, 0
FROM `sys_role` role_row
JOIN `sys_permission` permission_row
  ON permission_row.`permission_key` IN (
    'aiadc:registration:view',
    'aiadc:registration:create',
    'aiadc:registration:update',
    'aiadc:registration:pay'
  )
 AND permission_row.`deleted` = 0
WHERE role_row.`deleted` = 0
  AND LOWER(role_row.`role_code`) IN (
    'commonuser',
    LOWER(COALESCE(
      (
        SELECT config_row.`config_value`
        FROM `sys_config` config_row
        WHERE config_row.`config_key` = 'auth.default-registration-role-code'
          AND config_row.`deleted` = 0
        ORDER BY config_row.`id` DESC
        LIMIT 1
      ),
      'commonuser'
    ))
  )
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;

INSERT INTO `sys_menu` (
  `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
  `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT root_menu.`id`, 'competition.registration', '赛事报名', 'MENU',
       '/competitions/register', '@/pages/competition', 'FormOutlined',
       1, NULL, 'ENABLED', 0, 0, 0
FROM `sys_menu` root_menu
WHERE root_menu.`menu_code` = 'registration.root'
  AND root_menu.`deleted` = 0
LIMIT 1
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `menu_name` = VALUES(`menu_name`),
  `menu_type` = VALUES(`menu_type`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `sort_no` = VALUES(`sort_no`),
  `permission_key` = NULL,
  `status` = 'ENABLED',
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;

-- Force active sessions to rebuild their permission snapshot after deployment.
INSERT INTO `ddd_read_model_version` (
  `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
  'IAM', 'permission-snapshot', 1,
  'migration:V202608010002:default-user-registration', NOW()
)
ON DUPLICATE KEY UPDATE
  `version` = IF(
    `last_event_key` = VALUES(`last_event_key`),
    `version`,
    `version` + 1
  ),
  `last_event_key` = VALUES(`last_event_key`),
  `rebuilt_at` = VALUES(`rebuilt_at`);
