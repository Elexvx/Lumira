# 单体微服务架构收敛说明

## 1. 当前目标

本仓库当前主线是“单体微服务”：运行时收敛为一个后端进程 `services/legendary-server`，工程内继续保留 `services/*-service` 的模块边界、契约边界和数据 owner。这样可以先降低部署、联调和运维成本，同时为后续按业务边界拆成物理微服务保留路径。

这不是回到历史 `backend` 单体，也不是多套架构并存。`legendary-server` 是唯一推荐后端启动入口；各服务模块是可拆分的业务边界。

## 2. 运行入口

- `services/legendary-server`：正式 Spring Boot 启动入口，默认端口 `8080`。
- `LEGENDARY_MONOLITH=true`：默认单体微服务模式，关闭会与聚合运行冲突的子模块独立安全配置。
- `deploy/docker-compose.prod.yml`：默认只启动 `legendary-server`、`api-proxy`、Redis、XXL-JOB Admin 和可选观测组件。
- `api-proxy`：对外统一暴露 `/api/**`、`/ws/**`，上游指向 `legendary-server:8080`。

## 3. 模块边界

- `services/system-service`：系统核心、IAM、配置、审计、AI、仪表盘等平台能力。
- `services/auth-service`：认证协议、登录保护、刷新 token、二次验证、Passkey、微信登录。
- `services/file-service`：文件对象、存储空间、上传校验、文件事件。
- `services/message-service`：站内信、WebSocket、消息归档、投递日志。
- `services/plugin-service`：插件定义、版本、启停、运行时和插件网关。
- `services/localization-service`：语言、命名空间、翻译词条和发布版本。
- `services/job-executor`：XXL-JOB 执行器和内部任务触发。
- `libs/*`：公共契约、公共 Web、安全、领域基础和插件 SPI。

## 4. 配置规范

- 当前默认不依赖 Nacos 服务发现；Nacos 仅作为未来拆分和配置中心预留。
- 敏感配置只能通过环境变量或密钥系统注入，不应明文硬编码进仓库。
- 聚合运行时，跨模块 base URL 默认指向 `http://localhost:${server.port}`。
- 子模块独立端口和 Nacos 配置可以保留，但只能作为未来拆分准备，不能成为默认运行说明。

## 5. 启动顺序

1. 启动 MySQL、Redis、XXL-JOB Admin。
2. 启动 `services/legendary-server`。
3. 启动 `api-proxy`。
4. 启动前端或使用 Vercel 前端。
5. 可选启动 Prometheus、Grafana、Loki、Tempo、Alloy。

## 6. 后续拆分顺序

建议按低耦合、owner 清晰、外部副作用少的模块开始：

1. `file-service`
2. `message-service`
3. `localization-service`
4. `plugin-service`
5. `auth-service`
6. `job-executor`
7. `system-service`

拆分一个模块时，必须同时完成：独立启动类、独立配置、健康检查、部署编排、API 路由、契约调用、数据 owner、迁移脚本和回滚方案。

## 7. 不允许的形态

- 新增历史 `backend` 单体入口。
- 同一业务同时维护“聚合模块实现”和“独立服务实现”两份逻辑。
- 为了拆服务直接跨库读写别的 owner 表。
- 只拆 Docker 容器，不拆契约、数据 owner 和权限边界。
