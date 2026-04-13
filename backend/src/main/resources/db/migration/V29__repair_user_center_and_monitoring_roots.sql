UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'user.center.root',
    menu_name = '用户中心',
    menu_type = 'CATALOG',
    path = '/user-center',
    component = '@/pages/user-center/index',
    icon = 'TeamOutlined',
    sort_no = 18,
    permission_key = 'user:center:view',
    status = 'ENABLED',
    updated_by = 0,
    deleted = 0
WHERE tenant_id = 1001
  AND menu_code = 'user.center.root';

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3020, 1001, 0, 'user.center.root', '用户中心', 'CATALOG', '/user-center', '@/pages/user-center/index', 'TeamOutlined', 18, 'user:center:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'user.center.root');

UPDATE sys_menu
SET parent_id = 0,
    menu_code = 'user.center.root',
    menu_name = '用户中心',
    menu_type = 'CATALOG',
    path = '/user-center',
    component = '@/pages/user-center/index',
    icon = 'TeamOutlined',
    sort_no = 18,
    permission_key = 'user:center:view',
    status = 'ENABLED',
    updated_by = 0,
    deleted = 0
WHERE tenant_id = 1002
  AND menu_code = 'user.center.root';

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4020, 1002, 0, 'user.center.root', '用户中心', 'CATALOG', '/user-center', '@/pages/user-center/index', 'TeamOutlined', 18, 'user:center:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'user.center.root');

UPDATE sys_menu
SET parent_id = 3002,
    menu_code = 'system.monitoring.root',
    menu_name = '系统监控',
    menu_type = 'CATALOG',
    path = '/system/monitoring',
    component = '@/pages/system/monitoring/index',
    icon = 'FundOutlined',
    sort_no = 21,
    permission_key = 'system:monitor:view',
    status = 'ENABLED',
    updated_by = 0,
    deleted = 0
WHERE tenant_id = 1001
  AND menu_code = 'system.monitoring.root';

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3016, 1001, 3002, 'system.monitoring.root', '系统监控', 'CATALOG', '/system/monitoring', '@/pages/system/monitoring/index', 'FundOutlined', 21, 'system:monitor:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.monitoring.root');

UPDATE sys_menu
SET parent_id = 4002,
    menu_code = 'system.monitoring.root',
    menu_name = '系统监控',
    menu_type = 'CATALOG',
    path = '/system/monitoring',
    component = '@/pages/system/monitoring/index',
    icon = 'FundOutlined',
    sort_no = 21,
    permission_key = 'system:monitor:view',
    status = 'ENABLED',
    updated_by = 0,
    deleted = 0
WHERE tenant_id = 1002
  AND menu_code = 'system.monitoring.root';

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4016, 1002, 4002, 'system.monitoring.root', '系统监控', 'CATALOG', '/system/monitoring', '@/pages/system/monitoring/index', 'FundOutlined', 21, 'system:monitor:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.monitoring.root');
