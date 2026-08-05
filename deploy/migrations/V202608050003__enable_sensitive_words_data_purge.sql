-- The built-in sensitive-words plugin has verified classpath up/down migrations.
-- Enable the guarded purge option for existing installations while keeping the
-- capability disabled for every other plugin unless it explicitly opts in.

UPDATE `sys_plugin_definition`
SET `supports_data_purge` = 1,
    `updated_by` = 0,
    `updated_at` = CURRENT_TIMESTAMP
WHERE `plugin_code` = 'sensitive-words'
  AND `builtin_flag` = 1
  AND `deleted` = 0
  AND `supports_data_purge` <> 1;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'plugin', 'bootstrap', 1,
    'migration:V202608050003:sensitive-words-data-purge', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
