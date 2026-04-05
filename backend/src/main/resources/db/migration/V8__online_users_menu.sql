INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:online-user:view', '查看在线用户', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:online-user:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:online-user:kick', '踢出在线会话', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:online-user:kick');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:online-user:ban', '封禁在线用户', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:online-user:ban');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1002, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted
FROM sys_permission
WHERE tenant_id = 1001
  AND permission_key IN ('system:online-user:view', 'system:online-user:kick', 'system:online-user:ban')
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p2
      WHERE p2.tenant_id = 1002 AND p2.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
SELECT 1001, 2001, permission_key, 0, 0, 0
FROM sys_permission
WHERE tenant_id = 1001
  AND permission_key IN ('system:online-user:view', 'system:online-user:kick', 'system:online-user:ban')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1001 AND rp.role_id = 2001 AND rp.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
SELECT 1002, 2002, permission_key, 0, 0, 0
FROM sys_permission
WHERE tenant_id = 1002
  AND permission_key IN ('system:online-user:view', 'system:online-user:kick', 'system:online-user:ban')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1002 AND rp.role_id = 2002 AND rp.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3015, 1001, 3002, 'system.online-users', '在线用户', 'MENU', '/system/online-users', '@/pages/system/online-users', 'UserSwitchOutlined', 22, 'system:online-user:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.online-users');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4015, 1002, 4002, 'system.online-users', '在线用户', 'MENU', '/system/online-users', '@/pages/system/online-users', 'UserSwitchOutlined', 22, 'system:online-user:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.online-users');
