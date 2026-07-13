# 持久化边界历史债务

本文件记录当前仍保留的直接 SQL 写库债务。它们不是新规范的例外模板，只是为了避免本阶段大规模扰动 System、IAM、AI 等历史模块。后续治理应按模块拆分，逐步迁移到 Repository、Mapper、DAO 或 Persistence Adapter。
Team 模块不允许进入本债务清单。本阶段已经要求 `TeamAppService` 和 `TeamInviteService` 清除直接写库 SQL，并由 Team repository 层承接持久化。
## 当前债务清单

“债务”列中的 `direct SQL` 和 `direct persistence dependency` 是架构测试识别的固定标记，不翻译。

| 模块/类 | 债务 | 本阶段处理 |
| --- | --- | --- |
| `SystemManagementAppService` | direct SQL | 记录债务，后续迁移到 System repository/persistence adapter |
| `SystemUserManagementAppService` | direct SQL | 记录债务，后续迁移到 User repository/persistence adapter |
| `SystemRoleManagementAppService` | direct SQL | 记录债务，后续迁移到 Role repository/persistence adapter |
| `SystemDepartmentAppService` | direct SQL | 记录债务，后续迁移到 Department repository/persistence adapter |
| `IamUserService` | direct SQL | 记录债务，后续迁移到 IAM repository/persistence adapter |
| `AiToolPolicyService` | direct SQL | 记录债务，后续迁移到 AI policy repository/persistence adapter |
| `AiConversationService` | direct SQL | 记录债务，后续迁移到 AI conversation repository/persistence adapter |
| `AiToolOrchestrationService` | direct SQL | 记录债务，后续迁移到 AI orchestration repository/persistence adapter |
| `AiKnowledgeBaseAppService` | direct SQL | 记录债务，后续迁移到 knowledge-base repository/persistence adapter |
| `AiNativeToolRuntimeService` | direct SQL | 记录债务，后续改为调用应用服务、Internal API、Domain Event 或 Outbox |
| `AiEmployeeRuntimeService` | direct SQL | 记录债务，后续迁移到 AI employee runtime repository/persistence adapter 或拥有方 Internal API |
| `AiManagementAppService` | direct SQL | 记录债务，后续迁移到 AI management repository/persistence adapter |
| `CompetitionManagementAppService` | direct SQL | 记录债务，后续迁移到 Competition repository/persistence adapter |
| `CompetitionRegistrationAppService` | direct SQL | MVP 报名、资料与支付编排债务；迁移到 Competition registration repository/persistence adapter |
| `WorkflowAppService` | direct SQL | 迁移到 Workflow repository/persistence adapter |
| `InternalSystemController` | direct SQL / direct persistence dependency | 记录债务，后续迁移到 Internal API application service 或 repository/persistence adapter |

## 治理原则

- 不为了“看起来干净”一次性重构所有历史模块。
- 新增业务写入默认不得加入本清单。
- 新增 AppService 禁止直接编写 `insert`、`update`、`delete` SQL，也不得直接调用 `jdbcTemplate.update`、`jdbcTemplate.batchUpdate` 或 `MyBatisQueryOperations.update`；写入必须通过 Repository、Mapper、DAO、Persistence Adapter、Internal API、Domain Event 或 Outbox 等拥有方边界。
- 历史债务迁移时必须补充模块级测试和架构测试。
- AI Tool Runtime 和 Job Handler 的业务表写入应优先改为调用业务拥有方。
- 每次从债务清单移除一项，都应同时删除对应架构测试 allowlist 项。
