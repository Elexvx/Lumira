INSERT INTO `sys_plugin_definition` (
    `plugin_code`, `plugin_name`, `plugin_type`, `description`, `author`, `plugin_api_version`,
    `builtin_flag`, `status`, `sort_no`, `schema_mode`, `supports_hot_disable`, `supports_data_purge`,
    `runtime_contributions_json`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'builtin-mock-sms', '内置模拟短信验证码', 'BUSINESS', 'Lumira managed mock SMS provider for local verification-code debugging.',
    'Lumira', '1.0', 1, 'DISABLED', 910, 'SHARED', 1, 0,
    JSON_ARRAY('sms-provider', 'verification-debug-modal'), 0, 0, 0
)
ON DUPLICATE KEY UPDATE
    `plugin_name` = VALUES(`plugin_name`),
    `plugin_type` = VALUES(`plugin_type`),
    `description` = VALUES(`description`),
    `author` = VALUES(`author`),
    `plugin_api_version` = VALUES(`plugin_api_version`),
    `builtin_flag` = VALUES(`builtin_flag`),
    `sort_no` = VALUES(`sort_no`),
    `schema_mode` = VALUES(`schema_mode`),
    `supports_hot_disable` = VALUES(`supports_hot_disable`),
    `supports_data_purge` = VALUES(`supports_data_purge`),
    `runtime_contributions_json` = VALUES(`runtime_contributions_json`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

INSERT INTO `sys_plugin_version` (
    `plugin_code`, `version`, `min_platform_version`, `install_status`, `load_status`, `health_status`,
    `lifecycle_status`, `schema_status`, `is_active`, `rollbackable`, `metadata_json`, `validation_report_json`,
    `installed_at`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'builtin-mock-sms', '1.0.0', '1.0.0', 'INSTALLED', 'LOADED', 'HEALTHY',
    'DISABLED', 'READY', 1, 0,
    JSON_OBJECT(
        'pluginCode', 'builtin-mock-sms',
        'pluginName', '内置模拟短信验证码',
        'version', '1.0.0',
        'kind', 'BUSINESS',
        'builtin', true
    ),
    JSON_OBJECT('builtin', true, 'status', 'VERIFIED'), CURRENT_TIMESTAMP, 0, 0, 0
)
ON DUPLICATE KEY UPDATE
    `min_platform_version` = VALUES(`min_platform_version`),
    `install_status` = VALUES(`install_status`),
    `load_status` = VALUES(`load_status`),
    `health_status` = VALUES(`health_status`),
    `schema_status` = VALUES(`schema_status`),
    `is_active` = VALUES(`is_active`),
    `metadata_json` = VALUES(`metadata_json`),
    `validation_report_json` = VALUES(`validation_report_json`),
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

-- Consolidate the unfinished local debug provider aliases without enabling the
-- plugin. Existing installations remain fail-closed until an administrator
-- explicitly enables builtin-mock-sms.
UPDATE `sys_config`
SET `config_value` = 'builtin_mock_sms',
    `updated_by` = 0,
    `updated_by_uuid` = NULL
WHERE `config_key` = 'verification.sms.provider'
  AND LOWER(TRIM(`config_value`)) IN ('debug', 'mock')
  AND `deleted` = 0;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'plugin', 'bootstrap', 1,
    'migration:V202608210005:builtin-mock-sms', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
