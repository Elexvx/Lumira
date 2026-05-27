INSERT INTO `sys_config`
(`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES
(1001, 'security.verification-code-expire-seconds', '验证码有效期', '300', 'PLATFORM', 1, '短信/邮箱验证码的有效秒数', 0, 0, 0),
(1001, 'security.verification-code-cooldown-seconds', '验证码发送倒计时', '60', 'PLATFORM', 1, '同一账号同一验证码渠道再次发送前需要等待的秒数', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `config_name` = VALUES(`config_name`),
  `config_scope` = VALUES(`config_scope`),
  `is_system` = VALUES(`is_system`),
  `remark` = VALUES(`remark`),
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;
