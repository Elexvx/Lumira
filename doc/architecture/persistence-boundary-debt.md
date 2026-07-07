# 持久化边界历史债务

本文件记录当前仍保留的直接 SQL 写库债务。它们不是新规范的例外模板，只是为了避免本阶段大规模扰动 System、IAM、AI 等历史模块。后续治理应按模块拆分，逐步迁移到 Repository、Mapper、DAO 或 Persistence Adapter。
Team 模块不允许进入本债务清单。本阶段已经要求 `TeamAppService` 和 `TeamInviteService` 清除直接写库 SQL，并由 Team repository 层承接持久化。
## 褰撳墠鍊哄姟娓呭崟

| 模块/类 | 债务 | 本阶段处理 |
| --- | --- | --- |
| `SystemManagementAppService` | direct SQL | 记录债务，后续迁移到 System repository/persistence adapter |
| `SystemUserManagementAppService` | direct SQL | 记录债务，后续迁移到 User repository/persistence adapter |
| `SystemRoleManagementAppService` | direct SQL | 记录债务，后续迁移到 Role repository/persistence adapter |
| `SystemDepartmentAppService` | direct SQL | 记录债务，后续迁移到 Department repository/persistence adapter |
| `AccountActivationService` | direct SQL | historical debt; migrate to Account activation repository/persistence adapter |
| `IamUserService` | direct SQL | 记录债务，后续迁移到 IAM repository/persistence adapter |
| `AiAssistantEmployeeResolver` | direct SQL | historical debt; migrate to AI assistant employee repository/persistence adapter |
| `DefaultDelegationGrantEvaluator` | direct persistence dependency | 记录债务，后续迁移到 IAM delegation repository/persistence adapter |
| `AiToolPolicyService` | direct SQL | 记录债务，后续迁移到 AI policy repository/persistence adapter |
| `AiConversationService` | direct SQL | 记录债务，后续迁移到 AI conversation repository/persistence adapter |
| `AiToolOrchestrationService` | direct SQL | 记录债务，后续迁移到 AI orchestration repository/persistence adapter |
| `AiKnowledgeBaseAppService` | direct SQL | 记录债务，后续迁移到 knowledge-base repository/persistence adapter |
| `AiNativeToolRuntimeService` | direct SQL | 记录债务，后续改为调用应用服务、Internal API、Domain Event 或 Outbox |
| `AiEmployeeRuntimeService` | direct SQL | 记录债务，后续迁移到 AI employee runtime repository/persistence adapter 或拥有方 Internal API |
| `AiIamQueryFacade` | direct persistence dependency | 记录债务，后续迁移到 AI IAM query port 或 IAM Internal API |
| `AiLlmServiceConfigProvider` | direct persistence dependency | 记录债务，后续迁移到 AI LLM config repository/persistence adapter |
| `AiManagementAppService` | direct SQL | 记录债务，后续迁移到 AI management repository/persistence adapter |
| `AiOwnerMetricsService` | direct persistence dependency | 记录债务，后续迁移到 AI metrics repository/persistence adapter |
| `AiPlatformQueryFacade` | direct persistence dependency | 记录债务，后续迁移到 Platform query port 或 Internal API |
| `AiReadQueryService` | direct persistence dependency | 记录债务，后续迁移到 AI read repository/persistence adapter |
| `AiSkillPermissionChecker` | direct persistence dependency | 记录债务，后续迁移到 AI permission repository/persistence adapter 或授权 Internal API |
| `AiToolRegistry` | direct persistence dependency | 记录债务，后续迁移到 AI tool registry repository/persistence adapter |
| `ActivityManagementAppService` | direct SQL | 记录债务，后续迁移到 Activity repository/persistence adapter |
| `OperationAuditService` | direct SQL | historical debt; migrate to Audit repository/persistence adapter |
| `CertificateAppService` | direct SQL | historical debt; migrate to Certificate repository/persistence adapter |
| `CompetitionManagementAppService` | direct SQL | 记录债务，后续迁移到 Competition repository/persistence adapter |
| `CompetitionRegistrationAppService` | direct SQL | MVP registration/material/payment orchestration debt; migrate to Competition registration repository/persistence adapter |
| `ExpertApprovalEventConsumer` | direct SQL | historical debt; migrate to Expert approval repository/persistence adapter |
| `ExpertManagementAppService` | direct SQL | 记录债务，后续迁移到 Expert repository/persistence adapter |
| `FileManagementAppService` | direct persistence dependency | 记录债务，后续迁移到 File repository/persistence adapter |
| `OnlineSessionManagementAppService` | direct SQL | 记录债务，后续迁移到 Online Session repository/persistence adapter |
| `ProjectManagementAppService` | direct SQL | 记录债务，后续迁移到 Project repository/persistence adapter |
| `DictRuntimeService` | direct persistence dependency | 记录债务，后续迁移到 Dict repository/persistence adapter |
| `SensitiveWordService` | direct SQL | 记录债务，后续迁移到 Sensitive Word repository/persistence adapter |
| `SensitiveWordDictionaryCache` | direct persistence dependency | 记录债务，后续迁移到 Sensitive Word repository/persistence adapter |
| `SensitiveWordPluginStateService` | direct persistence dependency | 记录债务，后续迁移到 Sensitive Word plugin state repository/persistence adapter |
| `SystemPlatformSettingsAppService` | direct SQL | 记录债务，后续迁移到 Platform Settings repository/persistence adapter |
| `SystemProfileSettingsAppService` | direct SQL | 记录债务，后续迁移到 Profile Settings repository/persistence adapter |
| `TeamInternalApiService` | direct persistence dependency | 记录债务，后续迁移到 Team repository/persistence adapter；不得扩展到 `TeamAppService` 或 `TeamInviteService` |
| `TeamPermissionService` | direct persistence dependency | 记录债务，后续迁移到 Team repository/persistence adapter；不得扩展到 `TeamAppService` 或 `TeamInviteService` |
| `WorkflowAppService` | direct SQL | historical debt; migrate to Workflow repository/persistence adapter |
| `WorkflowSchemaBootstrap` | direct SQL | historical debt; migrate to Workflow schema repository/persistence adapter |
| `WorkOrderFeedbackService` | direct SQL | 记录债务，后续迁移到 Work Order repository/persistence adapter |
| `WorkOrderFeedbackPluginStateService` | direct persistence dependency | 记录债务，后续迁移到 Work Order plugin state repository/persistence adapter |
| `InternalSystemController` | direct SQL / direct persistence dependency | 记录债务，后续迁移到 Internal API application service 或 repository/persistence adapter |

## 治理原则

- 不为了“看起来干净”一次性重构所有历史模块。
- 新增业务写入默认不得加入本清单。
- 新增 AppService 禁止直接编写 `insert`、`update`、`delete` SQL，也不得直接调用 `jdbcTemplate.update`、`jdbcTemplate.batchUpdate` 或 `MyBatisQueryOperations.update`；写入必须通过 Repository、Mapper、DAO、Persistence Adapter、Internal API、Domain Event 或 Outbox 等拥有方边界。
- 历史债务迁移时必须补充模块级测试和架构测试。
- AI Tool Runtime 和 Job Handler 的业务表写入应优先改为调用业务拥有方。
- 每次从债务清单移除一项，都应同时删除对应架构测试 allowlist 项。
