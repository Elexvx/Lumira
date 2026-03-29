CREATE TABLE IF NOT EXISTS tenant_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    tenant_short_name VARCHAR(64) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_info_code (tenant_code)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    nickname VARCHAR(64) DEFAULT NULL,
    real_name VARCHAR(64) DEFAULT NULL,
    avatar_url VARCHAR(255) DEFAULT NULL,
    password_hash VARCHAR(255) NOT NULL,
    mobile VARCHAR(32) DEFAULT NULL,
    email VARCHAR(128) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_user_username (username)
);

CREATE TABLE IF NOT EXISTS sys_user_tenant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_user_tenant_rel (tenant_id, user_id)
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    role_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOM',
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_role_code (tenant_id, role_code)
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(128) NOT NULL,
    menu_type VARCHAR(32) NOT NULL,
    path VARCHAR(255) DEFAULT NULL,
    component VARCHAR(255) DEFAULT NULL,
    icon VARCHAR(64) DEFAULT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    permission_key VARCHAR(128) DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_menu_code (tenant_id, menu_code)
);

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_menu'
          AND column_name = 'icon'
    ),
    'SELECT 1',
    'ALTER TABLE sys_menu ADD COLUMN icon VARCHAR(64) DEFAULT NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_menu'
          AND column_name = 'sort_no'
    ),
    'SELECT 1',
    'ALTER TABLE sys_menu ADD COLUMN sort_no INT NOT NULL DEFAULT 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_menu'
          AND column_name = 'permission_key'
    ),
    'SELECT 1',
    'ALTER TABLE sys_menu ADD COLUMN permission_key VARCHAR(128) DEFAULT NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_user_role_rel (tenant_id, user_id, role_id)
);

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

CREATE TABLE IF NOT EXISTS audit_login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT NULL,
    user_id BIGINT DEFAULT NULL,
    username VARCHAR(64) DEFAULT NULL,
    login_type VARCHAR(32) NOT NULL DEFAULT 'PASSWORD',
    login_result VARCHAR(32) NOT NULL,
    fail_reason VARCHAR(255) DEFAULT NULL,
    login_ip VARCHAR(64) DEFAULT NULL,
    user_agent VARCHAR(255) DEFAULT NULL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_plugin_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plugin_code VARCHAR(64) NOT NULL,
    plugin_name VARCHAR(128) NOT NULL,
    plugin_type VARCHAR(32) NOT NULL,
    description VARCHAR(512) DEFAULT NULL,
    author VARCHAR(128) DEFAULT NULL,
    plugin_api_version VARCHAR(32) NOT NULL,
    builtin_flag TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_plugin_definition_code (plugin_code)
);

CREATE TABLE IF NOT EXISTS sys_plugin_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plugin_code VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL,
    package_path VARCHAR(512) DEFAULT NULL,
    artifact_path VARCHAR(512) DEFAULT NULL,
    frontend_manifest_path VARCHAR(512) DEFAULT NULL,
    backend_jar_path VARCHAR(512) DEFAULT NULL,
    checksum VARCHAR(128) DEFAULT NULL,
    signature_path VARCHAR(512) DEFAULT NULL,
    min_platform_version VARCHAR(32) NOT NULL,
    install_status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    load_status VARCHAR(32) NOT NULL DEFAULT 'UNLOADED',
    health_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    is_active TINYINT NOT NULL DEFAULT 0,
    rollbackable TINYINT NOT NULL DEFAULT 0,
    metadata_json JSON DEFAULT NULL,
    validation_report_json JSON DEFAULT NULL,
    staged_path VARCHAR(512) DEFAULT NULL,
    installed_at DATETIME DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_plugin_version_code_version (plugin_code, version)
);

CREATE TABLE IF NOT EXISTS sys_plugin_tenant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    plugin_code VARCHAR(64) NOT NULL,
    plugin_version VARCHAR(32) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    config_json JSON DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_plugin_tenant_rel (tenant_id, plugin_code)
);

CREATE TABLE IF NOT EXISTS sys_plugin_dependency (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plugin_code VARCHAR(64) NOT NULL,
    depends_on_plugin_code VARCHAR(64) NOT NULL,
    min_version VARCHAR(32) NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_plugin_dependency_rel (plugin_code, depends_on_plugin_code)
);

CREATE TABLE IF NOT EXISTS sys_plugin_runtime_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT NULL,
    plugin_code VARCHAR(64) NOT NULL,
    plugin_version VARCHAR(32) DEFAULT NULL,
    operation_type VARCHAR(32) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    detail_message VARCHAR(512) DEFAULT NULL,
    request_id VARCHAR(64) DEFAULT NULL,
    trace_id VARCHAR(64) DEFAULT NULL,
    failure_stack TEXT DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_plugin_menu_rel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plugin_code VARCHAR(64) NOT NULL,
    plugin_version VARCHAR(32) NOT NULL,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(128) NOT NULL,
    route_path VARCHAR(255) NOT NULL,
    icon VARCHAR(64) DEFAULT NULL,
    permission_key VARCHAR(128) DEFAULT NULL,
    parent_menu_code VARCHAR(64) DEFAULT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_plugin_menu_rel (plugin_code, plugin_version, menu_code)
);

CREATE TABLE IF NOT EXISTS sys_plugin_permission_rel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plugin_code VARCHAR(64) NOT NULL,
    plugin_version VARCHAR(32) NOT NULL,
    permission_key VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_group VARCHAR(64) DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_plugin_permission_rel (plugin_code, plugin_version, permission_key)
);

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND index_name = 'idx_sys_user_mobile'
    ),
    'SELECT 1',
    'CREATE INDEX idx_sys_user_mobile ON sys_user (mobile)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user_tenant'
          AND index_name = 'idx_sys_user_tenant_user_status'
    ),
    'SELECT 1',
    'CREATE INDEX idx_sys_user_tenant_user_status ON sys_user_tenant (user_id, status)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'audit_login_log'
          AND index_name = 'idx_audit_login_log_trace_id'
    ),
    'SELECT 1',
    'CREATE INDEX idx_audit_login_log_trace_id ON audit_login_log (trace_id)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_plugin_runtime_log'
          AND index_name = 'idx_sys_plugin_runtime_log_plugin_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_sys_plugin_runtime_log_plugin_created ON sys_plugin_runtime_log (plugin_code, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO tenant_info (
    id,
    tenant_code,
    tenant_name,
    tenant_short_name,
    status,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'default', '默认租户', '默认', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM tenant_info WHERE id = 1001);

INSERT INTO tenant_info (
    id,
    tenant_code,
    tenant_name,
    tenant_short_name,
    status,
    created_by,
    updated_by,
    deleted
)
SELECT 1002, 'demo', '演示租户', '演示', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM tenant_info WHERE id = 1002);

INSERT INTO sys_user (
    id,
    username,
    nickname,
    real_name,
    password_hash,
    mobile,
    email,
    status,
    created_by,
    updated_by,
    deleted
)
SELECT
    1001,
    'admin',
    '管理员',
    '系统管理员',
    '$2a$10$ko3RP4YpfVgyQC5pZjq5t.d1TKrqmBGoehczMjqn1k.pLeAAnTI9G',
    '13800000000',
    'admin@example.com',
    'ENABLED',
    0,
    0,
    0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1001);

UPDATE sys_user
SET password_hash = '$2a$10$ko3RP4YpfVgyQC5pZjq5t.d1TKrqmBGoehczMjqn1k.pLeAAnTI9G'
WHERE id = 1001
   OR username = 'admin';

INSERT INTO sys_user_tenant (
    tenant_id,
    user_id,
    is_default,
    status,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 1001, 1, 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user_tenant WHERE tenant_id = 1001 AND user_id = 1001);

INSERT INTO sys_user_tenant (
    tenant_id,
    user_id,
    is_default,
    status,
    created_by,
    updated_by,
    deleted
)
SELECT 1002, 1001, 0, 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user_tenant WHERE tenant_id = 1002 AND user_id = 1001);

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'dashboard:view', '查看首页', 'dashboard', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'dashboard:view');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'system:view', '查看系统管理', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:view');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'profile:view', '查看个人中心', 'profile', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'profile:view');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'plugin:management:view', '查看插件管理', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:view');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'plugin:management:upload', '上传插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:upload');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'plugin:management:install', '安装插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:install');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'plugin:management:upgrade', '升级插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:upgrade');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'plugin:management:rollback', '回滚插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:rollback');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'plugin:management:enable', '启用插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:enable');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'plugin:management:disable', '停用插件', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:disable');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1001, 'plugin:management:logs', '查看插件日志', 'plugin', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'plugin:management:logs');

INSERT INTO sys_permission (
    tenant_id,
    permission_key,
    permission_name,
    permission_group,
    source_type,
    plugin_code,
    created_by,
    updated_by,
    deleted
)
SELECT 1002, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted
FROM sys_permission
WHERE tenant_id = 1001
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission p2
      WHERE p2.tenant_id = 1002 AND p2.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_role_permission (
    tenant_id, role_id, permission_key, created_by, updated_by, deleted
)
SELECT 1001, 2001, permission_key, 0, 0, 0
FROM sys_permission
WHERE tenant_id = 1001
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1001 AND rp.role_id = 2001 AND rp.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_role_permission (
    tenant_id, role_id, permission_key, created_by, updated_by, deleted
)
SELECT 1002, 2002, permission_key, 0, 0, 0
FROM sys_permission
WHERE tenant_id = 1002
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1002 AND rp.role_id = 2002 AND rp.permission_key = sys_permission.permission_key
  );

INSERT INTO sys_menu (
    id,
    tenant_id,
    parent_id,
    menu_code,
    menu_name,
    menu_type,
    path,
    component,
    icon,
    sort_no,
    permission_key,
    created_by,
    updated_by,
    deleted
)
SELECT 3001, 1001, 0, 'dashboard.home', '首页', 'MENU', '/dashboard/home', '@/pages/dashboard/Home', 'DashboardOutlined', 10, 'dashboard:view', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 3001);

INSERT INTO sys_menu (
    id,
    tenant_id,
    parent_id,
    menu_code,
    menu_name,
    menu_type,
    path,
    component,
    icon,
    sort_no,
    permission_key,
    created_by,
    updated_by,
    deleted
)
SELECT 3002, 1001, 0, 'system.root', '系统管理', 'CATALOG', '/system/plugins', '@/pages/system/Management', 'AppstoreOutlined', 20, 'system:view', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 3002);

INSERT INTO sys_menu (
    id,
    tenant_id,
    parent_id,
    menu_code,
    menu_name,
    menu_type,
    path,
    component,
    icon,
    sort_no,
    permission_key,
    created_by,
    updated_by,
    deleted
)
SELECT 3003, 1001, 3002, 'system.plugins', '插件管理中心', 'MENU', '/system/plugins', '@/pages/system/Plugins', 'ApiOutlined', 21, 'plugin:management:view', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 3003);

INSERT INTO sys_menu (
    id,
    tenant_id,
    parent_id,
    menu_code,
    menu_name,
    menu_type,
    path,
    component,
    icon,
    sort_no,
    permission_key,
    created_by,
    updated_by,
    deleted
)
SELECT 3004, 1001, 0, 'profile.center', '个人中心', 'MENU', '/profile/center', '@/pages/profile/Center', 'UserOutlined', 30, 'profile:view', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 3004);

INSERT INTO sys_menu (
    id,
    tenant_id,
    parent_id,
    menu_code,
    menu_name,
    menu_type,
    path,
    component,
    icon,
    sort_no,
    permission_key,
    created_by,
    updated_by,
    deleted
)
SELECT 4001, 1002, 0, 'dashboard.home', '首页', 'MENU', '/dashboard/home', '@/pages/dashboard/Home', 'DashboardOutlined', 10, 'dashboard:view', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 4001);

INSERT INTO sys_menu (
    id,
    tenant_id,
    parent_id,
    menu_code,
    menu_name,
    menu_type,
    path,
    component,
    icon,
    sort_no,
    permission_key,
    created_by,
    updated_by,
    deleted
)
SELECT 4002, 1002, 0, 'system.root', '系统管理', 'CATALOG', '/system/plugins', '@/pages/system/Management', 'AppstoreOutlined', 20, 'system:view', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 4002);

INSERT INTO sys_menu (
    id,
    tenant_id,
    parent_id,
    menu_code,
    menu_name,
    menu_type,
    path,
    component,
    icon,
    sort_no,
    permission_key,
    created_by,
    updated_by,
    deleted
)
SELECT 4003, 1002, 4002, 'system.plugins', '插件管理中心', 'MENU', '/system/plugins', '@/pages/system/Plugins', 'ApiOutlined', 21, 'plugin:management:view', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 4003);

INSERT INTO sys_menu (
    id,
    tenant_id,
    parent_id,
    menu_code,
    menu_name,
    menu_type,
    path,
    component,
    icon,
    sort_no,
    permission_key,
    created_by,
    updated_by,
    deleted
)
SELECT 4004, 1002, 0, 'profile.center', '个人中心', 'MENU', '/profile/center', '@/pages/profile/Center', 'UserOutlined', 30, 'profile:view', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 4004);
