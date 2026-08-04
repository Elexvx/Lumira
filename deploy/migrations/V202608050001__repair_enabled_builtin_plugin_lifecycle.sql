-- Repair built-in plugin rows created with an enabled definition but an
-- INSTALLED active version. The disable transition requires the active version
-- to be in ENABLED lifecycle state, so the inconsistent seed made disable roll
-- back with "Plugin state changed, please retry".

UPDATE `sys_plugin_version` version_row
JOIN `sys_plugin_definition` definition_row
  ON definition_row.`plugin_code` = version_row.`plugin_code`
 AND definition_row.`deleted` = 0
 AND definition_row.`status` = 'ENABLED'
SET version_row.`lifecycle_status` = 'ENABLED',
    version_row.`updated_at` = CURRENT_TIMESTAMP
WHERE version_row.`plugin_code` IN ('sensitive-words', 'work-order-feedback')
  AND version_row.`is_active` = 1
  AND version_row.`deleted` = 0
  AND version_row.`lifecycle_status` = 'INSTALLED'
  AND version_row.`install_status` IN ('INSTALLED', 'LOADED')
  AND version_row.`load_status` = 'LOADED'
  AND version_row.`schema_status` = 'READY';

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'plugin', 'bootstrap', 1,
    'migration:V202608050001:plugin-lifecycle-repair', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
