-- Rename the global reviewer task entry to the concise user-facing name.
-- Review administration remains available inside the selected competition workspace.
SET NAMES utf8mb4;

UPDATE `sys_menu`
SET `menu_name` = '我的评审',
    `updated_by` = 0
WHERE `menu_code` = 'expert.review.tasks'
  AND `deleted` = 0
  AND `menu_name` IN ('我的评审任务', '我的评审');

UPDATE `sys_localization_entry`
SET `default_message` = '我的评审',
    `updated_by` = 0
WHERE `message_key` = 'nav.expertReview.reviews'
  AND `source_locale` = 'zh-CN'
  AND `deleted` = 0
  AND `default_message` IN ('我的评审任务', '我的评审');

UPDATE `sys_localization_translation`
SET `translated_message` = CASE `locale_code`
      WHEN 'zh-CN' THEN '我的评审'
      WHEN 'en-US' THEN 'My Reviews'
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
  AND `locale_code` IN ('zh-CN', 'en-US');
