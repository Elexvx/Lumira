-- Add the per-competition award quotas used to generate published ranking lists.
-- The insert is idempotent and does not overwrite an existing competition choice.
SET NAMES utf8mb4;

UPDATE `sys_menu`
SET `status` = 'DISABLED',
    `deleted` = 1,
    `updated_by` = 0
WHERE `menu_code` = 'certificate.generate'
  AND `deleted` = 0;

UPDATE `sys_menu`
SET `status` = 'DISABLED',
    `deleted` = 1,
    `updated_by` = 0
WHERE `menu_code` = 'certificate.generate.create'
  AND `deleted` = 0;

INSERT INTO `competition_config_item_template` (
    `template_code`, `item_type`, `item_key`, `title`, `content_json`, `content_text`,
    `sort_order`, `required_flag`, `enabled`, `deleted`
)
VALUES (
    'DEFAULT',
    'AWARD_SETTINGS',
    'award-rules',
    '获奖设置',
    '{"version":1,"rules":[{"awardName":"一等奖","quota":1},{"awardName":"二等奖","quota":2},{"awardName":"三等奖","quota":3},{"awardName":"优秀奖","quota":5}]}',
    '按评审发布结果的最终排名生成获奖名单。',
    600,
    1,
    1,
    0
)
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`),
    `content_json` = VALUES(`content_json`),
    `content_text` = VALUES(`content_text`),
    `sort_order` = VALUES(`sort_order`),
    `required_flag` = VALUES(`required_flag`),
    `enabled` = VALUES(`enabled`),
    `deleted` = 0,
    `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `competition_config_item` (
    `competition_uuid`, `config_set_id`, `item_type`, `item_key`, `title`, `content_json`, `content_text`,
    `sort_order`, `required_flag`, `enabled`, `created_by`, `created_by_uuid`, `updated_by`, `updated_by_uuid`, `deleted`
)
SELECT
    config_set.`competition_uuid`,
    config_set.`id`,
    template.`item_type`,
    template.`item_key`,
    template.`title`,
    template.`content_json`,
    template.`content_text`,
    template.`sort_order`,
    template.`required_flag`,
    template.`enabled`,
    0,
    NULL,
    0,
    NULL,
    0
FROM `competition_config_set` AS config_set
JOIN `competition_config_item_template` AS template
  ON template.`template_code` = 'DEFAULT'
 AND template.`item_type` = 'AWARD_SETTINGS'
 AND template.`item_key` = 'award-rules'
 AND template.`deleted` = 0
WHERE config_set.`status` IN ('DRAFT', 'PUBLISHED')
  AND config_set.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1
      FROM `competition_config_item` AS item
      WHERE item.`config_set_id` = config_set.`id`
        AND item.`item_type` = 'AWARD_SETTINGS'
        AND item.`item_key` = 'award-rules'
        AND item.`deleted` = 0
  );
