CREATE TABLE IF NOT EXISTS sys_verification_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    factor_code VARCHAR(32) NOT NULL,
    factor_name VARCHAR(64) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    bound TINYINT NOT NULL DEFAULT 0,
    email_required TINYINT NOT NULL DEFAULT 0,
    masked_contact VARCHAR(255) DEFAULT NULL,
    secret_key VARCHAR(255) DEFAULT NULL,
    recovery_codes_json JSON DEFAULT NULL,
    verified_at DATETIME DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_verification_binding (tenant_id, user_id, factor_code)
);

CREATE TABLE IF NOT EXISTS sys_verification_challenge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    challenge_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    factor_code VARCHAR(32) NOT NULL,
    challenge_type VARCHAR(16) NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed_flag TINYINT NOT NULL DEFAULT 0,
    setup_secret VARCHAR(255) DEFAULT NULL,
    setup_uri VARCHAR(512) DEFAULT NULL,
    recovery_codes_json JSON DEFAULT NULL,
    code_hash VARCHAR(128) DEFAULT NULL,
    masked_contact VARCHAR(255) DEFAULT NULL,
    debug_code VARCHAR(32) DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_verification_challenge (challenge_id)
);

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:verification:view', '查看验证管理', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:verification:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:verification:manage', '管理验证方式', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:verification:manage');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1002, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted
FROM sys_permission
WHERE tenant_id = 1001
  AND permission_key IN ('system:verification:view', 'system:verification:manage')
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p2
      WHERE p2.tenant_id = 1002 AND p2.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
SELECT 1001, 2001, permission_key, 0, 0, 0
FROM sys_permission
WHERE tenant_id = 1001
  AND permission_key IN ('system:verification:view', 'system:verification:manage')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1001 AND rp.role_id = 2001 AND rp.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
SELECT 1002, 2002, permission_key, 0, 0, 0
FROM sys_permission
WHERE tenant_id = 1002
  AND permission_key IN ('system:verification:view', 'system:verification:manage')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1002 AND rp.role_id = 2002 AND rp.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 3027, 1001, 3002, 'system.verification', '验证管理', 'MENU', '/system/verification', '@/pages/system/verification', 'SafetyOutlined', 28, 'system:verification:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.verification');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key,
    status, created_by, updated_by, deleted
)
SELECT 4027, 1002, 4002, 'system.verification', '验证管理', 'MENU', '/system/verification', '@/pages/system/verification', 'SafetyOutlined', 28, 'system:verification:view',
       'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'system.verification');
