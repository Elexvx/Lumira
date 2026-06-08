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
  permission_key,
  0,
  CURRENT_TIMESTAMP,
  0,
  CURRENT_TIMESTAMP,
  0
FROM (
  SELECT 'payment:config:view' AS permission_key
  UNION ALL SELECT 'payment:config:update'
  UNION ALL SELECT 'payment:config:test'
) permissions
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_role_permission existing
  WHERE existing.tenant_id = 1001
    AND existing.role_id = 2001
    AND existing.permission_key = permissions.permission_key
    AND existing.deleted = 0
);
