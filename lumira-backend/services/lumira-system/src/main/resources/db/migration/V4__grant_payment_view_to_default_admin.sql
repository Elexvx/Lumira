INSERT INTO sys_role_permission (
  tenant_id,
  role_id,
  permission_key,
  created_by,
  created_at,
  updated_by,
  updated_at,
  deleted
)
SELECT
  1001,
  2001,
  'payment:view',
  0,
  CURRENT_TIMESTAMP,
  0,
  CURRENT_TIMESTAMP,
  0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_role_permission
  WHERE tenant_id = 1001
    AND role_id = 2001
    AND permission_key = 'payment:view'
    AND deleted = 0
);
