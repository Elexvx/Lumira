-- Consolidate the expert library and review workbench under one navigation catalog.
-- Keep the legacy expert root row for compatibility, but retire it from the menu tree.
SET NAMES utf8mb4;

UPDATE `sys_menu` AS expert_management
JOIN `sys_menu` AS review_root
  ON review_root.`menu_code` = 'expert.review.root'
 AND review_root.`deleted` = 0
SET expert_management.`parent_id` = review_root.`id`,
    expert_management.`sort_no` = 1,
    expert_management.`status` = 'ENABLED',
    expert_management.`deleted` = 0,
    expert_management.`updated_by` = 0
WHERE expert_management.`menu_code` = 'expert.management';

UPDATE `sys_menu`
SET `sort_no` = CASE `menu_code`
      WHEN 'expert.review.tasks' THEN 2
      WHEN 'expert.application' THEN 3
      ELSE `sort_no`
    END,
    `status` = 'ENABLED',
    `deleted` = 0,
    `updated_by` = 0
WHERE `menu_code` IN ('expert.review.tasks', 'expert.application');

UPDATE `sys_menu`
SET `status` = 'DISABLED',
    `deleted` = 1,
    `updated_by` = 0
WHERE `menu_code` = 'expert.root'
  AND `deleted` = 0;

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'platform', 'menu-tree', 1, 'migration:V202608130004:consolidate-expert-navigation', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
