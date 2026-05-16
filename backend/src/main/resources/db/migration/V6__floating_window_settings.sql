insert into sys_config (tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted)
select 1001, 'floating-window.api-docs-qr-enabled', '接口文档二维码开关', 'true', 'PLATFORM', 0, '是否在全局悬浮窗展示接口文档二维码入口', 1, 1, 0
where not exists (
    select 1 from sys_config where tenant_id = 1001 and config_key = 'floating-window.api-docs-qr-enabled'
);

insert into sys_config (tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted)
select 1001, 'floating-window.api-docs-qr-title', '接口文档二维码标题', '微信扫码联系我们', 'PLATFORM', 0, '接口文档二维码弹层标题', 1, 1, 0
where not exists (
    select 1 from sys_config where tenant_id = 1001 and config_key = 'floating-window.api-docs-qr-title'
);

insert into sys_config (tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted)
select 1001, 'floating-window.api-docs-qr-image-url', '接口文档二维码图片', '', 'PLATFORM', 0, '接口文档悬浮入口展开后展示的二维码图片', 1, 1, 0
where not exists (
    select 1 from sys_config where tenant_id = 1001 and config_key = 'floating-window.api-docs-qr-image-url'
);
