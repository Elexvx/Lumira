# DDD Completion Audit

本审计记录当前仓库对“全域 DDD + 高性能架构升级方案”的完成证据。状态只基于当前代码、文档和已执行命令，不把未运行的真实环境演练视为完成。

## 1. 已有代码级证据

| 要求 | 当前证据 | 状态 |
| --- | --- | --- |
| 统一 DDD 基座 | `libs/common-domain` 已提供 AggregateRoot、EntityId、ValueObject、DomainEvent、Repository、Specification、PageQuery、ReadModel、VersionedReadModel | 已证明 |
| 跨上下文 DTO/事件契约 | `libs/lumira-api` 已包含 architecture、event、query、file、system 等跨上下文 DTO | 已证明 |
| 架构护栏 | `DddArchitectureBoundaryTest` 全量运行通过，覆盖 domain 技术依赖、Controller 直连 Mapper、owner 表写入归属、跨模块 Mapper/Entity 引用、独立 owner service 不反向依赖其他 service artifact | 已证明 |
| 十个上下文 readiness/health/metrics | `node scripts/ddd-readiness-gate.mjs` 通过；`scripts/ddd-runtime-readiness-smoke.mjs` 在本地聚合服务验证 30 个 runtime endpoint 并写入 `artifacts/ddd/readiness` | 已证明 |
| v2 API adapter | 各 owner 的 `*V2ControllerTest`、readiness contract test 和 `./mvnw test` 已通过 | 已证明 |
| Auth session bootstrap 热路径 | `AuthSessionStore` 记录 hit/miss/save/remove/corrupt payload；`AuthSessionStoreTest`、`AuthReadinessV2ControllerTest` 通过 | 已证明 |
| Message capped count/owner API | Message 相关服务测试、controller 测试、架构边界测试通过 | 已证明 |
| File async processing | File processing、security scan、OCR、thumbnail、text extraction、AI_PARSE、outbox relay 测试通过 | 已证明 |
| Plugin lifecycle outbox | Plugin outbox、domain model、management app、v2/readiness 测试通过 | 已证明 |
| Localization runtime bundle cache | Runtime bundle cache metrics、v2/readiness/domain tests 通过 | 已证明 |
| Payment webhook idempotency/outbox | Payment webhook、outbox、relay、v2/readiness/domain tests 通过；本地聚合服务 sandbox E2E 验证真实签名、重复 eventId 幂等、nonce replay、坏签名和订单状态流转 | 已证明 |
| AI async knowledge indexing/vector projection | AI knowledge base/vector/metrics/v2/readiness tests 通过 | 已证明 |
| AI 远程 owner gateway | `ai-service` 已新增 `AiOwnerToolGateway` 和 `RemoteAiOwnerToolGateway`，通过 `lumira.ai.owner-integrations.*` 可配置 IAM/Platform/File owner baseUrl 和 `X-Job-Token`；权限快照、平台配置、内置菜单、文件搜索工具走 owner internal API，未配置或失败时返回明确 degraded fallback；`RemoteAiOwnerToolGatewayTest`、`AiCommandServiceTest`、`AiReadinessV2ControllerTest` 通过 | 已证明 |
| AI provider runtime | `ai-service` 已新增 `AiProviderRuntime` 和 `DefaultAiProviderRuntime`，默认使用 `lumira-local`/`local-hashing-v1`，配置 `lumira.ai.provider.openai-compatible.*` 后可走 OpenAI-compatible `/chat/completions` 与 `/embeddings`；远端失败自动回落本地 provider，chunk 写入 `embedding_model`、`embedding_dim`、`embedding_vector_json`、`vector_indexed_at`；`DefaultAiProviderRuntimeTest`、`AiCommandServiceTest`、`AiReadinessV2ControllerTest` 通过 | 已证明 |
| Job tableless adapter | Job readiness、handler adapter、domain model tests 通过；readiness gate 校验 Job 无 owner 表；`XxlJobExecutorConfigTest` 证明 `XXL_JOB_EXECUTOR_ENABLED=false` 可禁用外部调度注册且默认仍启用 executor；本地聚合服务以 `XXL_JOB_EXECUTOR_ENABLED=false` 启动后未出现 XXL-JOB 注册/remoting server 日志，`scripts/ddd-job-e2e-smoke.mjs` 仍 9/9 通过 | 已证明 |
| 性能脚本 | `ddd-performance-smoke` 支持 baseline/actual、场景文件、POST body、multipart upload、状态码分布；本地聚合服务 runtime smoke 写入 `artifacts/ddd/performance/runtime-actual.json`，`failed=0` | 已证明 |
| 认证成功态 v2 热路径性能 | `scripts/ddd-authenticated-performance-smoke.mjs` 通过 v2 RSA 登录获取真实 token；本地聚合服务验证 current-user、IAM、Message、File、Plugin、Localization、Payment 全部 2xx 成功 envelope，611 个样本 `failed=0`，p95=88ms；`/api/v2/files/upload` 一次性回包 200/85.88ms，`/api/v2/auth/session/keepalive` 一次性 200/64ms，写入 `artifacts/ddd/performance/authenticated-runtime-actual.json` | 已证明 |
| EXPLAIN gate | `ddd-collect-explain` 支持 `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_USER`/`MYSQL_PASSWORD`；本地 MySQL 8.4 采集 6 个 `EXPLAIN FORMAT=JSON` 到 `tmp/ddd-explain`，`ddd-explain-gate` 通过 | 已证明 |
| 共享 outbox owner relay 索引 | Message/File relay 热路径使用 `idx_platform_event_outbox_owner_queue (source_type, created_at, id, dispatch_status, next_retry_at, deleted)`；本地 MySQL explain 显示 Message/File owner relay 均 `access_type=ref`、`key=idx_platform_event_outbox_owner_queue`、`using_index=true` | 已证明 |
| Outbox/job 内部 E2E | `scripts/ddd-job-e2e-smoke.mjs` 验证无 `X-Job-Token` 被拒绝，并触发 platform/message/file/payment/plugin outbox relay、message/online heartbeat、file processing、AI knowledge index；本地聚合服务 9 个内部任务入口全部 200；开启 DB 诊断后确认 `source_type != MESSAGE` 的 payload 反序列化失败行数 before=12、after=12、delta=0，写入 `artifacts/ddd/jobs/job-e2e-smoke.json`；脚本已写入 `productionEquivalence`，readiness summary 可对旧 artifact 推导 HTTPS/localhost 缺口 | 本地已证明；strict 仍需生产等价证据 |
| 共享 outbox owner 污染修复 | `scripts/ddd-outbox-ownership-repair.mjs` dry-run 命中 12 条 `source_type=FILE` 的历史 Message payload 反序列化失败；apply 后命中行清零；逐条调用 `/file/internal/jobs/outbox/{id}/replay` 后 `fileDelivered=12`、`fileFailed=0`、`fileRecorded=0`、`crossOwnerPayloadFailures=0`，写入 `artifacts/ddd/outbox/outbox-ownership-repair.json` | 已证明 |
| Outbox replay/dead-letter 状态机 | `scripts/ddd-outbox-replay-dead-letter-smoke.mjs` 运行 System、Message、File、Plugin、Payment owner outbox 聚焦测试，覆盖 claim、delivered、失败重试、`retry_count >= 8` 进入 `DEAD_LETTER`、manual replay 重置后重新投递、relay disabled 下显式 replay；28 个测试 `failures=0/errors=0`，写入 `artifacts/ddd/outbox/outbox-replay-dead-letter-test-evidence.json` | 已证明 |
| File processing 端到端耗时 | `scripts/ddd-file-processing-e2e-smoke.mjs` 本地聚合服务通过 v2 登录上传 `.txt` 文件，上传回包 109.64ms；调用 `/file/internal/jobs/processing/run`，job 52.97ms；目标 `fileId=3` 的 `SECURITY_SCAN`、`TEXT_EXTRACT`、`AI_PARSE` 均 `SUCCEEDED`，生成 `SECURITY_SCAN_RESULT`、`TEXT_CONTENT`、`AI_PARSE_READY`，写入 `artifacts/ddd/file/file-processing-e2e.json`；脚本已写入 `productionEquivalence`，strict 发布要求 HTTPS 且非 localhost baseURL | 本地已证明；strict 仍需生产等价证据 |
| Payment webhook 端到端状态流转 | `scripts/ddd-payment-webhook-e2e-smoke.mjs` 本地聚合服务通过 v2 登录配置 Stripe sandbox provider，创建订单 `DDD-PAY-1781377399313-eb3af4`；有效签名 webhook 21.63ms 并将订单推进到 `PAID`，重复 eventId 7.05ms 幂等返回已处理，同 nonce replay 10.08ms 被拒绝，坏签名 13.19ms 被拒绝，写入 `artifacts/ddd/payment/payment-webhook-e2e.json`；脚本已写入 `productionEquivalence`，strict 发布要求 HTTPS 且非 localhost webhook baseURL | 本地已证明；strict 仍需生产等价证据 |
| AI runtime drill 脚本 | `scripts/ddd-ai-runtime-drill.mjs` 已可采集 `/api/v2/ai/readiness`、`/api/v2/ai/health`、`/api/v2/ai/metrics`，校验 AI v2 chat/search/tool 契约、`ai.provider-runtime`、`ai.remote-owner-gateway`、`ai.provider.remote_configured`、`ai.owner_gateway.configured`；设置 `DDD_AI_EXPECT_PROVIDER_REMOTE=true` 和 `DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true` 后可作为准生产/生产等价远程 provider 与 owner gateway 验收门禁；失败产物会输出 `failureDetails` 与 `summary.failureCategories`，用于区分 endpoint、契约、health、metrics、provider runtime、owner gateway 和 provenance 缺口 | 已证明脚本，真实远程证据待采集 |
| 前端 smoke 证据归档 | `scripts/ddd-frontend-smoke-evidence.mjs` 已可把 Playwright JSON report 转换为 `artifacts/ddd/frontend/frontend-smoke.json`，校验 dashboard、download center、AI assistant、用户、角色、安全设置、支付设置、文件、插件、国际化、session refresh、消息中心和登出 smoke 均通过；`DDD_FRONTEND_EXPECT_DEPLOYED=true` 可阻断 localhost artifact | 已证明脚本，部署环境证据待采集 |
| Frontend static evidence | `scripts/ddd-frontend-static-evidence.mjs` 已可执行并归档 `lint`、`typecheck`、`test`，当前本地 artifact `artifacts/ddd/frontend/frontend-static-evidence.json` 三项均 PASS | 已证明 |
| Frontend build evidence | `scripts/ddd-frontend-build-evidence.mjs` 已可执行 `corepack pnpm --dir frontend build` 并归档 `frontend/dist` 产物，当前本地 artifact `artifacts/ddd/frontend/frontend-build-evidence.json` 记录 182 个构建文件、入口 HTML 和静态资源 checksum | 已证明 |
| Backend test evidence | `scripts/ddd-backend-test-evidence.mjs` 已可读取 surefire XML，校验关键架构边界、owner v2/readiness、AI provider/owner gateway、Payment webhook、File processing 和 Job adapter 测试存在且 failures/errors 为 0；当前本地 artifact `artifacts/ddd/tests/backend-test-evidence.json` 汇总 143 个 suite、392 个测试 | 已证明 |
| Backend build evidence | `scripts/ddd-backend-build-evidence.mjs` 已可执行 `./mvnw -DskipTests package` 并校验 `lumira-server`、Auth、Message、File、Plugin、Localization、Payment、AI、Job 启动入口和各后端模块 jar；当前本地 artifact `artifacts/ddd/build/backend-build-evidence.json` 汇总 10 个 jar | 已证明 |
| Docker build evidence | `scripts/ddd-docker-build-evidence.mjs` 已可构建并 inspect `lumira-server` 与 `frontend` 两个部署镜像，归档 Dockerfile checksum、image id、tag 和 size；Docker CLI/daemon 不可用时会为每个 skipped image 写入具体 `skipReason` 和 blockers；release gate strict 会要求镜像 artifact 存在且两个镜像均 PASS | 已证明脚本，真实镜像构建证据待采集 |
| Performance baseline promotion | `scripts/ddd-promote-performance-baseline.mjs` 已可将通过验收的生产等价 `authenticated-runtime-actual.json` 晋级为 `authenticated-runtime-baseline.json`，并拒绝 localhost actual、失败样本、缺失 p95/上传耗时、缺失 actual provenance、actual 环境和晋级环境不一致、或缺失验收人/环境名的基线来源 | 已证明脚本，真实生产等价 baseline 待采集 |
| Runtime evidence provenance | `scripts/ddd-runtime-readiness-smoke.mjs`、`scripts/ddd-authenticated-performance-smoke.mjs`、`scripts/ddd-file-processing-e2e-smoke.mjs`、`scripts/ddd-payment-webhook-e2e-smoke.mjs`、`scripts/ddd-job-e2e-smoke.mjs`、`scripts/ddd-ai-runtime-drill.mjs`、`scripts/ddd-frontend-smoke-evidence.mjs` 已统一写入 `sourceEnvironment`、`releaseCandidate`、`evidenceOperator`；release gate strict 会要求运行时证据具备环境、版本和执行人来源 | 已证明脚本，真实生产等价 provenance 待采集 |
| Release artifact freshness | `scripts/ddd-release-evidence-gate.mjs` strict 模式已要求关键 artifact 的 `generatedAt`、`checkedAt`、`finishedAt` 或 `startedAt` 在 `DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS` 窗口内，EXPLAIN JSON 使用文件 mtime 校验；authenticated performance baseline 作为已验收历史基线不参与 freshness | 已证明脚本，真实发布流水线需重新生成新鲜 artifact |
| Release evidence manifest | `scripts/ddd-release-evidence-manifest.mjs` 已可生成 `artifacts/ddd/release/evidence-manifest.json`，记录关键 artifact 的 SHA-256、大小和时间戳，并验证 EXPLAIN JSON 存在；release gate strict 要求 manifest 存在且 PASS | 已证明脚本，真实发布流水线需生成完整 manifest |
| Release evidence orchestrator | `scripts/ddd-release-evidence-orchestrator.mjs` 已可在 plan mode 输出证据执行计划，并在 `--run --strict` 下按后端测试/构建、Docker、前端、迁移、配置、runtime smoke、回滚、EXPLAIN、manifest、release gate、readiness summary 的顺序执行并归档每步退出码、耗时和输出尾部 | 已证明脚本，真实发布流水线执行时生成完整报告 |
| Release readiness summary | `scripts/ddd-release-readiness-summary.mjs` 已可读取 strict release gate、manifest、Docker、Frontend smoke、AI runtime artifact，输出 JSON/Markdown 行动摘要，并按 Docker、性能 baseline、回滚演练、配置、前端 smoke、AI runtime、迁移、运行时 provenance 等类别归属 owner 与建议命令；`diagnostics.readinessSummary.contractIssues` 会自检摘要状态、blocker 计数和行动分组一致性 | 已证明脚本，用于排障分派，不作为放行证据 |
| Rollback drill validator | `scripts/ddd-rollback-drill-evidence.mjs` 已可独立校验 rollback drill artifact 的十个上下文覆盖、PASS/DEFERRED 必填字段和时间格式，避免回滚证据只在最终 release gate 才暴露问题 | 已证明脚本，真实 rollback drill artifact 待采集 |
| Migration evidence | `scripts/ddd-migration-evidence.mjs` 已可扫描 owner Flyway migration location，校验必需目录、SQL 非空和同一 location 无重复版本；本地 artifact `artifacts/ddd/migration/migration-evidence.json` 汇总 34 个 migration 文件并通过静态检查，并输出 `runtimeDiagnostics` 分派 fresh DB、old DB upgrade、环境、版本、执行人和完成时间缺口；strict 仍要求准生产/生产等价新库迁移和旧库升级运行证据 | 已证明脚本，strict 真实环境证据待采集 |
| Release config evidence | `scripts/ddd-release-config-evidence.mjs` 已可读取 `DDD_RELEASE_ENV_FILE` 和当前环境，校验准生产/生产等价后端 baseURL、前端 baseURL、DB、Redis、JWT/FIELD secret、owner service URL、Job/XXL token、AI provider、AI owner gateway、Payment public URL，并只输出脱敏 URL、secret 长度和 hash 前缀；artifact 会输出 `blockerDetails`、`blockersByGroup`、`blockersByOwner`，strict 发布要求非 localhost 且 AI provider/owner gateway 明确启用 | 已证明脚本，strict 真实环境证据待采集 |
| Release evidence gate | `scripts/ddd-release-evidence-gate.mjs` 已可汇总校验 backend build evidence、backend test evidence、Docker image evidence、frontend build/static evidence、migration evidence、release config evidence、release evidence manifest、readiness、authenticated performance、性能 baseline 回归、File processing、Payment webhook、Outbox/job、AI runtime drill、physical split readiness、frontend smoke、rollback drill 和 EXPLAIN artifact；默认 advisory 输出缺口，`DDD_RELEASE_EVIDENCE_STRICT=true` 会把缺失后端构建/测试证据、缺失 Docker 镜像证据、缺失前端构建/测试证据、缺失 Flyway 新库/旧库运行验证、缺失生产等价配置和运行证据、缺失 checksum manifest、运行时证据缺少 provenance、artifact 超过 freshness 窗口、缺失 authenticated performance baseline、baseline 为 localhost 或缺少验收元数据、p95/上传耗时回退超过阈值、缺失回滚演练证据和 localhost-only 运行时证据作为发布 blocker；共享 release gate contract 会复核 `summary`、`checks[]`、`blockers[]`、`warnings[]` 自洽性，避免手工汇总掩盖 blocker | 已证明脚本，strict 真实环境证据待采集 |
| 独立服务启动入口 | Auth、Message、File、Plugin、Localization、Payment、Job、AI 均已新增可编译 Spring Boot application entrypoint；Auth/Localization/AI 已补齐独立 owner baseline migration；`services/ai-service` 独立 Maven module 已承载 AI readiness、读模型、chat、knowledge upload/reindex/search 和 tool execute/propose/confirm v2 契约，并提供独立 Flyway baseline `db/migration/ai/V1__baseline_ai_domain.sql` 创建 AI owner 表；AI tool 远程 owner gateway 与 provider runtime 已落地并可观测配置/降级状态；`lumira-server` 聚合启动通过 component-scan exclude 排除聚合依赖内的独立入口，`ai-service` 不作为聚合依赖引入，避免 `/api/v2/ai` 观测端点重复装配；`./mvnw -pl services/ai-service -am -Dtest=AiReadinessV2ControllerTest,AiV2ControllerTest,AiReadQueryServiceTest,AiCommandServiceTest,RemoteAiOwnerToolGatewayTest,DefaultAiProviderRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`、`./mvnw -pl services/auth-service,services/localization-service,services/ai-service -am -DskipTests compile` 和 `./mvnw -pl services/lumira-server -am -DskipTests compile` 通过 | 已证明 |
| 物理拆分 advisory/strict gate | `scripts/ddd-physical-split-gate.mjs` 校验 owner manifest、readiness/health/metrics、owner internal contract、跨 service module POM 依赖、owner Flyway 依赖、关键 owner 表迁移 SQL、拆分文档、运行手册覆盖和 AI required business endpoint 覆盖；本地运行 advisory 与 `DDD_SPLIT_STRICT=true` 均通过，`blockers=0`，输出 `artifacts/ddd/split/physical-split-readiness.json` | 已证明 |
| CI 门禁 | `.github/workflows/ci.yml` 已接入架构测试、owner contract tests、readiness gate、physical split advisory gate、performance script syntax、explain gate | 已证明 |
| 前端静态与单元回归 | `corepack pnpm --dir frontend lint`、`typecheck`、`test` 通过，lint 仅有 warning | 已证明 |
| 前端浏览器主流程 smoke | `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8010 ... corepack pnpm --dir frontend test:e2e:smoke` 通过，16/16 覆盖登录、强制改密、匿名跳转、首页、用户/角色、消息中心、文件、插件、国际化、支付设置、AI assistant、session refresh、登出 | 已证明 |
| 后端全量测试 | `./mvnw test` 通过，合计后端模块测试全部成功，system-service 有 4 个显式 skipped integration tests | 已证明 |

## 2. 已执行验证命令

```bash
./mvnw test
./mvnw -pl services/lumira-server -am -DskipTests compile
./mvnw -pl services/lumira-server,services/system-service -am -Dtest=SecurityPermitPathsTest -Dsurefire.failIfNoSpecifiedTests=false test package
./mvnw -pl services/job-executor -am -Dtest='com.lumira.job.XxlJobExecutorConfigTest,com.lumira.job.JobReadinessV2ControllerTest,com.lumira.job.FileOutboxRelayJobHandlerTest,com.lumira.job.FileProcessingTaskJobHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test
corepack pnpm --dir frontend lint
corepack pnpm --dir frontend typecheck
corepack pnpm --dir frontend test
node scripts/ddd-readiness-gate.mjs
node --check scripts/ddd-runtime-readiness-smoke.mjs
DDD_PERF_BASELINE_FILE=docs/30-ddd-performance-baseline.example.json \
DDD_PERF_ACTUAL_FILE=docs/30-ddd-performance-baseline.example.json \
node scripts/ddd-performance-smoke.mjs
DDD_SMOKE_SCENARIOS_FILE=docs/32-ddd-performance-scenarios.example.json \
DDD_PERF_BASELINE_FILE=docs/30-ddd-performance-baseline.example.json \
DDD_PERF_ACTUAL_FILE=docs/30-ddd-performance-baseline.example.json \
node scripts/ddd-performance-smoke.mjs
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_RUNTIME_READINESS_DIR=artifacts/ddd/readiness \
node scripts/ddd-runtime-readiness-smoke.mjs
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_SMOKE_SCENARIOS_FILE=docs/32-ddd-performance-scenarios.example.json \
node scripts/ddd-performance-smoke.mjs > artifacts/ddd/performance/runtime-actual.json
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_AUTH_PERF_DURATION_MS=8000 \
DDD_AUTH_PERF_CONCURRENCY=6 \
node scripts/ddd-authenticated-performance-smoke.mjs
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-collect-explain.mjs
node scripts/ddd-explain-gate.mjs
node --check scripts/ddd-physical-split-gate.mjs
node scripts/ddd-physical-split-gate.mjs
DDD_SPLIT_STRICT=true node scripts/ddd-physical-split-gate.mjs
./mvnw -pl services/auth-service,services/localization-service,services/ai-service -am -DskipTests compile
./mvnw -pl services/ai-service -am -Dtest=AiReadinessV2ControllerTest,AiV2ControllerTest,AiReadQueryServiceTest,AiCommandServiceTest,RemoteAiOwnerToolGatewayTest,DefaultAiProviderRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl services/auth-service,services/message-service,services/file-service,services/plugin-service,services/localization-service,services/payment-service,services/job-executor -am -DskipTests compile
./mvnw -pl services/system-service -am -DskipTests compile
./mvnw -pl services/message-service,services/file-service -am \
-Dtest='com.lumira.message.app.PlatformEventOutboxServiceTest,com.lumira.file.event.PlatformEventOutboxRelayServiceTest,com.lumira.file.event.FileOutboxMetricsServiceTest' \
-Dsurefire.failIfNoSpecifiedTests=false test
SAAS_JOB_INTERNAL_TOKEN=lumira-runtime-readiness-job-token \
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
node scripts/ddd-job-e2e-smoke.mjs
XXL_JOB_EXECUTOR_ENABLED=false \
SAAS_JOB_INTERNAL_TOKEN=lumira-runtime-readiness-job-token \
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
node scripts/ddd-job-e2e-smoke.mjs
SAAS_JOB_INTERNAL_TOKEN=lumira-runtime-readiness-job-token \
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_JOB_SMOKE_DB_CHECK=true \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-job-e2e-smoke.mjs
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-outbox-ownership-repair.mjs
DDD_OUTBOX_REPAIR_APPLY=true \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-outbox-ownership-repair.mjs
node scripts/ddd-outbox-replay-dead-letter-smoke.mjs
node --check scripts/ddd-ai-runtime-drill.mjs
node scripts/ddd-backend-build-evidence.mjs
node scripts/ddd-backend-test-evidence.mjs
node --check scripts/ddd-docker-build-evidence.mjs
node --check scripts/ddd-promote-performance-baseline.mjs
node scripts/ddd-migration-evidence.mjs
node scripts/ddd-frontend-build-evidence.mjs
node scripts/ddd-frontend-static-evidence.mjs
node --check scripts/ddd-release-config-evidence.mjs
node --check scripts/ddd-release-evidence-manifest.mjs
node --check scripts/ddd-release-evidence-orchestrator.mjs
node --check scripts/ddd-release-readiness-summary.mjs
node --check scripts/ddd-rollback-drill-evidence.mjs
node --check scripts/ddd-frontend-smoke-evidence.mjs
node --check scripts/ddd-release-evidence-gate.mjs
node scripts/ddd-release-evidence-gate.mjs
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
SAAS_JOB_INTERNAL_TOKEN=lumira-runtime-readiness-job-token \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-file-processing-e2e-smoke.mjs
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-payment-webhook-e2e-smoke.mjs
PLAYWRIGHT_BASE_URL=http://127.0.0.1:8010 \
PLAYWRIGHT_ADMIN_USER=admin \
PLAYWRIGHT_ADMIN_PASSWORD=123456 \
PLAYWRIGHT_NEW_PASSWORD=E2eAdmin123! \
corepack pnpm --dir frontend test:e2e:smoke
git diff --check
```

## 3. 仍需真实环境证明

| 要求 | 缺少的权威证据 | 建议命令或产物 |
| --- | --- | --- |
| 可部署 Docker 镜像 | 已有 Maven/前端构建证据和 CI docker job；仍缺归档到 release gate 的镜像构建/inspect artifact | `node scripts/ddd-docker-build-evidence.mjs` |
| 生产或准生产登录/bootstrap p95 不回退 | 已有本地 runtime actual；仍缺生产等价环境 actual 与带验收元数据的历史基线对比 | `LUMIRA_BASE_URL=... DDD_AUTH_PERF_ENVIRONMENT=staging-production-equivalent node scripts/ddd-authenticated-performance-smoke.mjs`，再用 `DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=... DDD_AUTH_PERF_BASELINE_ENVIRONMENT=... node scripts/ddd-promote-performance-baseline.mjs` 晋级上一轮通过验收的 baseline |
| 运行时证据来源可追溯 | 已有脚本字段和 strict 校验；仍缺准生产/生产流水线设置 `DDD_EVIDENCE_ENVIRONMENT`、`DDD_RELEASE_CANDIDATE`、`DDD_EVIDENCE_OPERATOR` 后重新生成 runtime artifact | 在所有 runtime smoke 前导出 `DDD_EVIDENCE_ENVIRONMENT=... DDD_RELEASE_CANDIDATE=... DDD_EVIDENCE_OPERATOR=...` |
| 发布证据新鲜度 | 已有 strict freshness 校验；仍缺发布流水线在 24 小时窗口内重新生成全部关键 artifact | `DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS=24 DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-gate.mjs` |
| 发布证据 checksum manifest | 已有 manifest 脚本和 strict 校验；仍缺所有真实 artifact 到位后生成 PASS manifest | `node scripts/ddd-release-evidence-manifest.mjs` |
| 发布证据编排报告 | 已有 orchestrator 脚本；仍需在真实发布流水线运行 `--run --strict` 并归档报告 | `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-orchestrator.mjs --run --strict` |
| 发布 blocker 分派 | 已有 readiness summary 脚本；仍需在真实发布流水线失败时将 `readiness-summary.md` 随 artifact 一起归档 | `node scripts/ddd-release-readiness-summary.mjs` |
| 回滚演练证据 | 已有模板和独立 validator；仍缺真实 IAM/Auth/Platform/Message/File/Plugin/Localization/Payment/AI/Job 演练 artifact | `cp docs/35-ddd-rollback-drill-template.json artifacts/ddd/rollback/rollback-drill.json` 后填入真实证据，再运行 `node scripts/ddd-rollback-drill-evidence.mjs` |
| 支付 webhook/provider p95、签名失败率、重复拦截 | 已有本地 sandbox E2E webhook 请求、响应、订单状态轨迹和落表证据；仍缺生产等价环境 provider webhook 指标、日志与历史基线对比 | 保存 provider 请求、响应、Payment metrics、订单状态轨迹、历史基线对比 |
| 准生产文件异步处理耗时 | 已有本地 `.txt` 上传、processing job、任务状态和 artifact 端到端证据；仍缺准生产对象存储/本地存储配置下的处理耗时和 metrics | 场景文件 multipart upload，保存 File metrics 和 artifact 记录 |
| 生产数据量 MySQL `EXPLAIN FORMAT=JSON` | 已有本地 MySQL 8.4 空库/种子库 explain；仍缺生产量级执行计划 | `MYSQL_HOST=... MYSQL_PORT=... MYSQL_USER=... MYSQL_PASSWORD=... DDD_EXPLAIN_DATABASE=lumira node scripts/ddd-collect-explain.mjs && node scripts/ddd-explain-gate.mjs` |
| Flyway 新库启动与旧库升级 | 已有 migration 静态扫描和重复版本检查；仍缺准生产/生产等价新库迁移日志、旧库升级日志和 `flyway_schema_history` 证据 | `DDD_MIGRATION_FRESH_DB_VALIDATED=true DDD_MIGRATION_UPGRADE_DB_VALIDATED=true DDD_MIGRATION_RUNTIME_EVIDENCE=... node scripts/ddd-migration-evidence.mjs` |
| 生产等价配置矩阵 | 已有配置证据脚本；仍缺由准生产/生产流水线生成的 env-file-backed artifact，证明 DB/Redis/owner URL/Job/AI/Payment/前后端 baseURL 均为非 localhost 且关键远程开关开启 | `DDD_RELEASE_ENV_FILE=.env.release DDD_RELEASE_CONFIG_STRICT=true node scripts/ddd-release-config-evidence.mjs` |
| 准生产跨进程 outbox replay/dead-letter | 已有本地状态机 smoke 和 job/internal relay E2E；仍缺准生产环境中由真实 dispatcher 失败触发的 dead-letter/replay 证据 | 按 `docs/31-ddd-operational-runbook.md` 第 3 节构造失败事件并逐 owner 演练 replay |
| 部署环境前端主流程 smoke | 已有本地浏览器 smoke；仍缺部署环境登录、首页、用户角色、消息、上传、插件、国际化、支付回调模拟、AI 会话录像/网络日志 | 保存 smoke 录像、截图、控制台和网络请求日志 |
| 物理拆服务就绪 | 已有 advisory/strict gate 产物证明结构条件无失败，Auth、Message、File、Plugin、Localization、Payment、Job、AI 均已有可编译启动入口，且 AI 独立 `ai-service` 已承载完整 v2 API 契约、代码级远程 IAM/Platform/File owner gateway、OpenAI-compatible provider runtime 和可执行 AI runtime drill；仍缺生产环境服务间超时/降级实测、真实 provider-native LLM/vector 调用和回滚演练证据 | `DDD_AI_EXPECT_PROVIDER_REMOTE=true DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true node scripts/ddd-ai-runtime-drill.mjs`，并按 `docs/29-ddd-physical-split-readiness.md` 和 `docs/31-ddd-operational-runbook.md` 逐 owner 执行 |
| 发布证据 strict gate | 本地 advisory `node scripts/ddd-release-evidence-gate.mjs` 已输出当前 artifact 缺口；strict 模式必须在生产等价环境完成 authenticated performance baseline 对比、Flyway 新库/旧库运行验证、AI runtime drill、生产量级 EXPLAIN、部署环境 frontend smoke、十个上下文 rollback drill 等证据后执行 | `DDD_RELEASE_EVIDENCE_STRICT=true node scripts/ddd-release-evidence-gate.mjs` |
| 运行时 owner 观测端点 | 本地聚合服务已生成 30 个 readiness/health/metrics JSON artifact；仍缺部署环境 artifact | `LUMIRA_BASE_URL=... DDD_RUNTIME_READINESS_DIR=artifacts/ddd/readiness node scripts/ddd-runtime-readiness-smoke.mjs` |

## 4. 当前结论

当前仓库已经具备 DDD 分层、owner 边界、v2 adapter、readiness/health/metrics、核心领域测试、全量后端测试、前端静态/单元回归、性能/EXPLAIN/readiness gate 和运行手册。

最终完成还需要在生产等价环境中补齐性能 actual 与历史基线对比、生产数据量 MySQL explain、provider webhook 指标和真实回调日志、准生产文件处理任务端到端耗时、准生产真实 dispatcher 失败触发的 outbox replay/dead-letter、部署环境前端浏览器 smoke 和回滚演练证据。未取得这些运行证据前，不应宣称物理拆分完全完成。
