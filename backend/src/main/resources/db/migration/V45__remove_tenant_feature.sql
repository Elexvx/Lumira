-- Remove the tenant management feature from the active navigation and RBAC surface.
-- The tenant_id columns and the default tenant record remain as compatibility scaffolding.

UPDATE sys_menu
SET status = 'DISABLED',
    deleted = 1,
    updated_by = 1001,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND (
    path = '/tenant/overview'
    OR permission_key LIKE 'tenant:%'
    OR menu_code LIKE 'tenant%'
  );

UPDATE sys_permission
SET deleted = 1,
    updated_by = 1001,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND permission_key LIKE 'tenant:%';

UPDATE sys_role_permission
SET deleted = 1,
    updated_by = 1001,
    updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND permission_key LIKE 'tenant:%';
