ALTER TABLE tenant_info
    ADD COLUMN IF NOT EXISTS tenant_short_name VARCHAR(64) DEFAULT NULL AFTER tenant_name;

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS nickname VARCHAR(64) DEFAULT NULL AFTER username,
    ADD COLUMN IF NOT EXISTS real_name VARCHAR(64) DEFAULT NULL AFTER nickname,
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255) DEFAULT NULL AFTER real_name;

ALTER TABLE sys_user_tenant
    ADD COLUMN IF NOT EXISTS is_default TINYINT NOT NULL DEFAULT 0 AFTER user_id,
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' AFTER is_default;

ALTER TABLE audit_login_log
    ADD COLUMN IF NOT EXISTS login_type VARCHAR(32) NOT NULL DEFAULT 'PASSWORD' AFTER username,
    ADD COLUMN IF NOT EXISTS fail_reason VARCHAR(255) DEFAULT NULL AFTER login_result;

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
