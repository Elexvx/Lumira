# 服务与数据库表 Ownership

## 1. 目标

这份文档定义当前单体微服务架构下的模块职责、数据库表归属和跨模块访问规则。它的作用是防止代码虽然聚合运行、业务边界却互相穿透，导致未来无法拆分。

机器可读的 owner 清单位于 [DDD Owner Table Manifest](./27-ddd-owner-table-manifest.csv)。`DddArchitectureBoundaryTest` 会读取该清单并扫描 Flyway DDL、MyBatis XML 写语句、`JdbcTemplate.update` 显式 SQL 和 `BaseMapper` 写方法调用，确保新增或修改表结构/写路径时必须声明长期 owner 或历史兼容写入模块。

此外，`BaseMapper` 管理的表本身也必须属于 mapper 所在模块的 owner 范围。已拆分为独立服务目标的 `auth-service`、`message-service`、`file-service`、`plugin-service`、`localization-service`、`payment-service` 和 `ai-service` 还会被扫描 `com.lumira.*.mapper` / `com.lumira.*.entity` import，只允许引用本上下文持久化类型。架构测试也会扫描 service module 的 Maven 依赖：除 `lumira-server` 聚合入口外，业务服务不得依赖另一个 service artifact。需要跨上下文读取时，不允许在消费方复制 owner 表的 Entity/Mapper；应通过 `libs/lumira-api` 契约、owner 查询 API、事件投影或缓存快照。

## 2. 基本原则

- 一个业务表只能有一个 owner module。
- 只有 owner module 可以写入自己的业务表。
- 历史聚合迁移或过渡期双写必须在 owner manifest 中显式列为 compatible writer。
- 其他模块需要数据时，优先通过应用服务契约、内部 API、事件投影或只读快照获取。
- Flyway migration 应放在 owner module 的 `src/main/resources/db/migration` 下。
- 跨服务共享 DTO、Feign contract 和事件契约放入 `libs/lumira-api`，插件 SPI 放入 `libs/plugin-api`。
- 业务服务不得直接依赖另一个服务的 Maven artifact、mapper、entity 或 migration。

## 3. 服务 Ownership

| 模块 | 当前职责 | 拥有的数据 |
| --- | --- | --- |
| `lumira-server` | 聚合运行入口、统一后端进程 | 不直接拥有业务表，数据仍归属各模块 |
| `auth-service` | 登录、刷新 token、二次认证、Passkey、微信登录入口 | 认证会话、登录保护、认证挑战等认证域数据；用户主数据仍由 `system-service` 维护 |
| `system-service` | 系统核心、IAM、菜单、角色、权限、配置、审计、AI 聚合兼容 adapter | `sys_*`、`iam_*`、审计和平台治理表；过渡期保留 `ai_*` 兼容写入 |
| `file-service` | 文件上传、文件对象、存储空间、文件访问控制 | `file_object`、`file_storage_space` 及后续文件处理投影 |
| `message-service` | 站内信、WebSocket 推送、消息归档、投递日志 | `msg_*`、消息 outbox |
| `plugin-service` | 插件定义、版本、租户启用、运行日志、插件网关 | `sys_plugin_*` 的长期 owner |
| `localization-service` | 语言、命名空间、翻译词条、发布版本 | `sys_localization_*` 的长期 owner |
| `job-executor` | XXL-JOB 执行器、内部任务触发 | 不拥有业务表，只调用内部任务接口 |

## 4. 表 Ownership 矩阵

| 表或表族 | Owner | 当前位置 | 说明 |
| --- | --- | --- | --- |
| `sys_user`、`sys_role`、`sys_menu`、`sys_permission`、`sys_user_role` | `system-service` | `services/system-service` | 用户、角色、菜单、权限主数据。其他服务只消费权限结果或用户摘要 |
| `iam_user*` | `system-service` | `services/system-service` | IAM 用户中心。`auth-service` 不直接写 IAM 表 |
| `sys_department`、`sys_user_department`、`sys_role_data_scope` | `system-service` | `services/system-service` | 组织和数据权限基础 |
| `sys_config`、`sys_dict_*` | `system-service` | `services/system-service` | 平台配置和字典。跨服务配置读取应通过 API 或配置快照 |
| 审计日志表 | `system-service` | `services/system-service` | 平台级操作审计。其他服务可通过事件或 API 上报 |
| `ai_*` | `ai-service` | `services/ai-service`，`system-service` 保留聚合兼容写入 | AI 数字员工、LLM 服务、知识库、会话、检索日志；业务 API 完成迁移前 `system-service` 仍作为兼容 adapter |
| `file_object` | `file-service` | `services/file-service` | 文件对象主表。`system-service` 中历史文件能力只能作为迁移兼容来源 |
| `file_storage_space` | `file-service` | `services/file-service`，`system-service` 保留历史兼容 migration | 文件服务已拥有正式 owner migration；旧 migration 不回改，避免破坏已应用环境 |
| `msg_notice`、`msg_notice_read`、`msg_delivery_log` | `message-service` | `services/message-service` | 消息归档、阅读状态和投递记录 |
| `platform_event_outbox` | 各事件生产服务按 `source_type` 分 owner | `system-service`、`message-service`、`file-service`，`system-service` 保留聚合 runtime migration | 聚合部署暂共享物理表；每个 owner relay、metrics、replay 必须过滤自己的 `source_type`，禁止跨 owner claim 或重放 |
| `sys_plugin_*` | `plugin-service` | `plugin-service`，`system-service` 有历史副本 | 长期以 `plugin-service` 为 owner，系统服务只保留菜单/权限展示和兼容读取 |
| `sys_localization_*` | `localization-service` | 当前主运行仍由本地化服务和系统迁移共同承载 | 长期应以 `localization-service` 为 owner |

插件启用时产生的 IAM 权限写入不再由 `plugin-service` 直接写 `sys_permission` 或 `sys_role_permission`。插件上下文只提交 `PluginPermissionRegistrationRequestDTO`，由 `system-service` 的内部 owner API 负责落库、绑定管理员角色并失效权限快照。

消息上下文产生的操作审计不再由 `message-service` 直接写 `audit_operation_log`。`message-service` 只提交 `OperationAuditRecordRequestDTO`，由 `system-service` 的平台审计 owner 写入审计表。

消息通知读取平台配置、角色用户关系时，也不再复制 `sys_config`、`sys_user_role` 的本地 Mapper。`message-service` 通过 `SystemInternalApi.platformConfigValues`、`SystemInternalApi.userIdsByRole`、`SystemInternalApi.userContactsByIds`、`SystemInternalApi.userContactsByRole` 和 `SystemInternalApi.tenantUserContacts` 消费 System/IAM owner 读契约。消息列表中的目标用户和目标角色展示名称也通过 `SystemInternalApi.usersByIds`、`SystemInternalApi.rolesByIds` 批量快照补全，列表主 SQL 不再为了展示字段联 `sys_user`、`sys_user_tenant`、`sys_role`。消息可见性判断使用 IAM 权限快照中的 roleIds 输入，不再在 Message SQL 中查询 `sys_user_role`。

## 5. 跨服务访问规则

### 5.1 读访问

- UI 查询走 `/api` 到 `lumira-server` 中的目标 owner module。
- 模块间查询优先通过 `libs/lumira-api` 中的契约或明确的应用服务接口。
- 高频只读数据可以做本地缓存或 Redis 缓存，但缓存 key 必须包含租户、用户或版本维度。
- 不能为了方便在一个服务里直接引入另一个服务的 mapper 或 entity。

### 5.2 写访问

- 写操作必须进入 owner service。
- 跨服务写入使用命令 API 或事件驱动流程。
- 同步 API 适合强一致、用户正在等待结果的动作。
- Outbox 事件适合异步处理、通知、索引、审计投影、缓存失效和补偿。

### 5.3 Migration

- 新表放入 owner service 的 migration。
- 从 `system-service` 拆出的表，需要先声明 owner，再做数据迁移和双写/停写窗口。
- 不允许同一张表在两个服务中同时新增结构变更。

## 6. 当前待收口事项

- 将 `sys_plugin_*` 的长期写入口收口到 `plugin-service`，`system-service` 只保留菜单和权限视图。
- 将 `sys_localization_*` 的长期 owner 收口到 `localization-service`。
- 为文件事件补充 relay/dispatcher 与下游消费者；当前已记录 `FILE_OBJECT_UPLOADED` 和 `FILE_OBJECT_DELETED` outbox 事件。
- 为跨服务事件继续完善统一事件命名、payload schema 和消费策略，详见 [事件与 Outbox 架构](./16-event-outbox-architecture.md)。
