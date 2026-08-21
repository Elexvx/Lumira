# Lumira 单租户业务平台架构设计

状态：Accepted

原始日期：2026-07-20

修订日期：2026-08-22

适用范围：当前 Lumira 仓库、活动、赛事、报名、团队与项目、专家、评审、证书、支付、文件、导出、异步任务、部署和数据库初始化。

## 1. 结论

Lumira 采用单租户部署：一个生产部署单元服务一个业务组织，使用一套数据库、缓存和文件存储。部署边界就是组织边界，不在业务表中重复建模组织隔离，也不在请求、事件、缓存键或导出任务中传递额外的组织上下文。

继续采用现有 DDD 模块化单体和三运行时拓扑，不立即拆成物理微服务。当前架构重点如下：

1. 活动和赛事保持独立业务模型，统一展示通过只读目录投影完成，不合并写入主表。
2. 报名数据采用规范化核心数据、独立动态字段值和不可变提交快照，历史证据与管理查询分离。
3. 导出采用按数据量自适应的流式协议，异步任务通过 Outbox、租约、重试和过期清理保障可靠性。
4. 角色、权限、资源归属和所有者规则在当前部署内完成鉴权；不支持组织切换、代理组织模式或跨部署管理。
5. 初始化 SQL 只负责当前部署的角色、菜单、权限、业务表和必要种子数据，不创建组织成员、组织角色或组织级行隔离结构。

## 2. 部署与业务边界

### 2.1 单租户部署规则

- 一个 `lumira-server`、一个 `lumira-async` 和一个 `lumira-job-executor` 组成一个部署单元。
- 一个部署单元连接一个逻辑业务数据库、一个 Redis 实例或逻辑空间以及一套文件存储空间。
- 活动、赛事、报名、文件、支付、评审和导出数据都属于当前部署；数据访问由登录身份、角色、权限和资源关系控制。
- `organization`、`organizer` 等字段如果出现在业务模型中，只表示报名资料或展示信息，不承担身份认证、授权或数据库隔离职责。
- 需要服务多个组织时，使用相互独立的部署和数据存储；本仓库不定义跨部署数据共享协议。

### 2.2 明确不属于本架构的能力

以下内容不应出现在新的业务设计、初始化 SQL 或迁移计划中：

- 组织实体、成员关系、组织角色和组织切换流程。
- 以组织字段开头的联合唯一键、索引、查询谓词或缓存键。
- 绑定组织上下文的 Token、请求头、事件 envelope、导出任务和观测标签。
- “平台态/代理态”跨组织授权，以及为历史数据回填保留的虚拟组织。

## 3. 事实基线

文档记录设计意图，不能替代可执行事实。判断现状时按以下顺序取证：

1. 当前源码的实际调用链、权限判断和 SQL。
2. `lumira-backend/sql/saas.sql`、正式 migration 和生产 Compose/启动脚本。
3. 架构测试、迁移契约测试和模块测试。
4. Git 中的模块依赖、路由和运行配置。
5. 本文和其他架构文档。

当前运行拓扑：

```text
Browser
  -> api-proxy
  -> lumira-server       同步请求与控制面
  -> lumira-async        Outbox relay 和后台处理
  -> lumira-job-executor 调度适配
  -> MySQL / Redis / File Storage
```

`saas.sql` 是 fresh-init 的 role-only 入口，包含权限、菜单、业务表和必要种子数据；它不定义组织表或组织级数据字段。版本化迁移用于已有部署的增量演进，不能把未落地的组织模型作为迁移目标。

## 4. 限界上下文与所有权

| 上下文 | 建议模块 | 拥有的数据 | 不拥有的数据 |
| --- | --- | --- | --- |
| IAM / 平台治理 | `lumira-system` | 用户、角色、权限、平台配置、审计和共享事件桥 | 活动、赛事和报名业务值 |
| ACTIVITY | `lumira-activity` | 活动、活动报名、活动表单和报名值 | 赛事配置、赛程与评审 |
| COMPETITION | `lumira-competition` | 赛事、配置版本、赛事报名、报名快照、材料、评审和支付编排 | 正式用户角色、文件对象主数据和支付订单主数据 |
| TEAM | `lumira-team` | 正式团队、成员、邀请和申请 | 某次报名的团队证据快照 |
| PROJECT | `lumira-project` | 可复用项目主数据 | 某次报名的项目提交快照 |
| EXPERT | `lumira-expert` | 专家档案、标签、申请状态和回避关系 | 赛事评审任务和评分结果 |
| CERTIFICATE | 证书上下文 | 模板、版本、批次、证书和公开核验 | 赛事晋级规则 |
| WORKFLOW | `lumira-workflow` | 流程定义、节点、实例、任务和动作日志 | 业务实体本身 |
| EXPORT | `lumira-export` | 导出任务、租约、进度、结果文件引用和清理策略 | 各业务域的查询规则和字段权限 |
| CATALOG | 可先置于 `lumira-system` 的 read model | 面向公开查询的活动/赛事摘要投影 | 管理写入和报名事实 |

模块首先表现为 Maven 包、代码 owner、表 owner 和契约边界，不等于新增生产进程。跨模块协作使用应用接口、Internal API、Outbox 或明确的只读投影，禁止依赖其他模块的 Mapper、Entity 或 Service 实现。

## 5. 活动、赛事与报名模型

### 5.1 活动和赛事保持独立

- Activity 和 Competition 保留独立聚合、表、管理 API 和报名表。
- 不创建通用 `event` 写入主表，不在管理查询中用 UNION 替代领域 owner。
- `event_catalog_item` 只保存公开查询需要的最小字段，并由发布、更新、撤回和归档事件驱动。
- 管理页面、报名确认和支付始终读取 owner 表，不依赖目录投影。

### 5.2 报名数据边界

赛事报名的基本查询边界是 `competition_id`，活动报名的基本查询边界是 `activity_id`。需要进一步限制时使用 `dataset_id`、`registration_id`、`owner_user_id`、状态和资源关系。

建议保留或演进以下模型：

```text
activity
activity_form_definition
activity_form_field
activity_registration
activity_registration_field_value

competition
competition_config_set
competition_form_field
competition_stage
competition_registration
competition_registration_dataset
competition_registration_dataset_row
competition_registration_team
competition_registration_project
competition_registration_member
competition_registration_field_value
registration_material_submission/value/revision
competition_review_*
```

`competition_registration` 是一次赛事报名的聚合根。正式 Team/Project 只通过可选 source 引用关联；提交时的团队、项目、成员和字段值进入报名自己的子表与快照，不因正式主数据后续修改而改变历史证据。

### 5.3 动态字段与快照

字段定义至少包含稳定 `field_key`、`scope`、`data_type`、`required`、校验规则、敏感级别、导出能力和版本。报名成员的 `member.role` 是业务提交字段，不是 IAM 角色。

动态字段使用 typed value 表保存，按报名、主题和字段键定位；核心高频字段使用规范化列。现有 snapshot JSON 继续作为提交证据、历史回放和旧数据兼容，不作为管理筛选、统计和大批量导出的主数据源。

## 6. 身份、权限与 API 契约

### 6.1 授权顺序

每个管理请求按固定顺序校验：

1. 会话可信且未过期。
2. 用户具备对应功能权限。
3. 请求资源存在且属于当前部署。
4. 角色、负责人或资源关系允许访问该活动、赛事、报名或任务。
5. 返回或导出的敏感字段满足字段级权限。
6. 查看、下载、导出、评分、定稿和发布写入审计日志。

“全部可见”表示在当前部署与当前职责范围内的全部资源，不依赖隐藏前端按钮，也不表示跨部署授权。

### 6.2 管理 API

管理 API 直接使用资源路径，不引入组织切换前缀：

```text
GET  /api/v2/activities
POST /api/v2/activities
GET  /api/v2/activities/{activityId}/registrations

GET  /api/v2/competitions
POST /api/v2/competitions
GET  /api/v2/competitions/{competitionId}
GET  /api/v2/competitions/{competitionId}/registrations
POST /api/v2/competitions/{competitionId}/exports
```

公开目录、报名确认、支付下单和材料提交继续使用独立幂等接口。资源 ID 表达资源层级，服务端依据会话、角色和资源关系完成授权；不接受客户端传入的部署边界参数作为授权依据。

## 7. 事件、缓存与异步任务

### 7.1 事件 envelope

事件至少包含 `eventId`、`eventType`、`occurredAt`、`aggregateId`、`aggregateVersion`、`traceId` 和 `payloadVersion`。只有业务确实需要时才增加 `competitionId`、`activityId`、`datasetId` 或 `resourceId`；不设置无业务含义的组织字段。

Activity 和 Competition 在发布、撤回、归档或更新公开信息时，在本地事务写 Outbox。异步消费者按事件 ID 和版本幂等更新目录、通知、审计和分析投影。

### 7.2 缓存与任务

- React Query key 和服务端缓存键包含真实资源 ID、状态和必要的版本信息。
- 导出任务保存资源、筛选条件、字段选择、权限快照、游标、租约、重试和过期时间。
- 异步 worker 使用 lease、heartbeat、cursor、retry、cancel 和 expiry；不按组织公平调度或设置组织级配额。
- 文件下载使用业务引用授权和短时凭证，不仅凭 file ID 或通用文件权限放行。

## 8. 全站自适应导出

每个可导出领域实现 `ExportProvider`：

```text
providerKey()
listFields(resource)
authorize(actor, resource, fields)
estimateCount(filterSnapshot)
openCursor(filterSnapshot, afterId, batchSize)
mapRow(record, selectedFields)
```

Export 平台拥有任务和执行协议，业务 owner 拥有查询、字段解释、脱敏和数据权限。小数据同步流式下载，大数据异步执行；XLSX 使用流式 workbook 或临时文件，超大数据使用 CSV/ZIP。任务和 Outbox 在同一业务事务中创建，结果文件使用对象存储和短时下载凭证。

## 9. 数据库初始化与迁移原则

### 9.1 Fresh-init

`lumira-backend/sql/saas.sql` 是当前部署的 consolidated schema：

- 只包含当前模块拥有的业务表、角色、权限、菜单和必要种子数据。
- 角色与权限是部署内的 IAM 数据，不延伸出组织成员或组织角色模型。
- 业务表按活动、赛事、报名、所有者和资源关系建模，不增加部署级重复字段。
- 新表和新索引必须由对应 owner 负责，并在 SQL 契约测试中登记。

### 9.2 Migration

- 继续使用 expand-only、幂等、可重试的正式迁移。
- 迁移计划只处理已经存在或已批准的业务表、列、索引、种子和数据修复。
- 不为未落地的组织模型创建表、回填脚本、影子双写、强制非空约束或清理任务。
- 如果未来确实需要部署拆分，应以独立部署、独立存储和数据导出/导入方案评审，不把该问题伪装成业务行级字段迁移。

## 10. 可靠性、可观测性与安全

- 每个本地业务事务同时写入事实和 Outbox；消费者幂等、可重试、可暂停和可重放。
- 关键接口使用乐观锁、幂等键和状态条件更新，避免重复支付、重复通知、重复证书和重复导出。
- 请求、任务和事件关联 `requestId`、`traceId`、`resourceId`、`actor`、状态和版本；通用 Prometheus/Loki/Tempo 配置不因本架构调整而改变。
- 记录登录、权限变更、资源查看、文件下载、导出、评分、定稿、发布和配置变更审计。
- 文件、个人信息和导出字段按现有权限、脱敏、短时凭证和过期策略保护。

## 11. 实施顺序与验收

### 11.1 实施顺序

1. 固化 activity、competition、registration、review、file、payment、export 的 owner 和表 manifest。
2. 将新增写路径收口到 Repository/Persistence Adapter，减少跨模块直接 SQL。
3. 完成报名规范化值与快照的双写、校验和逐域切换。
4. 完成事件目录、Outbox、导出任务和文件业务授权的契约测试。
5. 仅在确有独立扩缩容、发布节奏或故障隔离需求时评估物理服务拆分。

本单租户决策不产生组织数据迁移。运行时、事件和安全上下文已按单部署边界移除组织上下文、租户字段及租户相关兼容参数；后续新增接口不得重新引入这些字段。

### 11.2 验收清单

- fresh-init SQL 不创建组织隔离表、组织级字段或组织级索引。
- 新增事件和导出任务只携带实际资源边界，不出现无业务含义的组织字段。
- 活动和赛事写模型独立，目录投影可重建且不成为 source of truth。
- 报名确认、材料提交、评审定稿、结果发布和导出均可幂等、可审计、可恢复。
- 文档不再把组织上下文、组织切换、跨组织授权或组织字段描述为待实施能力。
- 被删除的 ADR 不再有引用；所有相关链接指向当前单租户架构或仍有效的领域 ADR。

## 12. 参考

- [ADR-0005：从 system-service 拆出业务限界上下文](../../doc/adr/0005-extract-business-bounded-contexts.md)
- [ADR-0006：活动和赛事独立写模型，公共查询使用目录投影](../../doc/adr/0006-use-event-catalog-read-model.md)
- [ADR-0007：报名采用规范化值和不可变快照双模型](../../doc/adr/0007-normalize-registration-values-and-keep-snapshots.md)
- [ADR-0008：采用全站自适应流式导出平台](../../doc/adr/0008-adopt-adaptive-streaming-export-platform.md)
- [赛事报名、材料与评审体系整改方案](../../doc/architecture/competition-registration-review-remediation-plan.md)
- [生产运行拓扑](2026-08-08-lumira-production-runtime-topology.md)
