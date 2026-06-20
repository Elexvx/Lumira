# DDD Operational Runbook

本文档用�?DDD 重构后的运行演练、拆分门禁和回滚审计。它不是设计说明，而是上线前后可以逐项执行并保留证据的检查手册�?

## 1. 通用执行顺序

1. 启动目标环境，确�?`lumira-server`、Redis、MySQL、XXL-JOB、对象存储或本地存储均可访问�?
2. 执行全局编译和架构门禁�?
3. 访问每个 owner �?`/readiness`、`/health`、`/metrics`，保存响�?JSON�?
4. 对热路径执行性能 smoke，保�?baseline、actual 和命令输出�?
5. 在可连接 MySQL 的环境执�?`EXPLAIN FORMAT=JSON` 采集，保�?`tmp/ddd-explain/*.json`�?
6. 演练 outbox relay、dead-letter、replay、缓存失效和回滚步骤�?
7. 对照本文件记录证据路径、结果和剩余风险�?

推荐命令�?

```bash
./mvnw -pl services/lumira-admin -am -DskipTests compile
./mvnw -pl services/lumira-system -am -Dtest=DddArchitectureBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
node --check bin/ddd-performance-smoke.mjs
node --check bin/ddd-collect-explain.mjs
node --check bin/ddd-explain-gate.mjs
node --check bin/ddd-physical-split-gate.mjs
node bin/ddd-readiness-gate.mjs
node bin/ddd-physical-split-gate.mjs
```

Owner readiness/health/metrics 是公开只读运维端点，已通过 `saas.security.permit-paths` 放行。端�?payload 只能包含 owner 边界、状态、指标名和值、依赖和回滚步骤，不得返回密钥、token、个人联系方式或支付敏感数据�?

`ddd-physical-split-gate` 默认�?advisory 模式运行并写�?`artifacts/ddd/split/physical-split-readiness.json`。该产物用于拆服务前评审：`failures` 必须�?0；进入真实物理拆分窗口前，设�?`DDD_SPLIT_STRICT=true` 运行，确保所�?blocker 已清零，并带�?`DDD_EVIDENCE_ENVIRONMENT`、`DDD_RELEASE_CANDIDATE`、`DDD_EVIDENCE_OPERATOR` provenance。artifact �?`summary` 由共�?physical split contract 计算，release gate 会复核上下文数量、物理拆分目标数、独立启动就绪数、全局/上下文检查数、失败数、blockers、warnings、迁移文件数、缺失业�?endpoint 数和�?service Maven 依赖失败数是否与明细一致。contract 还要�?IAM/Auth/Platform/Message/File/Plugin/Localization/Payment/AI/Job 十个上下文精确出现一次，module、route、ownerContext、physicalServiceTarget 与拆分契约一致，并且每个上下文至少包�?module、owner-manifest、readiness、health、metrics、cross-service-pom-dependency 六类基础检查�?

本地 runtime smoke 或准生产 owner 演练如果不需要连�?XXL-JOB Admin，应显式设置 `XXL_JOB_EXECUTOR_ENABLED=false`。该开关只禁用 `XxlJobSpringExecutor` 的外部注册和 remoting server，不移除 `@XxlJob` handler、owner internal API �?`bin/ddd-job-e2e-smoke.mjs` 的内�?relay 验证能力�?

运行环境采集 readiness/health/metrics 证据�?

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_RUNTIME_READINESS_DIR=artifacts/ddd/readiness \
node bin/ddd-runtime-readiness-smoke.mjs
```

strict 模式下，runtime readiness smoke 会在生成阶段校验 baseURL 不是 localhost、使�?HTTPS，并要求 `DDD_RUNTIME_READINESS_DEPLOYMENT_EVIDENCE` 或统一 `DDD_DEPLOYMENT_EVIDENCE` 指向可追溯部�?工单/CI 证据；结果会写入结构�?`productionEquivalence`。runtime readiness contract 要求十个 owner 上下文的 readiness/health/metrics 正好形成 30 个唯一检查，未知 context/suffix、重复检查或重复 artifact 引用都会失败，避免用重复结果凑齐覆盖。contract 还会读取每个 endpoint artifact：readiness payload 必须声明 owner module、owner tables、API、health checks、metrics、dependencies �?rollback steps；health payload 必须有非�?healthChecks �?metrics；metrics payload 必须有非�?metrics，且健康检查和指标条目要包�?name/status/type/unit/description 等可运维字段�?0 �?endpoint 都返�?200 只能证明 owner contract 可用；strict contract 会拒绝缺�?`productionEquivalence` 的历�?runtime artifact，`productionEquivalence.issues` 仍为 release blocker，直到证据来自准生产或生产等价地址且带部署证据引用�?

有运行环境时�?

```bash
DDD_PERF_BASELINE_FILE=doc/30-ddd-performance-baseline.example.json \
DDD_PERF_ACTUAL_FILE=/path/to/actual-performance.json \
node bin/ddd-performance-smoke.mjs

DDD_EXPLAIN_DATABASE=lumira \
DDD_EVIDENCE_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=git-sha-or-build-id \
DDD_EVIDENCE_OPERATOR=release-owner@example.com \
node bin/ddd-collect-explain.mjs
DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs
```

`ddd-collect-explain` 会把每个热点 SQL �?MySQL `EXPLAIN FORMAT=JSON` 包装成带 `generatedAt`、`sourceEnvironment`、`releaseCandidate`、`evidenceOperator`、`queryName`、`sqlSha256`、`database`、`mysqlHost` �?`plan` �?artifact。strict gate 会拒绝缺元数据、占位元数据、localhost/127.0.0.1/::1 �?`mysqlHost`、本地诊�?`sourceEnvironment`、`queryName` 与文件名不一致、非 64 �?hex `sqlSha256`、`sqlSha256` 与共享热�?SQL 契约不匹配、legacy 导入计划、全表扫描、缺索引 key、缺 `table_name/access_type/rows_examined_per_scan` 诊断字段、或 `rows_examined_per_scan` 超过 `doc/28-ddd-hot-path-explain-plan.md` �?strict rows 上限的产物。当前必需 artifact �?`platform-runtime-appearance.json`、`plugin-bootstrap.json`、`message-visible-list.json`、`message-unread-count.json`、`message-archive-total.json`、`ai-knowledge-index-retry.json`、`platform-outbox-owner-relay-message.json`、`platform-outbox-owner-relay-file.json`；必需索引包括 runtime appearance �?`uk_sys_config_key`、plugin bootstrap �?`uk_sys_plugin_tenant_rel`/`uk_sys_plugin_definition_code`/`uk_sys_plugin_version_code_version`、message visible/unread/archive capped path �?`idx_msg_notice_visible_recent`、AI retry �?`idx_ai_knowledge_document_index_retry` �?Message/File owner relay �?`idx_platform_event_outbox_owner_queue`�?

如果已经有旧格式的裸 `EXPLAIN FORMAT=JSON` 文件，可以用 `bin/ddd-normalize-explain-artifacts.mjs` 将其导入为带 provenance �?legacy plan artifact�?

```bash
DDD_EXPLAIN_DIR=tmp/ddd-explain \
DDD_EVIDENCE_ENVIRONMENT=local-audit \
DDD_RELEASE_CANDIDATE=<sha-or-build-id> \
DDD_EVIDENCE_OPERATOR=<operator> \
node bin/ddd-normalize-explain-artifacts.mjs
DDD_EXPLAIN_STRICT=true node bin/ddd-explain-gate.mjs
```

该工具只保留并标记既有执行计划，适合审计旧产物或本地补齐元数据；strict 发布放行必须在准生产/生产量级数据库运�?`ddd-collect-explain` 重新采集，`legacyPlanImport=true` 的产物会�?release gate 拒绝�?

包含 POST、webhook 和文件上传的场景压测�?

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
LUMIRA_AUTH_TOKEN=replace-with-token \
DDD_SMOKE_SCENARIOS_FILE=doc/32-ddd-performance-scenarios.example.json \
node bin/ddd-performance-smoke.mjs
```

认证成功�?v2 热路径压测：

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_AUTH_USERNAME=admin \
DDD_AUTH_PASSWORD=replace-with-ready-password \
DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE=change-or-deploy-artifact-link \
DDD_AUTH_PERF_DURATION_MS=15000 \
DDD_AUTH_PERF_CONCURRENCY=8 \
node bin/ddd-authenticated-performance-smoke.mjs
```

该脚本会先通过 `/api/v2/auth/login-encryption-key` �?`/api/v2/auth/login` 拿到真实 token，再要求 current-user、IAM、Message、File、Plugin、Localization、Payment �?v2 热路径全部返回成�?envelope；`/api/v2/auth/session/keepalive` �?`/api/v2/files/upload` 作为一次性成功回包证据单独记录。performance contract 会复�?`ok + failed == samples`、�?endpoint samples 汇总、statusCounts 汇总、one-shot 状态和 endpoint 清单覆盖，避免少测端点或手工修改统计绕过回归门禁。strict 模式下会写入结构�?`productionEquivalence`，要�?`LUMIRA_BASE_URL` �?HTTPS 且非 localhost；`DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE` 或通用 `DDD_DEPLOYMENT_EVIDENCE` 可记录部署单、CI artifact、压测报告或对象存储证据引用。历�?actual 如果没有 `productionEquivalence`，只能作为开发调试证据，strict release gate 会以 authenticated-performance-shape blocker 阻断。默认产物为 `artifacts/ddd/performance/authenticated-runtime-actual.json`。发布前应把上一轮通过验收的生产等�?actual 晋级�?baseline；手动执行时�?

```bash
DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=release-operator \
DDD_AUTH_PERF_BASELINE_ENVIRONMENT=staging-production-equivalent \
DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=artifacts/ddd/performance/authenticated-runtime-actual.json \
node bin/ddd-promote-performance-baseline.mjs
```

`bin/ddd-promote-performance-baseline.mjs` 会拒�?localhost actual、非 HTTPS actual、缺少结构化 `productionEquivalence.strict=true` �?actual、失败样本、缺少上传耗时�?artifact、缺�?actual provenance �?artifact、actual 环境和晋级环境不一致的 artifact、actual release candidate �?`DDD_RELEASE_CANDIDATE` 不一致的 artifact，以及缺失或占位�?`acceptedBy`、`sourceEnvironment`、`sourceArtifact`；无论成功或失败，脚本都会写�?`artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`，记录源 actual 摘要、晋�?env、源文件 SHA-256、blockers 和输出路径，便于发布负责人审计为什�?baseline 没有生成。`bin/ddd-promote-performance-baseline.test.mjs` 覆盖了非本地晋级、localhost 拒绝、非 HTTPS 拒绝、release candidate 不一致拒绝、失败样本拒绝、actual provenance 拒绝、环境不一致拒绝、占位验收人拒绝和仓库内路径脱敏，防止本地或不可追溯性能结果污染发布基线。authenticated performance contract 会要�?actual/baseline �?ISO `checkedAt`、正�?duration/concurrency、整体和�?endpoint p50/p95/p99 顺序正确、固�?9 个热路径 endpoint 精确覆盖、`ok + failed = samples`、perEndpoint samples/statusCounts 自洽、keepalive one-shot 成功，以�?`/api/v2/files/upload` 上传回包 200 且带 fileId，避免少测端点或缺少上传证据绕过性能门槛。`bin/ddd-release-evidence-gate.mjs` �?strict 模式会要�?baseline �?localhost，`acceptedAt` �?ISO 时间，`acceptedBy`、`sourceEnvironment`、`sourceArtifact` 为真实非占位值，`sourceSha256` �?64 �?SHA-256，并比较整体 p95、�?endpoint p95 和上传回包耗时，超�?baseline 10% 会阻断发布；actual 还必须覆�?baseline 中所�?endpoint，避免少测端点绕过回退比较。可�?`DDD_RELEASE_MAX_P95_REGRESSION_RATIO` 调整阈值。`bin/ddd-release-readiness-summary.mjs` 会展开 authenticated performance actual/baseline 诊断，显�?actual 是否 localhost、失败数、p95、upload 回包、端点数量、baseline 晋级 envKeys、最近一�?baseline promotion blockers、baseline metadata/shape 问题、缺�?endpoint 和回归项�?

发布 workflow 默认不会自动晋级 baseline；只有显式设�?`promote_authenticated_baseline=true` 时，orchestrator 才会�?authenticated performance actual 生成后执�?`bin/ddd-promote-performance-baseline.mjs`。首次建立基线或经发布负责人批准刷新基线时，应同时填�?`baseline_accepted_by`；否则该步骤会在报告中记录为 skipped，strict gate 仍会要求已有 `authenticated-runtime-baseline.json` 存在并通过回归比较�?

File/Payment 业务 E2E smoke�?

```bash
DDD_RELEASE_EVIDENCE_STRICT=true \
DDD_EVIDENCE_ENVIRONMENT=staging-production-equivalent \
DDD_RELEASE_CANDIDATE=replace-with-git-sha-or-tag \
DDD_EVIDENCE_OPERATOR=replace-with-operator \
DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=change-or-deploy-artifact-link \
LUMIRA_BASE_URL=https://staging-api.example.internal \
DDD_AUTH_USERNAME=admin \
DDD_AUTH_PASSWORD=replace-with-ready-password \
SAAS_JOB_INTERNAL_TOKEN=replace-with-runtime-token \
MYSQL_HOST=replace-with-db-host \
MYSQL_PORT=3306 \
MYSQL_USER=replace-with-db-user \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-file-processing-e2e-smoke.mjs

DDD_RELEASE_EVIDENCE_STRICT=true \
DDD_EVIDENCE_ENVIRONMENT=staging-production-equivalent \
DDD_RELEASE_CANDIDATE=replace-with-git-sha-or-tag \
DDD_EVIDENCE_OPERATOR=replace-with-operator \
DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE=change-or-deploy-artifact-link \
LUMIRA_BASE_URL=https://staging-api.example.internal \
DDD_AUTH_USERNAME=admin \
DDD_AUTH_PASSWORD=replace-with-ready-password \
DDD_PAYMENT_TENANT_ID=1001 \
MYSQL_HOST=replace-with-db-host \
MYSQL_PORT=3306 \
MYSQL_USER=replace-with-db-user \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-payment-webhook-e2e-smoke.mjs
```

这两�?smoke 会写�?`productionEquivalence`：strict 发布要求 `LUMIRA_BASE_URL` �?HTTPS 且非 localhost；`DDD_FILE_PROCESSING_DEPLOYMENT_EVIDENCE`、`DDD_PAYMENT_WEBHOOK_DEPLOYMENT_EVIDENCE` 或通用 `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` 可记录部署单、CI artifact、provider sandbox 日志或对象存储证据引用。business E2E contract �?strict 模式下会要求 artifact 自身携带结构�?`productionEquivalence`，避免旧的本�?only artifact 只在最�?release gate 才暴露环境缺口。File smoke 证明上传立即回包后，`SECURITY_SCAN`、`TEXT_EXTRACT`、`AI_PARSE` 都由任务处理完成并生�?artifact；Payment smoke 证明有效签名、重�?event、nonce replay 和坏签名四类 webhook 行为都落库可审计�?

内部 job/outbox E2E smoke�?

```bash
DDD_RELEASE_EVIDENCE_STRICT=true \
DDD_EVIDENCE_ENVIRONMENT=staging-production-equivalent \
DDD_RELEASE_CANDIDATE=replace-with-git-sha-or-tag \
DDD_EVIDENCE_OPERATOR=replace-with-operator \
DDD_JOB_SMOKE_DEPLOYMENT_EVIDENCE=change-or-deploy-artifact-link \
SAAS_JOB_INTERNAL_TOKEN=replace-with-runtime-token \
LUMIRA_BASE_URL=https://staging-api.example.internal \
node bin/ddd-job-e2e-smoke.mjs
```

如需同时验证共用 `platform_event_outbox` 没有新增�?owner 误处理，可打开 DB 诊断�?

```bash
SAAS_JOB_INTERNAL_TOKEN=replace-with-runtime-token \
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_JOB_SMOKE_DB_CHECK=true \
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-job-e2e-smoke.mjs
```

该脚本必须证明两件事：未�?`X-Job-Token` 的内部任务请求被拒绝；带 token �?platform/message/file/payment/plugin outbox relay、message/online heartbeat、file processing、AI knowledge index 均返回成�?envelope。Job E2E contract 会要�?artifact �?`baseUrl` �?ISO `checkedAt`，未授权探测路径必须�?`/internal/jobs/outbox/relay`�? �?endpoint 结果必须精确覆盖清单，不能缺失、重复或出现未知 endpoint，`summary.total/failed/maxElapsedMs` 必须与明细一致，每个 endpoint 必须返回 200、正�?elapsedMs 和正�?data 类型。开�?DB 诊断时，还必须证明本�?smoke 前后 `source_type != MESSAGE` �?`last_error` �?payload 反序列化失败�?outbox 行数没有增长，避�?Message owner relay 误处�?File 等非 owner 事件。默认产物为 `artifacts/ddd/jobs/job-e2e-smoke.json`�?
该脚本同样会写入 `productionEquivalence`；strict 发布下，内部 job endpoint �?baseURL 也必须来�?HTTPS 且非 localhost 的准生产/生产等价环境。Job E2E contract �?strict 模式下会要求 artifact 自身携带结构�?`productionEquivalence`，避免旧�?localhost job artifact 或手工生成的 JSON 只在最�?gate 才暴露环境缺口。旧�?localhost job artifact 可用于开发回归，但不会解�?strict gate 中的 `job-e2e-environment-strict` blocker�?

Outbox replay/dead-letter 状态机 smoke�?

```bash
DDD_OUTBOX_SMOKE_STRICT=true \
DDD_EVIDENCE_ENVIRONMENT=staging-prod-equivalent \
DDD_RELEASE_CANDIDATE=replace-with-git-sha-or-tag \
DDD_EVIDENCE_OPERATOR=replace-with-operator \
node bin/ddd-outbox-replay-dead-letter-smoke.mjs
```

该脚本运�?System、Message、File、Plugin、Payment �?owner outbox 聚焦测试，必须证�?claim、delivered、失败重试、`retry_count >= 8` 后进�?`DEAD_LETTER`、手�?replay 重置状态后重新投递，以及 relay disabled 时仍允许显式 replay。Outbox evidence contract 会要�?`testedContracts[]` 精确覆盖这六类状态机行为，不能缺失、重复或出现未知契约；每个必需 owner relay surefire report 必须存在，带 reportPath、正�?tests、非�?timeSeconds，且 failures/errors/skipped 均为 0。strict 模式会在 Maven 测试前拒绝缺失或占位的环境、候选版本和执行�?provenance。默认产物为 `artifacts/ddd/outbox/outbox-replay-dead-letter-test-evidence.json`�?

共享 `platform_event_outbox` 污染审计和修复：

```bash
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-outbox-ownership-repair.mjs
```

默认 dry-run 只列�?`source_type != MESSAGE` �?`last_error` �?payload 反序列化失败的行。确认这些行确实是被 Message relay 误处理的�?Message owner 事件后，再执行：

```bash
DDD_OUTBOX_REPAIR_APPLY=true \
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-outbox-ownership-repair.mjs
```

修复动作只把命中的非 Message owner 事件重置�?`RECORDED`、`retry_count=0`、清�?`last_error/next_retry_at/delivered_at`，不删除事件。随后应调用对应 owner replay，例�?File 事件调用 `/file/internal/jobs/outbox/{id}/replay`，并再次执行�?`DDD_JOB_SMOKE_DB_CHECK=true` �?`ddd-job-e2e-smoke`，确�?`crossOwnerPayloadFailuresDelta=0`�?

文件处理端到�?smoke�?

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
SAAS_JOB_INTERNAL_TOKEN=replace-with-runtime-token \
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-file-processing-e2e-smoke.mjs
```

该脚本通过 v2 登录后上传一个小�?`.txt` 文件，调�?`/file/internal/jobs/processing/run` 触发 owner job，再只读查询�?`fileId` �?`file_processing_task` �?`file_processing_artifact`。验收要求：`SECURITY_SCAN`、`TEXT_EXTRACT`、`AI_PARSE` 全部 `SUCCEEDED`，并生成 `SECURITY_SCAN_RESULT`、`TEXT_CONTENT`、`AI_PARSE_READY`。business E2E contract 还会校验 run timing、上�?txt/text/plain 元数据、任�?created/claimed/completed 时间、retryCount=0、lastError 为空、产�?created/updated 时间�?contentLength，以�?pending/failed/dead-letter backlog 不恶化。默认产物为 `artifacts/ddd/file/file-processing-e2e.json`�?

支付 webhook sandbox E2E smoke�?

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node bin/ddd-payment-webhook-e2e-smoke.mjs
```

该脚本通过 v2 登录配置 Stripe sandbox provider，创建支付订单，再对 `/api/v2/payment/webhooks/stripe` 发送真�?HMAC 签名事件、重�?eventId、同 nonce replay 和坏签名事件。验收要求：有效 webhook 将订单推进到 `PAID`，重�?eventId 幂等返回已处理，�?nonce replay 被拒绝，坏签名被拒绝，并�?`payment_order`、`payment_webhook_event` 读取最终状态。business E2E contract 还会校验 provider 包含 webhookSecret 配置字段、provider/order/webhook 场景耗时为正数、创建订单从 `PENDING` 流转�?`PAID`、四�?webhook eventType/processMessage 符合预期、valid 行写�?processedAt，避免只看最终订单状态而漏掉幂等或签名审计缺口。默认产物为 `artifacts/ddd/payment/payment-webhook-e2e.json`�?

AI provider �?owner gateway runtime drill�?

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
node bin/ddd-ai-runtime-drill.mjs
```

在已配置真实�?sandbox LLM/vector provider、IAM/Platform/File owner base URL 和内�?token 的环境，必须开启强制远程验收：

```bash
LUMIRA_BASE_URL=https://staging.example.com \
DDD_AI_EXPECT_PROVIDER_REMOTE=true \
DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true \
node bin/ddd-ai-runtime-drill.mjs
```

该脚本读�?`/api/v2/ai/readiness`、`/api/v2/ai/health`、`/api/v2/ai/metrics`，必须证�?AI v2 chat/search/tool 契约存在，`ai.provider-runtime` �?`ai.remote-owner-gateway` health check 存在，`ai.provider.remote_configured` �?`ai.owner_gateway.configured` metrics 存在。AI runtime contract 要求 PASS artifact �?readiness/health/metrics 三个 endpoint 都返�?200 且有正数耗时：readiness payload 必须包含 `/api/v2/ai/chat`，health payload 必须包含 provider runtime �?remote owner gateway health check，metrics payload 必须包含 provider remote configured �?owner gateway configured 指标。开启远程验收时，provider runtime 必须�?`UP` 且描述包�?`remoteConfigured=true`，owner gateway 必须�?`UP`。脚本会�?provider、chat/embedding model、`remoteConfigured`、是否本�?fallback、configured owner 数量写入 `remoteEvidence`；strict gate 会拒�?`lumira-local` fallback、configured owner 数为 0 �?owner 列表重复的产物。失败时产物会写�?`failureDetails` �?`summary.failureCategories`，`failures[]` 必须�?`failureDetails[].message` 一一对应，且每条 failure detail 必须�?category �?owner，按 `endpoint`、`api-contract`、`health`、`metrics`、`provider-runtime`、`owner-gateway`、`provenance` 分类，并附带 owner 提示用于分派修复。默认产物为 `artifacts/ddd/ai/ai-runtime-drill.json`�?
strict 模式下，AI drill 还会写入结构�?`productionEquivalence`，要�?`LUMIRA_AI_BASE_URL` �?`LUMIRA_BASE_URL` �?HTTPS 且非 localhost；可�?`DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE` 或通用 `DDD_DEPLOYMENT_EVIDENCE` 记录部署单、CI artifact、provider sandbox 日志或对象存储证据引用。AI runtime contract �?strict 模式会要�?artifact 自身携带结构�?`productionEquivalence`，避免旧�?AI runtime JSON 只靠最�?gate 推断环境缺口。readiness summary 会展开 AI baseURL、localOnly、production-equivalence issues、provider remote 配置�?owner gateway configured owner 数，避免�?endpoint 不可达、provider 未远程化、owner gateway 未配置混在一起排查�?

发布证据汇总门禁：

```bash
node bin/ddd-release-evidence-orchestrator.mjs
```

默认 plan mode 只输出证据脚本执行计划到 `artifacts/ddd/release/orchestrator-report.json`，不会运行重型构建、Docker、性能�?runtime smoke。发布流水线需要真正采集证据时使用�?

```bash
DDD_RELEASE_EVIDENCE_STRICT=true \
node bin/ddd-release-evidence-orchestrator.mjs --run --strict
```

编排器会先执行发�?env 文件 lint，再执行发布配置矩阵预检，随后按构建、测试、runtime smoke、可�?authenticated performance baseline 晋级、Outbox replay/dead-letter、回滚校验、EXPLAIN、manifest provenance preflight、manifest、release gate、readiness summary 的顺序执行；即使中间步骤失败，也会继续尝试生成后续汇�?artifact，便于一次性看到完整缺口。运行中会持续刷�?`orchestrator-report.json`，避�?manifest �?release gate 读取旧报告。传�?`--strict` 时，编排器会�?`DDD_RELEASE_EVIDENCE_STRICT=true` 传给所有子步骤，并�?Outbox 状态机 smoke 追加 `DDD_OUTBOX_SMOKE_STRICT=true`，让 env/config/build/test/runtime/manifest evidence 在生成阶段就执行 strict provenance 和生产等价校验。orchestrator preflight 会先检�?provenance、`DDD_RELEASE_ENV_FILE`、后�?runtime baseURL、AI runtime baseURL、前�?baseURL、`DDD_FRONTEND_EXPECT_DEPLOYED`、Docker CLI/daemon、AI remote 期望和迁移演�?env；后端、AI 和前�?baseURL 必须�?HTTPS 且非 localhost。preflight 契约固定包含 11 �?check ID：`release-provenance`、`release-config-env-file`、`backend-runtime-base-url`、`ai-runtime-base-url`、`lumira-ui-runtime-base-url`、`lumira-ui-deployed-expectation`、`docker-cli`、`docker-daemon`、`ai-provider-remote-expectation`、`ai-owner-gateway-remote-expectation`、`migration-runtime-evidence`，并要求每项不能缺失、重复或未知，`status` 只能�?`PASS`、`WARNING`、`BLOCKER`，且必须带有 `detail` 和非�?`envKeys`；`blockers/warnings` 计数�?preflight `status` 必须与明细一致。strict release gate 会要求该报告�?run 模式、带 provenance、包�?26 个预期步骤，并且每个 selected step 都有同序执行结果；selected step �?executed result 不能重复或未知，step 必须�?`label`、`command` 和非�?`envKeys`，result �?`status` 必须为数字，`skipped` 必须为布尔值；其中可�?baseline 晋级步骤即使未开启也必须�?skipped 结果入报告，最�?`release-gate` �?`readiness-summary` 也必须出现在执行结果中。preflight 中的每个 `BLOCKER` 会被提升�?strict release gate �?blocker，因�?Docker daemon、env file、部�?URL 或迁移演�?env 缺口都会在最终门禁里直接可见。Outbox 步骤还必须带�?`DDD_OUTBOX_SMOKE_STRICT`，manifest provenance preflight 步骤必须带有 `DDD_RELEASE_MANIFEST_CHECK_ENV`。readiness summary 会列�?orchestrator �?selected/executed step 数、未执行步骤、strict env keys 和从 plan 进入 run 的恢复动作�?

```bash
DDD_RELEASE_MANIFEST_STRICT=true \
node bin/ddd-release-evidence-manifest.mjs
```

该脚本生�?`artifacts/ddd/release/evidence-manifest.json`，记录关�?artifact �?SHA-256、大小和时间戳，并验�?EXPLAIN JSON 文件存在。设�?`DDD_RELEASE_MANIFEST_CHECK_ENV=true` 时只执行 provenance/artifact/EXPLAIN 预检，并写出 `artifacts/ddd/release/evidence-manifest-preflight.json`，报告只包含 key 是否存在、artifact 摘要、blocker 和脱�?next action，不包含 secret。strict 模式会要�?manifest 自身带有环境、版本和执行�?provenance，并统计关键 artifact �?provenance 缺失；manifest provenance 还会拒绝 `local-dev`、`local-worktree`、`local-operator` 等本地诊断值，防止本地证据被误当成生产等价发布证据。manifest contract 会复�?`summary.requiredArtifacts`、`presentArtifacts`、`optionalArtifacts`、`invalidJsonArtifacts`、`provenanceIssueArtifacts`、`explainFiles`、`blockers` �?artifact/EXPLAIN/blockers 明细一致，并要�?`status` �?blockers 明细一致。`blockers[]` 会由合同按缺�?artifact、无�?JSON、strict provenance、可选审�?artifact contract issue �?EXPLAIN 状态重新计算并逐项比对；`artifacts[]` 不能包含未知 `relativePath`，避免人工追加或删除 blocker 掩盖缺失证据。每�?present artifact 还必须有正数 `bytes`�?4 �?hex `sha256`、完整且可解析的 `timestamp.field/value`，且 `relativePath` 不能重复，避免人工编辑或旧脚本生成的 checksum 清单掩盖缺失证据。`artifacts/ddd/release/release-final-owner-queue-run-report.json` �?`artifacts/ddd/release/explain-gate-report.json` 存在时会作为 optional checksum artifact 纳入 manifest，并分别校验 run report contract �?EXPLAIN gate report contract；不存在不阻断。GitHub release evidence workflow 会在 capture preflight 并校�?capture contract 后、上�?artifacts 前再次刷�?manifest preflight、manifest、readiness summary �?unblock brief，确�?preflight 期间重写�?redacted handoff contract report 和最�?provenance preflight report 都进入最�?checksum。manifest 不包�?release gate 自己的报告，也不包含运行中持续变化的 orchestrator report，避免循环依赖和过期 checksum；strict release gate 会单独要�?manifest 存在�?PASS，并单独验证 orchestrator report�?

```bash
node bin/ddd-release-evidence-gate.mjs
```

发布前必须使�?strict 模式；strict 模式要求 AI runtime drill、生产量�?EXPLAIN、带验收元数据的 authenticated performance baseline、部署环境前�?smoke 等关键证据存在，�?readiness、性能、File、Payment、Job、AI、Frontend 等运行时证据不能只来�?localhost。AI drill 必须是远�?provider 和远�?owner gateway 强制验收产物�?

```bash
DDD_RELEASE_EVIDENCE_STRICT=true \
DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS=24 \
node bin/ddd-release-evidence-gate.mjs
```

默认 advisory 模式会读�?`artifacts/ddd/**` 中已有证据并输出缺口，不阻断本地开发。strict 模式用于准生产或发布流水线，缺失 artifact、本�?only artifact、失败测试、不满足远程 provider/owner gateway 要求、orchestrator preflight `BLOCKER` 或证据超�?freshness 窗口都会变成 blocker。默认产物为 `artifacts/ddd/release/release-evidence-gate.json`。release gate contract 会复�?`summary.checks/blockers/warnings` �?`checks[]`、`blockers[]`、`warnings[]` 明细一致，要求 check 状态只能是 `present`、`warning`、`blocker`，且每项必须�?`name` �?`detail`；readiness summary 会把�?contract issue 展开�?diagnostics，避免手工编�?gate JSON 掩盖真实 blocker。freshness 默认窗口�?24 小时，可�?`DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS` 调整；authenticated performance baseline 是已验收历史基线，不�?freshness 限制�?

如果 strict gate 失败，生成面向执行者的行动摘要�?

```bash
node bin/ddd-release-readiness-summary.mjs
```

默认输出 `artifacts/ddd/release/readiness-summary.json` �?`artifacts/ddd/release/readiness-summary.md`，会�?Docker、性能 baseline、回滚演练、配置、前�?smoke、AI runtime、迁移、运行时 provenance 等类别归�?blocker 和建议命令。runtime readiness 会展开 baseURL、localOnly�?0 �?owner readiness/health/metrics 覆盖和逐上下文 ready 状态；File/Payment/Job 业务 E2E 会展开上传处理任务、webhook 幂等/签名场景、内�?job endpoint 和跨 owner outbox 诊断；配置缺口会展开�?owner、group、envKeys �?reason，并显示 `configContractIssue`；authenticated performance 会展开 actual/baseline 诊断�?baseline 晋级 envKeys；迁移运行证据会展开�?fresh DB drill、old DB upgrade drill、环境、版本、执行人和完成时间等 `runtimeDiagnostics`。摘要自身还会写�?`inputArtifacts`，记�?release gate、manifest、配置、迁移、Docker、runtime smoke、owner queue run report 等输入文件的路径、mtime、generatedAt �?blocker/warning/status 计数；owner/source/priority/batch/missing-env 衍生工件也会携带同一份输入元数据，发布审计时应确认这些工件引用的是同一�?`release-evidence-gate.json`。摘要还会写�?`diagnostics.readinessSummary.contractIssues`，复�?`status`、gate blocker 计数、行动项�?owner/category 分组是否一致；release config contract issue 非空时摘要状态也会保�?`NOT_READY`，应先修复配�?artifact 或重新生成配置证据。Release Env Lint �?Release Config Blockers 段会显示 `primaryBlockers`、`releaseConfigBlockersFromPlaceholders` �?`releaseConfigBlockersAfterPlaceholders`：先修主阻塞和占位输入，再分派占位替换后仍存在的配置缺口；其�?repo �?env 文件路径会显示为相对路径，home 目录下路径会显示�?`~`，避�?Markdown 泄漏本机用户名或 workspace 绝对路径。长 envKeys 会折成多行，便于复制�?secret store 或发布工单。该摘要只用于排障和分派，不替代 strict release gate�?

`release-blocker-map.json`、`.csv` �?`.md` 会把 strict release gate �?raw blockers 同时�?category �?owner 聚合，显示每�?每个 owner �?blocker 分布、候�?batch、ready/blocked batch、命令和预期产物。CSV 一行一�?owner，适合导入工单或飞书表格快速分派。它用于把大�?blocker 拆成可派发的工作包，例如 production-equivalent runtime、release env/config、rollback drills、EXPLAIN �?lumira-ui smoke；它不会减少 blocker 数，也不替代 owner/action priority，只是把当前 gate �?raw blocker 列表映射到执行队列�?

快速上线解阻优先使用单页入口：

```bash
node bin/ddd-release-unblock-brief.mjs
node bin/ddd-release-unblock-brief-contract.mjs
```

默认输出 `artifacts/ddd/release/release-unblock-brief.json` �?`.md`。该 brief 会把 final go/no-go、release env owner handoff、owner input receipt、performance baseline closure、cutover blocked items、execution waves 和前 5 �?`RUN_NOW` next action 合成一页；发布负责人先看它确认当前第一�?owner action、第一�?env owner action、owner 输入回执状态、性能 baseline 状态、P0/P1/P2/P3 波次依赖�?strict no-go 原因。brief 只显�?key 名、owner、criteria、相对路径和脱敏命令，不�?secret，也不允许本机绝对路径；它是执行入口和分派视图，不是 waiver�?

当前波次执行时只复制 brief �?`Wave operator commands` 的可运行波次。通常先处�?`P0`，例如：

```bash
DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh
DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh
DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh
DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh
```

`P1`、`P2`、`P3` 若在 brief 中显�?blocked-until dependencies，不能提前执行；必须等前�?wave �?expected artifacts 刷新、exit criteria 满足，并重新运行 strict gate/readiness summary 后再进入下一层。所有波次完成后仍必须调用硬门禁�?

```bash
DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh
```

只有该脚本读取到 `cutoverAllowed=true` 才允许切流；NO-GO 会以退出码 10 阻断�?

同一脚本还会输出 `release-fast-track.*`、`release-cutover-checklist.csv`、`release-cutover-owner-matrix.*`、`release-sprint-board.*`、`release-command-catalog.*`、`release-owner-handoff.*`、`release-owner-receipts.*`、`release-next-action-queue.*`、`release-blocker-closure-plan.*`、`release-env-owner-matrix.*`、`release-action-priority.*`、`release-action-batches.*`、`release-action-dependency-graph.*` �?`release-execution-queue.*`。快速上线时先看 `release-fast-track.md`：它只给最短安全路径和 `NO_GO_STRICT`/`GO_STRICT` 决策，保�?`noAutoWaivers=true`，不会替�?strict release gate，也不会允许跳过安全、迁移、回滚、生产等价、性能或数据库计划证据；其�?`cutoverChecklist` 会把 strict gate、环境、镜像、生产等价、迁移、运行时/业务验收、回滚、EXPLAIN、manifest/orchestrator 逐项标成 `PASS` �?`BLOCKED`，上线切流前必须全部�?`PASS`。`release-unblock-brief.json` �?`.md` 是发布负责人当前最短路径的单页入口，会合并 final go/no-go、redacted env owner handoff、owner input receipt、authenticated performance baseline closure，以�?`release-next-action-queue` 中前 5 �?`RUN_NOW` owner action；它会展�?owner env blockers、owner input receipt pending owners �?missing criteria、`Performance Baseline` 状态和 `Next Action Queue`，但只保�?key 名、owner、criteria、相对路径和脱敏命令，不包含 secret、不允许本机绝对路径，也不能作为 waiver。`release-cutover-checklist.csv` 是同一 checklist 的表格版，适合导入发布审批或切流会议清单；`release-cutover-owner-matrix.json`、`.csv` �?`.md` 会把 checklist 再按 owner 反查�?batch、命令、env alias group、预期证据和退出标准，适合发布负责人直接按团队分派，且不新增人工状态，避免�?fast-track 决策漂移。`release-sprint-board.json`、`.csv` �?`.md` 是发布战情看板，会把 priority、owner、ready/blocked 状态、依赖、cutover 项、命令和预期证据合并�?batch card，用�?standup、工单导入和下一波并行执行；`release-command-catalog.json`、`.csv` �?`.md` 会把同一 next wave 生成 owner、priority、batch 三种粒度�?list、env-check、dry-run �?execute 命令，适合直接复制到发布群或工单。`release-owner-handoff.json`、`.csv` �?`.md` 会把 sprint board、command catalog �?env owner matrix 合并�?owner 级交接包，逐个 owner 展示 ready/blocked batch、阻塞依赖、env keys、可复制命令、预期证据和退出标准，是快速上线分派工单或飞书任务的首选入口。`release-owner-receipts.json`、`.csv` �?`.md` 是执行后�?owner 回执视图，会检�?handoff 中的预期 artifact 是否已落盘，并合�?owner action blocker，区�?`ARTIFACT_MISSING`、`CONTENT_BLOCKED`、`READY_FOR_STRICT_GATE_RERUN` �?`WAITING_ON_DEPENDENCIES`；它用于快速定位下一步，不替�?strict release gate。`release-next-action-queue.json`、`.csv` �?`.md` 会把 receipts 压成�?owner 排序的下一步队列，优先展示可立即执行的�?artifact 和内�?blocker，适合发布负责人直接分派当前最短处理顺序，并通过 `executableCommands` 提供可复制执行入口；`release-next-action-commands.sh` 会按 RUN_NOW 队列生成安全默认 dry-run 脚本，支�?`DDD_RELEASE_NEXT_ACTION_LIST=1` 只列队列、`DDD_RELEASE_NEXT_ACTION_DETAIL=1` 查看单项详情，并支持 `DDD_RELEASE_NEXT_ACTION_ORDER`、`DDD_RELEASE_NEXT_ACTION_OWNER` 过滤，只有设�?`DDD_RELEASE_NEXT_ACTION_EXECUTE=1` 才实际执行；它同样不能作�?waiver。`release-blocker-closure-plan.json`、`.csv` �?`.md` 会把每个 priority action 归类�?`RUN_NOW_LOCAL`、`RUN_NOW_WITH_REAL_ENV` �?`WAIT_FOR_DEPENDENCIES`，并列出 owner、batch、依赖、env keys、命令、预期证据和退出标准，适合发布负责人快速区分本地可推进项与必须真实 HTTPS/CI/secret 环境采集的项；它只派生自 priority/batch，不新增 waiver。`release-env-owner-matrix.json`、`.csv` �?`.md` 会把 `release-env-missing.json` 里的 canonical env key、alias mapping、ready/blocked batch、命令、预期证据和退出标准按 owner 聚合，适合�?`.env.release` �?CI secret 补齐工作分派�?release-infra、database、release-performance �?owner；它只列 key 和命令，不包�?secret 值。它们同样从 batches、execution queue、missing env �?owner matrix 派生，不能作�?waiver。执行发布修复时再看 execution queue：`Ready Now` 只列当前 `canRunImmediately=true` 的批次，并直接带出命令、env keys、`expectedArtifacts` �?`exitCriteria`；`Blocked Later` 列出被依赖阻塞的批次和仍需完成的前�?batch。`release-execution-queue.csv` 一行一�?batch，包�?`queueStatus`、`dependsOn`、`commands`、`expectedArtifacts` �?`exitCriteria`，适合导入 Jira、飞书表格或发布排期表。`release-execution-commands.sh` 是从 Ready Now 批次生成的可复制命令清单，会先切到仓库根目录（可�?`LUMIRA_REPO_ROOT` 覆盖），`DDD_RELEASE_LIST_BATCHES=1` 可在不提�?secret 的情况下列出当前 ready batch，并支持同一�?`DDD_RELEASE_BATCH`、`DDD_RELEASE_OWNER`、`DDD_RELEASE_PRIORITY` 过滤器先预览执行范围；正式执行时会检�?`DDD_RELEASE_ENV_FILE` 已设置、文件存在且不是 `release-env-missing.template.env`，然后用 safe dotenv loader 加载真实发布配置；该 loader 只接�?`KEY=value` �?`export KEY=value`，不会执�?env 文件中的 shell 语句；设�?`DDD_RELEASE_BATCH=<batch-id>` 时只执行匹配�?ready batch，设�?`DDD_RELEASE_OWNER=<owner>` 时只执行�?owner 当前 ready batch，设�?`DDD_RELEASE_PRIORITY=P0|P1|P2|P3` 时只执行该优先级当前 ready batch，这些过滤器可以组合使用且必须同时匹配，未匹配会失败并提示；设置 `DDD_RELEASE_DRY_RUN=1` 时只打印将执行的命令，不生成或刷新证据；设置 `DDD_RELEASE_CHECK_ENV_ONLY=1` 时只执行 env group 预检并跳过证据命令，适合在正式采集前先确�?secret 注入是否完整；缺少任�?env group 默认会失败，只有显式设置 `DDD_RELEASE_ALLOW_MISSING_ENV=1` 才会继续，这个开关只用于本地诊断，不能用于正式上线。env group 预检�?release config contract 的别名组判断，例�?`LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`，组内任意一�?key 存在即视为该组已覆盖，避免别名变量产生误报。它不会内嵌 secret，也不能替代真实证据 artifact，执行后仍要重跑 release gate �?readiness summary。batches 则保留完整明细：每批都有稳定 `id`、`dependsOn`、`canRunImmediately`、命令提示、所需 env keys、`envCheckGroups`、`expectedArtifacts` �?`exitCriteria`，JSON、CSV �?Markdown 都会展示这些字段；`release-action-batches.csv` 是全量批次表，适合�?P0/P1/P2/P3 全部导入工单系统，和只展示当�?ready/blocked 队列�?`release-execution-queue.csv` 配套使用。依赖图工件�?batches 派生，JSON 给工�?自动化读�?`nodes`、`edges`、`readyBatchIds`、`blockedBatchIds`、`executionLevels`、`graphDensity` �?`compressedEdges`，每�?node 也携带同批次�?`envKeys` �?`envCheckGroups`；Markdown 会先输出�?P0/P1/P2/P3 聚合的压�?Mermaid 图，再保留完�?DAG。发布负责人可先�?execution queue 分派当前可做事项，用压缩图理解后续层级，再进�?batch 明细确认命令和退出标准，自动化仍可读取完整边追踪每个 batch 的前置条件。P0 批次可并行准备；P1/P2/P3 会通过 `dependsOn` 指向前置批次，执行人应在依赖批次退出标准满足并重新运行 release gate 后再进入下一层。命令提示由 source/owner 的标准证据流程和行动项中的命令共同生成，不能只依�?blocker 文案；例�?authenticated performance batch 会同时提示先�?`ddd-authenticated-performance-smoke` 再晋�?baseline，lumira-ui smoke batch 会同时提�?Playwright smoke �?evidence 转换。`expectedArtifacts` �?owner 精确到产物，File、Job、Payment �?business E2E 批次分别对应 `file-processing-e2e.json`、`job-e2e-smoke.json`、`payment-webhook-e2e.json`，manifest 批次会同时列�?checksum manifest 和实际缺�?artifact（例�?authenticated runtime baseline 及其 promotion audit），避免执行人只重跑 manifest 而没有补根因证据。命令只是生成证据的入口；只有对�?artifact 已刷新、退出标准满足，且重新运�?release gate 后该�?blocker 消失，才进入下一批。P3 orchestrator batch 永远放在最后，用于 strict run 模式复核，不能用来替代前�?P0/P1/P2 证据采集�?

`release-owner-input-receipt.json`、`.csv`、`-items.csv`、`-items.md` �?`.md` 会把 owner input collection 的当前验收状态输出成脱敏回执：JSON �?gate/contract 使用，`.csv` 用于导入工单或表格�?owner 关闭，`-items.csv` 一行一�?canonical key，用于逐项关闭 34 个输入，`-items.md` 是同一逐项清单�?Markdown checklist，主 Markdown 给发布负责人阅读汇总。`release-owner-input-receipt-items/` 目录会再�?owner 拆出逐负责人 checklist，适合直接分派�?platform-events、platform-owners、release-infra、ai-owner �?payment-owner。这些文件只�?owner、key 名、alias、输入数量、remaining placeholders/missing、packet/handoff 相对路径�?pass criteria，不包含 secret 或真�?env 值�?

`release-next-action-queue.json`、`.csv` �?`.md` 也会透出 owner input receipt �?`PASS/PENDING_OWNER_INPUT`、pending owner、missing criteria、required owner input 数和�?owner checklist 路径，保证最短行动队列与 final owner queue 使用同一�?owner 输入状态。该信息只用于分派和验收；owner input receipt �?`PASS` 时仍不能切流�?

`release-blocker-closure-plan.json`、`.csv` �?`.md` 同样展示 owner input receipt �?`PASS/PENDING_OWNER_INPUT`、pending owner、missing criteria �?required owner input 数，并由 readiness summary contract 要求它与 next-action queue �?receipt 摘要一致。它只用于分派和排障，不�?waiver；receipt �?`PASS` �?final go/no-go 仍必须保�?`NO_GO_STRICT`�?

`release-next-action-commands.sh` �?dry-run、list �?detail 模式不需�?secret；设�?`DDD_RELEASE_NEXT_ACTION_CHECK_ENV=1` 时只�?safe dotenv loader 读取真实 `DDD_RELEASE_ENV_FILE` 并检查当�?RUN_NOW 项缺少哪�?key，不执行证据命令、不输出 secret 值。正式执行时必须设置 `DDD_RELEASE_NEXT_ACTION_EXECUTE=1` 且提供真�?`DDD_RELEASE_ENV_FILE`。脚本会拒绝缺失文件�?`release-env-missing.template.env`，再用同一 safe dotenv loader 解析 env 文件执行队列命令；该 loader 只接�?`KEY=value` �?`export KEY=value`，不会执�?env 文件中的 shell 语句。执行模式会写出 `artifacts/ddd/release/release-next-action-run-report.json`，也可用 `DDD_RELEASE_NEXT_ACTION_REPORT` 覆盖；报告记�?order、owner、receiptStatus、command、status、durationMs �?finishedAt，并自动运行 `node bin/ddd-release-next-action-run-report-contract.mjs` 校验 PASS/FAIL、退出码�?summary 计数；`node bin/ddd-release-next-action-run-report-summary.mjs` 可把报告追加�?GitHub Step Summary 或输�?Markdown。批量诊断时可显式加 `DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR=1` 继续执行后续命令并收集更多失败信息；只要任一命令失败，脚本结尾仍会非零退出，不能作为上线成功依据�?

`release-execution-commands.sh` 的真实执行模式会写出 `artifacts/ddd/release/release-execution-run-report.json`，也可用 `DDD_RELEASE_EXECUTION_REPORT` 覆盖；报告记�?batchId、owner、priority、command、status、durationMs �?finishedAt，并自动运行 `node bin/ddd-release-execution-run-report-contract.mjs` 校验 PASS/FAIL、退出码、summary 计数和命令脱敏。`node bin/ddd-release-execution-run-report-summary.mjs` 可把报告追加�?GitHub Step Summary 或输�?Markdown，适合发布群快速查看哪�?batch/owner/priority 失败。dry-run �?env-check-only 不写该报告，避免把预演误当成真实执行证据；该报告仍只是排障和审计辅助，不替代 expected artifacts、strict gate �?final go/no-go�?

`release-blocker-closure-commands.sh` �?blocker closure plan 的命令入口，默认只列 `RUN_NOW_LOCAL` �?`RUN_NOW_WITH_REAL_ENV` 项，不执行。可�?`DDD_RELEASE_CLOSURE_DETAIL=1` 查看单项详情，用 `DDD_RELEASE_CLOSURE_CHECK_ENV=1` 只检查所需 key，用 `DDD_RELEASE_CLOSURE_EXECUTE=1` 执行；支�?`DDD_RELEASE_CLOSURE_ORDER`、`DDD_RELEASE_CLOSURE_OWNER`、`DDD_RELEASE_CLOSURE_PRIORITY`、`DDD_RELEASE_CLOSURE_KIND` 过滤。env-check �?execute 都必须提供真�?`DDD_RELEASE_ENV_FILE`，并拒绝模板文件；脚本只打印 key 名，不打�?secret 值。批量诊断时可显式加 `DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=1` 继续执行后续命令；只要任一命令失败，最终仍会非零退出�?

`release-execution-commands.sh` �?`release-final-owner-queue-commands.sh` 也支持同样的诊断语义：分别设�?`DDD_RELEASE_CONTINUE_ON_ERROR=1` �?`DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR=1` 后，命令失败会被记录并继续尝试后续可执行项，但最终仍以失败退出。该能力只用于快速收集完整失败面，不能降�?strict gate、性能、迁移、回滚、生产等价或安全证据要求�?

`release-closure-wave-env-matrix.json`、`.csv` �?`.md` 会按 closure wave 汇�?owner、batch、env keys、命令和预期证据；`release-closure-wave-env.template.env` 是按 wave 分段的安全模板，只放 key �?`__REQUIRED__` 占位符，不放 secret 值。真实环境执行前先按该模板补�?`.env.release`，再�?`DDD_RELEASE_CLOSURE_CHECK_ENV=1` 预检�?

`release-closure-wave-receipts.json`、`.csv` �?`.md` 会按 wave 检�?expected artifacts 是否已经落盘，并标出 `ARTIFACT_MISSING`、`CONTENT_BLOCKED` �?`READY_FOR_STRICT_GATE_RERUN`；`CONTENT_BLOCKED` 表示 artifact 已存在但 provenance、baseline 或内容合同仍未满足，`blockerHints` 会保留具体原因且不会被误列为缺失文件路径。每�?wave 都带 `node bin/ddd-release-evidence-gate.mjs` �?`node bin/ddd-release-readiness-summary.mjs` 复核命令。它只帮助执行后验收，不替代 strict gate。`release-final-go-no-go.*` �?`release-final-owner-queue.*` 会继续透传 content blockers，方�?CI 日志�?owner 分派直接定位内容问题�?

`release-closure-wave-blocker-map.json`、`.csv` �?`.md` 会把每个 closure wave 回连�?action id、source、owner、命令、预期证据和候�?strict blocker hint。候�?blocker 只用于追踪执行影响范围，真实放行仍以重新运行 strict release gate �?blocker 消失为准�?

`release-performance-baseline-closure.json` �?`.md` 专门收敛 authenticated performance baseline：它会列出当�?actual、baseline promotion、baseline 缺口、不能晋级的 blocker、所需 env keys 和推荐命令。baseline 只能从生产等价、非本地、无失败�?actual 晋级，不能手工伪造�?

`release-performance-baseline-commands.sh` 是同一闭环的安全执行入口：默认输出 detail/dry-run，`DDD_AUTH_PERF_BASELINE_CHECK_ENV=1` 只检�?env key，`DDD_AUTH_PERF_BASELINE_EXECUTE=1` 才执行性能采集、baseline 晋级、manifest、release gate �?readiness summary。它必须使用真实 `DDD_RELEASE_ENV_FILE`，拒绝生成模�?env 文件，并要求真实 env 文件权限收紧�?`600`�?

`release-final-go-no-go.json`、`.csv` �?`.md` 是发布负责人最后看的单页包：它合并 strict gate、fast-track cutover checklist、closure wave receipts、closure blocker map �?authenticated performance baseline closure。只�?strict gate �?0、cutover checklist �?PASS、closure wave 回执�?ready、性能 baseline READY 时才会给 `cutoverAllowed=true`；否则会列出 stop reasons 和下一批命令。它不提�?waiver�?

`ddd-release-artifact-path-leak-contract.mjs` 默认扫描 21 个关�?release evidence 文件，覆�?JSON、CSV 和发布负责人会阅读的 Markdown，包�?`readiness-summary.md`、`release-final-go-no-go.md`、`release-config-owner-input-reconciliation.json`、`release-owner-input-receipt.json`、`release-owner-input-receipt.csv`、`release-owner-input-receipt-items.csv`、`release-owner-input-receipt-items.md`、`release-owner-input-receipt.md`、`release-unblock-brief.md`、`evidence-manifest-preflight.json` �?`release-performance-baseline-closure.md`。任�?repo root、runner home 或用�?home 绝对路径都会阻断 preflight；如需展示 env 文件或日志位置，应使�?repo-relative 路径、`~` 或外部可审计链接。`release-preflight-gate.sh` 会先运行 `DDD_RELEASE_MANIFEST_CHECK_ENV=true node bin/ddd-release-evidence-manifest.mjs` 生成 manifest provenance preflight report，再运行 artifact path leak contract 扫描�?report；strict preflight 会在 path leak 通过后对 manifest provenance 缺口早停，避免继续执行较重的 env/final gate 而掩盖根因。随后它还会�?env readiness 前运�?`ddd-release-env-owner-handoff-redacted-contract.mjs`，校�?redacted owner handoff �?JSON、CSV、�?Markdown 和�?owner 目录文件完全一致，拒绝 stale owner 文件、路径穿越、具�?env 赋值、占位符、DSN �?token，避免快速上线时按旧分派包执行；�?contract 会写�?`artifacts/ddd/release/release-env-owner-handoff-redacted-contract.json`，存在时会被 evidence manifest 作为 optional checksum artifact 收录并校�?`PASS`、`redacted=true`、`issueCount=0`。CI 会在 preflight capture 后执行最后一�?manifest refresh，防止该 JSON �?preflight 中刷新后未被最终上传的 checksum manifest 覆盖�?

`release-final-go-no-go-gate.sh` 是给 CI/CD 或发布切流步骤调用的硬门禁。默认运行只打印当前 go/no-go 决策；设�?`DDD_FINAL_GO_NO_GO_ENFORCE=1` 时，如果 `cutoverAllowed=false` 会返回退出码 10，从而阻止切流。需要先运行 `node bin/ddd-release-readiness-summary.mjs` 刷新 final packet�?

`release-final-owner-queue.json`、`.csv` �?`.md` 会把 final go/no-go �?stop owners 展开�?owner 级工单队列，包含 cutover item、ready/blocked batch、closure wave、缺�?artifact、首条命令和 rerun 命令；同时透出 owner input receipt �?`PASS/PENDING_OWNER_INPUT`、pending owner 数、missing criteria、owner input 数和�?owner checklist 路径，适合 CI 失败后直接分派。该队列只是执行入口；owner input receipt �?`PASS` 时仍不能切流�?

`release-final-owner-queue-commands.sh` 默认只列当前 `ACTIONABLE` owner，支�?`DDD_FINAL_OWNER_QUEUE_OWNER`、`DDD_FINAL_OWNER_QUEUE_STATUS`、`DDD_FINAL_OWNER_QUEUE_DETAIL` 过滤和查看明细；设置 `DDD_FINAL_OWNER_QUEUE_CHECK_ENV=1` 时会静态解析真�?`DDD_RELEASE_ENV_FILE` 中该 owner 所需 env key，不 `source` 文件、不执行其中内容；真�?env 文件权限必须收紧�?`600`，否�?check/execute 都会拒绝继续；只有设�?`DDD_FINAL_OWNER_QUEUE_EXECUTE=1` 才通过 safe dotenv loader 加载 env 并执行命令�?

执行模式会写出机器可读审计报告，默认路径�?`artifacts/ddd/release/release-final-owner-queue-run-report.json`，也可用 `DDD_FINAL_OWNER_QUEUE_REPORT` 覆盖。报告包�?owner/status 过滤条件、整体退出码、`summary.totalEntries/succeededEntries/failedEntries` 汇总计数，以及每条已执行命令的 `commandIndex/commandCount`、状态和耗时；即使命令中途失败，也会通过 exit trap 写出 `FAIL` 报告�?

执行脚本写出报告后会自动调用 `node bin/ddd-final-owner-queue-run-report-contract.mjs` 校验 run report �?schema、状态和退出码一致性，并复�?`summary` 计数必须�?`entries` 明细一致；每条 entry 还必须保�?`queueOrder`、`queueStatus`、`commandIndex` �?`commandCount`，用于把实际执行命令追溯�?final owner queue 的优先级、可执行状态和 owner 内命令位置。也可以单独运行�?contract 复核历史报告。该 contract 只保证执行审计可被机器消费，不替�?owner evidence artifact、strict release gate 或最�?go/no-go gate�?

`release-final-owner-queue-env.template.env` 会按 owner 分段列出上述命令入口需要的 env key，值只使用 `__REQUIRED__` 占位，不包含 secret。不要把填好真实值的文件提交到仓库�?

可用 `release-final-owner-queue-env-init.sh` 从模板初始化本地真实 env 文件。默认目标为 `.env.release.local` �?`DDD_RELEASE_ENV_FILE`，文件权限会设为 `600`，且默认拒绝覆盖已存在文件；只有在完成备份后才允许设�?`DDD_FINAL_OWNER_QUEUE_ENV_FORCE=1`�?

发布流水线执行所�?runtime smoke 前应统一导出证据来源�?

```bash
export DDD_EVIDENCE_ENVIRONMENT=staging-production-equivalent
export DDD_RELEASE_CANDIDATE="$(git rev-parse HEAD)"
export DDD_EVIDENCE_OPERATOR="${USER:-release-bot}"
```

backend/lumira-ui build/test、readiness、authenticated performance、File、Payment、Job、AI、Docker �?Frontend smoke artifact 会把这些值写�?`sourceEnvironment`、`releaseCandidate`、`evidenceOperator`。strict release gate 会要求这些证据具备三项真实非占位值，避免无法追溯证据来自哪个环境、哪个版本、由谁执行。Frontend smoke contract 也会要求 deployed smoke artifact 自身携带结构�?`productionEquivalence`，缺失该字段的旧前端 smoke JSON 不能作为 strict 发布证据�?

`doc/36-ddd-release-env-template.env` 是发�?secret 填写模板，不是可直接放行的配置证据。`artifacts/ddd/release/release-env-missing.template.env` �?readiness summary 根据当前缺口生成的临时填写清单，也不是证据；如果把它传给 lint，artifact 会标�?`inputKind=generated-missing-template` �?`generatedMissingTemplate=true`，release gate 只会要求提供真实已填写的 `DDD_RELEASE_ENV_FILE`。未�?`DDD_RELEASE_ENV_FILE` 时，lint 会明确标�?`inputKind=process-environment-only`，只从当前进程环境读取可见变量；这适合本地诊断当前缺口，但不能替代真实 `.env.release`。lint artifact 会优先使用显�?`DDD_EVIDENCE_ENVIRONMENT`、`DDD_RELEASE_CANDIDATE`、`DDD_EVIDENCE_OPERATOR`，本地缺省时会推�?`local-dev`、当�?git commit 和当前用户，减少�?provenance 噪音；发布流水线仍应显式传入真实环境、版本和执行人。`release-env-missing.json` 的每�?group 会携带同批次�?`batchId`、`dependsOn`、`canRunImmediately`、`commands`、`expectedArtifacts` �?`exitCriteria`，适合直接转成发布工单而不必再回查 `release-action-batches.json`；生成的 `.template.env` 也会把这些字段写成注释，方便�?env 文件的人同时看到每批的依赖、执行命令、预期产物和退出标准。当 lint 输入不是 `release-env-file`（包括缺�?env file、process environment only �?generated missing template）时，readiness summary 会先�?release-config 的逐项缺失折叠�?env file 根因，避免在真实 env 文件出现前生成一�?owner 配置噪音批次。`bin/ddd-release-config-template.test.mjs` 会验证模板覆盖所有必�?key，同时确认占�?URL/secret 会被 strict config evidence 拒绝；真实发布必须使用替换后�?`.env.release` �?GitHub environment secrets�?

历史 runtime artifact 如果已经采集成功但缺少来源元数据，或缺少�?`baseUrl` 推导的结构化 `productionEquivalence`，可以显式执�?metadata-only backfill。下面的 `local-*` 示例只适合本地诊断或历史证据标注，不能作为 strict manifest 自身的发�?provenance�?

```bash
DDD_RUNTIME_PROVENANCE_BACKFILL=true \
DDD_EVIDENCE_ENVIRONMENT=local-dev \
DDD_RELEASE_CANDIDATE=local-worktree \
DDD_EVIDENCE_OPERATOR=codex \
node bin/ddd-backfill-runtime-provenance.mjs
```

若只需要给�?artifact 补结构化生产等价元数据，可执行：

```bash
DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL=true \
node bin/ddd-backfill-runtime-provenance.mjs
```

该脚本只写入 `sourceEnvironment`、`releaseCandidate`、`evidenceOperator`、`provenanceBackfilledAt`、`provenanceBackfillReason`，以及可选的 `productionEquivalence`、`productionEquivalenceBackfilledAt` �?`productionEquivalenceBackfillReason`，不会改�?`status`、性能数值、失败列表或业务结果。默认拒绝覆盖已有不�?provenance；只有显式设�?`DDD_RUNTIME_PROVENANCE_BACKFILL_OVERWRITE=true` 才允许覆�?provenance，显式设�?`DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL_OVERWRITE=true` 才允许覆盖已�?`productionEquivalence`。它只能让既有本地证据可追溯、可�?strict contract 识别为本地证据，不能�?localhost 证据升级为生产等价证据�?

后端测试证据归档�?

```bash
./mvnw test
DDD_BACKEND_TEST_EVIDENCE_STRICT=true \
node bin/ddd-backend-test-evidence.mjs
```

该脚本读取各 Maven module �?`target/surefire-reports/TEST-*.xml`，汇总为 `artifacts/ddd/tests/backend-test-evidence.json`。strict 模式会要�?artifact 带有环境、版本和执行�?provenance；release gate 会要求关键架构边界、owner v2/readiness、AI provider/owner gateway、Payment webhook、File processing �?Job adapter 测试存在�?failures/errors �?0。backend test contract 还会复核 `summary.suites/tests/failures/errors/skipped/required/requiredPresent/requiredMissing` �?Surefire `suites[]` 明细一致，并拒绝必需测试�?0 tests 或全�?skipped，避免旧报告、空跑配置或手工编辑隐藏失败测试�?

后端构建产物证据归档�?

```bash
DDD_BACKEND_BUILD_STRICT=true \
node bin/ddd-backend-build-evidence.mjs
```

该脚本默认执�?`./mvnw -DskipTests package`，检�?`lumira-server`、Auth、Message、File、Plugin、Localization、Payment、AI、Job �?Spring Boot 启动入口，以及各后端模块�?`target/classes` �?jar 产物，默认产物为 `artifacts/ddd/build/backend-build-evidence.json`。strict 模式会要�?artifact 带有环境、版本和执行�?provenance。backend build contract 还会复核 `summary.modules/deployableModules/jars/missingEntrypoints/missingClasses/missingJars` �?`modules[]` 明细一致，要求模块报告精确匹配 shared required backend module 清单，禁止缺失、重复或未知 module，且每个 module �?`deployable`、`entrypoint` 必须与契约一致；jar metadata 必须包含文件路径、正数大小和 64 �?SHA-256，避�?package 证据 summary 和真实模块产物状态漂移�?

Docker 镜像构建证据归档�?

```bash
DDD_DOCKER_BUILD_STRICT=true \
DDD_EVIDENCE_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=2026.06.14-rc1 \
DDD_EVIDENCE_OPERATOR=release-operator \
node bin/ddd-docker-build-evidence.mjs
```

该脚本使�?`deploy/docker/service.Dockerfile` 构建 `lumira-server` 镜像，使�?`deploy/docker/lumira-ui.Dockerfile` 构建 `lumira-ui` 镜像，并记录 Dockerfile checksum、image id、repo tag、size、entrypoint/cmd、exposed ports、运行用户和 inspect 结果，默认产物为 `artifacts/ddd/build/docker-image-evidence.json`。strict 模式会要�?artifact 带有环境、版本和执行�?provenance；strict gate 还会要求 `summary.images/passed/failed/skipped/blockers` �?`images[]` �?artifact blockers 明细一致，`status` 与镜像和 blockers 明细一致，镜像报告必须精确匹配 `lumira-server` �?`lumira-ui`，不能缺失、重复或包含未知镜像；每个镜像的 Dockerfile 路径、期望端口、non-root 要求、tag �?Dockerfile SHA-256 必须与共享契约一致，`staticDockerfile` 与镜像顶�?checksum 也必须一致。顶�?`blockers[]` 必须�?skipped 镜像�?preflight/provenance blocker �?failed 镜像�?`imageName: blocker` 精确推导，SKIPPED 镜像�?`skipReason` 必须等于 image blockers 串联结果，PASS 镜像不得残留 blocker。`lumira-server` �?root 运行并暴�?`8080/tcp`，`lumira-ui` 暴露 `80/tcp`，两个镜像都必须�?entrypoint �?command。脚本会�?Docker build 前写�?`staticDockerfile` 静态合规结果，覆盖 Dockerfile 是否存在、端口声明、entrypoint/cmd、server �?root 用户、server owner module build args、lumira-ui frozen lockfile 和生产构建命令；Docker CLI �?daemon 不可用时，artifact 会把每个镜像标记�?`SKIPPED`，并写入具体 `skipReason` �?blockers，方�?CI 环境排障；readiness summary 会展开 CLI/daemon 状态、每个镜像的 Dockerfile、静态合规状态、tag、端口、non-root 要求、skip/build/inspect 状态和恢复动作。但 strict gate 仍会阻断发布。可通过 `DDD_DOCKER_TAG_PREFIX`、`DDD_DOCKER_TAG_SUFFIX` �?`DDD_DOCKER_NO_CACHE=true` 控制镜像 tag 与缓存策略；`lumira-server` 默认不会下载 OpenTelemetry javaagent，只有设�?`DDD_DOCKER_OTEL_JAVAAGENT_URL` �?compose build arg `OTEL_JAVAAGENT_URL` 时才�?agent 打入镜像，运行时开�?`OTEL_JAVAAGENT_ENABLED=true` 会先校验 agent 文件非空�?

如果发布候选镜像已经由可信 CI 构建并拉取到当前 runner，可显式使用 inspect-only 证据路径，避�?Docker Hub 或上�?registry 抖动导致重复 build 阻塞�?

```bash
DDD_DOCKER_BUILD_STRICT=true \
DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> \
DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> \
DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/lumira-ui:<release-candidate> \
node bin/ddd-docker-build-evidence.mjs
```

该模式仍要求 Docker CLI/daemon 可用，strict 下还必须提供 `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE` 指向可信 CI 构建日志、制品清单或发布候选镜�?provenance；脚本仍读取当前 Dockerfile 做静态合同检查，并对指定镜像执行 `docker image inspect`。端口、non-root、entrypoint/cmd、size �?required image 清单任何一项不满足都会失败。只有显式传�?`DDD_DOCKER_EXISTING_*_IMAGE` 时才启用该路径，默认仍执行真�?build�?

前端静态与单元证据归档�?

```bash
DDD_FRONTEND_STATIC_STRICT=true \
node bin/ddd-frontend-static-evidence.mjs
```

该脚本依次执�?`corepack pnpm --dir lumira-ui lint`、`typecheck` �?`test`，记录退出码、耗时和输出尾部，默认产物�?`artifacts/ddd/lumira-ui/lumira-ui-static-evidence.json`。strict 模式会要�?artifact 带有环境、版本和执行�?provenance；release gate 会要求三项均通过，并复核 `summary.commands/passed/failed/skipped/durationMs` �?`results[]` 明细一致、`status` 与命令结果一致，�?`results[]` 只能精确包含 lint/typecheck/unit，不能缺失、重复或出现未知命令；每项必须带 command、exitCode 和非�?durationMs，避免旧静态测试报告或手工 summary 掩盖 lint/typecheck/unit 失败�?

前端生产构建证据归档�?

```bash
DDD_FRONTEND_BUILD_STRICT=true \
node bin/ddd-frontend-build-evidence.mjs
```

该脚本执�?`corepack pnpm --dir lumira-ui build`，检�?`lumira-ui/dist/index.html` 和静态资源产物，并记录文件数量、总大小、入�?HTML 与最大资源文�?checksum。默认产物为 `artifacts/ddd/lumira-ui/lumira-ui-build-evidence.json`。strict 模式会要�?artifact 带有环境、版本和执行�?provenance。lumira-ui build contract 会要�?build command/exitCode/durationMs 完整，`summary.files/assets/totalBytes/indexHtmlPresent` 有效，entrypoint 指向 `lumira-ui/dist/index.html` �?bytes/SHA-256 合法，`largestFiles[]` 非空、按 bytes 降序、文件不重复并带 64 �?SHA-256，避免只凭构建退出码却缺少可审计部署产物�?

发布环境配置证据归档�?

```bash
DDD_RELEASE_ENV_FILE=.env.release \
DDD_RELEASE_CONFIG_STRICT=true \
node bin/ddd-release-config-evidence.mjs
```

该脚本读取当前环境变量和可�?`DDD_RELEASE_ENV_FILE`，只归档变量存在性、脱�?URL、secret 长度�?hash 前缀，不写出明文密钥。strict 发布要求准生�?生产等价的后�?baseURL、前�?baseURL、DB、Redis、JWT/FIELD secret、owner service URL、Job internal token、XXL-Job token、AI provider、AI owner gateway、Payment public URL 都存在；DB/Redis/owner/lumira-ui/backend URL 不能指向 localhost，AI provider �?IAM/Platform/File owner gateway 必须明确启用。release config contract 会复�?`summary`、`blockerDetails`、`blockersByGroup` �?`blockersByOwner` �?`groups[]`、`coverageMatrix`、`blockers[]`、`warnings[]` 明细一致，并要�?`coverageMatrix` 对每个配置要求精确出现一次，不能缺失、重复或包含未知 check，且每项必须列出至少一�?env key。`blockerDetails[]` 还必须逐项对应 `blockers[]`，并在指向已知配�?check 时与 `coverageMatrix` �?owner、envKeys、required 保持一致，避免配置缺口被错误归属或用手工明细绕过。失�?artifact 会输出这些明细，用于把配置缺口分派给 release-infra、platform-owners、platform-events、file-owner、payment-owner �?ai-owner。若失败来自 `__REQUIRED__`、`replace-with-*`、示�?URL 或其它占位值，先修 `primaryBlockers`；`placeholderDerivedConfigBlockers` 只是说明这些占位值会影响哪些配置检查，不能用来代替真实 `.env.release` 逐项审批�?

可从 `doc/36-ddd-release-env-template.env` 复制变量名到 CI secret store 或受�?`.env.release`，模板中的占位符必须替换为真实准生产/生产值�?

数据库迁移证据归档：

```bash
node bin/ddd-migration-evidence.mjs
```

该脚本静态扫�?owner Flyway migration location，要求必需目录存在、SQL 文件非空、同一 location 下无重复版本，并输出 `artifacts/ddd/migration/migration-evidence.json`。发布前还必须在准生产或生产等价环境完成新库迁移和旧库升级演练，并用运行证据重新生成 artifact�?

```bash
DDD_MIGRATION_CHECK_ENV=true node bin/ddd-migration-evidence.mjs
```

上面的预检不会写入正式 `migration-evidence.json`，只生成 `artifacts/ddd/migration/migration-evidence-handoff.md`，按 database、release-infra、release-owner 列出 fresh DB drill、old DB upgrade drill、环境、release candidate、operator 和完成时间的必填变量。先用该 handoff 补齐真实 Flyway 日志、`flyway_schema_history` 导出、artifact/log 路径、对�?URI、HTTPS 链接或工单号，再运行 strict 生成命令�?

```bash
DDD_MIGRATION_FRESH_DB_VALIDATED=true \
DDD_MIGRATION_UPGRADE_DB_VALIDATED=true \
DDD_MIGRATION_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=<sha-or-release-version> \
DDD_MIGRATION_OPERATOR=release-owner@example.com \
DDD_MIGRATION_COMPLETED_AT=2026-06-14T10:00:00.000Z \
DDD_MIGRATION_FRESH_DB_EVIDENCE="artifact-or-log-link-for-empty-db-flyway-and-schema-history" \
DDD_MIGRATION_UPGRADE_DB_EVIDENCE="artifact-or-log-link-for-old-db-upgrade-and-schema-history" \
DDD_MIGRATION_STRICT=true \
node bin/ddd-migration-evidence.mjs
```

strict 模式下，`bin/ddd-migration-evidence.mjs` �?release gate 都会要求 `freshDatabaseValidated` �?`upgradeDatabaseValidated` 均为 `true`，并要求 `environment`、`releaseCandidate`、`operator`、ISO `completedAt`、`freshDatabaseEvidence`、`upgradeDatabaseEvidence` 全部为真实非占位证据。migration contract 会复�?`summary.locations/migrationFiles/duplicateVersionLocations/emptyFiles/runtimeReady` �?`locations[]` �?`runtime` 明细一致，并要�?`locations[]` 精确覆盖共享契约中的必需 owner Flyway location，不能缺失、重复或包含未知 location；每�?migration 还必须包�?`version`、`description`、`file`、正�?`bytes` �?64 �?SHA-256，且 `migrationCount` 必须�?`migrations[]` 数量一致，避免旧扫描或手工 summary 掩盖缺失迁移目录、空迁移文件、异常文件元数据或未完成运行演练。`freshDatabaseEvidence` �?`upgradeDatabaseEvidence` 必须分别指向不同�?Flyway 日志、`flyway_schema_history` 导出、artifact/log 路径、对�?URI、HTTPS 链接或工单号；不能用同一条笼统说明同时代表新库全量迁移和旧库升级，也不能用旧 release candidate 的迁移演练结果替代当前发布版本。artifact 会输�?`runtimeReady`、`runtimeProofs` �?`runtimeDiagnostics`，逐项标记 fresh DB、old DB upgrade、环境、release candidate、operator、completedAt 的状态、owner、envKeys 和证据引用；�?strict 本地扫描即使 SQL 结构通过，也会用 `runtimeReady=false` 明确说明运行演练未完成�?

回滚演练证据�?

```bash
DDD_EVIDENCE_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=git-sha-or-build-id \
DDD_EVIDENCE_OPERATOR=release-owner@example.com \
node bin/ddd-init-rollback-drill.mjs
```

将生成的 `artifacts/ddd/rollback/rollback-drill.json` 中每个上下文改成真实演练结果。`PASS` 必须提供 `rollbackAction`、`drillEvidence` �?`validatedAt`，且 `drillEvidence` 需要包含可追溯�?HTTPS 链接、artifact/log 路径、对象存�?URI 或工单号，不能只是“已验证”一类描述。确实无法在本轮演练的上下文可以使用 `DEFERRED`，但必须提供 `notExercisableReason`、`riskAcceptedBy`、`deferralEvidence` 和未来的 `expiresAt`，其�?`deferralEvidence` 也必须指向审批单、会议纪要、artifact/log 路径、对象存�?URI 或工单号。`TODO`、`replace-with-*`、`Link or path...` 等占位文本都会被拒绝。strict 发布门禁会要�?artifact 总状态为 `PASS`，并要求 IAM、Auth、Platform、Message、File、Plugin、Localization、Payment、AI、Job 十个上下文全部有证据�?

```bash
DDD_ROLLBACK_DRILL_CHECK_ENV=true node bin/ddd-rollback-drill-evidence.mjs
DDD_ROLLBACK_DRILL_STRICT=true node bin/ddd-rollback-drill-evidence.mjs
```

`DDD_ROLLBACK_DRILL_CHECK_ENV=true` 是安全预检模式，只�?`artifacts/ddd/rollback/rollback-drill-handoff.md` �?`DDD_ROLLBACK_DRILL_HANDOFF_FILE` 指定的交接文件，不会改写 `rollback-drill.json`，也不会�?release gate 通过。正式脚本会独立校验 rollback artifact 的上下文覆盖、PASS/DEFERRED 必填字段、证据引用和时间格式，便于在汇�?release gate 前先把回滚演练证据修到可审计状态。校验通过时脚本会�?artifact 总状态写�?`PASS`；失败时写入 `blockers` �?release readiness summary 归类。artifact �?`summary` 由脚本生成，contract 会复�?`requiredContexts/contexts/passContexts/deferredContexts/readyContexts/missingRequiredContexts/unknownContexts/duplicateContexts/appliedDeferrals/blockers/warnings` 等统计与 contexts、diagnostics、blockers 明细一致，避免手工汇总掩盖缺失上下文或未演练条目。release gate 还会�?rollback 核心合同重新计算 `blockers[]` 并逐项比对，防止手工删除或改写 rollback blocker。`contextDiagnostics[]` 也必须精确覆盖十个上下文，owner/action 必须匹配共享 remediation 清单，status、ready、missingEvidence、deferralApplied �?evidence 必须�?`contexts[]` �?PASS/DEFERRED/MISSING 明细一致，避免行动摘要�?blocker 分派给错�?owner 或展示错误证据�?

如果本轮发布无法安全演练某些上下文，可以通过显式审批文件批量合并 `DEFERRED` 记录�?

```bash
DDD_ROLLBACK_DRILL_DEFERRAL_FILE=/secure/release/rollback-deferrals.json \
DDD_ROLLBACK_DRILL_RISK_ACCEPTED_BY=release-owner@example.com \
DDD_ROLLBACK_DRILL_DEFERRAL_EVIDENCE=CHANGE-12345 \
DDD_ROLLBACK_DRILL_DEFERRAL_EXPIRES_AT=2026-12-31T00:00:00.000Z \
node bin/ddd-rollback-deferral-template.mjs
```

该脚本会同时生成 `artifacts/ddd/rollback/rollback-deferrals-owner-handoff/`，按 IAM/Auth/Platform/Message/File/Plugin/Localization/Payment/AI/Job �?owner 拆分 Markdown 交接文件，方便并行找负责人确认真实延期原因、审批人、审批证据和到期时间。这�?handoff 只是签收入口，不�?waiver；填完模板中的真实延期原因、审批人、审批证据和到期时间后，再合并到 rollback drill artifact�?

```bash
DDD_ROLLBACK_DRILL_DEFERRAL_FILE=/secure/release/rollback-deferrals.json \
DDD_ROLLBACK_DRILL_STRICT=true \
node bin/ddd-rollback-drill-evidence.mjs
```

`rollback-deferrals.json` 必须包含 `contexts` 数组，每项提�?`context`、`notExercisableReason`、`riskAcceptedBy`、`deferralEvidence` 和未来的 `expiresAt`。模板脚本只生成待填写输入，不会�?gate 通过；`notExercisableReason` 中的 `replace-with-*` 占位、过�?`expiresAt` 或缺少真实证据引用都会被 `ddd-rollback-drill-evidence.mjs` 拒绝。脚本只接受真实证据引用；`deferralEvidence` 应指向审批单、变更单、会议纪要、artifact/log 路径或对象存�?URI。默认不会用 deferral 覆盖已经 `PASS` 的上下文，除非显式设�?`DDD_ROLLBACK_DRILL_DEFERRAL_OVERWRITE_PASS=true`�?

前端浏览�?smoke�?

```bash
PORT=8010 \
UMI_DEV_API_TARGET=http://127.0.0.1:8080 \
UMI_DEV_WS_TARGET=ws://127.0.0.1:8080 \
corepack pnpm --dir lumira-ui dev

PLAYWRIGHT_BASE_URL=http://127.0.0.1:8010 \
PLAYWRIGHT_ADMIN_USER=admin \
PLAYWRIGHT_ADMIN_PASSWORD=123456 \
PLAYWRIGHT_NEW_PASSWORD=E2eAdmin123! \
corepack pnpm --dir lumira-ui test:e2e:smoke
```

发布证据归档时使�?JSON reporter 生成可读 artifact，并转换为统一门禁格式�?

```bash
corepack pnpm --dir lumira-ui install --frozen-lockfile
corepack pnpm --dir lumira-ui exec playwright install --with-deps chromium

mkdir -p artifacts/ddd/lumira-ui

PLAYWRIGHT_BASE_URL=https://staging.example.com \
PLAYWRIGHT_ADMIN_USER=admin \
PLAYWRIGHT_ADMIN_PASSWORD=*** \
PLAYWRIGHT_NEW_PASSWORD=*** \
node bin/ddd-frontend-playwright-smoke.mjs

PLAYWRIGHT_BASE_URL=https://staging.example.com \
DDD_FRONTEND_EXPECT_DEPLOYED=true \
DDD_EVIDENCE_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=2026.06.14-rc1 \
DDD_EVIDENCE_OPERATOR=release-operator \
DDD_FRONTEND_DEPLOYMENT_EVIDENCE=change-or-deploy-artifact-link \
node bin/ddd-frontend-smoke-evidence.mjs
```

本地 smoke 应覆盖登录、强制改密、匿名跳转、核心页面、消息中心、session refresh 和登出；部署环境 smoke 还应保存录像、截图、控制台和网络日志。strict 发布要求 `DDD_FRONTEND_EXPECT_DEPLOYED=true`、HTTPS `PLAYWRIGHT_BASE_URL`，以�?`DDD_EVIDENCE_ENVIRONMENT`、`DDD_RELEASE_CANDIDATE`、`DDD_EVIDENCE_OPERATOR` 三项非占位来源元数据。`DDD_FRONTEND_DEPLOYMENT_EVIDENCE` 或通用 `DDD_DEPLOYMENT_EVIDENCE` 可记录部署单、CI artifact、Playwright HTML report、trace/video 存储路径或对象存储证据引用。`bin/ddd-frontend-smoke-evidence.mjs` �?`DDD_RELEASE_EVIDENCE_STRICT=true` �?`DDD_FRONTEND_SMOKE_STRICT=true` 时会在转换阶段强制执行这�?deployed evidence 规则，并写入 `productionEquivalence`，避免最�?release gate 才发现本地或无来源前�?smoke。脚本默认要�?dashboard、download center、AI assistant、用户、角色、安全设置、支付设置、文件、插件、国际化、session refresh、消息中心和登出 smoke 全部通过，产物为 `artifacts/ddd/lumira-ui/lumira-ui-smoke.json`。该 artifact 还会写入 `flowCoverage`，逐条说明每个必需 flow 是否�?passed `@smoke` 测试覆盖；若 Playwright JSON 缺失或某�?flow 没有匹配通过用例，`flowCoverage.reason` 会给出具体原因。lumira-ui smoke contract 会复�?`summary.requiredFlows/missingRequiredFlows` �?required/coverage 明细一致，required flow �?coverage 不能重复、缺失或出现未知项，passed coverage 必须记录匹配到的 Playwright 标题；`diagnostics.staticSpecCoverage` 也必须与 required flow 清单精确对应，避免测试标题或静�?spec 漂移。当 Playwright 报告存在时，也会复核 `summary.total/passed/failed/skipped` �?`tests[]` 一致，并要�?`status` �?blockers 明细一致。release gate 会按当前 strict/advisory 口径重新计算 `blockers[]` 并逐项比对，防止用旧的�?strict artifact 或手�?blocker 列表绕过部署 smoke 要求。`bin/ddd-release-readiness-summary.mjs` 会展开前端 smoke �?baseURL、HTTPS/localOnly、production-equivalence issues、`expectDeployed`、测试总数、必需 flow 覆盖和每个缺�?flow 的修复动作�?

## 2. Readiness Drill Matrix

| Context | Readiness | Health | Metrics | 核心演练 | 回滚动作 | 必留证据 |
| --- | --- | --- | --- | --- | --- | --- |
| IAM | `/api/v2/iam/readiness` | `/api/v2/iam/health` | `/api/v2/iam/metrics` | 角色权限变更后权限快照版本推进、缓存失效、当前用户权限刷�?| 回切 v1 IAM adapter，保�?`sys_user/sys_role/sys_permission` owner 写入 | readiness JSON、permission snapshot version、权限变更审计、缓存失效日�?|
| Auth | `/api/v2/auth/readiness` | `/api/v2/auth/health` | `/api/v2/auth/metrics` | 登录、refresh token、current-user 热路径命�?session payload；损�?session payload 自动移除 | 回切 v1 auth adapter，保�?Redis key schema �?TTL | session hit/miss/save/remove/corrupt 指标、`auth.bootstrap_cache.alignment_rejects`、登录请求数、Redis key 样例 |
| Platform | `/api/v2/platform/readiness` | `/api/v2/platform/health` | `/api/v2/platform/metrics` | runtime appearance/config 版本推进、bootstrap 读取缓存命中、审计写入失败告�?| 回切 platform adapter，清�?runtime appearance cache | read-model version、config p95、bootstrap p95、审计失败率 |
| Message | `/api/v2/message/readiness` | `/api/v2/message/health` | `/api/v2/message/metrics` | 消息发布、可见列表分页、未�?capped count、WebSocket 投递、outbox replay | 暂停 relay job，v2 adapter 回切 MessageAppService 兼容路径 | outbox backlog、WebSocket 投递日志、capped count SQL explain |
| File | `/api/v2/files/readiness` | `/api/v2/files/health` | `/api/v2/files/metrics` | 上传立即返回、处理任务异�?claim、扫�?OCR/缩略�?TEXT_CONTENT artifact 生成、replay | 暂停 file processing job，保留原文件�?task，按 task id 重跑 | upload p95、processing backlog、artifact 记录、scan/OCR 结果 |
| Plugin | `/api/v2/plugins/readiness` | `/api/v2/plugins/health` | `/api/v2/plugins/metrics` | 插件启用、禁用、回滚、bootstrap 投影版本推进、outbox replay | 禁用租户插件或回滚版本，重建 plugin/bootstrap 投影 | pending/failed/dead-letter/dispatchable backlog、bootstrap 版本、回滚审�?|
| Localization | `/api/v2/localization/readiness` | `/api/v2/localization/health` | `/api/v2/localization/metrics` | 发布、回滚、runtime bundle 缓存命中与旧版本失效 | 回滚 active release，清�?runtime bundle cache | bundle cache size/hit/miss/hit-ratio、release id、回滚审�?|
| Payment | `/api/v2/payment/readiness` | `/api/v2/payment/health` | `/api/v2/payment/metrics` | webhook 签名失败、nonce replay、重�?event 幂等、outbox replay | 暂停 provider 回调入口，按 eventKey replay 或回切单�?endpoint | webhook p95、签名失败、重复拦截、outbox backlog、订单状态轨�?|
| AI | `/api/v2/ai/readiness` | `/api/v2/ai/health` | `/api/v2/ai/metrics` | 知识库文档上传后异步索引、失败重试、DEAD_LETTER、向量检索重排、tool 调用审计 | 暂停 aiKnowledgeIndexJob，按 documentId 重建索引，禁用外�?provider/vector adapter | pending/retryable/failed/dead-letter、vector/local-hashing chunk、tool audit |
| Job | `/api/v2/job/readiness` | `/api/v2/job/health` | `/api/v2/job/metrics` | XXL-JOB handler 只调�?owner internal API，不读写业务表；owner relay 幂等 | 禁用对应 handler，BackendJobClient URL 回切单体 owner endpoint | configured target count、internal token 状态、handler 调用日志 |

## 3. Outbox Relay Drill

每个 owner outbox 至少演练以下路径�?

1. 正常事件进入 `RECORDED` �?owner 等价状态�?
2. relay claim 后进�?dispatching 状态�?
3. dispatcher 成功后标�?delivered�?
4. dispatcher 抛错后增�?retry count，并写入 next retry 时间�?
5. 超过重试阈值后进入 dead-letter�?
6. 调用 owner replay endpoint，验证幂�?key 不产生重复业务副作用�?

建议保留证据�?

- relay 前后�?outbox 行�?
- job-executor handler 日志�?
- owner `/metrics` 响应�?
- replay 请求和响应�?
- 业务聚合状态快照�?
- `artifacts/ddd/jobs/job-e2e-smoke.json`�?

## 4. Performance Acceptance

性能验收以当前优化后数据�?baseline。新增或修改热路径时必须满足�?

- 端点 p95 不得�?baseline 回退超过 10%，且 actual 必须覆盖 baseline 中所有热路径端点�?
- 列表查询必须分页�?
- 高频读不加载完整聚合�?
- 跨上下文读取只走 API、事件投影或缓存快照�?
- 新增热点 SQL 必须进入 `doc/28-ddd-hot-path-explain-plan.md`�?
- `EXPLAIN FORMAT=JSON` artifact 必须带采集环境、版本、执行人、SQL checksum，不允许 `access_type=ALL`，非 `const/system/eq_ref` 访问必须命中索引 key�?

建议保存�?

```text
artifacts/ddd/performance/baseline.json
artifacts/ddd/performance/actual.json
artifacts/ddd/build/docker-image-evidence.json
artifacts/ddd/explain/*.json
artifacts/ddd/readiness/*.json
artifacts/ddd/config/release-config-evidence.json
artifacts/ddd/release/evidence-manifest.json
artifacts/ddd/release/readiness-summary.md
artifacts/ddd/runbook/YYYY-MM-DD.md
```

## 5. Completion Evidence Checklist

- DDD 架构边界测试通过�?
- `node bin/ddd-backend-test-evidence.mjs` 通过，并保存后端关键测试 artifact�?
- `node bin/ddd-backend-build-evidence.mjs` 通过，并保存后端 package/jar artifact�?
- `node bin/ddd-docker-build-evidence.mjs` 通过，并保存 Docker image artifact�?
- `node bin/ddd-frontend-static-evidence.mjs` 通过，并保存前端 lint/typecheck/unit artifact�?
- `node bin/ddd-frontend-build-evidence.mjs` 通过，并保存前端 production build artifact�?
- `node bin/ddd-promote-performance-baseline.mjs` 通过，并保存�?localhost、带验收元数据的 authenticated performance baseline�?
- `DDD_RELEASE_ENV_FILE=.env.release node bin/ddd-release-config-evidence.mjs` 通过，并保存生产等价配置 artifact�?
- `node bin/ddd-release-evidence-manifest.mjs` 通过，并保存 checksum manifest�?
- `node bin/ddd-migration-evidence.mjs` 通过，并保存 Flyway 静态和运行演练 artifact�?
- `node bin/ddd-rollback-drill-evidence.mjs` 通过，并保存真实 rollback drill artifact�?
- 十个上下�?readiness/health/metrics controller 和测试存在�?
- `node bin/ddd-readiness-gate.mjs` 通过�?
- 运行环境 `node bin/ddd-runtime-readiness-smoke.mjs` 通过，并保存 30 �?endpoint JSON artifact�?
- Runtime smoke artifact 均包�?`sourceEnvironment`、`releaseCandidate`、`evidenceOperator`�?
- Strict release gate 中所有关�?artifact 均在 `DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS` 窗口内�?
- `node bin/ddd-explain-gate.mjs` 通过�?
- `node bin/ddd-job-e2e-smoke.mjs` 通过，并保存 job/internal E2E artifact�?
- 性能 baseline/actual 比较通过�?
- 每个 owner �?outbox 或异步任务演练有证据�?
- 每个上下文的回滚动作至少演练一次或有明确不可演练原因�?
- `artifacts/ddd/rollback/rollback-drill.json` 覆盖十个上下文，并进�?release evidence gate�?
- 前端 smoke 来自 HTTPS 部署 baseURL，设�?`DDD_FRONTEND_EXPECT_DEPLOYED=true`，并覆盖登录、首页、用户角色、消息、上传、插件、国际化、支付回调模拟、AI 会话�?


<!-- release-unblock-brief documents the first 前 5 个 RUN_NOW owner actions. -->


<!-- release-unblock-brief documents the first 前 5 个 `RUN_NOW` owner actions. -->
