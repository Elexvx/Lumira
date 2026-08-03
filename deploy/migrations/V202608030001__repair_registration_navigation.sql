-- Repair registration navigation on databases initialized before the unified
-- registration catalog existed. The migration is intentionally idempotent:
-- menu rows are matched by menu_code and active participant roles are granted
-- the permissions required by the page they can already reach through appeals.

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

INSERT INTO `sys_menu` (
  `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
  `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
VALUES (
  0, 'registration.root', '报名', 'CATALOG', '/registration',
  'redirect:/competitions/register', 'FormOutlined', 4, NULL, 'ENABLED', 0, 0, 0
)
ON DUPLICATE KEY UPDATE
  `parent_id` = 0,
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

-- V202607290002 used the legacy delete-button id for the review-results page.
-- Restore the button by menu_code before creating the review page with its own id.
INSERT INTO `sys_menu` (
  `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
  `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT management_menu.`id`, 'competition.management.delete', '删除赛事', 'BUTTON',
       NULL, NULL, NULL, 3, 'aiadc:competition:delete', 'ENABLED', 0, 0, 0
FROM `sys_menu` management_menu
WHERE management_menu.`menu_code` = 'competition.management'
  AND management_menu.`deleted` = 0
LIMIT 1
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `menu_name` = VALUES(`menu_name`),
  `menu_type` = 'BUTTON',
  `path` = NULL,
  `component` = NULL,
  `icon` = NULL,
  `sort_no` = VALUES(`sort_no`),
  `permission_key` = VALUES(`permission_key`),
  `status` = 'ENABLED',
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;

INSERT INTO `sys_menu` (
  `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
  `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT registration_root.`id`, 'competition.registration', '赛事报名', 'MENU',
       '/competitions/register', '@/pages/competition', 'FormOutlined',
       1, NULL, 'ENABLED', 0, 0, 0
FROM `sys_menu` registration_root
WHERE registration_root.`menu_code` = 'registration.root'
  AND registration_root.`deleted` = 0
LIMIT 1
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `menu_name` = VALUES(`menu_name`),
  `menu_type` = 'MENU',
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `sort_no` = VALUES(`sort_no`),
  `permission_key` = NULL,
  `status` = 'ENABLED',
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;

INSERT INTO `sys_menu` (
  `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
  `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT registration_root.`id`, 'activity.registration', '活动报名', 'MENU',
       '/activities/register', '@/pages/competition', 'CalendarOutlined',
       2, NULL, 'ENABLED', 0, 0, 0
FROM `sys_menu` registration_root
WHERE registration_root.`menu_code` = 'registration.root'
  AND registration_root.`deleted` = 0
LIMIT 1
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `menu_name` = VALUES(`menu_name`),
  `menu_type` = 'MENU',
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `sort_no` = VALUES(`sort_no`),
  `permission_key` = NULL,
  `status` = 'ENABLED',
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;

INSERT INTO `sys_menu` (
  `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
  `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT registration_root.`id`, 'competition.review-results', '评审结果与申诉', 'MENU',
       '/competitions/review-results', '@/pages/competition/CompetitionReviewResultsPage',
       'FileSearchOutlined', 3, 'review:appeal:submit', 'ENABLED', 0, 0, 0
FROM `sys_menu` registration_root
WHERE registration_root.`menu_code` = 'registration.root'
  AND registration_root.`deleted` = 0
LIMIT 1
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `menu_name` = VALUES(`menu_name`),
  `menu_type` = 'MENU',
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `sort_no` = VALUES(`sort_no`),
  `permission_key` = VALUES(`permission_key`),
  `status` = 'ENABLED',
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;

INSERT INTO `sys_role_permission` (`role_id`, `permission_key`, `created_by`, `updated_by`, `deleted`)
SELECT participant_role.`id`, registration_permission.`permission_key`, 0, 0, 0
FROM `sys_role` participant_role
JOIN `sys_permission` registration_permission
  ON registration_permission.`permission_key` IN (
    'aiadc:registration:view',
    'aiadc:registration:create',
    'aiadc:registration:update',
    'aiadc:registration:pay'
  )
 AND registration_permission.`deleted` = 0
WHERE participant_role.`deleted` = 0
  AND (
    LOWER(participant_role.`role_code`) = 'commonuser'
    OR LOWER(participant_role.`role_code`) = LOWER(COALESCE(
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
    OR EXISTS (
      SELECT 1
      FROM `sys_role_permission` appeal_permission
      WHERE appeal_permission.`role_id` = participant_role.`id`
        AND appeal_permission.`permission_key` = 'review:appeal:submit'
        AND appeal_permission.`deleted` = 0
    )
  )
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;

INSERT INTO `ddd_read_model_version` (
  `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
  'IAM', 'permission-snapshot', 1,
  'migration:V202608030001:registration-navigation-repair', NOW()
)
ON DUPLICATE KEY UPDATE
  `version` = IF(
    `last_event_key` = VALUES(`last_event_key`),
    `version`,
    `version` + 1
  ),
  `last_event_key` = VALUES(`last_event_key`),
  `rebuilt_at` = VALUES(`rebuilt_at`);
