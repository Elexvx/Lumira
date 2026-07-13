-- Existing-database migration. Fresh databases receive this record from saas.sql.
INSERT INTO `sys_config` (
    `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`,
    `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
VALUES (
    'account.activation.url', '账户激活地址', 'http://localhost:8000/account-activation',
    'PLATFORM', 1, '前端账户激活页面地址',
    0, '00000000-0000-0000-0000-000000000000',
    0, '00000000-0000-0000-0000-000000000000', 0
)
ON DUPLICATE KEY UPDATE
    `config_name` = VALUES(`config_name`),
    `config_scope` = VALUES(`config_scope`),
    `is_system` = VALUES(`is_system`),
    `remark` = VALUES(`remark`),
    `updated_by` = VALUES(`updated_by`),
    `updated_by_uuid` = VALUES(`updated_by_uuid`),
    `deleted` = 0;
