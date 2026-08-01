-- Restore maintenance-mode settings that were present in fresh databases but
-- missing from the online platform-setting definition migration.
INSERT INTO `sys_platform_setting_definition` (
  `group_code`, `config_key`, `config_name`, `remark`, `default_value`, `reset_value`,
  `sort_no`, `status`, `created_by`, `updated_by`, `deleted`
)
VALUES
  ('BRANDING', 'branding.maintenance-mode-enabled', '维护模式开关', '开启后全站仅显示维护模式页面', 'false', 'false', 140, 'ENABLED', 0, 0, 0),
  ('BRANDING', 'branding.maintenance-title', '维护模式标题', '维护模式页面标题', '系统维护中', '系统维护中', 150, 'ENABLED', 0, 0, 0),
  ('BRANDING', 'branding.maintenance-message', '维护模式说明', '维护模式页面说明', '服务正在升级优化，请稍后再试。', '服务正在升级优化，请稍后再试。', 160, 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `group_code` = VALUES(`group_code`),
  `config_name` = VALUES(`config_name`),
  `remark` = VALUES(`remark`),
  `default_value` = VALUES(`default_value`),
  `reset_value` = VALUES(`reset_value`),
  `sort_no` = VALUES(`sort_no`),
  `status` = 'ENABLED',
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;

INSERT INTO `sys_config` (
  `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`,
  `created_by`, `updated_by`, `deleted`
)
VALUES
  ('branding.maintenance-mode-enabled', '维护模式开关', 'false', 'PLATFORM', 0, '开启后全站仅显示维护模式页面', 0, 0, 0),
  ('branding.maintenance-title', '维护模式标题', '系统维护中', 'PLATFORM', 0, '维护模式页面标题', 0, 0, 0),
  ('branding.maintenance-message', '维护模式说明', '服务正在升级优化，请稍后再试。', 'PLATFORM', 0, '维护模式页面说明', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `config_name` = VALUES(`config_name`),
  `config_scope` = VALUES(`config_scope`),
  `is_system` = VALUES(`is_system`),
  `remark` = VALUES(`remark`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;
