-- Built-in mock payment participates in the existing payment checkout surface.
-- Keep provider callbacks and refunds, but retire the standalone SPA route.
UPDATE `sys_plugin_definition`
SET `description` = 'Lumira managed mock payment provider adapted to the standard payment checkout flow.',
    `runtime_contributions_json` = JSON_ARRAY(
        'payment-provider', 'checkout-adapter', 'callbacks', 'refunds'
    ),
    `updated_by` = 0,
    `updated_by_uuid` = NULL
WHERE `plugin_code` = 'builtin-mock-payment'
  AND `deleted` = 0;

UPDATE `sys_plugin_version`
SET `metadata_json` = JSON_SET(
        COALESCE(`metadata_json`, JSON_OBJECT()),
        '$.checkoutMode', 'ADAPTER'
    ),
    `updated_by` = 0,
    `updated_by_uuid` = NULL
WHERE `plugin_code` = 'builtin-mock-payment'
  AND `is_active` = 1
  AND `deleted` = 0;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'plugin', 'bootstrap', 1,
    'migration:V202608210006:adapt-builtin-mock-payment-checkout', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
