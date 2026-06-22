# Lumira

Lumira 是一套面向企业管理场景的 SaaS 平台底座，提供用户、权限、配置、文件、消息、插件、国际化、支付、AI 助手和后台任务等基础能力。

当前仓库已经按职责整理为更清晰的目录结构：前端集中在 `lumira-ui/`，后端集中在 `lumira-backend/`，部署、脚本、文档和发布证据保留在仓库根目录下。

## 目录结构

```text
Lumira/
├─ lumira-ui/              前端工程（React、TypeScript、Umi Max）
├─ lumira-backend/               后端工程（Java、Spring Boot、Maven 多模块）
│  ├─ services/           后端启动入口和业务模块
│  ├─ libs/               公共基础库和跨模块 API 契约
│  ├─ sql/           数据库初始化脚本和说明
│  ├─ storage/            本地运行时存储目录
│  ├─ pom.xml             后端 Maven 父工程
│  └─ mvnw / mvnw.cmd     Maven Wrapper
├─ deploy/                Docker、Nginx、监控和生产部署资源
├─ doc/                  架构、权限、部署、测试和运维文档
├─ bin/               本地启动、部署、自检和发布脚本
├─ artifacts/             发布、验证和运行证据产物
└─ README.md
```

## 后端模块

正式后端启动入口是 `lumira-backend/services/lumira-admin`，它聚合各业务模块并统一暴露 API、WebSocket、健康检查和运维端点。

- `lumira-backend/services/lumira-admin`：聚合后端启动入口。
- `lumira-backend/services/lumira-system`：系统、权限、配置、审计等核心能力。
- `lumira-backend/services/lumira-team`：团队、成员、邀请和加入申请等 Team 业务域。
- `lumira-backend/services/lumira-auth`：认证、会话、登录保护和二次验证。
- `lumira-backend/services/lumira-file`：文件对象、上传、存储空间和文件安全。
- `lumira-backend/services/lumira-message`：站内消息、通知和 WebSocket。
- `lumira-backend/services/lumira-plugin`：插件管理、插件运行时和插件网关。
- `lumira-backend/services/lumira-localization`：国际化语言、词条和发布。
- `lumira-backend/services/lumira-payment`：支付配置、订单、退款和支付事件。
- `lumira-backend/services/lumira-ai`：AI 助手、知识库、会话和工具调用。
- `lumira-backend/services/lumira-quartz`：后台任务、XXL-JOB handler 和 relay 调度。
- `lumira-backend/libs/lumira-team-api`：Team 跨模块 Internal API 契约。
- `lumira-backend/libs/*`：后端公共能力和跨模块契约。

## 架构文档入口

- [模块边界](doc/architecture/module-boundary.md)
- [表归属边界](doc/architecture/table-ownership.md)
- [事件边界](doc/architecture/event-boundary.md)
- [业务模块模板](doc/architecture/business-module-template.md)

## 常用命令

启动平台：

```bash
node bin/start-platform.mjs
```

跳过重新构建启动：

```bash
node bin/start-platform.mjs --skip-build
```

停止平台：

```bash
node bin/stop-platform.mjs
```

后端编译：

```bash
./lumira-backend/mvnw -f lumira-backend/pom.xml clean compile
```

后端发行包：

```bash
./lumira-backend/mvnw -f lumira-backend/pom.xml -pl services/lumira-admin -am -DskipTests package
```

前端生产构建：

```bash
corepack pnpm --dir lumira-ui run build
```

部署自检：

```bash
node bin/check-deployment.mjs
```

## 常用访问地址

- 后端：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`
- API 代理：`http://localhost:8000`

## 技术栈

- 后端：Java 21、Spring Boot 4、MyBatis-Plus、Flyway、Redis、MySQL。
- 前端：React、TypeScript、Umi Max、Ant Design、ProComponents。
- 异步与实时：Outbox、XXL-JOB、WebSocket、SSE。
- 部署：Docker Compose、Nginx、Prometheus、Grafana、Loki、Tempo、Alloy。
