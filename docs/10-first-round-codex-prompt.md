# 第一轮 Codex 执行提示词：工程骨架与基础设施底座初始化

## 1. 目标

你现在要为一个大型 SaaS 系统搭建第一轮工程底座。该系统前端基于 `Ant Design Pro + React 18 + TypeScript + Umi Max`，后端基于 `Spring Boot 3 + Java 21 + MyBatis Plus + Spring Security + Redis + MySQL`。

本轮只做工程骨架与基础设施底座初始化，不要急着铺业务页面，不要提前实现系统管理模块，不要为了演示效果写很多假页面。

唯一目标：

1. 搭建前后端标准工程骨架。
2. 建立统一目录结构与分层边界。
3. 建立统一请求/响应/异常/错误码基础设施。
4. 建立多租户、认证、权限、缓存、日志、任务、文件等平台能力的标准接入位置，即使本轮不全部实现业务逻辑，也必须完成骨架。
5. 保证项目可以本地启动，具备后续迭代基础。

成功标准不是页面多，而是骨架稳、结构对、入口统一。

## 2. 前端必须完成的内容

### 2.1 初始化前端工程

基于 Umi Max 和 Ant Design Pro 建立前端项目骨架，使用：

- React 18
- TypeScript
- Ant Design 5
- Ant Design Pro / ProComponents
- 统一 request 请求封装
- 统一运行时配置入口

### 2.2 前端目录结构标准化

请建立并整理为以下结构，名称允许微调，但职责必须保持一致：

```text
src/
├── app.ts
├── access.ts
├── global.less
├── layouts/
│   ├── BasicLayout/
│   ├── UserLayout/
│   ├── BlankLayout/
│   └── components/
├── pages/
│   ├── dashboard/
│   ├── system/
│   ├── tenant/
│   ├── iam/
│   ├── audit/
│   ├── profile/
│   └── exception/
├── components/
│   ├── QueryPanel/
│   ├── ActionBar/
│   ├── DataTable/
│   ├── DetailDrawer/
│   ├── PermissionButton/
│   ├── TenantSelector/
│   ├── EmptyState/
│   └── common/
├── services/
│   ├── auth/
│   ├── tenant/
│   ├── user/
│   ├── iam/
│   ├── dict/
│   ├── config/
│   ├── audit/
│   └── common/
├── auth/
├── tenant/
├── responsive/
├── cache/
├── hooks/
├── utils/
├── constants/
├── enums/
├── types/
└── assets/
```

要求这些目录不要只是空壳，要放入基础占位实现和必要说明。

### 2.3 实现前端三类布局骨架

完成以下布局骨架：

- `BasicLayout`：登录后的主业务壳层
- `UserLayout`：登录页等用户入口页
- `BlankLayout`：空白页或特殊流程页

其中 `BasicLayout` 必须具备以下占位或基础能力：

- 左侧导航区域
- 顶部工具区
- 页面内容容器
- 用户菜单入口占位
- 租户切换器占位
- 通知入口占位
- 响应式折叠基础能力

### 2.4 建立统一前端请求层

实现统一请求封装，至少支持：

- 统一 API 前缀管理
- Token 自动注入
- 预留租户 header 注入位
- 统一错误码拦截
- 登录失效拦截
- 统一 message 提示机制
- 文件流接口预留处理
- requestId 透传位预留

不要让页面里直接到处写 `fetch` 或 `axios` 原始调用。

### 2.5 建立前端基础能力骨架

建立以下能力的基础文件与最小实现：

- `auth/`：token 管理、登录态恢复、登出清理
- `tenant/`：当前租户上下文存储、租户切换基础方法、缓存清理预留
- `responsive/`：断点常量、设备判断 hook、布局断点策略
- `cache/`：本地缓存封装，要求支持 key 规范和租户维度预留
- `access.ts`：权限表达层骨架
- `hooks/`：至少放入 `useResponsive`、`usePermission` 等基础 hooks 占位

### 2.6 建立前端基础页面占位

不要铺很多业务页，但需要有基础占位页面用于验证架构，例如：

- 登录页
- 首页占位页
- 无权限页
- 404 页
- 一个标准的系统管理占位页

这些页面必须挂到标准布局和路由体系里。

## 3. 后端必须完成的内容

### 3.1 初始化后端工程

创建 `Spring Boot 3 + Java 21` 工程，并接入：

- Spring Web
- Spring Security
- MyBatis Plus
- MySQL
- Redis
- Validation
- Lombok
- 数据库迁移工具（Flyway 或 Liquibase，二选一）
- OpenAPI/Swagger（建议接入）

### 3.2 后端目录结构模块化

建立以下结构，名称可适度微调，但职责不能乱：

```text
src/main/java/com/yourcompany/saas/
├── SaasApplication.java
├── common/
│   ├── api/
│   ├── exception/
│   ├── enums/
│   ├── model/
│   ├── util/
│   └── constant/
├── infrastructure/
│   ├── config/
│   ├── db/
│   ├── redis/
│   ├── security/
│   ├── tenant/
│   ├── logging/
│   ├── observability/
│   └── storage/
├── modules/
│   ├── auth/
│   ├── tenant/
│   ├── user/
│   ├── org/
│   ├── iam/
│   ├── dict/
│   ├── config/
│   ├── file/
│   ├── task/
│   └── audit/
```

每个模块内部要有基本分层结构，不要整项目平铺。

建议模块内部至少有：

- `controller`
- `app`
- `domain`
- `mapper`
- `entity`
- `dto`
- `vo`
- `convert`

### 3.3 统一 API 返回结构

实现统一返回体，至少包括：

- `code`
- `message`
- `data`
- `requestId`
- `timestamp`

### 3.4 统一错误码体系

建立基础错误码枚举体系，至少包含：

- 成功
- 参数错误
- 未登录
- 无权限
- 资源不存在
- 业务异常
- 租户异常
- 系统异常

不允许魔法字符串散落代码中。

### 3.5 统一全局异常处理

实现全局异常处理器，至少处理：

- 参数校验异常
- 业务异常
- 权限异常
- 认证异常
- 系统异常

要求统一转标准返回体，不向前端暴露堆栈，日志中保留 `requestId/traceId`。

### 3.6 建立基础安全与认证骨架

本轮先不做完整登录闭环，但必须搭出骨架，包括：

- Spring Security 基础配置
- 放行登录接口预留
- 获取当前用户上下文占位
- JWT / token 过滤器骨架
- Redis 会话能力预留
- 认证模块目录与基础类

### 3.7 建立多租户上下文骨架

至少需要：

- 平台上下文类
- 请求中解析 tenant 的拦截器/过滤器骨架
- tenant header 常量
- 自动注入租户维度设计预留
- 日志中记录 tenantId 的能力预留

### 3.8 建立日志与 traceId 基础设施

完成：

- 请求级 traceId 生成或透传
- 日志上下文注入
- 统一日志格式基础配置
- `requestId/traceId` 在返回体中输出

### 3.9 建立 Redis 与缓存基础封装

完成：

- Redis 连接配置
- 基础缓存工具类或封装层
- key 命名规范常量预留
- tenant/user 维度 key 组合方法预留

### 3.10 建立文件与任务骨架模块

在 `modules/file` 和 `modules/task` 中建立基础骨架，至少包含：

- 分层结构占位
- `file_object` 与 `task_job` 实体定义预留
- 上传与任务查询的接口占位结构

## 4. 数据库必须完成的内容

本轮要落地建表迁移脚本，至少包括：

- `tenant_info`
- `tenant_package`
- `tenant_domain`
- `tenant_quota`
- `sys_user`
- `sys_user_tenant`
- `sys_user_tenant_profile`
- `sys_department`
- `sys_position`
- `sys_role`
- `sys_menu`
- `sys_user_role`
- `sys_role_menu`
- `sys_data_scope_rule`
- `sys_dict_type`
- `sys_dict_item`
- `sys_config`
- `file_object`
- `task_job`
- `audit_operate_log`
- `audit_login_log`

要求：

1. 字段命名统一小写下划线。
2. 底座表统一具备必要审计字段预留。
3. 租户级表要有 `tenant_id`。
4. 关键主键和唯一键必须正确设计。
5. 所有建表通过迁移工具管理，不允许零散 SQL 堆放。

## 5. 必须输出的工程能力

本轮完成后项目必须达到：

前端：

- 可以启动
- 有统一布局
- 有基础路由
- 有统一请求层
- 有 `auth/tenant/responsive/cache` 基础能力目录与实现
- 有基础异常页和登录页占位

后端：

- 可以启动
- 有统一返回体
- 有统一异常处理
- 有统一错误码
- 有 Spring Security 骨架
- 有租户上下文骨架
- 有 Redis 基础接入
- 有 traceId 基础接入
- 有数据库迁移脚本
- 有 `file/task/audit` 等模块基础骨架

工程侧：

- 有清晰目录结构
- 有基础配置文件
- 有开发环境示例配置
- 可以本地运行和调试

## 6. 严格禁止事项

1. 不要开始铺大量系统管理页面。
2. 不要为了演示效果写很多静态假数据页面。
3. 不要在前端把菜单直接写死成最终方案。
4. 不要在页面里直接写大量请求逻辑。
5. 不要在后端控制器直接操作 mapper。
6. 不要把多租户做成“将来再说”，必须把上下文骨架搭起来。
7. 不要跳过统一异常处理和统一返回结构。
8. 不要把缓存和日志留到后面再补。
9. 不要为了快速跑通写与后续规范冲突的临时代码。
10. 不要让目录结构失控，所有代码必须落在统一模块边界中。

## 7. 本轮验收标准

1. 前端项目可启动，并显示统一壳层或登录页。
2. 后端项目可启动，并暴露基础健康接口或测试接口。
3. 前端存在统一请求封装与错误拦截入口。
4. 后端存在统一响应结构、统一错误码和统一异常处理。
5. 数据库迁移脚本可执行，核心底座表可建表成功。
6. 后端已具备认证、租户、缓存、日志、文件、任务等模块骨架。
7. 项目目录结构清晰，能支撑下一轮直接开发认证与租户闭环。
8. 代码组织方式符合“大型 SaaS 底座”思路，而不是 demo 思路。

## 8. 额外输出要求

本轮结束时额外输出：

1. 前端目录说明
2. 后端目录说明
3. 本轮已完成内容说明
4. 下一轮推荐开发顺序说明
5. 本地运行方式说明（前后端分别说明）
6. 环境变量样例或配置说明

## 9. 执行风格要求

请以企业级长期维护项目标准写代码，不以快速 demo 标准写代码。优先统一性、可维护性、扩展性和多轮衔接。代码命名、分层和模块边界必须清晰。必要时宁可多做一层封装，也不要让后续迭代陷入混乱。
