INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7017, 1001, 'security.captcha-enabled', '验证码开关', '0', 'PLATFORM', 1, '是否开启登录时的人机验证码', 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_config
    WHERE tenant_id = 1001 AND config_key = 'security.captcha-enabled' AND deleted = 0
);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7018, 1001, 'security.captcha-type', '验证码类型', 'IMAGE', 'PLATFORM', 1, '验证码类型：IMAGE=图片验证码，SLIDER=图片拖动验证码', 0, 0, 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_config
    WHERE tenant_id = 1001 AND config_key = 'security.captcha-type' AND deleted = 0
);
