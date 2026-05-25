# 网关、认证与权限职责边界

## 1. 请求主链路

```text
frontend -> /api -> gateway-service -> target service -> database/cache/outbox
```

前端不直接绑定业务服务地址。所有业务 API 都从 `/api` 进入 `gateway-service`，由网关按路径路由到目标服务。

## 2. 职责矩阵

| 层 | 应做 | 不应做 |
| --- | --- | --- |
| 前端 | 登录态恢复、路由守卫、按钮显隐、统一错误提示、携带 token 和 requestId | 作为唯一权限边界、硬编码服务地址、绕过网关 |
| `gateway-service` | CORS、路由、Trace 透传、基础限流、基础 token 形态校验、请求头清理 | 复杂业务鉴权、直接查业务表、生成菜单 |
| `auth-service` | 登录、登出、刷新 token、二次认证、Passkey、微信登录、登录保护 | 维护角色菜单、决定业务数据范围 |
| `system-service` IAM | 用户主数据、角色、菜单、权限快照、数据范围 | 处理登录协议、替代网关路由 |
| 业务服务 | 资源级权限、数据权限、业务规则、审计、事件发布 | 自己实现一套登录和权限字符串体系 |

## 3. 认证边界

`auth-service` 是认证协议入口，负责把“用户证明了自己是谁”变成可信会话和 token。

认证服务可以：

- 校验账号密码、验证码、Passkey、微信登录等认证方式。
- 签发 access token 和 refresh token。
- 执行登录保护、二次认证和刷新 token。
- 调用 `system-service` 获取用户状态、安全设置和基础用户信息。

认证服务不拥有：

- 菜单树。
- 角色权限分配。
- 业务资源授权规则。
- AI、文件、消息等业务域数据。

## 4. 权限边界

权限分为四层：

1. 登录态：请求是否有有效会话。
2. 路由权限：用户是否能访问某个页面或 API 类别。
3. 操作权限：用户是否拥有 `permission_key`。
4. 数据权限：用户能看到哪些组织、人员或资源。

落点：

- 登录态由 `auth-service`、共享安全组件和业务服务安全过滤器共同校验。
- 路由和按钮权限由 `system-service` 生成权限快照，前端消费。
- 操作权限在后端业务服务必须再次校验。
- 数据权限由 owner service 根据权限快照、组织关系和资源 owner 执行。

## 5. permission_key 规范

权限标识必须统一使用 `permission_key`。

命名建议：

```text
<domain>:<resource>:<action>
```

示例：

- `system:user:view`
- `system:role:permissions`
- `ai:knowledge:document:upload`
- `message:message:write`
- `plugin:management:install`

规则：

- 前端页面、按钮、后端接口、菜单种子必须使用同一套 key。
- 新增 key 必须同时补菜单/权限 migration、前端 access、后端校验点。
- 不允许页面私有字符串和后端私有字符串各写一套。

## 6. 网关路由策略

当前网关按路径路由：

- `/api/v1/auth/**` -> `auth-service`
- `/api/v1/files/**`、`/api/uploads/**` -> `file-service`
- `/api/v1/message/**`、`/ws/message` -> `message-service`
- `/api/v1/plugins/**`、`/api/p/**` -> `plugin-service`
- `/api/v1/localization/**` -> `localization-service`
- `/api/v1/system/**`、`/api/v1/profile/**`、`/api/v1/dashboard/**`、`/api/v1/audit/**`、`/api/ai/**` -> `system-service`

新增服务时，先在 owner service 完成 API，再在 `gateway-service` 增加路由，最后前端服务层只调用 `/api` 下的相对路径。

## 7. 常见错误

- 只在前端隐藏按钮，后端接口不校验。
- 业务服务为了判断权限直接查 `sys_role` 或 `sys_menu`。
- 新页面绕过菜单/权限 migration，直接写死在前端导航。
- 网关承担复杂业务权限，导致规则散落在路由配置里。
- `auth-service` 和 `system-service` 同时维护用户状态，出现双主数据。
