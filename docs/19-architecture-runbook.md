# 架构运行手册

## 1. 本地开发

### 1.1 环境要求

- Node.js 20+
- pnpm 10.33.0 或兼容版本
- Java 21
- Maven 3.9+
- Docker Desktop
- MySQL 8 和 Redis 6+，可以由启动脚本复用或拉起

### 1.2 一键启动

```bash
node scripts/start-platform.mjs
```

脚本会检查端口、复用已有 MySQL/Redis/Nacos，再启动后端服务、网关和前端。

常用参数：

```bash
node scripts/start-platform.mjs --skip-infra
node scripts/start-platform.mjs --skip-services
node scripts/start-platform.mjs --skip-frontend
```

关闭本地环境：

```bash
node scripts/stop-platform.mjs
```

### 1.3 本地访问

- 前端：`http://localhost:8000`
- 网关：`http://localhost:8081`
- system-service：`http://localhost:8080`
- API 健康检查：`http://localhost:8081/api/health`

## 2. 测试环境

测试环境建议复用生产 compose，但使用独立 `.env`、独立数据库和测试域名。

启动：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml up -d --build
```

检查：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml ps
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml logs -f gateway-service
```

测试环境必须显式配置：

- `DB_PASSWORD`
- `JWT_SECRET`
- `FIELD_SECRET`
- `PLUGIN_SIGNATURE_SECRET`
- `SAAS_JOB_INTERNAL_TOKEN`
- `CORS_ALLOWED_ORIGIN_PATTERNS`

## 3. 生产环境

当前推荐形态：

```text
Vercel frontend
  -> /api rewrite
  -> api-proxy Nginx
  -> gateway-service
  -> auth/system/file/message/plugin/localization/job services
  -> MySQL / Redis / Nacos / XXL-Job
```

最简单部署：

```bash
node scripts/deploy-container.mjs --rebuild
```

常用命令：

```bash
node scripts/deploy-container.mjs --ps
node scripts/deploy-container.mjs --logs
node scripts/deploy-container.mjs --stop
```

部署后检查：

```bash
node scripts/check-deployment.mjs
```

公网后端检查：

```bash
DEPLOY_CHECK_BASE_URL=https://api.elexvx.com \
DEPLOY_CHECK_GATEWAY_URL=http://127.0.0.1:8081 \
node scripts/check-deployment.mjs
```

## 4. 构建与验证

后端全量构建：

```bash
./mvnw -DskipTests package
```

指定服务构建：

```bash
./mvnw -q -pl services/system-service -am -DskipTests compile
```

前端检查：

```bash
corepack pnpm --dir frontend typecheck
corepack pnpm --dir frontend build
```

格式和 Git hygiene：

```bash
git diff --check
```

## 5. 排障顺序

### 5.1 前端显示服务不可用

1. 检查浏览器请求是否打到 `/api`。
2. 检查 Vercel rewrite 或 `UMI_APP_API_BASE_URL`。
3. 检查 `api-proxy` 是否健康。
4. 检查 `gateway-service` 路由和日志。
5. 检查目标业务服务日志。

### 5.2 登录失败

1. 检查 `/api/v1/public/login-capabilities`。
2. 检查 `auth-service` 日志。
3. 检查 `system-service` 用户状态和安全设置读取。
4. 检查 Redis 会话和 token 配置。

### 5.3 权限或菜单异常

1. 检查用户权限快照。
2. 检查 `sys_menu`、`sys_permission`、角色授权 migration。
3. 检查前端 `access.ts` 和路由 meta。
4. 检查后端接口是否使用同一 `permission_key`。

### 5.4 Outbox 不投递

1. 检查 `platform_event_outbox` 是否有 `RECORDED` 或 `FAILED` 记录。
2. 检查 `SAAS_EVENT_OUTBOX_RELAY_ENABLED`。
3. 检查 `SAAS_EVENT_OUTBOX_DISPATCHER`。
4. 检查 `POST /internal/jobs/outbox/relay` 是否被 `job-executor` 调用。
5. 检查 `last_error` 和服务日志。

### 5.5 文件或知识库处理异常

1. 检查 `file-service` 上传和文件对象记录。
2. 检查 `system-service` AI 知识库文档状态。
3. 检查 `ai_knowledge_chunk` 是否生成。
4. 检查 `AI_KNOWLEDGE_DOCUMENT_INDEXED` 事件是否记录。

## 6. 变更前后检查清单

- 涉及新表：确认 owner service 和 migration 位置。
- 涉及新 API：确认网关路由、权限 key、前端 service。
- 涉及写操作：确认审计日志和必要的 Outbox 事件。
- 涉及跨服务：确认是否通过 API、事件或契约库通信。
- 涉及生产：先备份，再部署，再跑 `check-deployment`。
