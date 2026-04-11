UPDATE sys_config
SET config_value = '1'
WHERE tenant_id IN (1001, 1002)
  AND config_key = 'security.captcha-enabled'
  AND deleted = 0;
