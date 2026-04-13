INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3020, 1001, 0, 'user.center.root', '用户中心', 'CATALOG', '/user-center', '@/pages/user-center/index', 'TeamOutlined', 18, NULL,
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'user.center.root');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4020, 1002, 0, 'user.center.root', '用户中心', 'CATALOG', '/user-center', '@/pages/user-center/index', 'TeamOutlined', 18, NULL,
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'user.center.root');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3016, 1001, 3002, 'system.monitoring.root', '系统监控', 'CATALOG', '/system/monitoring', '@/pages/system/monitoring/index', 'FundOutlined', 21, 'system:monitor:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.monitoring.root');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4016, 1002, 4002, 'system.monitoring.root', '系统监控', 'CATALOG', '/system/monitoring', '@/pages/system/monitoring/index', 'FundOutlined', 21, 'system:monitor:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.monitoring.root');
