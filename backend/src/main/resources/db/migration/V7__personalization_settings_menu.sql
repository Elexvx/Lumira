INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7006, 1001, 'branding.website-name', '站点名称', '宏翔商道', 'PLATFORM', 0, '控制台顶部与浏览器标题展示名称', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'branding.website-name' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7007, 1001, 'branding.website-favicon-url', '站点图标地址', '', 'PLATFORM', 0, '浏览器标签页 icon 地址', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'branding.website-favicon-url' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7008, 1001, 'branding.website-logo-url', '站点 Logo 地址', '', 'PLATFORM', 0, '控制台左上角品牌 Logo 地址', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'branding.website-logo-url' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7009, 1001, 'branding.footer-icp', '页脚 ICP 备案', '', 'PLATFORM', 0, '页脚备案信息', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'branding.footer-icp' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7010, 1001, 'branding.footer-copyright', '页脚版权声明', '', 'PLATFORM', 0, '页脚版权声明', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'branding.footer-copyright' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7106, 1002, 'branding.website-name', '站点名称', '宏翔商道', 'PLATFORM', 0, '控制台顶部与浏览器标题展示名称', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1002 AND config_key = 'branding.website-name' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7107, 1002, 'branding.website-favicon-url', '站点图标地址', '', 'PLATFORM', 0, '浏览器标签页 icon 地址', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1002 AND config_key = 'branding.website-favicon-url' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7108, 1002, 'branding.website-logo-url', '站点 Logo 地址', '', 'PLATFORM', 0, '控制台左上角品牌 Logo 地址', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1002 AND config_key = 'branding.website-logo-url' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7109, 1002, 'branding.footer-icp', '页脚 ICP 备案', '', 'PLATFORM', 0, '页脚备案信息', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1002 AND config_key = 'branding.footer-icp' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7110, 1002, 'branding.footer-copyright', '页脚版权声明', '', 'PLATFORM', 0, '页脚版权声明', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1002 AND config_key = 'branding.footer-copyright' AND deleted = 0);

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3014, 1001, 3002, 'system.personalization', '个性化设置', 'MENU', '/system/personalization', '@/pages/system/personalization', 'SkinOutlined', 28, 'system:config:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.personalization');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4014, 1002, 4002, 'system.personalization', '个性化设置', 'MENU', '/system/personalization', '@/pages/system/personalization', 'SkinOutlined', 28, 'system:config:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.personalization');
