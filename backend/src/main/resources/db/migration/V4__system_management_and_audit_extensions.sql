SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_menu'
          AND column_name = 'status'
    ),
    'SELECT 1',
    'ALTER TABLE sys_menu ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT ''ENABLED'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_dict_type'
          AND column_name = 'status'
    ),
    'SELECT 1',
    'ALTER TABLE sys_dict_type ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT ''ENABLED'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_dict_type'
          AND column_name = 'is_system'
    ),
    'SELECT 1',
    'ALTER TABLE sys_dict_type ADD COLUMN is_system TINYINT NOT NULL DEFAULT 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_dict_type'
          AND column_name = 'remark'
    ),
    'SELECT 1',
    'ALTER TABLE sys_dict_type ADD COLUMN remark VARCHAR(512) DEFAULT NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_dict_item'
          AND column_name = 'status'
    ),
    'SELECT 1',
    'ALTER TABLE sys_dict_item ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT ''ENABLED'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_dict_item'
          AND column_name = 'remark'
    ),
    'SELECT 1',
    'ALTER TABLE sys_dict_item ADD COLUMN remark VARCHAR(512) DEFAULT NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_config'
          AND column_name = 'config_name'
    ),
    'SELECT 1',
    'ALTER TABLE sys_config ADD COLUMN config_name VARCHAR(128) NOT NULL DEFAULT '''' AFTER config_key'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_config'
          AND column_name = 'config_scope'
    ),
    'SELECT 1',
    'ALTER TABLE sys_config ADD COLUMN config_scope VARCHAR(32) NOT NULL DEFAULT ''PLATFORM'' AFTER config_value'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_config'
          AND column_name = 'is_system'
    ),
    'SELECT 1',
    'ALTER TABLE sys_config ADD COLUMN is_system TINYINT NOT NULL DEFAULT 0 AFTER config_scope'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_config'
          AND column_name = 'remark'
    ),
    'SELECT 1',
    'ALTER TABLE sys_config ADD COLUMN remark VARCHAR(512) DEFAULT NULL AFTER is_system'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT NULL,
    dict_code VARCHAR(64) NOT NULL,
    dict_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    is_system TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(512) DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_dict_type_code (tenant_id, dict_code)
);

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type_id BIGINT NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    item_value VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    remark VARCHAR(512) DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_dict_item_rel (dict_type_id, item_value)
);

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_name VARCHAR(128) NOT NULL,
    config_value VARCHAR(1024) NOT NULL,
    config_scope VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
    is_system TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(512) DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_config_key (tenant_id, config_key)
);

CREATE TABLE IF NOT EXISTS audit_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT NULL,
    user_id BIGINT DEFAULT NULL,
    username VARCHAR(64) DEFAULT NULL,
    module_name VARCHAR(64) DEFAULT NULL,
    action_name VARCHAR(128) DEFAULT NULL,
    operation_type VARCHAR(32) NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    detail_message VARCHAR(512) DEFAULT NULL,
    request_id VARCHAR(64) DEFAULT NULL,
    trace_id VARCHAR(64) DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'audit_operation_log'
          AND index_name = 'idx_audit_operation_log_module_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_audit_operation_log_module_created ON audit_operation_log (module_name, created_at)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF (
    EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_menu'
          AND index_name = 'idx_sys_menu_tenant_status'
    ),
    'SELECT 1',
    'CREATE INDEX idx_sys_menu_tenant_status ON sys_menu (tenant_id, status, sort_no)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO sys_dict_type (
    id, tenant_id, dict_code, dict_name, status, is_system, remark, created_by, updated_by, deleted
)
SELECT 5001, 1001, 'user_status', '用户状态', 'ENABLED', 1, '系统用户状态字典', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE id = 5001);

INSERT INTO sys_dict_type (
    id, tenant_id, dict_code, dict_name, status, is_system, remark, created_by, updated_by, deleted
)
SELECT 5002, 1001, 'role_type', '角色类型', 'ENABLED', 1, '系统角色类型字典', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE id = 5002);

INSERT INTO sys_dict_item (
    id, tenant_id, dict_type_id, item_label, item_value, sort_no, status, remark, created_by, updated_by, deleted
)
SELECT 6001, 1001, 5001, '启用', 'ENABLED', 1, 'ENABLED', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE id = 6001);

INSERT INTO sys_dict_item (
    id, tenant_id, dict_type_id, item_label, item_value, sort_no, status, remark, created_by, updated_by, deleted
)
SELECT 6002, 1001, 5001, '停用', 'DISABLED', 2, 'ENABLED', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE id = 6002);

INSERT INTO sys_dict_item (
    id, tenant_id, dict_type_id, item_label, item_value, sort_no, status, remark, created_by, updated_by, deleted
)
SELECT 6003, 1001, 5002, '系统角色', 'SYSTEM', 1, 'ENABLED', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE id = 6003);

INSERT INTO sys_dict_item (
    id, tenant_id, dict_type_id, item_label, item_value, sort_no, status, remark, created_by, updated_by, deleted
)
SELECT 6004, 1001, 5002, '自定义角色', 'CUSTOM', 2, 'ENABLED', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE id = 6004);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7001, 1001, 'platform.name', '平台名称', 'SaaS Foundation', 'PLATFORM', 1, '平台展示名称', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE id = 7001);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7002, 1001, 'tenant.theme', '租户主题', 'default', 'TENANT', 0, '租户级展示主题', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE id = 7002);

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'tenant:view', '查看租户中心', 'tenant', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'tenant:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'audit:view', '查看审计中心', 'audit', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'audit:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'audit:login:view', '查看登录日志', 'audit', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'audit:login:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'audit:operation:view', '查看操作日志', 'audit', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'audit:operation:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'iam:view', '查看权限中心', 'iam', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'iam:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:user:view', '查看用户管理', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:user:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:user:create', '创建用户', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:user:create');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:user:update', '编辑用户', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:user:update');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:user:status', '启停用户', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:user:status');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:role:view', '查看角色管理', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:role:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:role:create', '创建角色', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:role:create');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:role:update', '编辑角色', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:role:update');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:role:permissions', '分配角色权限', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:role:permissions');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:menu:view', '查看菜单管理', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:menu:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:menu:create', '创建菜单', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:menu:create');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:menu:update', '编辑菜单', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:menu:update');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:menu:status', '启停菜单', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:menu:status');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:dict:view', '查看字典管理', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:dict:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:dict:create', '创建字典', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:dict:create');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:dict:update', '编辑字典', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:dict:update');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:config:view', '查看参数配置', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:config:view');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1001, 'system:config:update', '编辑参数配置', 'system', 'CORE', NULL, 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE tenant_id = 1001 AND permission_key = 'system:config:update');

INSERT INTO sys_permission (tenant_id, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted)
SELECT 1002, permission_key, permission_name, permission_group, source_type, plugin_code, created_by, updated_by, deleted
FROM sys_permission
WHERE tenant_id = 1001
  AND permission_key IN (
      'tenant:view',
      'audit:view',
      'audit:login:view',
      'audit:operation:view',
      'iam:view',
      'system:user:view',
      'system:user:create',
      'system:user:update',
      'system:user:status',
      'system:role:view',
      'system:role:create',
      'system:role:update',
      'system:role:permissions',
      'system:menu:view',
      'system:menu:create',
      'system:menu:update',
      'system:menu:status',
      'system:dict:view',
      'system:dict:create',
      'system:dict:update',
      'system:config:view',
      'system:config:update'
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
      'tenant:view',
      'audit:view',
      'audit:login:view',
      'audit:operation:view',
      'iam:view',
      'system:user:view',
      'system:user:create',
      'system:user:update',
      'system:user:status',
      'system:role:view',
      'system:role:create',
      'system:role:update',
      'system:role:permissions',
      'system:menu:view',
      'system:menu:create',
      'system:menu:update',
      'system:menu:status',
      'system:dict:view',
      'system:dict:create',
      'system:dict:update',
      'system:config:view',
      'system:config:update'
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
      'tenant:view',
      'audit:view',
      'audit:login:view',
      'audit:operation:view',
      'iam:view',
      'system:user:view',
      'system:user:create',
      'system:user:update',
      'system:user:status',
      'system:role:view',
      'system:role:create',
      'system:role:update',
      'system:role:permissions',
      'system:menu:view',
      'system:menu:create',
      'system:menu:update',
      'system:menu:status',
      'system:dict:view',
      'system:dict:create',
      'system:dict:update',
      'system:config:view',
      'system:config:update'
  )
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.tenant_id = 1002 AND rp.role_id = 2002 AND rp.permission_key = sys_permission.permission_key
  );

UPDATE sys_menu
SET path = '/system/management',
    component = '@/pages/system/Management',
    status = 'ENABLED'
WHERE menu_code = 'system.root';

UPDATE sys_menu
SET path = '/system/plugins',
    component = '@/pages/system/Plugins',
    status = 'ENABLED'
WHERE menu_code = 'system.plugins';

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 3005, 1001, 0, 'tenant.overview', '租户中心', 'MENU', '/tenant/overview', '@/pages/tenant/Overview', 'ApartmentOutlined', 15, 'tenant:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'tenant.overview');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 3006, 1001, 0, 'iam.overview', '权限中心', 'MENU', '/iam/overview', '@/pages/iam/Overview', 'SafetyCertificateOutlined', 16, 'iam:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'iam.overview');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 3007, 1001, 0, 'audit.overview', '审计中心', 'MENU', '/audit/overview', '@/pages/audit/Overview', 'AuditOutlined', 17, 'audit:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'audit.overview');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 3008, 1001, 3002, 'system.users', '用户管理', 'MENU', '/system/users', '@/pages/system/users', 'UserOutlined', 22, 'system:user:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.users');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 3009, 1001, 3002, 'system.roles', '角色管理', 'MENU', '/system/roles', '@/pages/system/roles', 'TeamOutlined', 23, 'system:role:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.roles');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 3010, 1001, 3002, 'system.menus', '菜单管理', 'MENU', '/system/menus', '@/pages/system/menus', 'MenuOutlined', 24, 'system:menu:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.menus');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 3011, 1001, 3002, 'system.dicts', '字典管理', 'MENU', '/system/dicts', '@/pages/system/dicts', 'DatabaseOutlined', 25, 'system:dict:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.dicts');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 3012, 1001, 3002, 'system.configs', '参数配置', 'MENU', '/system/configs', '@/pages/system/configs', 'SettingOutlined', 26, 'system:config:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1001 AND menu_code = 'system.configs');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 4010, 1002, 0, 'tenant.overview', '租户中心', 'MENU', '/tenant/overview', '@/pages/tenant/Overview', 'ApartmentOutlined', 15, 'tenant:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'tenant.overview');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 4011, 1002, 0, 'iam.overview', '权限中心', 'MENU', '/iam/overview', '@/pages/iam/Overview', 'SafetyCertificateOutlined', 16, 'iam:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'iam.overview');

INSERT INTO sys_menu (
    id, tenant_id, parent_id, menu_code, menu_name, menu_type, path, component, icon, sort_no, permission_key, status, created_by, updated_by, deleted
)
SELECT 4012, 1002, 0, 'audit.overview', '审计中心', 'MENU', '/audit/overview', '@/pages/audit/Overview', 'AuditOutlined', 17, 'audit:view', 'ENABLED', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1002 AND menu_code = 'audit.overview');
