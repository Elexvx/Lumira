CREATE TABLE IF NOT EXISTS msg_notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    notice_type VARCHAR(32) NOT NULL,
    target_scope VARCHAR(32) NOT NULL,
    target_user_id BIGINT DEFAULT NULL,
    title VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    publish_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_msg_notice_tenant_type_status_created (tenant_id, notice_type, publish_status, created_at),
    KEY idx_msg_notice_tenant_target_created (tenant_id, target_user_id, created_at)
);

CREATE TABLE IF NOT EXISTS msg_notice_read (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    notice_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at DATETIME NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_msg_notice_read (tenant_id, notice_id, user_id),
    KEY idx_msg_notice_read_user_created (tenant_id, user_id, read_at)
);

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'message:announcement:view', '查看公告', 'message', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'message:announcement:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'message:announcement:write', '发布公告', 'message', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'message:announcement:write');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'message:announcement:read', '标记公告已读', 'message', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'message:announcement:read');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'message:announcement:retract', '撤回公告', 'message', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'message:announcement:retract');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'message:message:view', '查看消息', 'message', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'message:message:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'message:message:write', '发送消息', 'message', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'message:message:write');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'message:message:read', '标记消息已读', 'message', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'message:message:read');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'message:message:retract', '撤回消息', 'message', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'message:message:retract');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1002, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted
FROM sys_permission
WHERE tenant_id = 1001
  AND permission_key IN (
      'message:announcement:view',
      'message:announcement:write',
      'message:announcement:read',
      'message:announcement:retract',
      'message:message:view',
      'message:message:write',
      'message:message:read',
      'message:message:retract'
  )
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p2
      WHERE p2.tenant_id = 1002 AND p2.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
SELECT 1001, 2001, permission_key, 0, 0, 0
FROM sys_permission
WHERE tenant_id = 1001
  AND permission_key IN (
      'message:announcement:view',
      'message:announcement:write',
      'message:announcement:read',
      'message:announcement:retract',
      'message:message:view',
      'message:message:write',
      'message:message:read',
      'message:message:retract'
  )
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1001 AND rp.role_id = 2001 AND rp.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
SELECT 1002, 2002, permission_key, 0, 0, 0
FROM sys_permission
WHERE tenant_id = 1002
  AND permission_key IN (
      'message:announcement:view',
      'message:announcement:write',
      'message:announcement:read',
      'message:announcement:retract',
      'message:message:view',
      'message:message:write',
      'message:message:read',
      'message:message:retract'
  )
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1002 AND rp.role_id = 2002 AND rp.permission_key = sys_permission.permission_key
  );
