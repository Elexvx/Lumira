# Vercel 前端 + 单体微服务后端部署说明

这套部署用于高安全、高稳定的准生产演示环境。前端托管在 Vercel，服务器负责单体微服务后端和统一 API 入口。

## 部署形态

```text
用户浏览器
  -> Vercel 前端
    -> /api/** rewrite 到 https://bm.aiadc.org.cn/api/**
    -> /ws/**  rewrite 到 https://bm.aiadc.org.cn/ws/**

bm.aiadc.org.cn / HTTPS / CDN / WAF
  -> 服务器 edge-proxy Nginx (80/443)
    -> /api/** 反向代理到 lumira-api-proxy
    -> /ws/** 反向代理到 lumira-api-proxy
    -> /health 反向代理到 lumira-api-proxy
  -> lumira-api-proxy
    -> /api/** 反向代理到 lumira-server
    -> /ws/** 反向代理到 lumira-server
    -> /api/health 反向代理到 lumira-server
  -> lumira-server
    -> auth module
    -> system module
    -> file module
    -> message module
    -> plugin module
    -> localization module
    -> job module
  -> lumira-async / lumira-job-executor
  -> MySQL / Redis / XXL-Job
```

## 默认启动组件

- MySQL：`mysql:8.4`
- Redis：`redis:7.4`
- Nacos：当前生产和安装拓扑不提供内置容器，请保持 `NACOS_CONFIG_ENABLED=false` 和 `NACOS_DISCOVERY_ENABLED=false`
- XXL-Job Admin：`xuxueli/xxl-job-admin:3.4.0`
- edge-proxy：80/443 对外统一入口，负责 HTTPS 终止和路由
- api-proxy：Nginx 后端统一入口
- lumira-server：单体微服务后端入口，聚合系统、认证、文件、消息、插件、本地化和任务模块
- lumira-async：异步 owner runtime，负责 outbox relay、文件后处理等异步工作
- lumira-job-executor：XXL-Job 执行器，调用 async/file/message/payment/plugin 后台任务

`lumira-ui` 容器只作为本地备用预览，默认不随生产部署启动。正式前端由 Vercel 托管。

## 一键部署后端完整平台

平台安装和环境检测统一由一个脚本分步执行。只检测服务器环境时运行：

```bash
node bin/install-platform.mjs --check-only
```

严格模式会把警告也视为失败，适合 CI 或正式交付前检查：

```bash
node bin/install-platform.mjs --check-only --strict
```

需要给自动化平台读取时输出纯 JSON：

```bash
node bin/install-platform.mjs --check-only --json
```

首次安装或服务器换规格时，推荐先运行交互式安装器：

```bash
node bin/install-platform.mjs
```

安装器会分步完成：

- 探测 CPU、内存、磁盘、系统架构。
- 检测 Node.js、Docker、Docker Compose、端口占用、必填环境变量、外部 MySQL 连通性和资源档建议。
- 交互确认 API 域名、前端 Origin、是否启用内置 MySQL、前端容器和观测栈。
- 按服务器规格自动写入 `deploy/.env` 的 JVM、容器内存、Redis、数据库连接池、Tomcat 线程池、限流和日志轮转参数。
- 检查 Docker；Linux 服务器缺少 Docker 时可自动安装。
- 按阶段启动基础组件、`lumira-server`、`lumira-async`、`lumira-job-executor`、API proxy、可选前端容器和可选观测栈。
- 自动运行部署健康检查和轻量并发冒烟。

无人值守安装：

```bash
node bin/install-platform.mjs --yes
```

常用参数：

```bash
node bin/install-platform.mjs \
  --api-domain=bm.aiadc.org.cn \
  --lumira-ui-origin=https://bm.aiadc.org.cn \
  --yes
```

如果需要完全本地化演示，可启用内置 MySQL 和前端容器：

```bash
node bin/install-platform.mjs --local-mysql --lumira-ui
```

- `--local-mysql` 仅启用内置 MySQL。当前生产与安装拓扑不提供 bundled Nacos，请保持 `NACOS_CONFIG_ENABLED=false` 和 `NACOS_DISCOVERY_ENABLED=false`。

日常已有环境更新推荐直接拉取 CI 产物：

从仓库根目录运行：

```bash
node bin/deploy-container.mjs --pull
```

部署脚本会在启动后端、异步任务或调度执行器之前，先运行独立的 Flyway 迁移容器：

- 全新空库由 `lumira-backend/sql/saas.sql` 创建完整结构和基础数据。
- 已有数据库按 `deploy/migrations/V<version>__<name>.sql` 顺序升级，执行记录保存在 `lumira_platform_update_schema_history`。
- 迁移失败时部署立即停止，不会启动与数据库版本不匹配的新应用。
- 仅部署前端时不会触发数据库迁移。`--skip-migrations` 只用于明确不涉及数据库的应急诊断，不应作为常规发布参数。

CI 会用一次性 MySQL 8.4 容器实际导入全新数据库、校验在线迁移链的一致性，并自动把最新迁移版本写入发布清单，禁止手工维护数据库目标版本。

`main` 分支 CI 会在后端 Maven 测试、前端 lint/typecheck/test 都通过后，自动构建并发布镜像：

- `ghcr.io/elexvx/lumira/lumira-server:main`
- `ghcr.io/elexvx/lumira/lumira-ui:main`
- `ghcr.io/elexvx/lumira/lumira-server:sha-<12位提交>`
- `ghcr.io/elexvx/lumira/lumira-ui:sha-<12位提交>`

服务器使用 `deploy/.env` 中的 `LUMIRA_SERVER_IMAGE` 和 `LUMIRA_FRONTEND_IMAGE` 决定要部署哪个镜像。追求可回滚和可复现时，建议把 `main` 改成对应的 `sha-<提交>` tag。如果需要在服务器本机重新编译镜像，可继续使用：

```bash
node bin/deploy-container.mjs --rebuild
```

默认镜像构建不会下载 OpenTelemetry Java agent，避免默认关闭的观测能力阻塞发布构建。生产环境需要启用 `OTEL_JAVAAGENT_ENABLED=true` 时，先在构建环境设置可信制品地址：

```bash
OTEL_JAVAAGENT_URL=https://your-artifact-repository/opentelemetry-javaagent.jar \
node bin/deploy-container.mjs --rebuild
```

如果运行时开启了 agent 但镜像内没有非空 agent 文件，`lumira-server` 会启动失败并输出明确错误，避免静默丢失 trace。

如果服务器无法稳定访问 Docker Hub，可在本机 rebuild 时切到可信镜像源：

```bash
MAVEN_IMAGE=registry.example.com/maven:3.9.11-eclipse-temurin-21 \
JRE_IMAGE=registry.example.com/eclipse-temurin:21-jre \
NODE_IMAGE=registry.example.com/node:22-bookworm-slim \
NGINX_IMAGE=registry.example.com/nginx:1.29-alpine \
node bin/deploy-container.mjs --rebuild
```


注意：上面的重建命令会保留现有 MySQL 数据，不会重置 `admin` 密码。全新部署的默认管理员账号来自 Flyway 基线数据：

- 用户名：`admin`
- 初始密码：`123456`
- 生产环境可通过 `LUMIRA_INITIAL_ADMIN_PASSWORD` 覆盖首次登录密码；覆盖只会在 `admin` 仍处于出厂密码时生效。
- 首次登录后会强制修改初始密码

如果需要在测试环境彻底重装数据库，先确认数据可以删除，再执行：

```bash
node bin/deploy-container.mjs --reset
node bin/deploy-container.mjs --rebuild
```

`--reset` 会删除数据库、上传文件、插件文件和任务日志数据，不能用于需要保留业务数据的环境。脚本会要求在交互终端输入 `DELETE_LEGENDARY_DATA`；CI 或自动化环境必须显式设置 `DEPLOY_RESET_CONFIRM=DELETE_LEGENDARY_DATA`，否则拒绝执行。

默认部署按 4C4G 小型服务器收敛资源占用：Java 服务限制堆比例和元空间，Tomcat 线程池、Hikari 连接池、Redis 内存、Docker 日志和 API 入口限流都有默认上限。高流量时优先返回 429 或排队，而不是让 JVM、数据库连接和磁盘日志把服务器打满。

首次运行会自动生成 `deploy/.env`，并为数据库、JWT、插件签名、任务内部调用等配置生成随机密钥。

文件安全扫描默认使用内置轻量规则引擎，不依赖外部进程。生产环境需要接入 ClamAV 时，在 `deploy/.env` 设置：

```bash
LUMIRA_FILE_SECURITY_SCAN_MODE=CLAMAV
LUMIRA_FILE_SECURITY_SCAN_CLAMAV_HOST=127.0.0.1
LUMIRA_FILE_SECURITY_SCAN_CLAMAV_PORT=3310
LUMIRA_FILE_SECURITY_SCAN_TIMEOUT_MILLIS=3000
```

扫描仍由 File owner 的异步处理任务执行，上传 HTTP 回包不会等待外部扫描；ClamAV 不可用时任务失败并进入既有重试和死信治理，不会把文件误标为安全。

图片 OCR 默认关闭，但 OCR 任务仍会写入 `OCR_RESULT/SKIPPED` 产物并成功结束，避免异步队列反复失败。生产环境需要 OCR 时，在镜像或宿主机侧准备 Tesseract，并设置：

```bash
LUMIRA_FILE_OCR_MODE=TESSERACT
LUMIRA_FILE_OCR_TESSERACT_COMMAND=tesseract
LUMIRA_FILE_OCR_LANGUAGES=eng+chi_sim
LUMIRA_FILE_OCR_TIMEOUT_MILLIS=5000
```

OCR 同样由 File owner 的异步处理任务执行；抽取到文本时会写入 `TEXT_CONTENT` artifact，供 AI owner 通过 `FileInternalApi` 的只读契约消费。

图片缩略图同样走 File owner 异步处理任务。本地存储会生成 `.thumb.jpg` 并写入 `THUMBNAIL_RESULT/GENERATED`；远程对象存储在未接入具体 provider 原生缩略图前会写入 `THUMBNAIL_RESULT/DEFERRED_REMOTE_STORAGE`，任务成功结束，避免队列反复重试。

部署完成后脚本会自动检查：

- 对外入口：`https://bm.aiadc.org.cn/health`
- API 健康检查：`https://bm.aiadc.org.cn/api/health`
- 版本检查：`https://bm.aiadc.org.cn/api/version`
- 平台更新提醒：后台 `系统监控 -> 平台更新` 会只读检查 GitHub 最新提交；默认更新源为 `https://api.github.com/repos/Elexvx/lumira/commits/main`，如需替换官方更新源，可在 `deploy/.env` 设置 `PLATFORM_UPDATE_SOURCE_URL`。
- 平台手动更新：CI 会把四个运行时镜像的 digest 固定 manifest 发布到 `continuous` GitHub Release。后台先执行资源、拓扑和迁移预检，再由宿主机 `lumira-updater` 启动空闲槽、验证、热切流并排空旧槽。
- HTTP/API 更新期间没有计划内中断；WebSocket 在 60 秒排空上限后可能重连。异步和 XXL-Job worker 会串行停止领取、等待在途工作、替换并恢复。
- Linux 首次安装会幂等注册并启动 `lumira-updater.service`。安装器也会把现有 `lumira-server` 无中断迁移到 blue 槽。已有服务器可补装：
```bash
sudo node bin/install-lumira-updater.mjs --deploy-dir /opt/lumira/deploy
```

`deploy/.env` 中的容器访问地址、允许主机和 token 必须保持一致：
```text
PLATFORM_UPDATE_MANIFEST_URL=https://api.github.com/repos/Elexvx/Lumira/releases/tags/continuous
PLATFORM_UPDATE_AGENT_URL=http://host.docker.internal:9788
PLATFORM_UPDATE_AGENT_ALLOWED_HOSTS=host.docker.internal
PLATFORM_UPDATE_AGENT_TOKEN=replace-with-strong-local-token
PLATFORM_UPDATE_ALLOWED_IMAGE_PREFIXES=ghcr.io/elexvx/lumira/
```

在线版本只能包含 Expand-only 数据库迁移。删除、重命名、缩窄类型和强制非空等收缩操作必须在兼容窗口结束后单独人工执行。自动回滚只切回上一应用槽位，不恢复数据库备份；上一槽位默认保留 30 分钟。

演练 updater 流程但不改写 `.env`、不执行部署命令时：

```bash
LUMIRA_UPDATER_DRY_RUN=true node bin/lumira-updater.mjs --dry-run
```
- lumira-server 健康检查：`http://127.0.0.1:8080/actuator/health`
- 公开登录配置接口：`https://bm.aiadc.org.cn/api/v1/public/login-capabilities`

## 可观测性闭环

默认部署不启动观测栈。需要 Prometheus 指标、OpenTelemetry trace、Loki 日志、Tempo trace 存储和 Grafana 看板时运行：

```bash
node bin/deploy-container.mjs --rebuild --observability
```

观测端口默认只绑定本机：

- Grafana：`http://127.0.0.1:3001`
- Prometheus：`http://127.0.0.1:9090`
- Loki：`http://127.0.0.1:3100`
- Tempo：`http://127.0.0.1:3200`
- Alloy：`http://127.0.0.1:12345`

Grafana 会自动 provision Prometheus、Loki、Tempo 数据源和 `Lumira Observability Overview` 看板。`lumira-server` 会暴露 `/actuator/prometheus`，并在启用观测栈时通过 OpenTelemetry Java Agent 把 trace 发送到 Alloy。

4C4G 服务器上不建议常驻完整观测栈；需要排查性能问题时短时开启，排查结束后停止观测栈释放内存。

## 4C4G 稳定运行建议

- 默认使用外部或 1Panel MySQL；本仓库内置 MySQL 仅用于 `local-mysql` profile。
- 默认不启用 Nacos；当前生产和安装拓扑不支持 `--nacos`，请保持 `NACOS_CONFIG_ENABLED=false` 和 `NACOS_DISCOVERY_ENABLED=false`
- 默认不启用 `lumira-ui` 容器，正式前端走 Vercel；服务器只承担后端和 API proxy。
- `deploy/.env` 里的 `*_MEM_LIMIT`、`SERVER_TOMCAT_THREADS_MAX`、`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` 和 `SAAS_TRAFFIC_*_QPS` 是小机器容量闸门。先压测观察，再逐步调大。
- API proxy 对单 IP 做基础限流和连接数限制；业务层 Sentinel 继续保护登录、公开配置、验证码和后端路由。
- Redis 默认 `maxmemory=256mb` 且使用 `allkeys-lru`，避免缓存或会话峰值把宿主机内存拖垮。
- Docker 日志默认轮转，避免高流量错误日志撑满磁盘。

部署后可以跑一个轻量压力冒烟：

```bash
LOAD_SMOKE_BASE_URL=https://bm.aiadc.org.cn \
LOAD_SMOKE_DURATION_MS=30000 \
LOAD_SMOKE_CONCURRENCY=24 \
LOAD_SMOKE_RPS=48 \
node bin/load-smoke.mjs
```

如果需要验证登录后的首屏接口，准备一个不需要二次验证和强制改密的测试账号后运行：

```bash
AUTH_LOAD_BASE_URL=https://bm.aiadc.org.cn \
AUTH_LOAD_USERNAME=admin \
AUTH_LOAD_PASSWORD='replace-with-test-password' \
AUTH_LOAD_DURATION_MS=30000 \
AUTH_LOAD_CONCURRENCY=16 \
AUTH_LOAD_RPS=32 \
node bin/auth-load-smoke.mjs
```

公网域名检查：

```bash
LOAD_SMOKE_BASE_URL=https://bm.aiadc.org.cn \
LOAD_SMOKE_DURATION_MS=30000 \
LOAD_SMOKE_CONCURRENCY=24 \
LOAD_SMOKE_RPS=48 \
node bin/load-smoke.mjs
```

## Vercel 前端配置

当前 `lumira-ui/vercel.json` 已将前端请求转发到后端域名：

```json
{
  "source": "/api/:path*",
  "destination": "https://bm.aiadc.org.cn/api/:path*"
}
```

前端默认使用同源 `/api`。如果不使用 Vercel rewrites，也可以在 Vercel 环境变量中配置：

```text
UMI_APP_API_BASE_URL=https://bm.aiadc.org.cn
```

## 服务器域名和 HTTPS

推荐让主机 Nginx、1Panel、负载均衡器、CDN 或 WAF 负责 HTTPS，并反向代理到容器 edge proxy。

```text
https://bm.aiadc.org.cn -> http://127.0.0.1:80
```

默认对外只暴露 80/443；`API_PROXY_BIND` 和 `FRONTEND_BIND` 只保留本机调试用途。

如果你已经有正式域名和证书，把 `deploy/.env` 里的 `API_DOMAIN`、`FRONTEND_ORIGIN` 和 `CORS_ALLOWED_ORIGIN_PATTERNS` 一并改成正式值。

### 使用 1Panel 部署

1Panel 只负责管理同一份生产 Compose，不需要维护另一套配置：

1. 将仓库放到服务器，例如 `/opt/lumira`。
2. 复制 `deploy/.env.example` 为 `deploy/.env`，按本文前面的环境变量清单替换密码和密钥。
3. 在“容器 → 编排”中新建编排，工作目录选择仓库根目录，Compose 文件选择 `deploy/docker-compose.prod.yml`，环境变量文件选择 `deploy/.env`。
4. 启动后检查 `api-proxy`、`lumira-server`、`lumira-async` 和 `lumira-job-executor` 状态。
5. 在“网站”中将后端域名反向代理到容器 edge proxy，并只对公网开放 80/443。

命令行等价操作：

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.prod.yml pull
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.prod.yml up -d
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.prod.yml ps
```

当前生产拓扑不提供内置 Nacos profile，应保持 `NACOS_CONFIG_ENABLED=false` 和 `NACOS_DISCOVERY_ENABLED=false`。MySQL、Redis 和 XXL-JOB 只在 Docker 内部网络使用，不要直接暴露到公网。

## 单独自检

演示前可以单独运行：

```bash
node bin/check-deployment.mjs
```

## 备份与恢复

生产变更、插件升级和数据迁移前，先创建平台备份：

```bash
bash deploy/backup-platform.sh
```

脚本会导出 MySQL、Redis RDB、上传文件目录、插件目录和 `deploy/.env` 快照，默认保存到 `backups/<时间戳>/`。如需指定备份根目录：

```bash
BACKUP_ROOT=/opt/lumira/backups bash deploy/backup-platform.sh
```

演练备份命令链但不写入数据、不访问容器：

```bash
DRY_RUN=1 BACKUP_ROOT=/tmp/lumira-backup-dry-run bash deploy/backup-platform.sh
```

恢复到测试环境或灾备环境：

```bash
bash deploy/restore-platform.sh backups/20260520-120000
```

恢复前请确认目标环境的 `deploy/.env` 已就位，并已启动 MySQL/Redis 容器。恢复脚本会覆盖目标数据库并重启 Redis。

演练恢复命令链但不写入数据库、不重启 Redis：

```bash
DRY_RUN=1 bash deploy/restore-platform.sh backups/20260520-120000
```

如果要检查公网后端域名：

```bash
DEPLOY_CHECK_BASE_URL=https://bm.aiadc.org.cn \
DEPLOY_CHECK_BACKEND_URL=http://127.0.0.1:8080 \
node bin/check-deployment.mjs
```

## 常用命令

查看容器状态：

```bash
node bin/deploy-container.mjs --ps
```

查看日志：

```bash
node bin/deploy-container.mjs --logs
```

停止完整后端部署：

```bash
node bin/deploy-container.mjs --stop
```

停止并删除数据卷：

```bash
DEPLOY_RESET_CONFIRM=DELETE_LEGENDARY_DATA node bin/deploy-container.mjs --reset
```

`--reset` 会删除数据库、上传文件、插件文件和任务日志数据，只能在确认不需要保留数据时使用。不要把 `DEPLOY_RESET_CONFIRM` 写入 `deploy/.env`、CI 默认变量或公开脚本里，只在确实需要清库的那一次命令前临时传入。

## 安全配置

- `deploy/.env` 不要提交到 Git。
- 对外只暴露 `https://bm.aiadc.org.cn`，容器内部服务端口只在内网访问。
- `DB_PASSWORD`、`JWT_SECRET`、`FIELD_SECRET`、`PLUGIN_SIGNATURE_SECRET`、`SAAS_INTERNAL_SYSTEM_TOKEN`、`SAAS_INTERNAL_AUTH_TOKEN`、`SAAS_INTERNAL_AUTH_SYSTEM_TOKEN`、`SAAS_INTERNAL_FILE_TOKEN`、`SAAS_INTERNAL_MESSAGE_TOKEN`、`SAAS_INTERNAL_PAYMENT_TOKEN`、`SAAS_INTERNAL_PLUGIN_TOKEN`、`SAAS_INTERNAL_TEAM_TOKEN`、`SAAS_INTERNAL_JOB_TOKEN` 必须使用强随机值。
- 聚合部署中，异步 owner 任务默认访问 `lumira-async`；需要控制面数据的用户导出任务通过 `SAAS_JOB_SYSTEM_SERVICE_BASE_URL=http://api-proxy:80` 到达当前蓝绿槽位。独立部署 owner 服务时再覆盖对应的 `SAAS_JOB_*_SERVICE_BASE_URL`。
- `CORS_ALLOWED_ORIGIN_PATTERNS` 在生产环境只保留实际 Vercel 域名和自定义前端域名；本地调试地址仅放入 dev/test 环境。
- `LUMIRA_INITIAL_ADMIN_PASSWORD` can set a production-only first-login password; leave it unset to use the factory default `admin / 123456`, which still requires an immediate password change.
- HTTPS/CDN/WAF 放在容器前面，API proxy 只承担容器内反向代理。
- `XXL_JOB_EXECUTOR_ENABLED=false` 可用于 runtime smoke、准生产 owner 演练或临时禁用外部调度注册；正式需要 XXL-JOB 调度时保持默认 `true` 并配置 `XXL_JOB_ADMIN_ADDRESSES`、`XXL_JOB_ACCESS_TOKEN`。
- `XXL_JOB_EXECUTOR_LOG_HOST_PATH` 默认使用 `/opt/lumira/data/xxl-job/logs`，部署前会授权给容器内 `app` 用户写入。

## 入口约定

- 前端访问入口：Vercel 域名
- 后端公网入口：`https://bm.aiadc.org.cn`
- 前端请求后端：`/api`
- WebSocket：`/ws`
- 本机 API proxy：`http://127.0.0.1:8000`
- 本机前端预览：`http://127.0.0.1:8001`
- 本机 lumira-server 健康检查：`http://127.0.0.1:8080/actuator/health`
