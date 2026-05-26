# Vercel 前端 + 完整后端部署说明

这套部署用于高安全、高稳定的准生产演示环境。前端托管在 Vercel，服务器负责完整后端平台和统一 API 入口。

## 部署形态

```text
用户浏览器
  -> Vercel 前端
    -> /api/** rewrite 到 https://api.elexvx.com/api/**
    -> /ws/**  rewrite 到 https://api.elexvx.com/ws/**

api.elexvx.com / HTTPS / CDN / WAF
  -> 服务器 API proxy Nginx
    -> /api/** 反向代理到 gateway-service
    -> /ws/** 反向代理到 gateway-service
    -> /api/health 反向代理到 system-service
  -> gateway-service
    -> auth-service
    -> system-service
    -> file-service
    -> message-service
    -> plugin-service
    -> localization-service
    -> job-executor
  -> MySQL / Redis / XXL-Job
  -> Nacos（仅在启用 Nacos 配置/发现时启动）
```

## 默认启动组件

- MySQL：`mysql:8.4`
- Redis：`redis:7.4`
- Nacos：`nacos/nacos-server:v3.2.1`，默认不启动；只有 `NACOS_CONFIG_ENABLED=true`、`NACOS_DISCOVERY_ENABLED=true` 或显式传入 `--nacos` 时启动
- XXL-Job Admin：`xuxueli/xxl-job-admin:3.4.0`
- api-proxy：Nginx 后端统一入口
- gateway-service：统一后端网关
- system-service：系统核心服务
- auth-service：认证服务
- file-service：文件服务
- message-service：消息服务
- plugin-service：插件服务
- localization-service：本地化服务
- job-executor：任务执行器

`frontend` 容器只作为本地备用预览，默认不随生产部署启动。正式前端由 Vercel 托管。

## 一键部署后端完整平台

从仓库根目录运行：

```bash
node scripts/deploy-container.mjs --rebuild
```

默认部署按 4C4G 小型服务器收敛资源占用：Java 服务限制堆比例和元空间，Tomcat 线程池、Hikari 连接池、Redis 内存、Docker 日志和 API 入口限流都有默认上限。高流量时优先返回 429 或排队，而不是让 JVM、数据库连接和磁盘日志把服务器打满。

首次运行会自动生成 `deploy/.env`，并为数据库、JWT、插件签名、任务内部调用等配置生成随机密钥。

部署完成后脚本会自动检查：

- API proxy：`http://127.0.0.1:8000/health`
- API 健康检查：`http://127.0.0.1:8000/api/health`
- 版本检查：`http://127.0.0.1:8000/api/version`
- Gateway 健康检查：`http://127.0.0.1:8081/actuator/health`
- 公开登录配置接口：`http://127.0.0.1:8000/api/v1/public/login-capabilities`

## 可观测性闭环

默认部署不启动观测栈。需要 Prometheus 指标、OpenTelemetry trace、Loki 日志、Tempo trace 存储和 Grafana 看板时运行：

```bash
node scripts/deploy-container.mjs --rebuild --observability
```

观测端口默认只绑定本机：

- Grafana：`http://127.0.0.1:3001`
- Prometheus：`http://127.0.0.1:9090`
- Loki：`http://127.0.0.1:3100`
- Tempo：`http://127.0.0.1:3200`
- Alloy：`http://127.0.0.1:12345`

Grafana 会自动 provision Prometheus、Loki、Tempo 数据源和 `Legendary Observability Overview` 看板。服务运行时会暴露 `/actuator/prometheus`，并在启用观测栈时通过 OpenTelemetry Java Agent 把 trace 发送到 Alloy。

4C4G 服务器上不建议常驻完整观测栈；需要排查性能问题时短时开启，排查结束后停止观测栈释放内存。

## 4C4G 稳定运行建议

- 默认使用外部或 1Panel MySQL；本仓库内置 MySQL 仅用于 `local-mysql` profile。
- 默认不启动 Nacos，本地配置和服务发现都是 optional；确实需要 Nacos 时运行 `node scripts/deploy-container.mjs --rebuild --nacos`。
- 默认不启动 `frontend` 容器，正式前端走 Vercel；服务器只承担后端和 API proxy。
- `deploy/.env` 里的 `*_MEM_LIMIT`、`SERVER_TOMCAT_THREADS_MAX`、`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` 和 `SAAS_TRAFFIC_*_QPS` 是小机器容量闸门。先压测观察，再逐步调大。
- API proxy 对单 IP 做基础限流和连接数限制；业务层 Sentinel 继续保护登录、公开配置、验证码和后端路由。
- Redis 默认 `maxmemory=256mb` 且使用 `allkeys-lru`，避免缓存或会话峰值把宿主机内存拖垮。
- Docker 日志默认轮转，避免高流量错误日志撑满磁盘。

部署后可以跑一个轻量压力冒烟：

```bash
LOAD_SMOKE_BASE_URL=http://127.0.0.1:8000 \
LOAD_SMOKE_DURATION_MS=30000 \
LOAD_SMOKE_CONCURRENCY=24 \
node scripts/load-smoke.mjs
```

公网域名检查：

```bash
LOAD_SMOKE_BASE_URL=https://api.elexvx.com \
LOAD_SMOKE_DURATION_MS=30000 \
LOAD_SMOKE_CONCURRENCY=24 \
node scripts/load-smoke.mjs
```

## Vercel 前端配置

当前 `frontend/vercel.json` 已将前端请求转发到后端域名：

```json
{
  "source": "/api/:path*",
  "destination": "https://api.elexvx.com/api/:path*"
}
```

前端默认使用同源 `/api`。如果不使用 Vercel rewrites，也可以在 Vercel 环境变量中配置：

```text
UMI_APP_API_BASE_URL=https://api.elexvx.com
```

## 服务器域名和 HTTPS

推荐让主机 Nginx、1Panel、负载均衡器、CDN 或 WAF 负责 HTTPS，并反向代理到容器 API proxy：

```text
https://api.elexvx.com -> http://127.0.0.1:8000
```

默认 `API_PROXY_BIND=127.0.0.1:8000`，容器 API 入口只监听本机。只有确认要直接暴露容器端口时，才改成：

```text
API_PROXY_BIND=0.0.0.0:8000
```

## 单独自检

演示前可以单独运行：

```bash
node scripts/check-deployment.mjs
```

## 备份与恢复

生产变更、插件升级和数据迁移前，先创建平台备份：

```bash
bash deploy/backup-platform.sh
```

脚本会导出 MySQL、Redis RDB、上传文件目录、插件目录和 `deploy/.env` 快照，默认保存到 `backups/<时间戳>/`。如需指定备份根目录：

```bash
BACKUP_ROOT=/opt/legendary-invention/backups bash deploy/backup-platform.sh
```

演练备份命令链但不写入数据、不访问容器：

```bash
DRY_RUN=1 BACKUP_ROOT=/tmp/legendary-backup-dry-run bash deploy/backup-platform.sh
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
DEPLOY_CHECK_BASE_URL=https://api.elexvx.com \
DEPLOY_CHECK_GATEWAY_URL=http://127.0.0.1:8081 \
node scripts/check-deployment.mjs
```

## 常用命令

查看容器状态：

```bash
node scripts/deploy-container.mjs --ps
```

查看日志：

```bash
node scripts/deploy-container.mjs --logs
```

停止完整后端部署：

```bash
node scripts/deploy-container.mjs --stop
```

停止并删除数据卷：

```bash
node scripts/deploy-container.mjs --reset
```

`--reset` 会删除数据库、上传文件、插件文件和任务日志数据，只能在确认不需要保留数据时使用。

## 安全配置

- `deploy/.env` 不要提交到 Git。
- 对外只暴露 `https://api.elexvx.com`，容器内部服务端口只在内网访问。
- `DB_PASSWORD`、`JWT_SECRET`、`FIELD_SECRET`、`PLUGIN_SIGNATURE_SECRET`、`SAAS_JOB_INTERNAL_TOKEN` 必须使用强随机值。
- `CORS_ALLOWED_ORIGIN_PATTERNS` 在生产环境只保留实际 Vercel 域名和自定义前端域名；本地调试地址仅放入 dev/test 环境。
- `DEFAULT_ADMIN_INIT_ENABLED` 在正式演示环境建议保持 `false`。
- HTTPS/CDN/WAF 放在容器前面，API proxy 只承担容器内反向代理。
- `XXL_JOB_EXECUTOR_LOG_HOST_PATH` 默认使用 `/opt/legendary-invention/data/xxl-job/logs`，部署前会授权给容器内 `app` 用户写入。

## 入口约定

- 前端访问入口：Vercel 域名
- 后端公网入口：`https://api.elexvx.com`
- 前端请求后端：`/api`
- WebSocket：`/ws`
- 本机 API proxy：`http://127.0.0.1:8000`
- 本机 Gateway 健康检查：`http://127.0.0.1:8081/actuator/health`
