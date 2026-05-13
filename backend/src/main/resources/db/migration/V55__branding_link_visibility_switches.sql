INSERT IGNORE INTO `sys_config` (
  `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT DISTINCT
  `tenant_id`,
  'branding.github-link-enabled',
  'GitHub 链接开关',
  'true',
  'PLATFORM',
  0,
  '是否显示顶部 GitHub 图标',
  0,
  0,
  0
FROM `sys_config`
WHERE `deleted` = 0
  AND `config_scope` = 'PLATFORM';

INSERT IGNORE INTO `sys_config` (
  `tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`
)
SELECT DISTINCT
  `tenant_id`,
  'branding.help-link-enabled',
  '帮助链接开关',
  'true',
  'PLATFORM',
  0,
  '是否显示顶部帮助图标',
  0,
  0,
  0
FROM `sys_config`
WHERE `deleted` = 0
  AND `config_scope` = 'PLATFORM';
