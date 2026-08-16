-- migration:V202608160003:maintenance-login-role-policy
-- Add the role allowlist used to complete logins while maintenance mode is enabled.
-- Existing custom values are preserved; all writes are idempotent.
SET NAMES utf8mb4;

INSERT INTO `sys_platform_setting_definition` (
    `group_code`, `config_key`, `config_name`, `remark`, `default_value`, `reset_value`,
    `sort_no`, `status`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'BRANDING',
    'branding.maintenance-allowed-role-ids',
    '维护模式允许登录角色',
    '维护模式开启后允许完成登录的角色 ID 列表，使用 JSON 数组保存',
    '[1001]',
    '[1001]',
    180,
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
    'branding.maintenance-allowed-role-ids',
    '维护模式允许登录角色',
    '[1001]',
    'PLATFORM',
    0,
    '维护模式开启后允许完成登录的角色 ID 列表，使用 JSON 数组保存',
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

INSERT INTO `sys_config_metadata` (
    `config_key`, `group_code`, `domain_code`, `value_type`, `sensitivity`, `refresh_policy`,
    `description`, `owner_code`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'branding.maintenance-allowed-role-ids',
    'BRANDING',
    'PLATFORM',
    'JSON',
    'NONE',
    'DYNAMIC',
    '维护模式开启后允许完成登录的角色 ID 列表',
    'lumira-system',
    0,
    0,
    0
)
ON DUPLICATE KEY UPDATE
    `group_code` = VALUES(`group_code`),
    `domain_code` = VALUES(`domain_code`),
    `value_type` = VALUES(`value_type`),
    `sensitivity` = VALUES(`sensitivity`),
    `refresh_policy` = VALUES(`refresh_policy`),
    `description` = VALUES(`description`),
    `owner_code` = VALUES(`owner_code`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;
