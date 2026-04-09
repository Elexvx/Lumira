INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7205, 1001, 'branding.github-link-url', 'GitHub 链接', 'https://github.com/Elexvx/legendary-invention', 'PLATFORM', 0, '顶部 GitHub 图标跳转地址', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'branding.github-link-url' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7206, 1001, 'branding.help-link-url', '帮助链接', 'https://github.com/Elexvx/legendary-invention/blob/main/README.md', 'PLATFORM', 0, '顶部帮助图标跳转地址', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'branding.help-link-url' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7207, 1002, 'branding.github-link-url', 'GitHub 链接', 'https://github.com/Elexvx/legendary-invention', 'PLATFORM', 0, '顶部 GitHub 图标跳转地址', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1002 AND config_key = 'branding.github-link-url' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7208, 1002, 'branding.help-link-url', '帮助链接', 'https://github.com/Elexvx/legendary-invention/blob/main/README.md', 'PLATFORM', 0, '顶部帮助图标跳转地址', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1002 AND config_key = 'branding.help-link-url' AND deleted = 0);
