-- Keep persisted menu hierarchy aligned with the frontend route catalog.
-- Catalog routes remain catalog identities even when they redirect to a page.

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `menu_code`, `menu_name`, `menu_type`, `path`, `component`, `icon`,
    `sort_no`, `permission_key`, `status`, `created_by`, `updated_by`, `deleted`
)
VALUES
    (-1113, -1069, 'competition.review-results', '评审结果与申诉', 'MENU',
     '/competitions/review-results', '@/pages/competition/CompetitionReviewResultsPage',
     'FileSearchOutlined', 3, 'review:appeal:submit', 'ENABLED', 0, 0, 0),
    (-1114, -1069, 'certificate.mine', '我的证书', 'MENU',
     '/certificates/mine', '@/pages/certificates/MyCertificatesPage',
     'SafetyCertificateOutlined', 4, NULL, 'ENABLED', 0, 0, 0)
ON DUPLICATE KEY UPDATE
    `parent_id` = VALUES(`parent_id`),
    `menu_name` = VALUES(`menu_name`),
    `menu_type` = VALUES(`menu_type`),
    `path` = VALUES(`path`),
    `component` = VALUES(`component`),
    `icon` = VALUES(`icon`),
    `sort_no` = VALUES(`sort_no`),
    `permission_key` = VALUES(`permission_key`),
    `status` = 'ENABLED',
    `updated_by` = VALUES(`updated_by`),
    `deleted` = 0;

UPDATE `sys_menu`
SET `parent_id` = -1068,
    `sort_no` = 2,
    `status` = 'ENABLED',
    `deleted` = 0,
    `updated_by` = 0
WHERE `menu_code` = 'expert.application';

UPDATE `sys_menu`
SET `status` = 'DISABLED',
    `deleted` = 1,
    `updated_by` = 0
WHERE `menu_code` = 'competition.root';

UPDATE `sys_menu`
SET `parent_id` = 0,
    `menu_name` = '证书管理',
    `component` = 'redirect:/certificates/templates',
    `status` = 'ENABLED',
    `deleted` = 0,
    `updated_by` = 0
WHERE `menu_code` = 'certificate.root';

UPDATE `sys_menu`
SET `parent_id` = -1079,
    `status` = 'ENABLED',
    `deleted` = 0,
    `updated_by` = 0
WHERE `menu_code` IN ('certificate.templates', 'certificate.generate', 'certificate.records');

INSERT INTO `ddd_read_model_version` (
    `context_name`, `scope`, `version`, `last_event_key`, `rebuilt_at`
)
VALUES (
    'platform', 'menu-tree', 1,
    'migration:V202608030002:built-in-navigation-hierarchy', NOW()
)
ON DUPLICATE KEY UPDATE
    `version` = IF(
        `last_event_key` = VALUES(`last_event_key`),
        `version`,
        `version` + 1
    ),
    `last_event_key` = VALUES(`last_event_key`),
    `rebuilt_at` = VALUES(`rebuilt_at`);
