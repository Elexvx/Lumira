# ADR-0005：从 system-service 拆出业务限界上下文

## 状态

Proposed

## 背景

当前运行时以 `lumira-admin` 聚合模块为同步入口，这一部署方式适合当前团队和规模。但 Activity、Competition、Registration、Project、Expert、Certificate、Workflow 等业务代码和表仍暂归 `lumira-system` 的 PLATFORM 上下文，部分应用服务直接持有低层数据库依赖并被登记为历史债务。

继续把新增赛事能力堆入 system-service 会扩大变更半径，并削弱 owner、权限和未来物理拆分边界。

## 决策

- 继续采用 DDD 模块化单体和单同步运行时，不立即拆物理微服务。
- 第一批新建 `lumira-activity`、`lumira-competition` 和 `lumira-export` Maven 模块。
- 第二批评估迁移 Project、Expert、Certificate、Workflow。
- 每个模块拥有自己的 Controller/Application/Domain/Repository/Infrastructure、表 owner、权限和事件。
- 跨模块协作使用 API 契约、Internal API、Outbox 或明确的只读投影，禁止依赖其他模块的 Mapper/Entity/Service 实现。
- 迁移期间旧 Controller 只做兼容 facade，不复制新旧业务逻辑。

## 影响

### 正面

- system-service 回归平台治理职责。
- 业务域可以独立测试、演进和评估扩容。
- 直接 SQL 债务可以随模块迁移收口到 Repository。

### 负面

- Maven 装配、owner manifest、迁移目录和架构测试需要同步修改。
- 迁移期新旧包结构并存，需要清晰的截止条件。

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
