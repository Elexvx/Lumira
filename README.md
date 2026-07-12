# Lumira

Lumira 是面向企业管理场景的 SaaS 平台底座，提供认证、用户与权限、团队、配置、文件、消息、插件、国际化、支付、AI 助手和后台任务等基础能力。

## 第一次使用

本地开发需要 Node.js 20+、pnpm、Java 21、Docker、MySQL 8 和 Redis 6+。

```bash
# 启动后端、异步任务、调度器和 API 代理
node bin/start-platform.mjs

# 不重新构建，直接启动
node bin/start-platform.mjs --no-build

# 停止本地环境
node bin/stop-platform.mjs
```

默认访问地址：

- API 代理：`http://localhost:8000/api`
- 后端：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`

本地前端需要单独启动：

```bash
corepack pnpm --dir lumira-ui install
corepack pnpm --dir lumira-ui dev
```

## 仓库结构

```text
Lumira/
├─ lumira-ui/          React、TypeScript、Umi Max 前端
├─ lumira-backend/     Java、Spring Boot、Maven 多模块后端
│  ├─ services/        聚合入口、业务模块和后台运行时
│  ├─ libs/            公共库与跨模块契约
│  └─ sql/             数据库初始化脚本
├─ deploy/             Docker Compose、Nginx 和可观测性配置
├─ bin/                启停、部署、自检、备份和压测脚本
├─ doc/                产品、架构、开发和测试文档
└─ README.md           本入口说明
```

后端采用模块化单体：`lumira-backend/services/lumira-admin` 是同步请求的唯一聚合入口，运行时名称为 `lumira-server`；`lumira-async` 和 `lumira-job-executor` 分别负责异步处理与任务调度。业务代码仍按 `services/lumira-*` 保持模块、数据和契约边界。

## 文档入口

从 [文档导航](doc/README.md) 开始阅读。该索引按“新成员、前端、后端、测试、部署”给出最短阅读路径，并说明每份文档的用途和维护规则。

常用入口：

- [技术方案总览](doc/01-technical-scheme.md)
- [项目目录与模块规范](doc/02-directory-module-spec.md)
- [前端开发规范](doc/06-frontend-architecture.md)
- [后端开发规范](doc/07-backend-architecture.md)
- [本地运行与排障](doc/17-architecture-runbook.md)
- [生产部署](deploy/README.md)

## 构建与检查

```bash
# 后端编译
./lumira-backend/mvnw -f lumira-backend/pom.xml clean compile

# 后端发行包
./lumira-backend/mvnw -f lumira-backend/pom.xml \
  -pl services/lumira-admin -am -DskipTests package

# 前端检查与构建
corepack pnpm --dir lumira-ui typecheck
corepack pnpm --dir lumira-ui build

# 部署配置自检
node bin/check-deployment.mjs
```

更完整的命令和故障处理见 [架构运行手册](doc/17-architecture-runbook.md)。

## 技术栈

- 后端：Java 21、Spring Boot 4、Spring Security、MyBatis-Plus、MySQL、Redis。
- 前端：React、TypeScript、Umi Max、Ant Design、ProComponents。
- 异步与实时：Outbox、`lumira-async`、XXL-JOB、WebSocket、SSE。
- 部署与观测：Docker Compose、Nginx、Prometheus、Grafana、Loki、Tempo、Alloy。
