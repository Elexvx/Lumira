-- Existing-database upgrade for the optional maintenance countdown setting.
-- Fresh databases receive the same definition and empty value from saas.sql.
-- The field is optional: an empty value means that the public maintenance page
-- does not render a countdown.

INSERT INTO `sys_platform_setting_definition` (
    `group_code`, `config_key`, `config_name`, `remark`, `default_value`, `reset_value`,
    `sort_no`, `status`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'BRANDING',
    'branding.maintenance-end-at',
    '维护结束时间',
    '可选的维护倒计时结束时间，使用 ISO-8601 格式；留空不显示倒计时',
    '',
    '',
    170,
    'ENABLED',
    0,
    0,
    0
)
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
    `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
VALUES (
    'branding.maintenance-end-at',
    '维护结束时间',
    '',
    'PLATFORM',
    0,
    '可选的维护倒计时结束时间，使用 ISO-8601 格式；留空不显示倒计时',
    0,
    NULL,
    0,
    NULL,
    0
)
ON DUPLICATE KEY UPDATE
    `config_name` = VALUES(`config_name`),
    `config_scope` = VALUES(`config_scope`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `updated_by_uuid` = VALUES(`updated_by_uuid`),
    `deleted` = 0;
