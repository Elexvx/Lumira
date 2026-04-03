INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7003, 1001, 'security.idle-timeout-seconds', '空闲超时时间', '1800', 'PLATFORM', 1, '用户在无操作状态下允许保持登录的秒数', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE id = 7003);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7004, 1001, 'security.access-token-expire-seconds', 'Access Token 过期时间', '1800', 'PLATFORM', 1, 'Access Token 的有效秒数', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE id = 7004);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7005, 1001, 'security.refresh-token-expire-seconds', 'Refresh Token 刷新时限', '604800', 'PLATFORM', 1, 'Refresh Token 的有效秒数', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE id = 7005);

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3013, 1001, 3002, 'system.security', '安全设置', 'MENU', '/system/security', '@/pages/system/security', 'SafetyOutlined', 27, 'system:config:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.security');
