# DDD 物理拆分准备清单

## 1. 定位

本文档用于回答一个问题：某个限界上下文什么时候可以从模块化单体中拆成独立服务。

当前 Lumira 的正确路线仍然是先完成 DDD 边界、契约、读模型和事件治理，再按运行压力拆分物理服务。拆分不是目标，拆分后的低耦合、可回滚、可观测和低延迟才是目标。

## 2. 全局拆分门禁

任一上下文必须同时满足以下条件，才允许进入物理拆分设计和实施：

| 门禁 | 要求 | 验证方式 |
| --- | --- | --- |
| Owner 表 | 每张业务表只有一个 owner context 写入 | `docs/27-ddd-owner-table-manifest.csv` + `DddArchitectureBoundaryTest` |
| API 契约 | 外部调用只通过 `/api/v2/**`、`libs/lumira-api` 或 owner internal API | v2 契约测试 + Controller 不直连 Mapper |
| 事件契约 | 弱一致流程通过领域事件和 outbox 发布，事件有 schema version 和幂等键 | outbox 单测、relay/replay 测试 |
| 读模型 | 高频查询不加载完整聚合，不跨 owner join 热表 | `docs/28-ddd-hot-path-explain-plan.md` + SQL explain |
| 缓存失效 | 热缓存使用 `tenantId + version + scope` 或明确等价键 | 读模型版本测试、缓存命中指标 |
| 配置密钥 | 配置、密钥、回调地址、job token 可独立注入 | 环境变量清单 + 启动检查 |
| 健康检查 | 能独立判断 DB、缓存、外部依赖、relay backlog 状态 | actuator/health 或 owner internal health |
| 观测指标 | 有 p95、错误率、缓存命中、队列积压、死信数量 | metrics dashboard + alert rule |
| 回滚方案 | 可在不丢数据的前提下回切模块化单体或禁用 adapter | runbook + 双读/双写窗口 |
| 兼容窗口 | v1 adapter 仍可委托新 application service | v1 回归 + v2 契约测试 |

`scripts/ddd-physical-split-gate.mjs` 将上述门禁中可静态验证的部分机器化：owner manifest、readiness/health/metrics 契约、owner internal job/API 契约、跨 service module POM 依赖、owner Flyway 依赖、关键 owner 表迁移 SQL、拆分文档和运行手册覆盖。默认 advisory 模式会输出 `artifacts/ddd/split/physical-split-readiness.json` 并保留 blocker 清单；发布或拆服务前可设置 `DDD_SPLIT_STRICT=true`，此时存在 blocker 会失败。共享 contract 会要求十个 bounded context 精确覆盖且不能重复或未知，基础检查至少包含 module、owner manifest、readiness、health、metrics 和 cross-service POM dependency，避免把缺失 owner 或缺失拆分基础检查的 artifact 误判为可拆分。

## 3. 共享运行要求

- 物理拆分后，owner 服务仍是唯一写入口；其他上下文只能读快照、调用契约 API 或消费事件投影。
- Job 只作为调度和 relay adapter，不拥有业务规则，不直连业务表。
- Relay 必须支持批量 claim、指数退避、幂等投递、`DEAD_LETTER` 和手动 replay；暂未具备 relay 的 owner 必须在拆分前补齐。
- 所有热点列表必须分页，禁止无界 `count` 和跨 owner 大 join；需要总量时使用 capped count、异步统计或投影。
- 任何拆分不得让登录/bootstrap、当前用户、权限校验、消息列表、语言包、插件 bootstrap、文件上传回包、支付 webhook 的 p95 回退超过 10%。
- 跨服务调用必须有超时、重试边界和降级语义；禁止在事务内同步调用另一个 owner 的写接口。

## 4. 上下文拆分清单

### 4.1 IAM

| 项目 | 清单 |
| --- | --- |
| Owner 表 | `iam_user*`、`sys_user`、`sys_role`、`sys_role_permission`、`sys_menu`、`sys_permission`、`sys_user_role`、`sys_user_tenant*`、`sys_department`、`sys_user_department`、`sys_role_data_scope`、`sys_tenant` |
| 对外/API 契约 | `/api/v2/iam/readiness`、`/api/v2/iam/health`、`/api/v2/iam/metrics` 已暴露拆分门禁观测契约；`/api/v2/iam` 已提供当前租户、我的租户、租户创建/更新/状态/归档/成员默认关系、用户、用户导出、角色、权限、菜单、部门 adapter；跨上下文只暴露用户/角色/租户/权限快照查询契约 |
| 事件/读模型 | `RolePermissionsChanged`、用户状态/部门/租户变更事件；`PermissionSnapshotReadModel` 按租户和版本失效 |
| 配置/密钥 | 默认租户、管理员初始化策略、权限快照缓存 TTL、密码/凭证策略 |
| 健康检查 | IAM DB、权限快照版本表、权限快照缓存读写 |
| 观测指标 | `/api/v2/iam/metrics` 已返回权限快照当前 read-model version；权限快照 p95、缓存命中率、失效延迟已接入 Micrometer runtime metrics；角色权限变更事件积压继续接入 |
| 回滚方案 | 网关/API 回切单体 IAM adapter；权限快照可从 owner 表重建；保留 v1 用户角色接口兼容窗口 |
| 拆分阻塞 | 权限快照重建 runbook、跨实例缓存失效演练和看板需要固化；权限快照 p95/cache hit/invalidation lag 已接入 Micrometer runtime metrics |

### 4.2 Auth

| 项目 | 清单 |
| --- | --- |
| Owner 表 | `sys_user_passkey_credential`、`sys_user_wechat_binding`、`sys_verification_binding`、`sys_verification_challenge`；session 主状态优先落 Redis/session store |
| 对外/API 契约 | `/api/v2/auth/readiness`、`/api/v2/auth/health`、`/api/v2/auth/metrics` 已暴露拆分门禁观测契约；`/api/v2/auth` 登录密钥、登录、刷新 token、当前用户、登出、keepalive；用户和权限信息只读 IAM 快照 |
| 事件/读模型 | session revoke、登录挑战完成、二次验证完成可作为审计/风控事件；登录 bootstrap 使用一次性 session payload |
| 配置/密钥 | JWT/refresh token 密钥、登录加密密钥、验证码/二次验证配置、passkey rpId/rpName |
| 健康检查 | token 签名组件、session store、登录加密密钥、验证码/挑战存储、IAM 用户/权限快照查询 |
| 观测指标 | `/api/v2/auth/metrics` 已返回 session store hit/miss/save/remove/corrupt payload 与 `auth.bootstrap_cache.alignment_rejects` 实时值；登录 p95、current-user p95、refresh-token p95 已声明，后续接入 Micrometer 直方图 |
| 回滚方案 | `/api/v2/auth` adapter 回切单体 `AuthAppService`；refresh token 和 session store 保持兼容 TTL |
| 拆分阻塞 | Auth owner baseline migration 已补齐 passkey、微信绑定、验证码绑定和登录挑战表；拆分前仍需完成真实 Redis/session store、IAM 快照远程调用和回滚窗口演练 |

### 4.3 Platform

| 项目 | 清单 |
| --- | --- |
| Owner 表 | `sys_config`、`sys_dict_*`、`audit_*`、`ddd_read_model_version`、`sys_export_task`、`sys_sensitive_word` |
| 对外/API 契约 | `/api/v2/platform/readiness`、`/api/v2/platform/health`、`/api/v2/platform/metrics` 已暴露拆分门禁观测契约；`/api/v2/platform` 已提供公开 bootstrap、配置、字典、runtime appearance、品牌、协议、水印、悬浮窗、安全、SMTP、微信公众号通知配置、审计、dashboard 和在线会话 monitoring adapter；`SystemInternalApi` 提供配置快照和审计记录 |
| 事件/读模型 | 配置变更、外观变更、字典发布、审计投影事件；runtime appearance 使用版本化读模型 |
| 配置/密钥 | 配置加密密钥、审计保留周期、监控采集开关、runtime bootstrap 缓存 TTL |
| 健康检查 | Platform DB、读模型版本表、审计写入、配置快照缓存 |
| 观测指标 | `/api/v2/platform/metrics` 已返回 runtime appearance 当前 read-model version；配置读取 p95、bootstrap p95、审计写入失败率、配置缓存命中率已接入 Micrometer runtime metrics；读模型版本推进延迟已从 `ddd_read_model_version.rebuilt_at` 读取真实值 |
| 回滚方案 | 单体继续承载 platform owner；配置快照可按 scope 重建；审计可按 requestId 幂等补写 |
| 拆分阻塞 | Platform 运行看板仍需固化；配置读取 p95、bootstrap p95、审计失败率、配置缓存命中率已接入 Micrometer runtime metrics，读模型版本推进延迟已接入版本表真实值；需要明确所有 `SystemInternalApi` 调用的超时和降级策略，避免 Platform 独立故障拖慢热路径 |

### 4.4 Message

| 项目 | 清单 |
| --- | --- |
| Owner 表 | `msg_*`、消息 owner 自有 outbox；历史兼容写入受 manifest 约束 |
| 对外/API 契约 | `/api/v2/message/readiness`、`/api/v2/message/health`、`/api/v2/message/metrics` 已暴露拆分门禁观测契约；`/api/v2/message` 主列表、归档、未读数、全读；实时投递和 relay 使用 owner internal API `/message/internal/jobs/outbox/relay`、`/message/internal/jobs/outbox/{id}/replay` |
| 事件/读模型 | `MESSAGE_NOTICE_CREATED`、`MESSAGE_NOTICE_RETRACTED`、已读/归档事件；列表使用 `NoticeListItemReadModel`，未读数使用 capped counter/cache |
| 配置/密钥 | WebSocket 配置、消息投递批量大小、relay job token、外部通知通道配置只读 Platform 快照 |
| 健康检查 | Message DB、WebSocket registry、outbox dispatchable backlog、capped unread count、IAM/Platform 快照查询 |
| 观测指标 | `/api/v2/message/metrics` 已返回 dispatchable outbox backlog 实时值；消息列表 p95、未读数 p95、全读 p95、WebSocket 投递计数、outbox record/delivered/failed/replay 计数已声明指标口径 |
| 回滚方案 | 关闭独立投递 adapter，单体 Message adapter 接管读写；outbox 按 eventKey 幂等 replay |
| 拆分阻塞 | 真实 WebSocket/relay 跨进程演练、dead-letter 状态和 replay 运行手册需要补齐；未读数仍是 capped query，后续可按压力升级为 dedicated unread-counter projection |

### 4.5 File

| 项目 | 清单 |
| --- | --- |
| Owner 表 | `file_object`、`file_storage_space`、`file_processing_task`、`file_processing_artifact`、文件 owner outbox |
| 对外/API 契约 | `/api/v2/files/readiness`、`/api/v2/files/health`、`/api/v2/files/metrics` 已暴露拆分门禁观测契约；`/api/v2/files` 已提供文件列表、详情、预览、下载、上传、删除、存储空间管理和连通性测试 adapter；跨上下文通过 `FileInternalApi` 读取文件快照 |
| 事件/读模型 | `FILE_OBJECT_UPLOADED`、`FILE_OBJECT_DELETED`、`FileProcessingTaskRequested`；File owner 已提供 outbox relay/replay 和 logging dispatcher；上传后幂等生成 `file_processing_task`，处理任务支持 claim、失败重试、指数退避和死信；处理产物写入 `file_processing_artifact`；`FileInternalApi.readProcessingArtifactForUser` 已向 AI owner 暴露 artifact 只读契约，AI 索引 job 优先消费 `TEXT_CONTENT` artifact；`/file/internal/jobs/processing/run` 已接入 `fileProcessingTaskJob`；安全扫描处理器已写入 `SECURITY_SCAN_RESULT` artifact，并抽象为 inline/ClamAV 可插拔扫描引擎；inline 命中 EICAR 测试签名或 ClamAV 返回 FOUND 时置文件为 `QUARANTINED`，高风险扩展无威胁时进入 `REVIEW_REQUIRED`；图片缩略图处理器已写入 `THUMBNAIL_RESULT` artifact，本地存储生成 `.thumb.jpg` 并标记 `GENERATED`，远程对象存储标记 `DEFERRED_REMOTE_STORAGE`；图片 OCR 已由异步任务执行并写入 `OCR_RESULT`，默认 disabled 引擎写 `SKIPPED` 产物，Tesseract 引擎抽到文本时同步写入 `TEXT_CONTENT`；本地 txt/md/csv/json/log 文本抽取已生成 `TEXT_CONTENT` artifact，PDF/Word/Excel/PPT 已通过 Tika 异步抽取并写入 `TEXT_CONTENT` artifact，File owner AI_PARSE 已生成 `AI_PARSE_READY` artifact |
| 配置/密钥 | 对象存储 endpoint/bucket/credential、上传大小限制、`lumira.file.security-scan.mode`、ClamAV host/port/timeout、`lumira.file.ocr.mode`、Tesseract command/languages/timeout、临时 URL TTL |
| 健康检查 | File DB、对象存储读写探测、outbox backlog、处理任务积压 |
| 观测指标 | `/api/v2/files/metrics` 已返回 outbox recorded/failed/dead-letter 和 processing pending/failed/dead-letter 当前值；upload response、object storage operation、security scan、processing task 均已接入 Micrometer timer/counter，错误率可由 `result=failed|missing` 或 scan failure 计数派生 |
| 回滚方案 | 保留单体文件 adapter；对象存储 key 不变；处理任务可按 fileId 重新投递 |
| 拆分阻塞 | p95 回退阈值脚本和 explain 采集脚本已具备，真实 CI/发布环境仍需提供 baseline、actual 和数据库连接；安全扫描已具备可插拔 ClamAV adapter，图片 OCR 已具备可插拔 Tesseract adapter，远程缩略图已具备 deferred artifact 策略，AI owner 已具备本地 embedding/vector projection 与检索重排；真实 ClamAV/Tesseract/provider-native thumbnail/外部 embedding 或 vector DB 部署可用性和压测演练、真实 relay/processing job 运行演练拆分前仍需补齐 |

### 4.6 Plugin

| 项目 | 清单 |
| --- | --- |
| Owner 表 | `sys_plugin_*`、`plugin_event_outbox` |
| 对外/API 契约 | `/api/v2/plugins/readiness`、`/api/v2/plugins/health`、`/api/v2/plugins/metrics` 已暴露拆分门禁观测契约；`/api/v2/plugins` 定义、版本、状态、启停、租户 available/bootstrap/menus/permissions/runtime security policy；内部 relay/replay 使用 `/plugin/internal/jobs/outbox/relay`、`/plugin/internal/jobs/outbox/{id}/replay` |
| 事件/读模型 | `PluginUploaded`、`PluginVersionPublished`、`TenantPluginEnabled`、`TenantPluginDisabled`、`PluginSchemaChanged`；bootstrap 投影按租户版本失效 |
| 配置/密钥 | 插件包存储位置、manifest schema 版本、运行时策略默认值、relay job token |
| 健康检查 | Plugin DB、插件包存储、`plugin_event_outbox` dispatchable backlog、IAM 权限注册契约、bootstrap read-model version |
| 观测指标 | `/api/v2/plugins/metrics` 已返回 pending/failed/dead-letter/dispatchable outbox backlog 实时值；plugin bootstrap p95、启停成功率、回滚成功率、投影版本滞后已声明指标口径 |
| 回滚方案 | 单体 plugin adapter 接管；租户插件状态可按事件重建；启停失败可回滚到上一版本投影 |
| 拆分阻塞 | 跨进程启停、权限注册和 bootstrap 投影重建需要一次完整演练；插件包存储探测和 bootstrap version lag 需要接入真实运行看板 |

### 4.7 Localization

| 项目 | 清单 |
| --- | --- |
| Owner 表 | `sys_localization_*` |
| 对外/API 契约 | `/api/v2/localization/readiness`、`/api/v2/localization/health`、`/api/v2/localization/metrics` 已暴露拆分门禁观测契约；`/api/v2/localization` runtime bundle、语言、命名空间、词条分页、发布、回滚、同步 |
| 事件/读模型 | 发布、回滚、同步事件；`BundleReadModel` 按 locale 和 release version 缓存 |
| 配置/密钥 | 默认语言、fallback 语言、bundle CDN/cache headers、发布审批开关 |
| 健康检查 | Localization DB、active release 查询、bundle cache、Platform 配置快照 |
| 观测指标 | `/api/v2/localization/metrics` 已返回 runtime bundle cache size/hit/miss/hit-ratio 实时值；语言包 p95、active release version lag、发布/回滚失败率已声明指标口径 |
| 回滚方案 | 回切单体 localization adapter；active release 指针回退；bundle cache 按 locale/version 清理 |
| 拆分阻塞 | 发布事件 outbox 可选但需明确：若不发事件，必须保证跨实例 bundle cache 通过 version 拉平；跨实例缓存失效 runbook 和 CDN/cache-header 演练需要补齐 |

### 4.8 Payment

| 项目 | 清单 |
| --- | --- |
| Owner 表 | `payment_*`、`payment_event_outbox` |
| 对外/API 契约 | `/api/v2/payment/readiness`、`/api/v2/payment/health`、`/api/v2/payment/metrics` 已暴露拆分门禁观测契约；`/api/v2/payment` 通道设置、订单、退款、webhook；webhook 是公开回调边界，签名和幂等在 owner 内完成；内部 relay/replay 使用 `/payment/internal/jobs/outbox/relay`、`/payment/internal/jobs/outbox/{id}/replay` |
| 事件/读模型 | 支付订单创建、支付成功、退款、webhook received/processed 事件；`WebhookEvent` 使用 provider event id 幂等 |
| 配置/密钥 | provider appId/mchId/api key/cert、webhook nonce TTL、relay job token、回调白名单 |
| 健康检查 | Payment DB、provider 配置可读性、签名组件、webhook 幂等存储、outbox dispatchable backlog |
| 观测指标 | `/api/v2/payment/metrics` 已返回 pending/failed/dead-letter/dispatchable outbox backlog 实时值；webhook p95、签名失败率、重复 webhook 拦截数、订单/退款成功率已声明指标口径 |
| 回滚方案 | webhook 网关回切单体 endpoint；事件按 eventKey replay；provider 回调地址保留双活窗口 |
| 拆分阻塞 | 真实 provider sandbox 或模拟网关下的跨进程 webhook/replay 演练需要固化；provider-specific webhook p95、签名失败和重复拦截计数需要接入真实运行看板 |

### 4.9 AI

| 项目 | 清单 |
| --- | --- |
| Owner 表 | `ai_*`，长期 owner 为 `ai-service`；`system-service` 保留聚合兼容写入窗口 |
| 对外/API 契约 | `ai-service` 已暴露 `/api/v2/ai/readiness`、`/api/v2/ai/health`、`/api/v2/ai/metrics` 和完整 `/api/v2/ai` v2 业务面：employees、assistant、conversations/messages、chat、knowledge bases/detail/documents/upload/reindex/search、tools/execute/propose/confirm；AI tool 调用已通过 `AiOwnerToolGateway` 接入远程 IAM/Platform/File owner 契约，未配置时保留明确 degraded fallback；chat/embedding 已通过 `AiProviderRuntime` 接入本地 provider 和 OpenAI-compatible provider 配置；`DDD_SPLIT_STRICT=true node scripts/ddd-physical-split-gate.mjs` 已可验证 AI 端点覆盖 |
| 事件/读模型 | `ai-service` 已有独立 Flyway baseline `db/migration/ai/V1__baseline_ai_domain.sql`，可创建 AI owner 表、工具确认表、知识库文档/chunk 表和必要索引；`AI_KNOWLEDGE_INDEX_REQUESTED`、知识库文档 indexed/deleted；索引任务使用 retry、next retry、last error、`DEAD_LETTER` 状态；chunk 已写入 embedding model/dimension/vector JSON 投影，检索按 owner 权限取 bounded candidates 后向量重排 |
| 配置/密钥 | `lumira.ai.provider.openai-compatible.*`、LLM provider key、embedding model、向量库配置、工具调用 allowlist、索引 job batch/retry；当前内置 `local-hashing-v1` embedding adapter 无外部密钥 |
| 健康检查 | AI DB、LLM provider 配置、`local-hashing-v1` embedding、OpenAI-compatible provider 配置状态、知识库索引 retry/dead-letter 治理、File/IAM/Platform owner gateway 配置状态；本地 embedding/chat adapter 由 `DefaultAiProviderRuntimeTest` 覆盖，远程 owner gateway fallback 由 `RemoteAiOwnerToolGatewayTest` 覆盖 |
| 观测指标 | `/api/v2/ai/metrics` 已返回知识库索引 pending/retryable/failed/dead-letter 和 vector/local-hashing chunk 实时值；chat p95、LLM 错误率、tool 执行 p95、知识库检索 p95 已声明，后续接入真实 provider 运行看板 |
| 回滚方案 | 禁用异步索引消费者并保留任务表；会话 API 回切单体；知识库索引可按 documentId 重建 |
| 拆分阻塞 | 端点级 blocker 已清零，远程 File/IAM/Platform owner gateway 与 OpenAI-compatible provider 代码级 adapter 已落地；剩余为发布前运行演练：真实 provider key/baseUrl 下的 LLM/vector 调用、远程 owner gateway 跨进程超时/降级证据、生产量级压测/EXPLAIN、provider-specific dashboard 和 job relay E2E |

### 4.10 Job

| 项目 | 清单 |
| --- | --- |
| Owner 表 | 无；`job-executor` 不允许拥有业务表 |
| 对外/API 契约 | `/api/v2/job/readiness`、`/api/v2/job/health`、`/api/v2/job/metrics` 已暴露 tableless adapter 观测契约；XXL-JOB handler、`BackendJobClient`、owner internal relay/replay/processing API |
| 事件/读模型 | 不生产业务事件；只触发 owner relay 或 owner job use case；`RelayTaskReadModel` 只描述 owner、batch 和 handler 名称 |
| 配置/密钥 | job token、owner backend base URL、超时、批量大小、handler 开关 |
| 健康检查 | `/api/v2/job/health` 已返回 XXL-JOB executor config、BackendJobClient target、internal token 和 handler registration 配置状态 |
| 观测指标 | `/api/v2/job/metrics` 已返回 BackendJobClient target configured count、internal token configured、declared owner handler count；handler duration、owner call error rate、relay delivered/failed/dead letter 和调度延迟由后续 XXL-JOB runtime/owner 看板补齐 |
| 回滚方案 | 禁用对应 handler；owner 服务可通过内部接口手动 relay/replay；不需要数据迁移 |
| 拆分阻塞 | 每个 handler 必须证明只调用 owner API，不读取/写入 owner 表；统一 job token、调用重试、幂等、死信 replay runbook 和真实 XXL-JOB runtime dashboard 需要固化 |

## 5. 推荐拆分顺序

1. `Localization`：读多写少，runtime bundle 易缓存，拆分收益高且业务回滚简单。
2. `Message`：高频读和实时投递压力明确，已有读模型和 outbox 基础。
3. `Plugin`：生命周期清晰，已具备 relay/replay，但需要权限注册和投影重建演练。
4. `Payment`：边界清晰但风险高，应在 webhook sandbox、签名、幂等、replay 完整演练后拆。
5. `File`：依赖对象存储和异步处理链，relay、处理任务状态机、processing job adapter、安全扫描 artifact、本地图片缩略图、本地文本/PDF/Office 抽取、AI-ready artifact 处理器和 AI owner artifact 消费契约已落地，必须继续补齐外部扫描/OCR/provider-native thumbnail 的真实部署压测。
6. `AI`：资源隔离价值最大，但依赖 LLM、向量库、File、IAM、Platform，适合作为后期独立服务。
7. `IAM`、`Auth`、`Platform`：核心热路径 owner，最后拆；优先把快照、契约和缓存失效稳定下来。
8. `Job`：保持 adapter 形态，不作为业务服务拆分目标。

## 6. 当前结论

Lumira 已具备物理拆分的结构基础：owner 表 manifest、v2 adapter、DDD 架构测试、读模型版本、热点 SQL explain 计划和多个 owner outbox 已落地。

但在真正拆出服务前，还需要完成三类收尾：

- CI 门禁：把架构测试、性能 smoke 和热点 SQL explain 纳入合并阻断。
- 物理拆分 advisory/strict gate：`scripts/ddd-physical-split-gate.mjs` 已可输出每个 context 的结构检查、internal contract 检查、owner Flyway 依赖、关键 owner 表迁移 SQL、完整 AI 业务端点覆盖和 blocker 清单；Auth、Message、File、Plugin、Localization、Payment、Job 和 AI 均已有可编译 Spring Boot 启动入口，Auth/Localization/AI 已补齐独立 owner baseline migration，AI 独立 `ai-service` 已承载完整 v2 API 契约、远程 IAM/Platform/File owner gateway 和 OpenAI-compatible provider runtime；advisory/strict gate 当前 `blockers=0`。
- 运行演练：在真实数据库、缓存、调度和对象存储环境下演练 outbox relay、dead letter、replay 和回滚。
- 契约补齐：File 已暴露 `/api/v2/files` adapter 和 readiness/health/metrics 契约，且 outbox backlog/failed/dead-letter 与 processing backlog/failed/dead-letter 指标已读取实时 owner 值，upload response、object storage operation 和 processing task 耗时/结果指标已接入 Micrometer，安全扫描、本地缩略图、本地文本/PDF/Office 抽取和 AI-ready 产物已归档到 File owner；Message 已暴露 `/api/v2/message` 主流程 adapter 和 readiness/health/metrics 契约，dispatchable outbox backlog 已读取实时 owner 值；Plugin 已暴露 `/api/v2/plugins` 主流程 adapter 和 readiness/health/metrics 契约，pending/failed/dead-letter/dispatchable outbox backlog 已读取实时 owner 值；Payment 已暴露 `/api/v2/payment` 主流程 adapter 和 readiness/health/metrics 契约，pending/failed/dead-letter/dispatchable outbox backlog 已读取实时 owner 值；Localization 已暴露 `/api/v2/localization` 主流程 adapter 和 readiness/health/metrics 契约，runtime bundle cache size/hit/miss/hit-ratio 已读取实时 owner 值；Auth 已暴露 `/api/v2/auth` 主流程 adapter 和 readiness/health/metrics 契约，session store hit/miss/save/remove/corrupt payload 与 `auth.bootstrap_cache.alignment_rejects` 已读取实时 owner 值；AI 已暴露 `/api/v2/ai` 主流程 adapter 和 readiness/health/metrics 契约，知识库索引 pending/retryable/failed/dead-letter 与 vector/local-hashing chunk 已读取实时 owner 值；Job 已暴露 `/api/v2/job` tableless adapter readiness/health/metrics 契约，BackendJobClient target、internal token 和 handler declaration 配置值已可观测；Platform 已暴露核心配置/字典/runtime/audit/monitoring v2 adapter 和 readiness/health/metrics 契约，runtime appearance read-model version、配置读取 p95、bootstrap p95、审计失败率、配置缓存命中率已读取实时 owner 值；IAM 已暴露核心租户读写生命周期/用户/用户导出/角色/权限/菜单/部门 v2 adapter 和 readiness/health/metrics 契约，permission snapshot read-model version、p95、cache hit ratio、invalidation lag 已读取实时 owner 值；IAM/Auth/Platform/Message/Plugin/Payment/Localization/AI/Job 剩余看板、runbook 和运行演练仍需补齐。
