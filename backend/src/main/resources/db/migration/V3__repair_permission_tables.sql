CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    permission_key VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_group VARCHAR(64) DEFAULT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'CORE',
    plugin_code VARCHAR(64) DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_permission_key (tenant_id, permission_key)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission_key VARCHAR(128) NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_role_permission_rel (tenant_id, role_id, permission_key)
);

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'dashboard:view', '查看首页', 'dashboard', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'dashboard:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:view', '查看系统管理', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'profile:view', '查看个人中心', 'profile', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'profile:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'plugin:management:view', '查看插件管理', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'plugin:management:upload', '上传插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:upload');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'plugin:management:install', '安装插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:install');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'plugin:management:upgrade', '升级插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:upgrade');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'plugin:management:rollback', '回滚插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:rollback');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'plugin:management:enable', '启用插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:enable');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'plugin:management:disable', '停用插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:disable');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'plugin:management:logs', '查看插件日志', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:logs');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1002, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted
FROM sys_permission
WHERE tenant_id = 1001
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p2
      WHERE p2.tenant_id = 1002 AND p2.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
SELECT 1001, 2001, permission_key, 0, 0, 0
FROM sys_permission
WHERE tenant_id = 1001
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1001 AND rp.role_id = 2001 AND rp.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_role_permission (tenant_id, role_id, permission_key, created_by, updated_by, deleted)
SELECT 1002, 2002, permission_key, 0, 0, 0
FROM sys_permission
WHERE tenant_id = 1002
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1002 AND rp.role_id = 2002 AND rp.permission_key = sys_permission.permission_key
  );
