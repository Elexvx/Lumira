-- Rename the login registration action while keeping runtime releases and the
-- editable localization catalog aligned for existing installations.

UPDATE `sys_localization_entry`
SET `default_message` = '注册账号',
    `source_ref` = 'lumira-ui/src/pages/user/Login.tsx',
    `updated_by` = 0
WHERE `message_key` = 'page.login.joinUs'
  AND `deleted` = 0;

UPDATE `sys_localization_translation` translation
JOIN `sys_localization_entry` entry
  ON entry.`id` = translation.`entry_id`
 AND entry.`deleted` = 0
SET translation.`translated_message` = CASE translation.`locale_code`
      WHEN 'zh-CN' THEN '注册账号'
      WHEN 'en-US' THEN 'Create account'
      ELSE translation.`translated_message`
    END,
    translation.`updated_by` = 0
WHERE entry.`message_key` = 'page.login.joinUs'
  AND translation.`locale_code` IN ('zh-CN', 'en-US')
  AND translation.`deleted` = 0;

UPDATE `sys_localization_release`
SET `bundle_json` = JSON_SET(
      `bundle_json`,
      '$.messages."page.login.joinUs"',
      CASE `locale_code`
        WHEN 'zh-CN' THEN '注册账号'
        WHEN 'en-US' THEN 'Create account'
        ELSE JSON_UNQUOTE(JSON_EXTRACT(`bundle_json`, '$.messages."page.login.joinUs"'))
      END
    ),
    `updated_by` = 0
WHERE `locale_code` IN ('zh-CN', 'en-US')
  AND `active_flag` = 1
  AND `deleted` = 0
  AND JSON_VALID(`bundle_json`);
