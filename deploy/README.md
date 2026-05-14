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
  -> MySQL / Redis / Nacos / XXL-Job
```

## 默认启动组件

- MySQL：`mysql:8.4`
- Redis：`redis:7.4`
- Nacos：`nacos/nacos-server:v3.2.1`
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

首次运行会自动生成 `deploy/.env`，并为数据库、JWT、插件签名、任务内部调用等配置生成随机密钥。

部署完成后脚本会自动检查：

- API proxy：`http://127.0.0.1:8000/health`
- API 健康检查：`http://127.0.0.1:8000/api/health`
- Gateway 健康检查：`http://127.0.0.1:8081/actuator/health`
- 公开登录配置接口：`http://127.0.0.1:8000/api/v1/public/login-capabilities`

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
- `DB_PASSWORD`、`JWT_SECRET`、`PLUGIN_SIGNATURE_SECRET`、`SAAS_JOB_INTERNAL_TOKEN` 必须使用强随机值。
- `CORS_ALLOWED_ORIGIN_PATTERNS` 只保留实际 Vercel 域名、自定义前端域名和必要的本地调试地址。
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
