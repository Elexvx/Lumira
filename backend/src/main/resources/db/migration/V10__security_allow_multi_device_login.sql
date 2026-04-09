INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7006, 1001, 'security.allow-multi-device-login', '多设备登录', '1', 'PLATFORM', 1, '是否允许同一账号在多个设备同时在线', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE id = 7006);
