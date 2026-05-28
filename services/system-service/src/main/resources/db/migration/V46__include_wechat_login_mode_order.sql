UPDATE `sys_config`
SET `config_value` = 'passkey,sms,email,wechat,password',
    `updated_by` = 0,
    `updated_at` = CURRENT_TIMESTAMP
WHERE `tenant_id` = 1001
  AND `config_key` = 'verification.login-mode.order'
  AND `config_value` = 'passkey,sms,email,password'
  AND `deleted` = 0;

INSERT INTO `sys_config` (
  `tenant_id`,
  `config_key`,
  `config_name`,
  `config_value`,
  `config_scope`,
  `is_system`,
  `remark`,
  `created_by`,
  `created_at`,
  `updated_by`,
  `updated_at`,
  `deleted`
)
VALUES (
  1001,
  'verification.login-mode.order',
  '登录方式排序',
  'passkey,sms,email,wechat,password',
  'PLATFORM',
  0,
  '登录页分段控制器展示顺序',
  0,
  CURRENT_TIMESTAMP,
  0,
  CURRENT_TIMESTAMP,
  0
)
ON DUPLICATE KEY UPDATE
  `config_value` = CASE
    WHEN `config_value` = 'passkey,sms,email,password' OR `config_value` IS NULL OR `config_value` = ''
      THEN VALUES(`config_value`)
    ELSE `config_value`
  END,
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;
