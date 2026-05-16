INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 'verification.password-login.enabled', '密码登录', 'true', 'PLATFORM', 0, '是否启用账号密码登录', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `config_name` = VALUES(`config_name`),
  `remark` = VALUES(`remark`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_scope`, `is_system`, `remark`, `created_by`, `updated_by`, `deleted`)
VALUES (1001, 'verification.login-mode.order', '登录方式排序', 'passkey,sms,email,password', 'PLATFORM', 0, '登录页分段控制器展示顺序', 0, 0, 0)
ON DUPLICATE KEY UPDATE
  `config_name` = VALUES(`config_name`),
  `remark` = VALUES(`remark`),
  `updated_at` = CURRENT_TIMESTAMP,
  `deleted` = 0;
