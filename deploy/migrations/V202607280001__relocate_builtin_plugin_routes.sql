-- Relocate built-in plugin pages for existing databases.
-- Fresh databases receive the same menu relations from lumira-backend/sql/saas.sql.

UPDATE `sys_plugin_menu_rel`
SET `menu_name` = '敏感词管理',
    `route_path` = '/settings/sensitive-words',
    `parent_menu_code` = 'settings.root',
    `sort_no` = 6,
    `updated_at` = CURRENT_TIMESTAMP,
    `deleted` = 0
WHERE `plugin_code` = 'sensitive-words'
  AND `plugin_version` = '1.0.0'
  AND `menu_code` = 'plugin.sensitive-words';

UPDATE `sys_plugin_menu_rel`
SET `menu_name` = '工单反馈',
    `route_path` = '/work-order-feedback',
    `parent_menu_code` = NULL,
    `sort_no` = 17,
    `updated_at` = CURRENT_TIMESTAMP,
    `deleted` = 0
WHERE `plugin_code` = 'work-order-feedback'
  AND `plugin_version` = '1.0.0'
  AND `menu_code` = 'plugin.work-order-feedback';
