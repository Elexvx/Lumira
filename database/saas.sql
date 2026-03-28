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

CREATE TABLE IF NOT EXISTS tenant_package (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    package_code VARCHAR(64) NOT NULL,
    package_name VARCHAR(128) NOT NULL,
    expire_at DATETIME DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_package_tenant_code (tenant_id, package_code)
);

CREATE TABLE IF NOT EXISTS tenant_domain (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    domain VARCHAR(255) NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_domain_domain (domain)
);

CREATE TABLE IF NOT EXISTS tenant_quota (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    quota_key VARCHAR(64) NOT NULL,
    quota_value BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_quota_key (tenant_id, quota_key)
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

CREATE TABLE IF NOT EXISTS sys_user_tenant_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    avatar_url VARCHAR(255) DEFAULT NULL,
    locale VARCHAR(32) DEFAULT 'zh-CN',
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_user_tenant_profile (tenant_id, user_id)
);

CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0,
    dept_code VARCHAR(64) NOT NULL,
    dept_name VARCHAR(128) NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_department_code (tenant_id, dept_code)
);

CREATE TABLE IF NOT EXISTS sys_position (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    position_code VARCHAR(64) NOT NULL,
    position_name VARCHAR(128) NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_position_code (tenant_id, position_code)
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
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_menu_code (tenant_id, menu_code)
);

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

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_role_menu_rel (tenant_id, role_id, menu_id)
);

CREATE TABLE IF NOT EXISTS sys_data_scope_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    rule_expr VARCHAR(512) NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_data_scope_rule_code (tenant_id, rule_code)
);

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    dict_code VARCHAR(64) NOT NULL,
    dict_name VARCHAR(128) NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_dict_type_code (tenant_id, dict_code)
);

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    dict_type_id BIGINT NOT NULL,
    item_value VARCHAR(64) NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_dict_item_value (tenant_id, dict_type_id, item_value)
);

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_value VARCHAR(2000) NOT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_config_key (tenant_id, config_key)
);

CREATE TABLE IF NOT EXISTS file_object (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    storage_type VARCHAR(32) NOT NULL,
    bucket VARCHAR(128) DEFAULT NULL,
    object_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) DEFAULT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    checksum VARCHAR(128) DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_file_object_key (tenant_id, object_key)
);

CREATE TABLE IF NOT EXISTS task_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    job_code VARCHAR(64) NOT NULL,
    job_name VARCHAR(128) NOT NULL,
    cron_expr VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    last_run_at DATETIME DEFAULT NULL,
    created_by BIGINT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_task_job_code (tenant_id, job_code)
);

CREATE TABLE IF NOT EXISTS audit_operate_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT DEFAULT NULL,
    user_id BIGINT DEFAULT NULL,
    operation VARCHAR(128) NOT NULL,
    request_uri VARCHAR(255) NOT NULL,
    request_method VARCHAR(16) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    result_code VARCHAR(32) NOT NULL,
    cost_ms BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
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

CREATE INDEX idx_sys_user_mobile ON sys_user (mobile);
CREATE INDEX idx_sys_user_tenant_user_status ON sys_user_tenant (user_id, status);
CREATE INDEX idx_audit_login_log_trace_id ON audit_login_log (trace_id);

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
    '$2a$10$vieE7/xcgtcStPtFa4qIzejdXPbS0xv3OvsOjUAy03w3vjKsGJd6C',
    '13800000000',
    'admin@example.com',
    'ENABLED',
    0,
    0,
    0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = 1001);

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
