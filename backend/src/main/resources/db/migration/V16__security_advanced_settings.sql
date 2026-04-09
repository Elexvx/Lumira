INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7007, 1001, 'security.captcha-enabled', '验证码开关', '0', 'PLATFORM', 1, '是否开启登录时的人机验证码', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'security.captcha-enabled' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7008, 1001, 'security.captcha-type', '验证码类型', 'IMAGE', 'PLATFORM', 1, '验证码类型：IMAGE=图片验证码，SLIDER=图片拖动验证码', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'security.captcha-type' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7009, 1001, 'security.login-defense-window-minutes', '登录防御统计窗口', '5', 'PLATFORM', 1, '统计登录尝试与错误次数的时间窗口（分钟）', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'security.login-defense-window-minutes' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7010, 1001, 'security.login-max-validation-attempts', '最大验证次数', '100', 'PLATFORM', 1, '统计窗口内允许的最大登录验证次数', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'security.login-max-validation-attempts' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7011, 1001, 'security.login-max-failure-count', '最大错误次数', '10', 'PLATFORM', 1, '统计窗口内允许的最大登录失败次数', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'security.login-max-failure-count' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7012, 1001, 'security.password-min-length', '密码最短长度', '6', 'PLATFORM', 1, '用户密码允许的最少字符数', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'security.password-min-length' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7013, 1001, 'security.password-require-uppercase', '密码必须包含大写字母', '0', 'PLATFORM', 1, '强制密码包含 A-Z', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'security.password-require-uppercase' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7014, 1001, 'security.password-require-lowercase', '密码必须包含小写字母', '0', 'PLATFORM', 1, '强制密码包含 a-z', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'security.password-require-lowercase' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7015, 1001, 'security.password-require-special-character', '密码必须包含特殊字符', '0', 'PLATFORM', 1, '强制密码包含特殊字符', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'security.password-require-special-character' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7016, 1001, 'security.password-allow-consecutive-characters', '允许连续字符', '1', 'PLATFORM', 1, '是否允许密码中出现连续字符', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'security.password-allow-consecutive-characters' AND deleted = 0);
