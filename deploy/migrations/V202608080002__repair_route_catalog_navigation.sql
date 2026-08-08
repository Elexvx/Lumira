-- Keep upgraded installations aligned with the frontend route catalog. The
-- menu tree is authoritative at runtime, so catalog roots must remain enabled
-- and keep components that exist in the current frontend bundle.

UPDATE `sys_menu`
SET `component` = CASE `menu_code`
        WHEN 'data.management.root' THEN '@/pages/DataManagementLandingPage'
        WHEN 'certificate.root' THEN 'redirect:/certificates/mine'
        WHEN 'user.center.root' THEN '@/layouts/SettingsLayout/SettingsLayout'
        WHEN 'user.center.personal' THEN '@/layouts/SettingsLayout/SettingsLayout'
        WHEN 'settings.root' THEN '@/layouts/SettingsLayout/SettingsLayout'
        WHEN 'settings.files' THEN '@/pages/files/Center'
        WHEN 'settings.notifications' THEN '@/pages/settings/notifications/NotificationsPage'
        WHEN 'settings.monitoring' THEN '@/pages/settings/monitoring/MonitoringPage'
        WHEN 'settings.monitoring.api-docs' THEN '@/pages/settings/monitoring/MonitoringPage'
        WHEN 'settings.plugins' THEN '@/pages/settings/plugins/PluginsPage'
        WHEN 'localization.root' THEN '@/pages/settings/localization/LocalizationPage'
        ELSE `component`
    END,
    `updated_by` = 0
WHERE `menu_code` IN (
    'data.management.root',
    'certificate.root',
    'user.center.root',
    'user.center.personal',
    'settings.root',
    'settings.files',
    'settings.notifications',
    'settings.monitoring',
    'settings.monitoring.api-docs',
    'settings.plugins',
    'localization.root'
);

UPDATE `sys_menu`
SET `status` = 'ENABLED',
    `deleted` = 0,
    `updated_by` = 0
WHERE `menu_code` IN (
    'registration.root',
    'certificate.root',
    'expert.root',
    'expert.review.root',
    'workflow.root',
    'expert.application'
);

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
    `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT -1112, data_root.`id`, 'competition.registrations', '报名团队资料', 'MENU',
       '/competitions/registrations', '@/pages/competition/CompetitionRegistrationDataPage', 'TeamOutlined',
       2, 'aiadc:registration:view', 'ENABLED', 0, 0, 0
FROM `sys_menu` data_root
WHERE data_root.`menu_code` = 'data.management.root'
  AND data_root.`deleted` = 0
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
    `updated_by` = 0,
    `deleted` = 0;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES ('platform', 'menu-tree', 1, 'migration:V202608080002:route-catalog-navigation', NOW())
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
