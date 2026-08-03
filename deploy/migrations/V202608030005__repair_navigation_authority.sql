-- Keep menu ownership in the database after the hierarchy repair. Resolve all
-- built-in parents through stable menu_code values and restore competition.root
-- only when it still owns active custom children that would otherwise be hidden.

UPDATE `sys_menu` child_menu
JOIN `sys_menu` parent_menu
  ON parent_menu.`menu_code` = 'registration.root'
 AND parent_menu.`deleted` = 0
SET child_menu.`parent_id` = parent_menu.`id`,
    child_menu.`updated_by` = 0
WHERE child_menu.`menu_code` IN (
    'competition.registration',
    'activity.registration',
    'competition.review-results',
    'certificate.mine'
);

UPDATE `sys_menu` child_menu
JOIN `sys_menu` parent_menu
  ON parent_menu.`menu_code` = 'expert.review.root'
 AND parent_menu.`deleted` = 0
SET child_menu.`parent_id` = parent_menu.`id`,
    child_menu.`updated_by` = 0
WHERE child_menu.`menu_code` IN (
    'expert.review.tasks',
    'expert.application'
);

UPDATE `sys_menu` child_menu
JOIN `sys_menu` parent_menu
  ON parent_menu.`menu_code` = 'certificate.root'
 AND parent_menu.`deleted` = 0
SET child_menu.`parent_id` = parent_menu.`id`,
    child_menu.`updated_by` = 0
WHERE child_menu.`menu_code` IN (
    'certificate.templates',
    'certificate.generate',
    'certificate.records'
);

UPDATE `sys_menu` competition_root
JOIN `sys_menu` custom_child
  ON custom_child.`parent_id` = competition_root.`id`
 AND custom_child.`deleted` = 0
 AND custom_child.`status` = 'ENABLED'
SET competition_root.`status` = 'ENABLED',
    competition_root.`deleted` = 0,
    competition_root.`updated_by` = 0
WHERE competition_root.`menu_code` = 'competition.root';

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'platform', 'menu-tree', 1,
    'migration:V202608030005:navigation-authority', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
