-- Existing-database upgrade for the creative maintenance-page copy.
-- Fresh databases receive these values from saas.sql.
-- Only the original built-in defaults are replaced; administrator-customized
-- values in sys_config are intentionally preserved.

START TRANSACTION;

UPDATE `sys_platform_setting_definition`
SET `default_value` = '马上回来，精彩不掉线',
    `reset_value` = '马上回来，精彩不掉线'
WHERE `config_key` = 'branding.maintenance-title'
  AND (`default_value` = '系统维护中' OR `reset_value` = '系统维护中');

UPDATE `sys_platform_setting_definition`
SET `default_value` = '我们正在给系统做个小升级，报名入口很快就回来。请稍等片刻，精彩不会缺席。',
    `reset_value` = '我们正在给系统做个小升级，报名入口很快就回来。请稍等片刻，精彩不会缺席。'
WHERE `config_key` = 'branding.maintenance-message'
  AND (`default_value` = '服务正在升级优化，请稍后再试。' OR `reset_value` = '服务正在升级优化，请稍后再试。');

UPDATE `sys_config`
SET `config_value` = '马上回来，精彩不掉线'
WHERE `config_key` = 'branding.maintenance-title'
  AND `config_scope` = 'PLATFORM'
  AND `deleted` = 0
  AND `config_value` = '系统维护中';

UPDATE `sys_config`
SET `config_value` = '我们正在给系统做个小升级，报名入口很快就回来。请稍等片刻，精彩不会缺席。'
WHERE `config_key` = 'branding.maintenance-message'
  AND `config_scope` = 'PLATFORM'
  AND `deleted` = 0
  AND `config_value` = '服务正在升级优化，请稍后再试。';

COMMIT;
