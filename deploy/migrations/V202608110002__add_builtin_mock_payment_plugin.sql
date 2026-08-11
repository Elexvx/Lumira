CREATE TABLE IF NOT EXISTS `payment_builtin_mock_callback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notify_id` varchar(128) NOT NULL,
  `order_no` varchar(64) NOT NULL,
  `provider_trade_no` varchar(128) NOT NULL,
  `outcome` varchar(32) NOT NULL,
  `trade_status` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `scheduled_at` datetime NOT NULL,
  `next_retry_at` datetime DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `max_retry` int NOT NULL DEFAULT '8',
  `claim_token` varchar(128) DEFAULT NULL,
  `claim_expires_at` datetime DEFAULT NULL,
  `payload_json` longtext DEFAULT NULL,
  `last_error` varchar(1024) DEFAULT NULL,
  `processed_at` datetime DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_by_uuid` char(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `updated_by_uuid` char(36) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_builtin_mock_notify` (`notify_id`),
  UNIQUE KEY `uk_payment_builtin_mock_order_active` (`order_no`,`deleted`),
  KEY `idx_payment_builtin_mock_due` (`deleted`,`status`,`next_retry_at`,`scheduled_at`,`id`),
  KEY `idx_payment_builtin_mock_claim` (`claim_token`),
  KEY `idx_payment_builtin_mock_trade` (`provider_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @builtin_mock_attempt_no_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `competition_payment_order_task` ADD COLUMN `attempt_no` int NOT NULL DEFAULT ''1'' AFTER `simulated_role_id`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'competition_payment_order_task'
    AND column_name = 'attempt_no'
);
PREPARE builtin_mock_attempt_no_statement FROM @builtin_mock_attempt_no_sql;
EXECUTE builtin_mock_attempt_no_statement;
DEALLOCATE PREPARE builtin_mock_attempt_no_statement;

INSERT INTO `sys_plugin_definition` (
    `plugin_code`, `plugin_name`, `plugin_type`, `description`, `author`, `plugin_api_version`,
    `builtin_flag`, `status`, `sort_no`, `schema_mode`, `supports_hot_disable`, `supports_data_purge`,
    `runtime_contributions_json`, `created_by`, `updated_by`, `deleted`
)
VALUES (
    'builtin-mock-payment', '内置模拟支付', 'BUSINESS', 'Lumira managed mock payment provider for end-to-end payment testing.',
    'Lumira', '1.0', 1, 'DISABLED', 900, 'SHARED', 1, 0,
    JSON_ARRAY('payment-provider', 'checkout-route', 'callbacks', 'refunds'), 0, 0, 0
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
    'builtin-mock-payment', '1.0.0', '1.0.0', 'INSTALLED', 'LOADED', 'HEALTHY',
    'DISABLED', 'READY', 1, 0,
    JSON_OBJECT(
        'pluginCode', 'builtin-mock-payment',
        'pluginName', '内置模拟支付',
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

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'plugin', 'bootstrap', 1,
    'migration:V202608110002:builtin-mock-payment', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
