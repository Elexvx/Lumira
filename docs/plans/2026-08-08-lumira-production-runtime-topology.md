# Lumira 正式生产运行时拓扑

状态：Active

## 正式后端运行时

Lumira 的正式生产后端只有三种运行时角色：

1. `lumira-server`：唯一同步 API 与控制面。蓝绿更新期间会短暂同时存在 `lumira-server-blue` 与 `lumira-server-green`，但 API proxy 只指向 active slot。
2. `lumira-async`：Outbox relay、Redis Streams 消费者和 owner 后台处理适配器。
3. `lumira-job-executor`：XXL-JOB 调度与幂等补偿触发器，不拥有业务表。

Auth、File、Message、Payment、Plugin、Localization、Team 和 AI 是 Maven/包/数据 owner 边界，不是已部署的独立生产进程。生产 Compose 不得增加这些名称的容器，也不得把 API proxy 路由到它们。

## API proxy

Nginx 保留按路由类别命名的变量，例如 `$auth_upstream` 与 `$payment_upstream`，用于保持精确的路由和限流规则。`active-upstreams.conf` 由更新器生成，并把全部变量固定到 active `lumira-server` slot；不提供 `*_UPSTREAM` 环境变量覆盖。

这保证蓝绿切换是一次同步控制面切换，而不是隐藏的 owner-service 拆分。

## 真正的跨运行时调用

- Async 对 Auth 与 Team 的远程读取通过 `api-proxy` 到 active control plane，保留 `AUTH_SERVICE_BASE_URL`、`TEAM_SERVICE_BASE_URL`。
- Job Executor 对 Message、File、Payment、Plugin 的内部任务调用统一到 `SAAS_JOB_ASYNC_RUNTIME_BASE_URL`。
- Job Executor 对用户导出、报名导出和评审过期任务统一到 `SAAS_JOB_CONTROL_PLANE_BASE_URL`，即 `api-proxy` 到 active slot。
- scoped internal token 继续按内部 API 权限保留；它们不表示相应 Maven owner 已独立部署。

`SAAS_JOB_*_SERVICE_BASE_URL` 旧名称仅由应用配置作为本地/历史兼容回退接受，不在正式 `.env.example` 或 Compose 注入。未来若要物理拆分，先新增具备独立部署、健康检查、镜像、数据 owner 和版本化契约证据的运行时，再显式设计新的 production topology。
