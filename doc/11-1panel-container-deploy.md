# 1Panel 后端容器化部署说明

本项目后端可以通过 1Panel 的“容器编排”能力部署。前端如果已经部署到 Vercel，1Panel 里只需要部署后端、网关和基础设施。

- `deploy/docker-compose.prod.yml`
- `deploy/.env.example`

## 最简单部署方式

如果是在普通服务器命令行部署，推荐直接运行：

```bash
node bin/deploy-container.mjs --pull
```

脚本会自动生成 `deploy/.env`，填入随机密钥，拉取 CI 已构建好的镜像，然后执行 Docker Compose 部署。默认不会启动前端 Nginx 容器。

`main` 分支 CI 会在编译和测试通过后发布镜像到 GitHub Container Registry。默认镜像配置在 `deploy/.env`：

```bash
LUMIRA_SERVER_IMAGE=ghcr.io/elexvx/lumira/lumira-server:main
LUMIRA_FRONTEND_IMAGE=ghcr.io/elexvx/lumira/lumira-ui:main
```

如果需要固定到某次发布，把 `main` 替换为 `sha-<12位提交>`。如果服务器必须本机重新构建镜像，再运行：

```bash
node bin/deploy-container.mjs --rebuild
```

常用命令：

```bash
node bin/deploy-container.mjs --ps
node bin/deploy-container.mjs --logs
node bin/deploy-container.mjs --stop
```

如果要彻底删除数据库、上传文件和容器卷：

```bash
DEPLOY_RESET_CONFIRM=DELETE_LEGENDARY_DATA node bin/deploy-container.mjs --reset
```

`--reset` 是高危操作。交互终端会要求输入 `DELETE_LEGENDARY_DATA`；非交互环境必须临时传入 `DEPLOY_RESET_CONFIRM=DELETE_LEGENDARY_DATA`，不要把这个变量写入长期配置。

## 1Panel 部署方式

1. 将代码上传到服务器，例如 `/opt/lumira`。
2. 复制环境变量模板：

```bash
cp deploy/.env.example deploy/.env
```

3. 修改 `deploy/.env`，至少替换以下变量：

- `DB_PASSWORD`
- `JWT_SECRET`
- `PLUGIN_SIGNATURE_SECRET`
- `SAAS_JOB_INTERNAL_TOKEN`

`DB_PASSWORD` 会同时作为容器内 MySQL root 用户密码和后端数据库连接密码，避免维护两套数据库密码。

4. 在 1Panel 中进入“容器” -> “编排” -> “创建编排”。
5. 编排目录选择项目根目录或 `deploy` 目录，Compose 文件选择 `deploy/docker-compose.prod.yml`。
6. 环境变量文件选择 `deploy/.env`。
7. 启动编排。

也可以在服务器命令行验证：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml pull
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml up -d
```

如果需要启用 Nacos profile，再替换 `NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY`、`NACOS_AUTH_IDENTITY_VALUE`。
如果需要启用定时任务 profile，再替换 `XXL_JOB_ADMIN_ACCESS_TOKEN`、`XXL_JOB_ACCESS_TOKEN`、`XXL_JOB_LOGIN_PASSWORD`。

## 访问入口

默认暴露：

- 网关：`http://服务器IP:8081`

正式域名建议在 1Panel 的“网站”中创建反向代理：

- `https://api.你的域名` -> `http://127.0.0.1:8081`

Vercel 前端的 API 地址配置成这个后端域名即可。跨域放行可以通过环境变量覆盖，例如只允许你的 Vercel 域名：

```bash
CORS_ALLOWED_ORIGIN_PATTERNS=https://你的前端.vercel.app,https://你的前端自定义域名
```

如果以后想让 1Panel 同时托管前端，可以启用可选 profile：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml --profile lumira-ui up -d --build lumira-ui
```

如果以后需要启用 Nacos 或 XXL-Job：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml --profile nacos up -d
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml --profile jobs up -d --build
```

## 持久化数据

Compose 已使用 Docker volume 保存：

- MySQL 数据：`mysql_data`
- Redis 数据：`redis_data`
- Nacos 数据和日志：`nacos_data`、`nacos_logs`，仅启用 `nacos` profile 时使用
- 上传文件：`upload_data`
- 插件文件：`plugin_data`、`plugin_staging`
- XXL-Job 执行器日志：`xxl_job_executor_logs`，仅启用 `jobs` profile 时使用

不要把本地的 `services/lumira-system/storage` 直接打包进生产镜像；该目录已经被 `.dockerignore` 排除。

## 生产注意事项

- `.env` 不要提交到 Git。
- 不要使用 `deploy/.env.example` 里的示例值作为生产密钥。
- `JWT_SECRET` 和 `PLUGIN_SIGNATURE_SECRET` 建议使用 32 字符以上随机字符串。
- 对公网只建议开放 80/443；MySQL、Redis、Nacos、XXL-Job 默认只在 Docker 内部网络中使用，不对宿主机暴露端口。
- 首次启动会构建多个 Java 镜像，耗时较长，建议服务器至少 4C8G。

## 常用命令

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml ps
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml logs -f api-proxy
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml restart api-proxy
docker compose --env-file deploy/.env -f deploy/docker-compose.prod.yml down
```
