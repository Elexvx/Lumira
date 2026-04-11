INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'user:center:view', '查看用户中心', 'user-center', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'user:center:view'
);

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1002, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted
FROM sys_permission
WHERE tenant_id = 1001
  AND permission_key = 'user:center:view'
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p2
      WHERE p2.tenant_id = 1002 AND p2.permission_key = sys_permission.permission_key
  );

UPDATE sys_menu
SET permission_key = 'user:center:view',
    updated_by = 0
WHERE tenant_id IN (1001, 1002)
  AND menu_code = 'user.center.root'
  AND deleted = 0;

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
SELECT 1001, 2001, 'user:center:view', 0, 0, 0
WHERE EXISTS (
    SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'user:center:view'
)
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission
      WHERE tenant_id = 1001 AND role_id = 2001 AND permission_key = 'user:center:view'
  );

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
SELECT 1002, 2002, 'user:center:view', 0, 0, 0
WHERE EXISTS (
    SELECT 1 FROM sys_permission WHERE tenant_id = 1002 AND permission_key = 'user:center:view'
)
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission
      WHERE tenant_id = 1002 AND role_id = 2002 AND permission_key = 'user:center:view'
  );
