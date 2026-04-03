INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, created_by, updated_by, deleted)
SELECT 2001, 1001, 'ADMIN', '平台管理员', 'BUILTIN', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 2001);

INSERT INTO sys_role (id, tenant_id, role_code, role_name, role_type, created_by, updated_by, deleted)
SELECT 2002, 1002, 'ADMIN', '演示管理员', 'BUILTIN', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 2002);

INSERT INTO sys_user_role (tenant_id, user_id, role_id, created_by, updated_by, deleted)
SELECT 1001, 1001, 2001, 0, 0, 0
WHERE EXISTS (SELECT 1 FROM sys_user WHERE id = 1001 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE id = 2001 AND tenant_id = 1001 AND deleted = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role
      WHERE tenant_id = 1001
        AND user_id = 1001
        AND role_id = 2001
        AND deleted = 0
  );

INSERT INTO sys_user_role (tenant_id, user_id, role_id, created_by, updated_by, deleted)
SELECT 1002, 1001, 2002, 0, 0, 0
WHERE EXISTS (SELECT 1 FROM sys_user WHERE id = 1001 AND deleted = 0)
  AND EXISTS (SELECT 1 FROM sys_role WHERE id = 2002 AND tenant_id = 1002 AND deleted = 0)
  AND NOT EXISTS (
      SELECT 1
      FROM sys_user_role
      WHERE tenant_id = 1002
        AND user_id = 1001
        AND role_id = 2002
        AND deleted = 0
  );
