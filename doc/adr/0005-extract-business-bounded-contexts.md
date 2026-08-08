# ADR-0005：从 system-service 拆出业务限界上下文

## 状态

Accepted — 已实施，并于 2026-08-09 完成全仓串行验证。

## 背景

`lumira-admin` 仍是同步聚合入口，生产仍只部署 `lumira-server`、`lumira-async` 和 `lumira-job-executor` 三个运行时。此前 Activity、Competition、Registration、Project、Expert、Certificate、Workflow、Export 和 AI 等业务代码长期堆在 `lumira-system`，使平台治理、业务 owner 与持久化边界混杂。

这份 ADR 的目标不是把每个 Maven 模块误表述成已独立部署的微服务，而是在共享数据底座上先建立可验证的业务 owner、契约与持久化边界。

## 决策

- 继续采用 DDD 模块化单体和单同步运行时，不立即拆物理微服务。
- 业务 owner 分别落在 `lumira-activity`、`lumira-competition`、`lumira-project`、`lumira-expert`、`lumira-workflow`、`lumira-export` 和 `lumira-ai`；Competition 同时拥有 Registration、Review 与 Certificate。
- `lumira-system` 保留 IAM、平台配置、审计、共享事件桥与受控的内部适配器，不再承载上述业务上下文的实现或运行期表写入。
- 每个模块拥有自己的 Controller/Application/Domain/Repository/Infrastructure、表 owner、权限和事件。
- 跨模块协作使用 API 契约、Internal API、Outbox 或明确的只读投影，禁止依赖其他模块的 Mapper/Entity/Service 实现。
- 兼容 Controller 只能委托 owner 的应用端口，不复制新旧业务逻辑；迁移完成以旧源码清零、POM/装配边界、跨 owner SQL 守卫和回归测试共同证明。

## 影响

### 正面

- system-service 回归平台治理职责。
- 业务域可以独立测试、演进和评估扩容。
- 直接 SQL 债务可以随模块迁移收口到 Repository。

### 负面

- Maven 装配、owner manifest、迁移目录、SQL bootstrap 和架构测试必须同步修改。
- 模块边界是逻辑和契约边界，不等于数据库已物理拆库；跨 owner 读取需要经最小契约、投影或 owner API 完成。

### 中性

- 生产进程数量不增加，数据库暂时仍共享。
- 模块拆分不要求修改前端统一 `/api` 入口。

## 备选方案

### 保持所有业务在 system-service

短期简单，但会扩大平台模块职责和回归半径，拒绝。

### 立即拆为微服务

在 tenant 和契约尚未稳定前引入分布式复杂度，拒绝。

## 参考

- [完整架构设计](../../docs/plans/2026-07-20-lumira-platform-domain-architecture-design.md)
- [ADR-0001：采用 DDD 模块化单体](0001-adopt-ddd-modular-monolith.md)
