insert into sys_config (
    tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
    created_by, updated_by, deleted
)
select 1001, 'verification.wechat-login.enabled', '微信登录启用', 'false', 'PLATFORM', 0, '是否启用微信扫码登录', 1, 1, 0
where not exists (
    select 1 from sys_config where tenant_id = 1001 and config_key = 'verification.wechat-login.enabled' and deleted = 0
);

insert into sys_config (
    tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
    created_by, updated_by, deleted
)
select 1001, 'verification.wechat-login.app-id', '微信 AppID', '', 'PLATFORM', 0, '微信开放平台网站应用 AppID', 1, 1, 0
where not exists (
    select 1 from sys_config where tenant_id = 1001 and config_key = 'verification.wechat-login.app-id' and deleted = 0
);

insert into sys_config (
    tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
    created_by, updated_by, deleted
)
select 1001, 'verification.wechat-login.app-secret', '微信 AppSecret', '', 'PLATFORM', 0, '微信开放平台网站应用 AppSecret', 1, 1, 0
where not exists (
    select 1 from sys_config where tenant_id = 1001 and config_key = 'verification.wechat-login.app-secret' and deleted = 0
);

insert into sys_config (
    tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
    created_by, updated_by, deleted
)
select 1001, 'verification.wechat-login.redirect-uri', '微信登录回调地址', '', 'PLATFORM', 0, '微信开放平台授权回调地址', 1, 1, 0
where not exists (
    select 1 from sys_config where tenant_id = 1001 and config_key = 'verification.wechat-login.redirect-uri' and deleted = 0
);

insert into sys_config (
    tenant_id, config_key, config_name, config_value, config_scope, is_system, remark,
    created_by, updated_by, deleted
)
select 1001, 'verification.wechat-login.state-expire-minutes', '微信登录状态有效期', '10', 'PLATFORM', 0, '微信登录 state 缓存有效期，单位分钟', 1, 1, 0
where not exists (
    select 1 from sys_config where tenant_id = 1001 and config_key = 'verification.wechat-login.state-expire-minutes' and deleted = 0
);
