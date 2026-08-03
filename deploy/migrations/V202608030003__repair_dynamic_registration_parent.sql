-- Legacy databases allocate registration.root with an auto-increment id.
-- Repair children introduced by the built-in hierarchy migration by resolving
-- the parent through its stable menu_code instead of a fresh-bootstrap id.

UPDATE `sys_menu` child_menu
JOIN `sys_menu` registration_root
  ON registration_root.`menu_code` = 'registration.root'
 AND registration_root.`deleted` = 0
SET child_menu.`parent_id` = registration_root.`id`,
    child_menu.`status` = 'ENABLED',
    child_menu.`deleted` = 0,
    child_menu.`updated_by` = 0
WHERE child_menu.`menu_code` IN (
    'competition.review-results',
    'certificate.mine'
);

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'platform', 'menu-tree', 1,
    'migration:V202608030003:dynamic-registration-parent', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
