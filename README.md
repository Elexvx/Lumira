# Lumira 启动与部署指南

Lumira 是一套面向企业管理场景的 SaaS 平台。仓库只保留两种正式启动环境：本地调试使用宿主机原生进程，生产使用 Docker Compose 容器。

## 启动环境

| 环境 | 入口 | 应用运行方式 | 配置文件 |
| --- | --- | --- | --- |
| 本地调试 | `npm run start:local` | JDK 21 + Maven Wrapper + Node.js/pnpm 原生进程，不调用 Docker | `lumira-backend/.env` |
| 生产 | `npm run start:production` | `deploy/docker-compose.prod.yml` 容器编排 | `deploy/.env` |

也可以运行 `npm start`，然后在交互菜单中选择这两个环境之一。CI 或其他非交互环境必须显式传入 `local` 或 `production`。

### 本地调试（原生启动）

本机需要预先安装并启动：

- JDK 21 或更高版本
- Node.js 22 或更高版本，并启用 Corepack/pnpm
- MySQL 8.4（默认 `127.0.0.1:3306`）
- Redis 兼容服务（Windows 可使用 Memurai，默认 `127.0.0.1:6379`）

首次准备：

```powershell
Copy-Item lumira-backend/.env.example lumira-backend/.env
corepack pnpm --dir lumira-ui install
```

按本机实际凭据修改 `lumira-backend/.env`，在 MySQL 中创建 `lumira` 数据库，并导入 `lumira-backend/sql/saas.sql`。随后先运行只读预检，再启动：

```powershell
npm run start:local -- --check
npm run start:local
```

需要在后台持续运行时：

```powershell
npm run start:local:daemon
Get-Content runtime-logs/local-native.log -Wait
npm run stop:local
```

后台入口记录精确的启动器 PID，停止时只终止该 PID 的进程树，不会停止本机 MySQL、Memurai 或其他 Node/Java 进程。

默认启动 `lumira-server:8080` 和 Umi 开发服务器 `127.0.0.1:8000`。前端使用 Umi HMR；后端默认监听所有模块的 `src/main/java`、`src/main/resources` 和 `pom.xml`，保存后自动执行 Maven reactor 编译，成功后只重启 Java 进程，前端开发服务器会持续在线。编译失败时旧后端继续提供服务，修复并再次保存即可重试。

需要联调 Outbox、异步文件任务或后台任务时，启动完整原生拓扑：

```powershell
npm run start:local:full
```

完整模式另外启动 `lumira-async:8081` 和 `lumira-job-executor:8082`。常用选项：

```powershell
npm run start:local -- --backend-only
npm run start:local -- --frontend-only
npm run start:local -- --skip-build
npm run start:local -- --no-watch
npm run start:local -- --frontend-port 8899
```

本地入口会强制前端 API/WS 指向 `127.0.0.1:8080`，并忽略浏览器中遗留的线上 API 地址，避免本地页面误操作生产数据。按 `Ctrl+C` 会停止该入口启动的全部原生进程。

### 生产环境（容器启动）

生产启动只复用已配置的镜像和容器，不会隐式执行 `pull` 或 `rebuild`：

```bash
npm run start:production -- --check
npm run start:production
```

该入口要求已经准备好不含 `change-me` 占位符的 `deploy/.env`，强制三个 Java 运行时使用 `prod` profile，并继续复用现有蓝绿槽位、迁移和部署检查。停止容器拓扑：

```bash
npm run stop:production
```

首次安装仍应使用 `node bin/install-platform.mjs`；发布新版本应走平台更新器或显式的部署流程，不要把普通“启动”与“拉取新版本”混为同一操作。

> 首次部署是完整初始化：安装运行环境、生成内部密钥、构建或拉取镜像、初始化数据库并启动全部服务。后续发布使用蓝绿更新和 Docker 分层复用，但容器仍会整体替换，并不是把差异文件覆盖进旧容器。

## 1. 部署方式

当前推荐通过仓库自带的 `bin/install-platform.mjs` 安装器完成首次部署。不要直接在浏览器中打开 Docker Registry 地址；OCI Registry 通常不会提供可浏览的首页，直接访问出现 `404` 属于正常现象。

本文以以下环境为例：

- 系统：Ubuntu 22.04 或 24.04
- 域名：`lumira.example.com`
- 前端：使用项目内置前端容器
- 数据库：使用外部 MySQL
- HTTPS：使用已有域名证书

如果使用内置 MySQL，请参阅[单机内置 MySQL](#9-单机内置-mysql)。

## 2. 服务器要求

最低要求：

- 4 核 CPU
- 3.5 GiB 内存，建议至少 4 GiB
- 15 GiB 可用磁盘空间
- Node.js 22 以上
- Docker 24 以上及 Docker Compose v2
- `curl`、`tar`、`gzip`、`sh`

网络要求：

- 域名已解析到服务器公网 IP
- 对外开放 TCP 80、443
- 本机 80、443，以及按 `API_PROXY_BIND`/`FRONTEND_BIND` 启用的 8000、8001 端口未被其他程序占用
- 服务器可以访问代码仓库、Docker Registry 和 MySQL

安装器可以在 Linux 上自动安装 Docker。正式服务器也可以提前安装 Docker，并确保执行部署的账户能够运行 `docker info`。

## 3. 获取固定版本

```bash
git clone https://github.com/Elexvx/Lumira.git
cd Lumira

# 正式环境应切换到经过确认的发行标签或提交，不建议直接跟随浮动的 main。
git checkout <release-tag-or-commit>
```

私有仓库需要先为 Git 配置具有读取权限的凭据。部署前可执行一次基础环境预检：

```bash
node bin/install-platform.mjs --check-only
```

全新服务器此时可能提示 Docker、`deploy/.env` 或密钥尚未准备，这是正常的预安装状态。不要在首次准备完成前使用 `--strict`，正式安装器会生成配置并再次检查。

## 4. 准备生产配置

复制环境变量模板：

```bash
cp deploy/.env.example deploy/.env
```

编辑 `deploy/.env`，至少确认以下配置：

```dotenv
API_DOMAIN=lumira.example.com
FRONTEND_ORIGIN=https://lumira.example.com

DB_URL=jdbc:mysql://mysql.example.com:3306/lumira?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=true
DB_USERNAME=lumira
DB_PASSWORD=<外部数据库密码>

LUMIRA_BOOTSTRAP_ADMIN_PASSWORD_FILE=./secrets/bootstrap-admin-password
```

注意：

- 外部 MySQL 数据库和账号需要提前创建，并授予目标数据库所需权限。
- 如果数据库不支持 TLS，应根据实际环境调整 JDBC 参数；公网数据库不建议关闭 TLS。
- 模板中以 `change-me` 开头的内部服务密钥会由安装器自动生成。已经配置的非占位值会被保留。
- `deploy/.env` 包含敏感信息，不要提交到 Git。

## 5. 设置首次管理员密码

首次安装通过宿主机密钥文件设置内置管理员的一次性临时密码。密码必须为 12～128 个字符，并同时包含大写字母、小写字母、数字和特殊字符。

以下命令会隐藏输入内容，避免密码直接出现在终端历史中：

```bash
install -d -m 700 deploy/secrets
read -rsp "首次管理员临时密码: " LUMIRA_ADMIN_PASSWORD && echo
printf '%s' "$LUMIRA_ADMIN_PASSWORD" > deploy/secrets/bootstrap-admin-password
unset LUMIRA_ADMIN_PASSWORD
chmod 600 deploy/secrets/bootstrap-admin-password
```

不要将该文件提交到 Git。数据库完成首次初始化后，重复执行迁移不会替换已经初始化的管理员密码。

## 6. 配置 HTTPS 证书

将证书和私钥放到以下固定位置：

```text
deploy/data/tls/fullchain.pem
deploy/data/tls/privkey.pem
```

确保证书覆盖 `lumira.example.com`，私钥权限仅允许部署账户读取。正式安装开始前，安装器会检查这两个文件是否存在。

## 7. 执行首次安装

### 交互式安装

推荐第一次部署使用交互式模式：

```bash
sudo node bin/install-platform.mjs
```

按提示选择：

1. API 域名：`lumira.example.com`
2. 前端 Origin：`https://lumira.example.com`
3. 启动内置 MySQL：否
4. 启动内置前端容器：是
5. 启动完整可观测栈：按服务器容量选择，4 GiB 机器建议先关闭
6. 资源配置：按检测结果选择 `tiny` 或 `standard`

### 非交互式示例

完成所有配置后，也可以自动执行：

```bash
sudo node bin/install-platform.mjs \
  --yes \
  --api-domain=lumira.example.com \
  --lumira-ui-origin=https://lumira.example.com \
  --lumira-ui \
  --profile=standard
```

如果 Node.js 仅安装在普通用户的版本管理器中，`sudo` 环境可能找不到 `node`。此时应安装系统级 Node.js 22，或者进入具备部署权限且能访问 Node.js 的管理员环境后运行命令。

安装器会依次完成：

- 生成或保留 `deploy/.env` 中的内部密钥
- 检查系统容量、端口、网络和数据库连接
- 安装或检查 Docker
- 安装 `lumira-updater` systemd 更新服务
- 拉取基础镜像并构建当前提交对应的业务镜像
- 启动 Redis、任务管理、后端蓝槽、异步任务、任务执行器、API 代理、边缘代理和前端
- 执行部署检查、负载冒烟测试和公开健康检查

安装过程中任一步失败都应先处理错误，再重新执行同一安装命令；不要使用会清空数据卷的重置参数。

## 8. 验证和交付

安装器结束时会自动执行验证。也可以手动再次检查：

```bash
node bin/check-deployment.mjs
```

查看容器状态：

```bash
docker compose \
  --env-file deploy/.env \
  -f deploy/docker-compose.prod.yml \
  --profile blue \
  --profile edge \
  --profile local-lumira-ui \
  ps
```

公开检查：

```bash
curl -fsS https://lumira.example.com/api/health
curl -fsS https://lumira.example.com/api/v2/runtime/version
```

交付前应确认：

- 所有预期容器均为运行状态
- `/api/health` 返回健康结果
- 运行时版本接口中的 `commitId` 与本次部署的 Git 提交一致
- 管理员可以登录，并在首次登录时修改临时密码
- 页面刷新后业务数据仍然存在

首次登录并确认数据库初始化成功后，删除或撤销一次性密码文件：

```bash
rm deploy/secrets/bootstrap-admin-password
```

删除前必须确认管理员已经成功登录并修改密码。该文件删除后不能用于恢复管理员密码。

## 9. 单机内置 MySQL

测试环境或单机部署可以使用内置 MySQL：

```bash
sudo node bin/install-platform.mjs \
  --yes \
  --local-mysql \
  --lumira-ui \
  --profile=tiny \
  --api-domain=lumira.example.com \
  --lumira-ui-origin=https://lumira.example.com
```

使用 `--local-mysql` 时会启用 `local-mysql` profile，并且不会启用项目内置的 `edge` profile。API 和前端默认只绑定到宿主机 `127.0.0.1:8000`、`127.0.0.1:8001`，需要再通过 1Panel、Nginx、Caddy 等宿主机反向代理对外提供 HTTPS。

内置 MySQL 适合快速启动，但正式环境仍应配置独立备份、磁盘监控和恢复演练。

## 10. 后续发行和更新

首次部署完成后，不需要每次重新运行完整安装流程。推荐通过系统后台的平台更新功能消费固定到 digest 的发行清单并执行蓝绿发布。

只有在 `deploy/.env` 中的业务镜像已经明确固定到目标 tag 或 digest 时，才在维护窗口手动执行：

```bash
node bin/deploy-container.mjs --pull
```

`deploy-container.mjs --pull` 会拉取 `deploy/.env` 当前配置的镜像引用；它本身不读取发行清单，也不会把浮动的 `main` 自动转换为 digest。平台更新器才负责读取发行清单、启动非活动槽、完成健康检查后切换流量。Docker 会复用本机已有镜像层以减少下载量，但新版本容器仍是不可变的完整容器。

正式更新前应备份数据库，并在完成后同时核对：

- CI 构建和镜像发布成功
- 使用的是预期发行提交或镜像 digest
- 健康检查通过
- 线上运行时 `commitId` 已切换到目标提交
- 关键业务功能和数据持久化正常

## 11. Docker Registry

项目支持以下镜像仓库前缀：

```text
ghcr.io/elexvx/lumira/
swr.cn-east-3.myhuaweicloud.com/aiadc/
```

主要镜像包括：

```text
lumira-server
lumira-ui
lumira-async
lumira-job-executor
lumira-migrator
```

Registry 是 Docker/OCI API 服务，直接用浏览器访问仓库根地址返回 `404` 并不表示镜像不存在。请通过 Docker 客户端拉取，例如：

```bash
docker pull ghcr.io/elexvx/lumira/lumira-server:main
```

正式发行应使用 `sha-<12位提交号>` 标签或发行清单中的 digest，不建议长期部署浮动的 `main` 标签。私有镜像需要先使用具有包读取权限的账号执行 `docker login`。

## 12. 常见问题

### Registry 地址能打开但显示 404

这是 OCI Registry 的常见行为。使用 `docker login`、`docker pull`，或进入 GitHub Packages、华为云 SWR 控制台查看镜像。

### 安装器提示找不到 TLS 文件

检查以下文件名和路径是否完全一致：

```text
deploy/data/tls/fullchain.pem
deploy/data/tls/privkey.pem
```

### 使用 sudo 后找不到 node

这通常是因为 Node.js 安装在普通用户的 nvm 环境。为服务器安装系统级 Node.js 22，或确保部署管理员环境能够访问正确的 `node` 可执行文件。

### 外部 MySQL 无法连接

确认安全组、防火墙、MySQL 监听地址、账号授权范围和 `DB_URL` 均正确。从部署服务器测试目标主机的 3306 端口，而不是仅在数据库控制台确认状态。

### 容器已启动但页面打不开

依次检查：

1. `docker compose ... ps` 中的服务状态。
2. `node bin/check-deployment.mjs` 的失败项。
3. 域名 DNS 是否指向当前服务器。
4. 80、443 是否放通。
5. HTTPS 证书与域名是否匹配。
6. `/api/v2/runtime/version` 返回的 `commitId` 是否为目标版本。

## 相关文件

- 首次安装器：[`bin/install-platform.mjs`](bin/install-platform.mjs)
- 容器部署脚本：[`bin/deploy-container.mjs`](bin/deploy-container.mjs)
- 环境变量模板：[`deploy/.env.example`](deploy/.env.example)
- 生产 Compose：[`deploy/docker-compose.prod.yml`](deploy/docker-compose.prod.yml)
- 首次管理员密码说明：[`deploy/secrets/README.md`](deploy/secrets/README.md)
