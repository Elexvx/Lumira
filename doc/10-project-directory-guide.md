# 项目目录结构说明

本文只描述当前仓库中真实存在的源码目录和运行入口。`.umi/`、`.umi-production/`、`node_modules/`、`target/`、运行日志和临时部署产物都不属于源码结构，不在本文展开。

## 1. 仓库根目录

```text
Lumira/
├─ lumira-ui/              React、TypeScript、Umi Max 前端
├─ lumira-backend/         Java、Spring Boot、Maven 多模块后端
│  ├─ services/            聚合入口、业务模块和后台运行时
│  ├─ libs/                公共库与跨模块 API 契约
│  ├─ sql/                 数据库初始化脚本与说明
│  ├─ storage/             后端本地运行存储
│  └─ pom.xml              Maven 父工程
├─ deploy/                 Docker Compose、Nginx 与可观测性配置
├─ bin/                    启动、部署、自检、备份和压测脚本
├─ doc/                    架构、产品、测试和运维文档
├─ artifacts/              本地生成的验证产物，不作为源码依赖
├─ storage/                根级本地运行存储
└─ README.md               项目入口说明
```

后端模块全部位于 `lumira-backend/`，仓库根目录不存在 `services/`、`libs/`、`sql/` 或根 Maven `pom.xml`。开发和文档引用必须使用上面的真实路径。

## 2. 后端目录与 Maven 名称

目录名用于定位源码，Maven `artifactId` 用于依赖和构建。两者不是同一个概念。

| 当前目录 | Maven artifactId | 主要职责 |
| --- | --- | --- |
| `services/lumira-admin` | `lumira-server` | 正式同步请求聚合入口 |
| `services/lumira-system` | `system-service` | 系统、IAM、配置、审计及平台业务模块 |
| `services/lumira-auth` | `auth-service` | 登录、Token、会话和认证保护 |
| `services/lumira-file` | `file-service` | 文件对象、上传下载和文件安全 |
| `services/lumira-message` | `message-service` | 站内消息、通知和 WebSocket |
| `services/lumira-plugin` | `plugin-service` | 插件生命周期、运行时和网关 |
| `services/lumira-localization` | `localization-service` | 语言、词条、发布和回滚 |
| `services/lumira-payment` | `payment-service` | 支付配置、订单、退款和回调 |
| `services/lumira-ai` | `ai-service` | AI 助手、知识库、会话和工具调用 |
| `services/lumira-team` | `team-service` | 团队、成员、邀请和加入申请 |
| `services/lumira-async` | `lumira-async` | Outbox relay 与可靠异步处理 |
| `services/lumira-quartz` | `job-executor` | XXL-JOB 执行器和调度适配 |

当前推荐运行拓扑是：

```text
api-proxy
  -> lumira-server             同步 API 与 WebSocket

lumira-async                   异步消费与后台处理
lumira-job-executor            XXL-JOB 调度执行
  -> lumira-server 内部任务接口

MySQL / Redis / XXL-JOB Admin  基础依赖
```

`lumira-admin` 聚合业务模块运行，但不应把模块边界写成任意跨模块依赖。跨模块读取优先使用 `libs/` 中的契约、owner API、事件或投影。

## 3. 系统模块

`lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/` 的主要结构为：

```text
com/lumira/saas/
├─ common/                    通用响应、异常与工具
├─ infrastructure/            数据库、安全、Redis、事件等基础设施
└─ modules/
   ├─ account/
   ├─ activity/
   ├─ ai/
   ├─ architecture/
   ├─ audit/
   ├─ auth/
   ├─ competition/
   ├─ config/
   ├─ expert/
   ├─ iam/
   ├─ platform/
   ├─ project/
   ├─ system/
   ├─ user/
   └─ workflow/
```

该模块的资源目录当前包含 `application.yml`、`banner.txt`、`logback-spring.xml`、`mapper/` 和 `captcha/`。数据库初始化入口位于 `lumira-backend/sql/saas.sql`；不要引用并不存在的 `application-dev.yml` 或本模块 `db/migration/` 目录。

## 4. 公共库

| 当前目录 | Maven artifactId | 主要职责 |
| --- | --- | --- |
| `libs/lumira-common-core` | `common-core` | 通用常量、响应与基础类型 |
| `libs/lumira-common-domain` | `common-domain` | 通用领域抽象 |
| `libs/lumira-common-web` | `common-web` | Web、异常、Trace 等公共能力 |
| `libs/lumira-common-security` | `common-security` | JWT、安全上下文和数据权限 |
| `libs/lumira-common-api` | `lumira-api` | 跨模块 DTO 与 Internal API 契约 |
| `libs/lumira-plugin-api` | `plugin-api` | 插件 SPI 与运行时接口 |
| `libs/lumira-team-api` | `lumira-team-api` | Team 跨模块契约 |

公共库不能反向依赖业务模块。`lumira-admin` 可以聚合业务模块，普通业务模块之间不应通过 Maven 依赖直接穿透实现。

## 5. 前端目录

前端主源码位于 `lumira-ui/src/`：

| 目录 | 职责 |
| --- | --- |
| `pages/` | 路由页面和页面级组合逻辑 |
| `routes/` | 真实路由、重定向和页面权限元数据 |
| `navigation/` | 导航树与菜单组织 |
| `services/` | API 请求与接口类型 |
| `auth/` | 登录态、Token、权限快照和登出生命周期 |
| `bootstrap/` | 应用启动与公开配置加载 |
| `components/` | 系统级复用组件 |
| `features/` | 可复用业务页面能力 |
| `query/` | React Query 基础封装 |
| `theme/` | 主题 token 与运行时桥接 |
| `cache/` | 带租户维度的缓存治理 |
| `locales/`、`i18n/` | 国际化资源与运行时 |
| `plugins/` | 前端插件容器与扩展能力 |

主要页面域包括账户、活动、AI、证书、竞赛、仪表盘、专家、文件、支付、插件、项目、设置、系统、团队、用户和工作流。路由事实以 `lumira-ui/src/routes/meta.ts` 为准，菜单可见性还需同时检查 `lumira-ui/src/access.ts` 和导航配置。

## 6. 部署与文档

- `deploy/docker-compose.prod.yml`：生产容器拓扑。
- `deploy/nginx/`：API proxy 与公网 edge 配置。
- `deploy/observability/`：Prometheus、Grafana、Loki、Tempo 和 Alloy 配置。
- `bin/start-platform.mjs`：本地平台启动入口。
- `bin/deploy-container.mjs`：容器更新、重建和部署入口。
- `bin/check-deployment.mjs`：部署健康检查。
- `doc/architecture/`：模块、表、事件和持久化边界。
- `doc/adr/`：已接受的架构决策。
- `doc/20-product-requirements-document.md`：产品范围与验收基线。

## 7. 事实来源优先级

目录或名称发生冲突时，按以下顺序判断：

1. 当前文件系统和源码。
2. `lumira-backend/pom.xml` 与各模块 `pom.xml`。
3. `lumira-ui/src/routes/meta.ts`、`access.ts` 和导航配置。
4. `deploy/docker-compose.prod.yml` 与实际环境变量。
5. 架构和产品文档。

生成目录、临时制品、旧部署记录和本地日志不能作为当前项目结构依据。
