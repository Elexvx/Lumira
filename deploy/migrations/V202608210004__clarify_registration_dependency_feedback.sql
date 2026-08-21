-- Existing installations may have registration enabled while SMS verification
-- remains unavailable. Keep the login feedback accurate and avoid claiming that
-- the registration policy itself is closed.

UPDATE `sys_localization_entry`
SET `default_message` = '暂时无法注册，请联系管理员配置注册与验证码服务',
    `source_ref` = 'lumira-ui/src/pages/user/Login.tsx',
    `updated_by` = 0
WHERE `message_key` = 'page.login.registrationUnavailable'
  AND `deleted` = 0;

UPDATE `sys_localization_translation` translation
JOIN `sys_localization_entry` entry
  ON entry.`id` = translation.`entry_id`
 AND entry.`deleted` = 0
SET translation.`translated_message` = CASE translation.`locale_code`
      WHEN 'zh-CN' THEN '暂时无法注册，请联系管理员配置注册与验证码服务'
      WHEN 'en-US' THEN 'Registration is unavailable. Ask an administrator to configure registration and verification.'
      ELSE translation.`translated_message`
    END,
    translation.`updated_by` = 0
WHERE entry.`message_key` = 'page.login.registrationUnavailable'
  AND translation.`locale_code` IN ('zh-CN', 'en-US')
  AND translation.`deleted` = 0;

UPDATE `sys_localization_release`
SET `bundle_json` = JSON_SET(
      `bundle_json`,
      '$.messages."page.login.registrationUnavailable"',
      CASE `locale_code`
        WHEN 'zh-CN' THEN '暂时无法注册，请联系管理员配置注册与验证码服务'
        WHEN 'en-US' THEN 'Registration is unavailable. Ask an administrator to configure registration and verification.'
        ELSE JSON_UNQUOTE(JSON_EXTRACT(`bundle_json`, '$.messages."page.login.registrationUnavailable"'))
      END
    ),
    `updated_by` = 0
WHERE `locale_code` IN ('zh-CN', 'en-US')
  AND `active_flag` = 1
  AND `deleted` = 0
  AND JSON_VALID(`bundle_json`);
