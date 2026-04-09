INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7201, 1001, 'branding.company-name', '公司名称', '宏翔商道', 'PLATFORM', 0, '页脚版权主体名称', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'branding.company-name' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7202, 1001, 'branding.copyright-start-year', '版权起始年份', '2025', 'PLATFORM', 0, '页脚版权起始年份', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'branding.copyright-start-year' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7203, 1002, 'branding.company-name', '公司名称', '宏翔商道', 'PLATFORM', 0, '页脚版权主体名称', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1002 AND config_key = 'branding.company-name' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7204, 1002, 'branding.copyright-start-year', '版权起始年份', '2025', 'PLATFORM', 0, '页脚版权起始年份', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1002 AND config_key = 'branding.copyright-start-year' AND deleted = 0);
