-- Keep the global expert-review entry focused on reviewer tasks.
-- Review administration remains available inside the selected competition workspace.
SET NAMES utf8mb4;

UPDATE `sys_menu`
SET `menu_name` = '我的评审任务',
    `permission_key` = 'review:task:view',
    `updated_by` = 0
WHERE `menu_code` = 'expert.review.tasks'
  AND `deleted` = 0;

UPDATE `sys_localization_entry`
SET `default_message` = '我的评审任务',
    `updated_by` = 0
WHERE `message_key` = 'nav.expertReview.reviews'
  AND `source_locale` = 'zh-CN'
  AND `default_message` = '跨赛事评审工作台'
  AND `deleted` = 0;

UPDATE `sys_localization_translation`
SET `translated_message` = CASE `locale_code`
      WHEN 'zh-CN' THEN '我的评审任务'
      WHEN 'en-US' THEN 'My Review Tasks'
      ELSE `translated_message`
    END,
    `updated_by` = 0
WHERE `entry_id` IN (
    SELECT `id`
    FROM `sys_localization_entry`
    WHERE `message_key` = 'nav.expertReview.reviews'
      AND `source_locale` = 'zh-CN'
      AND `deleted` = 0
)
  AND `deleted` = 0
  AND (
      (`locale_code` = 'zh-CN' AND `translated_message` = '跨赛事评审工作台')
      OR (`locale_code` = 'en-US' AND `translated_message` = 'Cross-competition Review Workbench')
  );
