# 项目目录与模块规范

本文说明仓库中真实存在的源码目录、各目录职责和新增代码的放置规则。目录事实以当前仓库为准；`.umi/`、`node_modules/`、`target/`、日志和临时产物不属于源码结构。

## 1. 仓库结构

```text
Lumira/
├─ lumira-ui/          前端工程
├─ lumira-backend/     后端工程
│  ├─ services/        聚合入口、业务模块和后台运行时
│  ├─ libs/            公共库与跨模块契约
│  ├─ sql/             数据库初始化脚本
│  └─ pom.xml          Maven 父工程
├─ deploy/             生产部署与可观测性配置
├─ bin/                启停、部署、自检、备份和压测脚本
├─ doc/                长期维护的项目文档
└─ README.md           仓库入口
```

路径必须从仓库根目录写全。后端内容位于 `lumira-backend/`，不要引用不存在的根级 `services/`、`libs/` 或 `pom.xml`。

## 2. 后端模块

目录名用于定位源码，`artifactId` 用于 Maven 构建和依赖，两者不一定相同。

| 目录 | artifactId / 运行名 | 职责 |
| --- | --- | --- |
| `services/lumira-admin` | `lumira-server` | 唯一同步请求聚合入口 |
| `services/lumira-system` | `system-service` | IAM、配置、审计和平台治理 |
| `services/lumira-auth` | `auth-service` | 登录、Token、会话和认证保护 |
| `services/lumira-file` | `file-service` | 文件对象、上传下载和安全处理 |
| `services/lumira-message` | `message-service` | 站内消息、通知和 WebSocket |
| `services/lumira-plugin` | `plugin-service` | 插件生命周期、运行时和网关 |
| `services/lumira-localization` | `localization-service` | 语言、词条、发布和回滚 |
| `services/lumira-payment` | `payment-service` | 支付配置、订单、退款和回调 |
| `services/lumira-ai` | `ai-service` | AI 助手、知识库、会话和工具 |
| `services/lumira-team` | `team-service` | 团队、成员、邀请和加入申请 |
| `services/lumira-async` | `lumira-async` | Outbox relay 和可靠异步处理 |
| `services/lumira-quartz` | `lumira-job-executor` | XXL-JOB 执行器和调度适配 |

当前运行拓扑：

```text
api-proxy -> lumira-server -> 各聚合业务模块
lumira-async              -> 异步消费和后台处理
lumira-job-executor       -> 调度并调用 owner 模块内部任务接口
MySQL / Redis / XXL-JOB   -> 基础依赖
```

只有 `lumira-admin` 是 Spring Boot 同步入口。普通业务模块之间不得依赖对方的实现；跨模块协作使用 `libs/` 中的契约、Internal API、事件或读模型。详细规则见 [模块边界](14-system-service-module-boundaries.md) 和 [数据归属](13-service-data-ownership.md)。

## 3. 后端模块内部结构

新业务模块按以下职责组织；简单模块可以合并目录，但不能混淆职责。

```text
src/main/java/com/lumira/{domain}/
├─ controller/    HTTP 协议适配
├─ app/           用例编排、事务、权限和审计
├─ domain/        领域规则、聚合和值对象
├─ repository/    持久化接口与实现
├─ mapper/        MyBatis 数据访问
├─ entity/        持久化实体，仅模块内部使用
├─ dto/           命令、查询和请求数据
├─ vo/            HTTP 响应对象
├─ convert/       对象转换
├─ event/         领域事件与集成事件
└─ security/      业务权限策略
```

- Controller 不访问 Mapper、Entity 或数据库。
- Application Service 负责事务和业务编排，可依赖 Repository 接口。
- Entity 不作为请求或响应对象跨模块传播。
- Repository/Mapper 只负责持久化，不负责权限、审计或复杂业务决策。

## 4. 公共库

| 目录 | 职责 |
| --- | --- |
| `libs/lumira-common-core` | 常量、响应和基础类型 |
| `libs/lumira-common-domain` | 通用领域抽象 |
| `libs/lumira-common-web` | Web、异常和 Trace |
| `libs/lumira-common-security` | JWT、安全上下文和数据权限 |
| `libs/lumira-common-api` | 跨模块 DTO 与 Internal API 契约 |
| `libs/lumira-plugin-api` | 插件 SPI 和运行时接口 |
| `libs/lumira-team-api` | Team 跨模块契约 |

公共库不得反向依赖 `services/*`。只有稳定且确实被多个模块使用的契约或基础能力才能进入公共库。

## 5. 前端目录

前端源码位于 `lumira-ui/src/`：

| 目录 | 职责 |
| --- | --- |
| `pages/` | 路由页面和页面级组合逻辑 |
| `routes/` | 路由、重定向和权限元数据 |
| `navigation/` | 导航树和菜单组织 |
| `services/` | API 请求和接口类型 |
| `auth/` | 登录态、Token、权限快照和登出 |
| `bootstrap/` | 应用启动和公开配置加载 |
| `components/` | 系统级复用组件 |
| `features/` | 可复用业务页面能力 |
| `hooks/` | 可复用状态和行为逻辑 |
| `query/` | React Query 基础封装 |
| `theme/` | 主题 Token 和运行时桥接 |
| `cache/` | 带租户维度的缓存治理 |
| `locales/`、`i18n/` | 国际化资源和运行时 |
| `plugins/` | 前端插件容器和扩展能力 |

页面只组合业务能力；请求统一进入 `services/`；系统级复用组件放入 `components/`，只在一个页面使用的组件留在对应页面目录。路由事实以 `lumira-ui/src/routes/meta.ts` 为准，权限可见性还需检查 `lumira-ui/src/access.ts` 和导航配置。

## 6. 新增功能放置流程

1. 判断功能属于现有模块还是新的业务域。
2. 明确表 owner、API 契约、权限标识、审计和事件需求。
3. 后端在 owner 模块内完成 Controller → Application → Domain/Repository 调用链。
4. 前端在对应页面域添加页面，在 `services/` 添加 API 封装。
5. 跨模块需求通过契约、Internal API、事件或读模型实现。
6. 补充模块测试、架构测试和必要文档。

## 7. 禁止事项

- 不在 `lumira-system` 中继续堆放无关业务域。
- 不从一个业务模块 import 另一个模块的 Mapper 或 Entity。
- 不在页面中散落服务地址、权限字符串、localStorage Key 或响应式断点。
- 不把生成目录、运行日志、截图或临时修复脚本当作长期源码目录。
- 不按“未来可能需要”提前创建空目录或公共抽象。
