INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7013, 1001, 'agreement.user-agreement-markdown', '用户协议',
       '# 用户协议

欢迎使用宏翔商道后台管理系统。

在使用本系统前，请仔细阅读并理解以下内容：

1. 您在登录、访问和使用本系统相关功能时，应遵守国家法律法规以及平台规则。
2. 您应妥善保管账号、密码及相关身份信息，不得将账号转借、共享或提供给无关第三方。
3. 平台可能会在提供服务所必需的范围内处理您的账号、日志与业务数据。
4. 如您不同意本协议内容，请停止使用本系统。

本协议自发布或更新之日起生效。', 'PLATFORM', 0, '用户协议 Markdown 内容', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'agreement.user-agreement-markdown' AND deleted = 0);

INSERT INTO sys_config (
    id, tenant_id, config_key, config_name, config_value, config_scope, is_system, remark, created_by, updated_by, deleted
)
SELECT 7014, 1001, 'agreement.privacy-agreement-markdown', '隐私协议',
       '# 隐私协议

我们重视并保护您的个人信息。

在提供服务所必需的范围内，我们可能会收集、使用、存储和传输您的账号信息、操作日志和业务数据。

我们不会在未经授权的情况下向无关第三方披露您的个人信息，除非法律法规或监管要求另有规定。

如您对隐私保护有任何疑问，请联系系统管理员。', 'PLATFORM', 0, '隐私协议 Markdown 内容', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE tenant_id = 1001 AND config_key = 'agreement.privacy-agreement-markdown' AND deleted = 0);
