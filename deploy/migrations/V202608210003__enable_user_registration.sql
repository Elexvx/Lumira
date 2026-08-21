-- Open the user-registration policy for existing installations. The login page
-- still exposes registration only when a configured SMS verification provider
-- is available, so this does not bypass verification delivery.

INSERT INTO `sys_config` (
  `config_key`,
  `config_name`,
  `config_value`,
  `config_scope`,
  `is_system`,
  `remark`,
  `created_by`,
  `updated_by`,
  `deleted`
) VALUES (
  'security.registration-enabled',
  '允许用户注册',
  '1',
  'PLATFORM',
  1,
  '是否允许未注册手机号通过短信验证码创建普通用户',
  0,
  0,
  0
)
ON DUPLICATE KEY UPDATE
  `config_value` = '1',
  `config_scope` = 'PLATFORM',
  `is_system` = 1,
  `updated_by` = 0,
  `deleted` = 0;
