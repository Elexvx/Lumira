-- Registration is a built-in authentication capability. SMS configuration
-- still controls whether a verification code can be delivered, but operators
-- can no longer close the registration policy independently.

DELETE FROM `sys_config`
WHERE `config_key` = 'security.registration-enabled';

INSERT INTO `sys_localization_entry` (
  `namespace_id`, `message_key`, `source_locale`, `default_message`,
  `source_type`, `source_ref`, `created_by`, `updated_by`, `deleted`
)
SELECT namespace.`id`, message.`message_key`, 'zh-CN', message.`default_message`,
       'UI', 'lumira-ui/src/pages/user/Login.tsx', 0, 0, 0
FROM `sys_localization_namespace` namespace
JOIN (
  SELECT 'page.login.backToLogin' AS `message_key`, '返回登录' AS `default_message`
  UNION ALL SELECT 'page.login.registerAndLogin', '注册并登录'
) message
WHERE namespace.`namespace_code` = 'page'
  AND namespace.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `default_message` = VALUES(`default_message`),
  `source_ref` = VALUES(`source_ref`),
  `updated_by` = 0,
  `deleted` = 0;

INSERT INTO `sys_localization_translation` (
  `entry_id`, `locale_code`, `translated_message`, `translation_status`,
  `machine_generated`, `review_status`, `created_by`, `updated_by`, `deleted`
)
SELECT entry.`id`, locale.`locale_code`,
       CASE
         WHEN entry.`message_key` = 'page.login.backToLogin' AND locale.`locale_code` = 'zh-CN' THEN '返回登录'
         WHEN entry.`message_key` = 'page.login.backToLogin' THEN 'Back to login'
         WHEN entry.`message_key` = 'page.login.registerAndLogin' AND locale.`locale_code` = 'zh-CN' THEN '注册并登录'
         ELSE 'Create account and log in'
       END,
       'TRANSLATED', 0, 'APPROVED', 0, 0, 0
FROM `sys_localization_entry` entry
JOIN (
  SELECT 'zh-CN' AS `locale_code`
  UNION ALL SELECT 'en-US'
) locale
WHERE entry.`message_key` IN ('page.login.backToLogin', 'page.login.registerAndLogin')
  AND entry.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `translated_message` = VALUES(`translated_message`),
  `translation_status` = 'TRANSLATED',
  `review_status` = 'APPROVED',
  `updated_by` = 0,
  `deleted` = 0;

UPDATE `sys_localization_release`
SET `bundle_json` = JSON_SET(
      `bundle_json`,
      '$.messages."page.login.backToLogin"',
      CASE `locale_code` WHEN 'zh-CN' THEN '返回登录' ELSE 'Back to login' END,
      '$.messages."page.login.registerAndLogin"',
      CASE `locale_code` WHEN 'zh-CN' THEN '注册并登录' ELSE 'Create account and log in' END
    ),
    `updated_by` = 0
WHERE `locale_code` IN ('zh-CN', 'en-US')
  AND `active_flag` = 1
  AND `deleted` = 0
  AND JSON_VALID(`bundle_json`);
