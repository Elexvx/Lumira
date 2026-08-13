-- Retire the duplicate expert query entry; expert management already contains
-- the expert and competition filters.
SET NAMES utf8mb4;

UPDATE `sys_menu`
SET `status` = 'DISABLED',
    `deleted` = 1,
    `updated_by` = 0
WHERE `menu_code` = 'expert.query'
  AND `deleted` = 0;

UPDATE `sys_localization_translation` AS translation
JOIN `sys_localization_entry` AS entry
  ON entry.`id` = translation.`entry_id`
 AND entry.`deleted` = 0
SET translation.`deleted` = 1,
    translation.`updated_by` = 0
WHERE entry.`message_key` = 'nav.experts.query'
  AND translation.`deleted` = 0;

UPDATE `sys_localization_entry`
SET `status` = 'DISABLED',
    `deleted` = 1,
    `updated_by` = 0
WHERE `message_key` = 'nav.experts.query'
  AND `deleted` = 0;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'platform', 'menu-tree', 1, 'migration:V202608130003:retire-expert-query-navigation', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
