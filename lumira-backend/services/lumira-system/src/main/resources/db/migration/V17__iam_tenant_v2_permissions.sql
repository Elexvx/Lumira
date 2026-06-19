INSERT INTO sys_permission (
    tenant_id, permission_key, permission_name, permission_group, source_type,
    plugin_code, created_by, updated_by, deleted
)
SELECT 1001, seed.permission_key, seed.permission_name, 'tenant', 'CORE',
       NULL, 0, 0, 0
FROM (
    SELECT 'system:tenant:create' AS permission_key, '创建租户' AS permission_name
    UNION ALL SELECT 'system:tenant:update', '更新租户'
    UNION ALL SELECT 'system:tenant:delete', '归档租户'
    UNION ALL SELECT 'system:tenant:member', '管理租户成员'
) seed
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    permission_group = VALUES(permission_group),
    source_type = VALUES(source_type),
    updated_by = VALUES(updated_by),
    deleted = 0;

INSERT INTO sys_role_permission (
    tenant_id, role_id, permission_key, created_by, updated_by, deleted
)
SELECT 1001, 2001, seed.permission_key, 0, 0, 0
FROM (
    SELECT 'system:tenant:create' AS permission_key
    UNION ALL SELECT 'system:tenant:update'
    UNION ALL SELECT 'system:tenant:delete'
    UNION ALL SELECT 'system:tenant:member'
) seed
ON DUPLICATE KEY UPDATE
    updated_by = VALUES(updated_by),
    deleted = 0;
