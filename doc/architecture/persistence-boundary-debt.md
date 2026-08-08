# 持久化边界历史债务

本文件曾用于临时登记历史直接 SQL 写库债务；这些债务现已全部迁移到 Repository、Mapper、DAO 或 Persistence Adapter。本文件保留为架构守卫的审计依据：任何新的直接 SQL 债务都必须被视为回归，而不是重新登记例外。

Team 模块不允许进入本债务清单；`TeamAppService` 和 `TeamInviteService` 继续由 Team repository 层承接持久化。
## 当前债务清单

“债务”列中的 `direct SQL` 和 `direct persistence dependency` 是架构测试识别的固定标记，不翻译。

| 模块/类 | 债务 | 本阶段处理 |
| --- | --- | --- |
目前无登记债务。

## 治理原则

- 新增业务写入不得加入本清单。
- 新增 AppService 禁止直接编写 `insert`、`update`、`delete` SQL，也不得直接调用 `jdbcTemplate.update`、`jdbcTemplate.batchUpdate` 或 `MyBatisQueryOperations.update`；写入必须通过 Repository、Mapper、DAO、Persistence Adapter、Internal API、Domain Event 或 Outbox 等拥有方边界。
- 持久化边界迁移必须补充模块级测试和架构测试。
- AI Tool Runtime 和 Job Handler 的业务表写入应优先改为调用业务拥有方。
- 不得通过新增 allowlist 来回避架构守卫。
