# DDD Completion Audit

本审计记录当前仓库对“全�?DDD + 高性能架构升级方案”的完成证据。状态只基于当前代码、文档和已执行命令，不把未运行的真实环境演练视为完成�?

## 1. 已有代码级证�?

| 要求 | 当前证据 | 状�?|
| --- | --- | --- |
| 统一 DDD 基座 | `libs/lumira-common-domain` 已提�?AggregateRoot、EntityId、ValueObject、DomainEvent、Repository、Specification、PageQuery、ReadModel、VersionedReadModel | 已证�?|
| 跨上下文 DTO/事件契约 | `libs/lumira-common-api` 已包�?architecture、event、query、file、system 等跨上下�?DTO | 已证�?|
| 架构护栏 | `DddArchitectureBoundaryTest` 全量运行通过，覆�?domain 技术依赖、Controller 直连 Mapper、owner 表写入归属、跨模块 Mapper/Entity 引用、独�?owner service 不反向依赖其�?service artifact | 已证�?|
| 十个上下�?readiness/health/metrics | `node bin/ddd-readiness-gate.mjs` 通过；`bin/ddd-runtime-readiness-smoke.mjs` 在本地聚合服务验�?30 �?runtime endpoint 并写�?`artifacts/ddd/readiness` | 已证�?|
| v2 API adapter | �?owner �?`*V2ControllerTest`、readiness contract test �?`./mvnw test` 已通过 | 已证�?|
| Auth session bootstrap 热路�?| `AuthSessionStore` 记录 hit/miss/save/remove/corrupt payload；`AuthSessionStoreTest`、`AuthReadinessV2ControllerTest` 通过 | 已证�?|
| Message capped count/owner API | Message 相关服务测试、controller 测试、架构边界测试通过 | 已证�?|
| File async processing | File processing、security scan、OCR、thumbnail、text extraction、AI_PARSE、outbox relay 测试通过 | 已证�?|
| Plugin lifecycle outbox | Plugin outbox、domain model、management app、v2/readiness 测试通过 | 已证�?|
| Localization runtime bundle cache | Runtime bundle cache metrics、v2/readiness/domain tests 通过 | 已证�?|
| Payment webhook idempotency/outbox | Payment webhook、outbox、relay、v2/readiness/domain tests 通过；本地聚合服�?sandbox E2E 验证真实签名、重�?eventId 幂等、nonce replay、坏签名和订单状态流�?| 已证�?|
| AI async knowledge indexing/vector projection | AI knowledge base/vector/metrics/v2/readiness tests 通过 | 已证�?|
| AI 远程 owner gateway | `ai-service` 已新�?`AiOwnerToolGateway` �?`RemoteAiOwnerToolGateway`，通过 `lumira.ai.owner-integrations.*` 可配�?IAM/Platform/File owner baseUrl �?`X-Job-Token`；权限快照、平台配置、内置菜单、文件搜索工具走 owner internal API，未配置或失败时返回明确 degraded fallback；`RemoteAiOwnerToolGatewayTest`、`AiCommandServiceTest`、`AiReadinessV2ControllerTest` 通过 | 已证�?|
| AI provider runtime | `ai-service` 已新�?`AiProviderRuntime` �?`DefaultAiProviderRuntime`，默认使�?`lumira-local`/`local-hashing-v1`，配�?`lumira.ai.provider.openai-compatible.*` 后可�?OpenAI-compatible `/chat/completions` �?`/embeddings`；远端失败自动回落本�?provider，chunk 写入 `embedding_model`、`embedding_dim`、`embedding_vector_json`、`vector_indexed_at`；`DefaultAiProviderRuntimeTest`、`AiCommandServiceTest`、`AiReadinessV2ControllerTest` 通过 | 已证�?|
| Job tableless adapter | Job readiness、handler adapter、domain model tests 通过；readiness gate 校验 Job �?owner 表；`XxlJobExecutorConfigTest` 证明 `XXL_JOB_EXECUTOR_ENABLED=false` 可禁用外部调度注册且默认仍启�?executor；本地聚合服务以 `XXL_JOB_EXECUTOR_ENABLED=false` 启动后未出现 XXL-JOB 注册/remoting server 日志，`bin/ddd-job-e2e-smoke.mjs` �?9/9 通过 | 已证�?|
| 性能脚本 | `ddd-performance-smoke` 支持 baseline/actual、场景文件、POST body、multipart upload、状态码分布；本地聚合服�?runtime smoke 写入 `artifacts/ddd/performance/runtime-actual.json`，`failed=0` | 已证�?|
| 认证成功�?v2 热路径性能 | `bin/ddd-authenticated-performance-smoke.mjs` 通过 v2 RSA 登录获取真实 token；本地聚合服务验�?current-user、IAM、Message、File、Plugin、Localization、Payment 全部 2xx 成功 envelope�?11 个样�?`failed=0`，p95=88ms；`/api/v2/files/upload` 一次性回�?200/85.88ms，`/api/v2/auth/session/keepalive` 一次�?200/64ms，写�?`artifacts/ddd/performance/authenticated-runtime-actual.json` | 已证�?|
| EXPLAIN gate | `ddd-collect-explain` 支持 `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_USER`/`MYSQL_PASSWORD`；本�?MySQL 8.4 采集 6 �?`EXPLAIN FORMAT=JSON` �?`tmp/ddd-explain`，`ddd-explain-gate` 通过 | 已证�?|
| 共享 outbox owner relay 索引 | Message/File relay 热路径使�?`idx_platform_event_outbox_owner_queue (source_type, created_at, id, dispatch_status, next_retry_at, deleted)`；本�?MySQL explain 显示 Message/File owner relay �?`access_type=ref`、`key=idx_platform_event_outbox_owner_queue`、`using_index=true` | 已证�?|
| Outbox/job 内部 E2E | `bin/ddd-job-e2e-smoke.mjs` 验证�?`X-Job-Token` 被拒绝，并触�?platform/message/file/payment/plugin outbox relay、message/online heartbeat、file processing、AI knowledge index；本地聚合服�?9 个内部任务入口全�?200；开�?DB 诊断后确�?`source_type != MESSAGE` �?payload 反序列化失败行数 before=12、after=12、delta=0，写�?`artifacts/ddd/jobs/job-e2e-smoke.json`；脚本已写入 `productionEquivalence`，readiness summary 可对�?artifact 推导 HTTPS/localhost 缺口 | 本地已证明；strict 仍需生产等价证据 |
| 共享 outbox owner 污染修复 | `bin/ddd-outbox-ownership-repair.mjs` dry-run 命中 12 �?`source_type=FILE` 的历�?Message payload 反序列化失败；apply 后命中行清零；逐条调用 `/file/internal/jobs/outbox/{id}/replay` �?`fileDelivered=12`、`fileFailed=0`、`fileRecorded=0`、`crossOwnerPayloadFailures=0`，写�?`artifacts/ddd/outbox/outbox-ownership-repair.json` | 已证�?|
| Outbox replay/dead-letter 状态机 | `bin/ddd-outbox-replay-dead-letter-smoke.mjs` 运行 System、Message、File、Plugin、Payment owner outbox 聚焦测试，覆�?claim、delivered、失败重试、`retry_count >= 8` 进入 `DEAD_LETTER`、manual replay 重置后重新投递、relay disabled 下显�?replay�?8 个测�?`failures=0/errors=0`，写�?`artifacts/ddd/outbox/outbox-replay-dead-letter-test-evidence.json` | 已证�?|
| File processing 端到端耗时 | `bin/ddd-file-processing-e2e-smoke.mjs` 本地聚合服务通过 v2 登录上传 `.txt` 文件，上传回�?109.64ms；调�?`/file/internal/jobs/processing/run`，job 52.97ms；目�?`fileId=3` �?`SECURITY_SCAN`、`TEXT_EXTRACT`、`AI_PARSE` �?`SUCCEEDED`，生�?`SECURITY_SCAN_RESULT`、`TEXT_CONTENT`、`AI_PARSE_READY`，写�?`artifacts/ddd/file/file-processing-e2e.json`；脚本已写入 `productionEquivalence`，strict 发布要求 HTTPS 且非 localhost baseURL | 本地已证明；strict 仍需生产等价证据 |
| Payment webhook 端到端状态流�?| `bin/ddd-payment-webhook-e2e-smoke.mjs` 本地聚合服务通过 v2 登录配置 Stripe sandbox provider，创建订�?`DDD-PAY-1781377399313-eb3af4`；有效签�?webhook 21.63ms 并将订单推进�?`PAID`，重�?eventId 7.05ms 幂等返回已处理，�?nonce replay 10.08ms 被拒绝，坏签�?13.19ms 被拒绝，写入 `artifacts/ddd/payment/payment-webhook-e2e.json`；脚本已写入 `productionEquivalence`，strict 发布要求 HTTPS 且非 localhost webhook baseURL | 本地已证明；strict 仍需生产等价证据 |
| AI runtime drill 脚本 | `bin/ddd-ai-runtime-drill.mjs` 已可采集 `/api/v2/ai/readiness`、`/api/v2/ai/health`、`/api/v2/ai/metrics`，校�?AI v2 chat/search/tool 契约、`ai.provider-runtime`、`ai.remote-owner-gateway`、`ai.provider.remote_configured`、`ai.owner_gateway.configured`；设�?`DDD_AI_EXPECT_PROVIDER_REMOTE=true` �?`DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true` 后可作为准生�?生产等价远程 provider �?owner gateway 验收门禁；失败产物会输出 `failureDetails` �?`summary.failureCategories`，用于区�?endpoint、契约、health、metrics、provider runtime、owner gateway �?provenance 缺口 | 已证明脚本，真实远程证据待采�?|
| 前端 smoke 证据归档 | `bin/ddd-frontend-smoke-evidence.mjs` 已可�?Playwright JSON report 转换�?`artifacts/ddd/lumira-ui/lumira-ui-smoke.json`，校�?dashboard、download center、AI assistant、用户、角色、安全设置、支付设置、文件、插件、国际化、session refresh、消息中心和登出 smoke 均通过；`DDD_FRONTEND_EXPECT_DEPLOYED=true` 可阻�?localhost artifact | 已证明脚本，部署环境证据待采�?|
| Frontend static evidence | `bin/ddd-frontend-static-evidence.mjs` 已可执行并归�?`lint`、`typecheck`、`test`，当前本�?artifact `artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json` 三项�?PASS | 已证�?|
| Frontend build evidence | `bin/ddd-frontend-build-evidence.mjs` 已可执行 `corepack pnpm --dir lumira-ui build` 并归�?`lumira-ui/dist` 产物，当前本�?artifact `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json` 记录 182 个构建文件、入�?HTML 和静态资�?checksum | 已证�?|
| Backend test evidence | `bin/ddd-backend-test-evidence.mjs` 已可读取 surefire XML，校验关键架构边界、owner v2/readiness、AI provider/owner gateway、Payment webhook、File processing �?Job adapter 测试存在�?failures/errors �?0；当前本�?artifact `artifacts/ddd/tests/backend-test-evidence.json` 汇�?143 �?suite�?92 个测�?| 已证�?|
| Backend build evidence | `bin/ddd-backend-build-evidence.mjs` 已可执行 `./mvnw -DskipTests package` 并校�?`lumira-server`、Auth、Message、File、Plugin、Localization、Payment、AI、Job 启动入口和各后端模块 jar；当前本�?artifact `artifacts/ddd/build/backend-build-evidence.json` 汇�?10 �?jar | 已证�?|
| Docker build evidence | `bin/ddd-docker-build-evidence.mjs` 已可构建�?inspect `lumira-server` �?`lumira-ui` 两个部署镜像，归�?Dockerfile checksum、image id、tag �?size；Docker CLI/daemon 不可用时会为每个 skipped image 写入具体 `skipReason` �?blockers；release gate strict 会要求镜�?artifact 存在且两个镜像均 PASS | 已证明脚本，真实镜像构建证据待采�?|
| Performance baseline promotion | `bin/ddd-promote-performance-baseline.mjs` 已可将通过验收的生产等�?`authenticated-runtime-actual.json` 晋级�?`authenticated-runtime-baseline.json`，并拒绝 localhost actual、失败样本、缺�?p95/上传耗时、缺�?actual provenance、actual 环境和晋级环境不一致、或缺失验收�?环境名的基线来源 | 已证明脚本，真实生产等价 baseline 待采�?|
| Runtime evidence provenance | `bin/ddd-runtime-readiness-smoke.mjs`、`bin/ddd-authenticated-performance-smoke.mjs`、`bin/ddd-file-processing-e2e-smoke.mjs`、`bin/ddd-payment-webhook-e2e-smoke.mjs`、`bin/ddd-job-e2e-smoke.mjs`、`bin/ddd-ai-runtime-drill.mjs`、`bin/ddd-frontend-smoke-evidence.mjs` 已统一写入 `sourceEnvironment`、`releaseCandidate`、`evidenceOperator`；release gate strict 会要求运行时证据具备环境、版本和执行人来�?| 已证明脚本，真实生产等价 provenance 待采�?|
| Release artifact freshness | `bin/ddd-release-evidence-gate.mjs` strict 模式已要求关�?artifact �?`generatedAt`、`checkedAt`、`finishedAt` �?`startedAt` �?`DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS` 窗口内，EXPLAIN JSON 使用文件 mtime 校验；authenticated performance baseline 作为已验收历史基线不参与 freshness | 已证明脚本，真实发布流水线需重新生成新鲜 artifact |
| Release evidence manifest | `bin/ddd-release-evidence-manifest.mjs` 已可生成 `artifacts/ddd/release/evidence-manifest.json`，记录关�?artifact �?SHA-256、大小和时间戳，并验�?EXPLAIN JSON 存在；release gate strict 要求 manifest 存在�?PASS | 已证明脚本，真实发布流水线需生成完整 manifest |
| Release evidence orchestrator | `bin/ddd-release-evidence-orchestrator.mjs` 已可�?plan mode 输出证据执行计划，并�?`--run --strict` 下按后端测试/构建、Docker、前端、迁移、配置、runtime smoke、回滚、EXPLAIN、manifest、release gate、readiness summary 的顺序执行并归档每步退出码、耗时和输出尾�?| 已证明脚本，真实发布流水线执行时生成完整报告 |
| Release readiness summary | `bin/ddd-release-readiness-summary.mjs` 已可读取 strict release gate、manifest、Docker、Frontend smoke、AI runtime artifact，输�?JSON/Markdown 行动摘要，并�?Docker、性能 baseline、回滚演练、配置、前�?smoke、AI runtime、迁移、运行时 provenance 等类别归�?owner 与建议命令；`diagnostics.readinessSummary.contractIssues` 会自检摘要状态、blocker 计数和行动分组一致�?| 已证明脚本，用于排障分派，不作为放行证据 |
| Rollback drill validator | `bin/ddd-rollback-drill-evidence.mjs` 已可独立校验 rollback drill artifact 的十个上下文覆盖、PASS/DEFERRED 必填字段和时间格式，避免回滚证据只在最�?release gate 才暴露问�?| 已证明脚本，真实 rollback drill artifact 待采�?|
| Migration evidence | `bin/ddd-migration-evidence.mjs` 已可扫描 owner Flyway migration location，校验必需目录、SQL 非空和同一 location 无重复版本；本地 artifact `artifacts/ddd/migration/migration-evidence.json` 汇�?34 �?migration 文件并通过静态检查，并输�?`runtimeDiagnostics` 分派 fresh DB、old DB upgrade、环境、版本、执行人和完成时间缺口；strict 仍要求准生产/生产等价新库迁移和旧库升级运行证�?| 已证明脚本，strict 真实环境证据待采�?|
| Release config evidence | `bin/ddd-release-config-evidence.mjs` 已可读取 `DDD_RELEASE_ENV_FILE` 和当前环境，校验准生�?生产等价后端 baseURL、前�?baseURL、DB、Redis、JWT/FIELD secret、owner service URL、Job/XXL token、AI provider、AI owner gateway、Payment public URL，并只输出脱�?URL、secret 长度�?hash 前缀；artifact 会输�?`blockerDetails`、`blockersByGroup`、`blockersByOwner`，strict 发布要求�?localhost �?AI provider/owner gateway 明确启用 | 已证明脚本，strict 真实环境证据待采�?|
| Release evidence gate | `bin/ddd-release-evidence-gate.mjs` 已可汇总校�?backend build evidence、backend test evidence、Docker image evidence、lumira-ui build/static evidence、migration evidence、release config evidence、release evidence manifest、readiness、authenticated performance、性能 baseline 回归、File processing、Payment webhook、Outbox/job、AI runtime drill、physical split readiness、lumira-ui smoke、rollback drill �?EXPLAIN artifact；默�?advisory 输出缺口，`DDD_RELEASE_EVIDENCE_STRICT=true` 会把缺失后端构建/测试证据、缺�?Docker 镜像证据、缺失前端构�?测试证据、缺�?Flyway 新库/旧库运行验证、缺失生产等价配置和运行证据、缺�?checksum manifest、运行时证据缺少 provenance、artifact 超过 freshness 窗口、缺�?authenticated performance baseline、baseline �?localhost 或缺少验收元数据、p95/上传耗时回退超过阈值、缺失回滚演练证据和 localhost-only 运行时证据作为发�?blocker；共�?release gate contract 会复�?`summary`、`checks[]`、`blockers[]`、`warnings[]` 自洽性，避免手工汇总掩�?blocker | 已证明脚本，strict 真实环境证据待采�?|
| 独立服务启动入口 | Auth、Message、File、Plugin、Localization、Payment、Job、AI 均已新增可编�?Spring Boot application entrypoint；Auth/Localization/AI 已补齐独�?owner baseline migration；`services/lumira-ai` 独立 Maven module 已承�?AI readiness、读模型、chat、knowledge upload/reindex/search �?tool execute/propose/confirm v2 契约，并提供独立 Flyway baseline `db/migration/ai/V1__baseline_ai_domain.sql` 创建 AI owner 表；AI tool 远程 owner gateway �?provider runtime 已落地并可观测配�?降级状态；`lumira-server` 聚合启动通过 component-scan exclude 排除聚合依赖内的独立入口，`ai-service` 不作为聚合依赖引入，避免 `/api/v2/ai` 观测端点重复装配；`./mvnw -pl services/lumira-ai -am -Dtest=AiReadinessV2ControllerTest,AiV2ControllerTest,AiReadQueryServiceTest,AiCommandServiceTest,RemoteAiOwnerToolGatewayTest,DefaultAiProviderRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`、`./mvnw -pl services/lumira-auth,services/lumira-localization,services/lumira-ai -am -DskipTests compile` �?`./mvnw -pl services/lumira-admin -am -DskipTests compile` 通过 | 已证�?|
| 物理拆分 advisory/strict gate | `bin/ddd-physical-split-gate.mjs` 校验 owner manifest、readiness/health/metrics、owner internal contract、跨 service module POM 依赖、owner Flyway 依赖、关�?owner 表迁�?SQL、拆分文档、运行手册覆盖和 AI required business endpoint 覆盖；本地运�?advisory �?`DDD_SPLIT_STRICT=true` 均通过，`blockers=0`，输�?`artifacts/ddd/split/physical-split-readiness.json` | 已证�?|
| CI 门禁 | `.github/workflows/ci.yml` 已接入架构测试、owner contract tests、readiness gate、physical split advisory gate、performance script syntax、explain gate | 已证�?|
| 前端静态与单元回归 | `corepack pnpm --dir lumira-ui lint`、`typecheck`、`test` 通过，lint 仅有 warning | 已证�?|
| 前端浏览器主流程 smoke | `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8010 ... corepack pnpm --dir lumira-ui test:e2e:smoke` 通过�?6/16 覆盖登录、强制改密、匿名跳转、首页、用�?角色、消息中心、文件、插件、国际化、支付设置、AI assistant、session refresh、登�?| 已证�?|
| 后端全量测试 | `./mvnw test` 通过，合计后端模块测试全部成功，system-service �?4 个显�?skipped integration tests | 已证�?|

## 2. 已执行验证命�?

```bash
./mvnw test
./mvnw -pl services/lumira-admin -am -DskipTests compile
./mvnw -pl services/lumira-admin,services/lumira-system -am -Dtest=SecurityPermitPathsTest -Dsurefire.failIfNoSpecifiedTests=false test package
./mvnw -pl services/lumira-quartz -am -Dtest='com.lumira.job.XxlJobExecutorConfigTest,com.lumira.job.JobReadinessV2ControllerTest,com.lumira.job.FileOutboxRelayJobHandlerTest,com.lumira.job.FileProcessingTaskJobHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test
corepack pnpm --dir lumira-ui lint
corepack pnpm --dir lumira-ui typecheck
corepack pnpm --dir lumira-ui test
node bin/ddd-readiness-gate.mjs
node --check bin/ddd-runtime-readiness-smoke.mjs
DDD_PERF_BASELINE_FILE=doc/30-ddd-performance-baseline.example.json \
DDD_PERF_ACTUAL_FILE=doc/30-ddd-performance-baseline.example.json \
node bin/ddd-performance-smoke.mjs
DDD_SMOKE_SCENARIOS_FILE=doc/32-ddd-performance-scenarios.example.json \
DDD_PERF_BASELINE_FILE=doc/30-ddd-performance-baseline.example.json \
DDD_PERF_ACTUAL_FILE=doc/30-ddd-performance-baseline.example.json \
node bin/ddd-performance-smoke.mjs
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_RUNTIME_READINESS_DIR=artifacts/ddd/readiness \
node bin/ddd-runtime-readiness-smoke.mjs
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_SMOKE_SCENARIOS_FILE=doc/32-ddd-performance-scenarios.example.json \
node bin/ddd-performance-smoke.mjs > artifacts/ddd/performance/runtime-actual.json
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_AUTH_PERF_DURATION_MS=8000 \
DDD_AUTH_PERF_CONCURRENCY=6 \
node bin/ddd-authenticated-performance-smoke.mjs
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-collect-explain.mjs
node bin/ddd-explain-gate.mjs
node --check bin/ddd-physical-split-gate.mjs
node bin/ddd-physical-split-gate.mjs
DDD_SPLIT_STRICT=true node bin/ddd-physical-split-gate.mjs
./mvnw -pl services/lumira-auth,services/lumira-localization,services/lumira-ai -am -DskipTests compile
./mvnw -pl services/lumira-ai -am -Dtest=AiReadinessV2ControllerTest,AiV2ControllerTest,AiReadQueryServiceTest,AiCommandServiceTest,RemoteAiOwnerToolGatewayTest,DefaultAiProviderRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl services/lumira-auth,services/lumira-message,services/lumira-file,services/lumira-plugin,services/lumira-localization,services/lumira-payment,services/lumira-quartz -am -DskipTests compile
./mvnw -pl services/lumira-system -am -DskipTests compile
./mvnw -pl services/lumira-message,services/lumira-file -am \
-Dtest='com.lumira.message.app.PlatformEventOutboxServiceTest,com.lumira.file.event.PlatformEventOutboxRelayServiceTest,com.lumira.file.event.FileOutboxMetricsServiceTest' \
-Dsurefire.failIfNoSpecifiedTests=false test
SAAS_JOB_INTERNAL_TOKEN=lumira-runtime-readiness-job-token \
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
node bin/ddd-job-e2e-smoke.mjs
XXL_JOB_EXECUTOR_ENABLED=false \
SAAS_JOB_INTERNAL_TOKEN=lumira-runtime-readiness-job-token \
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
node bin/ddd-job-e2e-smoke.mjs
SAAS_JOB_INTERNAL_TOKEN=lumira-runtime-readiness-job-token \
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_JOB_SMOKE_DB_CHECK=true \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-job-e2e-smoke.mjs
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-outbox-ownership-repair.mjs
DDD_OUTBOX_REPAIR_APPLY=true \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-outbox-ownership-repair.mjs
node bin/ddd-outbox-replay-dead-letter-smoke.mjs
node --check bin/ddd-ai-runtime-drill.mjs
node bin/ddd-backend-build-evidence.mjs
node bin/ddd-backend-test-evidence.mjs
node --check bin/ddd-docker-build-evidence.mjs
node --check bin/ddd-promote-performance-baseline.mjs
node bin/ddd-migration-evidence.mjs
node bin/ddd-frontend-build-evidence.mjs
node bin/ddd-frontend-static-evidence.mjs
node --check bin/ddd-release-config-evidence.mjs
node --check bin/ddd-release-evidence-manifest.mjs
node --check bin/ddd-release-evidence-orchestrator.mjs
node --check bin/ddd-release-readiness-summary.mjs
node --check bin/ddd-rollback-drill-evidence.mjs
node --check bin/ddd-frontend-smoke-evidence.mjs
node --check bin/ddd-release-evidence-gate.mjs
node bin/ddd-release-evidence-gate.mjs
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
SAAS_JOB_INTERNAL_TOKEN=lumira-runtime-readiness-job-token \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-file-processing-e2e-smoke.mjs
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root \
MYSQL_PASSWORD=local-dev-mysql-password DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-payment-webhook-e2e-smoke.mjs
PLAYWRIGHT_BASE_URL=http://127.0.0.1:8010 \
PLAYWRIGHT_ADMIN_USER=admin \
PLAYWRIGHT_ADMIN_PASSWORD=123456 \
PLAYWRIGHT_NEW_PASSWORD=E2eAdmin123! \
corepack pnpm --dir lumira-ui test:e2e:smoke
git diff --check
```

## 3. 仍需真实环境证明

| 要求 | 缺少的权威证�?| 建议命令或产�?|
| --- | --- | --- |
| 可部�?Docker 镜像 | 已有 Maven/前端构建证据�?CI docker job；仍缺归档到 release gate 的镜像构�?inspect artifact | `node bin/ddd-docker-build-evidence.mjs` |
| 生产或准生产登录/bootstrap p95 不回退 | 已有本地 runtime actual；仍缺生产等价环�?actual 与带验收元数据的历史基线对比 | `LUMIRA_BASE_URL=... DDD_AUTH_PERF_ENVIRONMENT=staging-production-equivalent node bin/ddd-authenticated-performance-smoke.mjs`，再�?`DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=... DDD_AUTH_PERF_BASELINE_ENVIRONMENT=... node bin/ddd-promote-performance-baseline.mjs` 晋级上一轮通过验收�?baseline |
| 运行时证据来源可追溯 | 已有脚本字段�?strict 校验；仍缺准生产/生产流水线设�?`DDD_EVIDENCE_ENVIRONMENT`、`DDD_RELEASE_CANDIDATE`、`DDD_EVIDENCE_OPERATOR` 后重新生�?runtime artifact | 在所�?runtime smoke 前导�?`DDD_EVIDENCE_ENVIRONMENT=... DDD_RELEASE_CANDIDATE=... DDD_EVIDENCE_OPERATOR=...` |
| 发布证据新鲜�?| 已有 strict freshness 校验；仍缺发布流水线�?24 小时窗口内重新生成全部关�?artifact | `DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS=24 DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-gate.mjs` |
| 发布证据 checksum manifest | 已有 manifest 脚本�?strict 校验；仍缺所有真�?artifact 到位后生�?PASS manifest | `node bin/ddd-release-evidence-manifest.mjs` |
| 发布证据编排报告 | 已有 orchestrator 脚本；仍需在真实发布流水线运行 `--run --strict` 并归档报�?| `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-orchestrator.mjs --run --strict` |
| 发布 blocker 分派 | 已有 readiness summary 脚本；仍需在真实发布流水线失败时将 `readiness-summary.md` �?artifact 一起归�?| `node bin/ddd-release-readiness-summary.mjs` |
| 回滚演练证据 | 已有模板和独�?validator；仍缺真�?IAM/Auth/Platform/Message/File/Plugin/Localization/Payment/AI/Job 演练 artifact | `cp doc/35-ddd-rollback-drill-template.json artifacts/ddd/rollback/rollback-drill.json` 后填入真实证据，再运�?`node bin/ddd-rollback-drill-evidence.mjs` |
| 支付 webhook/provider p95、签名失败率、重复拦�?| 已有本地 sandbox E2E webhook 请求、响应、订单状态轨迹和落表证据；仍缺生产等价环�?provider webhook 指标、日志与历史基线对比 | 保存 provider 请求、响应、Payment metrics、订单状态轨迹、历史基线对�?|
| 准生产文件异步处理耗时 | 已有本地 `.txt` 上传、processing job、任务状态和 artifact 端到端证据；仍缺准生产对象存�?本地存储配置下的处理耗时�?metrics | 场景文件 multipart upload，保�?File metrics �?artifact 记录 |
| 生产数据�?MySQL `EXPLAIN FORMAT=JSON` | 已有本地 MySQL 8.4 空库/种子�?explain；仍缺生产量级执行计�?| `MYSQL_HOST=... MYSQL_PORT=... MYSQL_USER=... MYSQL_PASSWORD=... DDD_EXPLAIN_DATABASE=lumira node bin/ddd-collect-explain.mjs && node bin/ddd-explain-gate.mjs` |
| Flyway 新库启动与旧库升�?| 已有 migration 静态扫描和重复版本检查；仍缺准生�?生产等价新库迁移日志、旧库升级日志和 `flyway_schema_history` 证据 | `DDD_MIGRATION_FRESH_DB_VALIDATED=true DDD_MIGRATION_UPGRADE_DB_VALIDATED=true DDD_MIGRATION_RUNTIME_EVIDENCE=... node bin/ddd-migration-evidence.mjs` |
| 生产等价配置矩阵 | 已有配置证据脚本；仍缺由准生�?生产流水线生成的 env-file-backed artifact，证�?DB/Redis/owner URL/Job/AI/Payment/前后�?baseURL 均为�?localhost 且关键远程开关开�?| `DDD_RELEASE_ENV_FILE=.env.release DDD_RELEASE_CONFIG_STRICT=true node bin/ddd-release-config-evidence.mjs` |
| 准生产跨进程 outbox replay/dead-letter | 已有本地状态机 smoke �?job/internal relay E2E；仍缺准生产环境中由真实 dispatcher 失败触发�?dead-letter/replay 证据 | �?`doc/31-ddd-operational-runbook.md` �?3 节构造失败事件并�?owner 演练 replay |
| 部署环境前端主流�?smoke | 已有本地浏览�?smoke；仍缺部署环境登录、首页、用户角色、消息、上传、插件、国际化、支付回调模拟、AI 会话录像/网络日志 | 保存 smoke 录像、截图、控制台和网络请求日�?|
| 物理拆服务就�?| 已有 advisory/strict gate 产物证明结构条件无失败，Auth、Message、File、Plugin、Localization、Payment、Job、AI 均已有可编译启动入口，且 AI 独立 `ai-service` 已承载完�?v2 API 契约、代码级远程 IAM/Platform/File owner gateway、OpenAI-compatible provider runtime 和可执行 AI runtime drill；仍缺生产环境服务间超时/降级实测、真�?provider-native LLM/vector 调用和回滚演练证�?| `DDD_AI_EXPECT_PROVIDER_REMOTE=true DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true node bin/ddd-ai-runtime-drill.mjs`，并�?`doc/29-ddd-physical-split-readiness.md` �?`doc/31-ddd-operational-runbook.md` �?owner 执行 |
| 发布证据 strict gate | 本地 advisory `node bin/ddd-release-evidence-gate.mjs` 已输出当�?artifact 缺口；strict 模式必须在生产等价环境完�?authenticated performance baseline 对比、Flyway 新库/旧库运行验证、AI runtime drill、生产量�?EXPLAIN、部署环�?lumira-ui smoke、十个上下文 rollback drill 等证据后执行 | `DDD_RELEASE_EVIDENCE_STRICT=true node bin/ddd-release-evidence-gate.mjs` |
| 运行�?owner 观测端点 | 本地聚合服务已生�?30 �?readiness/health/metrics JSON artifact；仍缺部署环�?artifact | `LUMIRA_BASE_URL=... DDD_RUNTIME_READINESS_DIR=artifacts/ddd/readiness node bin/ddd-runtime-readiness-smoke.mjs` |

## 4. 当前结论

当前仓库已经具备 DDD 分层、owner 边界、v2 adapter、readiness/health/metrics、核心领域测试、全量后端测试、前端静�?单元回归、性能/EXPLAIN/readiness gate 和运行手册�?

最终完成还需要在生产等价环境中补齐性能 actual 与历史基线对比、生产数据量 MySQL explain、provider webhook 指标和真实回调日志、准生产文件处理任务端到端耗时、准生产真实 dispatcher 失败触发�?outbox replay/dead-letter、部署环境前端浏览器 smoke 和回滚演练证据。未取得这些运行证据前，不应宣称物理拆分完全完成�?
