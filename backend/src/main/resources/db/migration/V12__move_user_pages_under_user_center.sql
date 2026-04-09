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
SELECT 3021, 1001, 3020, 'system.users', '用户管理', 'MENU', '/user-center/users', '@/pages/system/users', 'UserOutlined', 21, 'system:user:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.users');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3022, 1001, 3020, 'system.online-users', '在线用户', 'MENU', '/user-center/online-users', '@/pages/system/online-users', 'UserSwitchOutlined', 22, 'system:online-user:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.online-users');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3023, 1001, 3020, 'system.roles', '角色管理', 'MENU', '/user-center/roles', '@/pages/system/roles', 'SafetyOutlined', 23, 'system:role:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.roles');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3024, 1001, 3020, 'profile.center', '个人中心', 'MENU', '/user-center/profile', '@/pages/profile/Center', 'UserOutlined', 24, 'profile:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'profile.center');

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
SELECT 4008, 1002, 4020, 'system.users', '用户管理', 'MENU', '/user-center/users', '@/pages/system/users', 'UserOutlined', 21, 'system:user:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.users');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4015, 1002, 4020, 'system.online-users', '在线用户', 'MENU', '/user-center/online-users', '@/pages/system/online-users', 'UserSwitchOutlined', 22, 'system:online-user:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.online-users');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4009, 1002, 4020, 'system.roles', '角色管理', 'MENU', '/user-center/roles', '@/pages/system/roles', 'SafetyOutlined', 23, 'system:role:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.roles');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4004, 1002, 4020, 'profile.center', '个人中心', 'MENU', '/user-center/profile', '@/pages/profile/Center', 'UserOutlined', 24, 'profile:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'profile.center');

UPDATE sys_menu
SET parent_id = 3020,
    path = '/user-center/users',
    component = '@/pages/system/users',
    icon = 'UserOutlined',
    sort_no = 21,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001 AND menu_code = 'system.users' AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3020,
    path = '/user-center/online-users',
    component = '@/pages/system/online-users',
    icon = 'UserSwitchOutlined',
    sort_no = 22,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001 AND menu_code = 'system.online-users' AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3020,
    path = '/user-center/roles',
    component = '@/pages/system/roles',
    icon = 'SafetyOutlined',
    sort_no = 23,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001 AND menu_code = 'system.roles' AND deleted = 0;

UPDATE sys_menu
SET parent_id = 3020,
    path = '/user-center/profile',
    component = '@/pages/profile/Center',
    icon = 'UserOutlined',
    sort_no = 24,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1001 AND menu_code = 'profile.center' AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    path = '/user-center/users',
    component = '@/pages/system/users',
    icon = 'UserOutlined',
    sort_no = 21,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002 AND menu_code = 'system.users' AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    path = '/user-center/online-users',
    component = '@/pages/system/online-users',
    icon = 'UserSwitchOutlined',
    sort_no = 22,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002 AND menu_code = 'system.online-users' AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    path = '/user-center/roles',
    component = '@/pages/system/roles',
    icon = 'SafetyOutlined',
    sort_no = 23,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002 AND menu_code = 'system.roles' AND deleted = 0;

UPDATE sys_menu
SET parent_id = 4020,
    path = '/user-center/profile',
    component = '@/pages/profile/Center',
    icon = 'UserOutlined',
    sort_no = 24,
    status = 'ENABLED',
    updated_by = 0
WHERE tenant_id = 1002 AND menu_code = 'profile.center' AND deleted = 0;
