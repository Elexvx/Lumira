# DDD Operational Runbook

本文档用于 DDD 重构后的运行演练、拆分门禁和回滚审计。它不是设计说明，而是上线前后可以逐项执行并保留证据的检查手册。

## 1. 通用执行顺序

1. 启动目标环境，确认 `lumira-server`、Redis、MySQL、XXL-JOB、对象存储或本地存储均可访问。
2. 执行全局编译和架构门禁。
3. 访问每个 owner 的 `/readiness`、`/health`、`/metrics`，保存响应 JSON。
4. 对热路径执行性能 smoke，保存 baseline、actual 和命令输出。
5. 在可连接 MySQL 的环境执行 `EXPLAIN FORMAT=JSON` 采集，保存 `tmp/ddd-explain/*.json`。
6. 演练 outbox relay、dead-letter、replay、缓存失效和回滚步骤。
7. 对照本文件记录证据路径、结果和剩余风险。

推荐命令：

```bash
./mvnw -pl services/lumira-server -am -DskipTests compile
./mvnw -pl services/system-service -am -Dtest=DddArchitectureBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
node --check scripts/ddd-performance-smoke.mjs
node --check scripts/ddd-collect-explain.mjs
node --check scripts/ddd-explain-gate.mjs
node --check scripts/ddd-physical-split-gate.mjs
node scripts/ddd-readiness-gate.mjs
node scripts/ddd-physical-split-gate.mjs
```

Owner readiness/health/metrics 是公开只读运维端点，已通过 `saas.security.permit-paths` 放行。端点 payload 只能包含 owner 边界、状态、指标名和值、依赖和回滚步骤，不得返回密钥、token、个人联系方式或支付敏感数据。

`ddd-physical-split-gate` 默认以 advisory 模式运行并写入 `artifacts/ddd/split/physical-split-readiness.json`。该产物用于拆服务前评审：`failures` 必须为 0；进入真实物理拆分窗口前，设置 `DDD_SPLIT_STRICT=true` 运行，确保所有 blocker 已清零，并带有 `DDD_EVIDENCE_ENVIRONMENT`、`DDD_RELEASE_CANDIDATE`、`DDD_EVIDENCE_OPERATOR` provenance。artifact 的 `summary` 由共享 physical split contract 计算，release gate 会复核上下文数量、物理拆分目标数、独立启动就绪数、全局/上下文检查数、失败数、blockers、warnings、迁移文件数、缺失业务 endpoint 数和跨 service Maven 依赖失败数是否与明细一致。contract 还要求 IAM/Auth/Platform/Message/File/Plugin/Localization/Payment/AI/Job 十个上下文精确出现一次，module、route、ownerContext、physicalServiceTarget 与拆分契约一致，并且每个上下文至少包含 module、owner-manifest、readiness、health、metrics、cross-service-pom-dependency 六类基础检查。

本地 runtime smoke 或准生产 owner 演练如果不需要连接 XXL-JOB Admin，应显式设置 `XXL_JOB_EXECUTOR_ENABLED=false`。该开关只禁用 `XxlJobSpringExecutor` 的外部注册和 remoting server，不移除 `@XxlJob` handler、owner internal API 或 `scripts/ddd-job-e2e-smoke.mjs` 的内部 relay 验证能力。

运行环境采集 readiness/health/metrics 证据：

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_RUNTIME_READINESS_DIR=artifacts/ddd/readiness \
node scripts/ddd-runtime-readiness-smoke.mjs
```

strict 模式下，runtime readiness smoke 会在生成阶段校验 baseURL 不是 localhost、使用 HTTPS，并要求 `DDD_RUNTIME_READINESS_DEPLOYMENT_EVIDENCE` 或统一 `DDD_DEPLOYMENT_EVIDENCE` 指向可追溯部署/工单/CI 证据；结果会写入结构化 `productionEquivalence`。runtime readiness contract 要求十个 owner 上下文的 readiness/health/metrics 正好形成 30 个唯一检查，未知 context/suffix、重复检查或重复 artifact 引用都会失败，避免用重复结果凑齐覆盖。contract 还会读取每个 endpoint artifact：readiness payload 必须声明 owner module、owner tables、API、health checks、metrics、dependencies 和 rollback steps；health payload 必须有非空 healthChecks 和 metrics；metrics payload 必须有非空 metrics，且健康检查和指标条目要包含 name/status/type/unit/description 等可运维字段。30 个 endpoint 都返回 200 只能证明 owner contract 可用；strict contract 会拒绝缺少 `productionEquivalence` 的历史 runtime artifact，`productionEquivalence.issues` 仍为 release blocker，直到证据来自准生产或生产等价地址且带部署证据引用。

有运行环境时：

```bash
DDD_PERF_BASELINE_FILE=docs/30-ddd-performance-baseline.example.json \
DDD_PERF_ACTUAL_FILE=/path/to/actual-performance.json \
node scripts/ddd-performance-smoke.mjs

DDD_EXPLAIN_DATABASE=lumira \
DDD_EVIDENCE_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=git-sha-or-build-id \
DDD_EVIDENCE_OPERATOR=release-owner@example.com \
node scripts/ddd-collect-explain.mjs
DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs
```

`ddd-collect-explain` 会把每个热点 SQL 的 MySQL `EXPLAIN FORMAT=JSON` 包装成带 `generatedAt`、`sourceEnvironment`、`releaseCandidate`、`evidenceOperator`、`queryName`、`sqlSha256`、`database`、`mysqlHost` 和 `plan` 的 artifact。strict gate 会拒绝缺元数据、占位元数据、localhost/127.0.0.1/::1 的 `mysqlHost`、本地诊断 `sourceEnvironment`、`queryName` 与文件名不一致、非 64 位 hex `sqlSha256`、`sqlSha256` 与共享热点 SQL 契约不匹配、legacy 导入计划、全表扫描、缺索引 key、缺 `table_name/access_type/rows_examined_per_scan` 诊断字段、或 `rows_examined_per_scan` 超过 `docs/28-ddd-hot-path-explain-plan.md` 中 strict rows 上限的产物。当前必需 artifact 为 `platform-runtime-appearance.json`、`plugin-bootstrap.json`、`message-visible-list.json`、`message-unread-count.json`、`message-archive-total.json`、`ai-knowledge-index-retry.json`、`platform-outbox-owner-relay-message.json`、`platform-outbox-owner-relay-file.json`；必需索引包括 runtime appearance 的 `uk_sys_config_key`、plugin bootstrap 的 `uk_sys_plugin_tenant_rel`/`uk_sys_plugin_definition_code`/`uk_sys_plugin_version_code_version`、message visible/unread/archive capped path 的 `idx_msg_notice_visible_recent`、AI retry 的 `idx_ai_knowledge_document_index_retry` 和 Message/File owner relay 的 `idx_platform_event_outbox_owner_queue`。

如果已经有旧格式的裸 `EXPLAIN FORMAT=JSON` 文件，可以用 `scripts/ddd-normalize-explain-artifacts.mjs` 将其导入为带 provenance 的 legacy plan artifact：

```bash
DDD_EXPLAIN_DIR=tmp/ddd-explain \
DDD_EVIDENCE_ENVIRONMENT=local-audit \
DDD_RELEASE_CANDIDATE=<sha-or-build-id> \
DDD_EVIDENCE_OPERATOR=<operator> \
node scripts/ddd-normalize-explain-artifacts.mjs
DDD_EXPLAIN_STRICT=true node scripts/ddd-explain-gate.mjs
```

该工具只保留并标记既有执行计划，适合审计旧产物或本地补齐元数据；strict 发布放行必须在准生产/生产量级数据库运行 `ddd-collect-explain` 重新采集，`legacyPlanImport=true` 的产物会被 release gate 拒绝。

包含 POST、webhook 和文件上传的场景压测：

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
LUMIRA_AUTH_TOKEN=replace-with-token \
DDD_SMOKE_SCENARIOS_FILE=docs/32-ddd-performance-scenarios.example.json \
node scripts/ddd-performance-smoke.mjs
```

认证成功态 v2 热路径压测：

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_AUTH_USERNAME=admin \
DDD_AUTH_PASSWORD=replace-with-ready-password \
DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE=change-or-deploy-artifact-link \
DDD_AUTH_PERF_DURATION_MS=15000 \
DDD_AUTH_PERF_CONCURRENCY=8 \
node scripts/ddd-authenticated-performance-smoke.mjs
```

该脚本会先通过 `/api/v2/auth/login-encryption-key` 和 `/api/v2/auth/login` 拿到真实 token，再要求 current-user、IAM、Message、File、Plugin、Localization、Payment 等 v2 热路径全部返回成功 envelope；`/api/v2/auth/session/keepalive` 和 `/api/v2/files/upload` 作为一次性成功回包证据单独记录。performance contract 会复核 `ok + failed == samples`、逐 endpoint samples 汇总、statusCounts 汇总、one-shot 状态和 endpoint 清单覆盖，避免少测端点或手工修改统计绕过回归门禁。strict 模式下会写入结构化 `productionEquivalence`，要求 `LUMIRA_BASE_URL` 为 HTTPS 且非 localhost；`DDD_AUTH_PERF_DEPLOYMENT_EVIDENCE` 或通用 `DDD_DEPLOYMENT_EVIDENCE` 可记录部署单、CI artifact、压测报告或对象存储证据引用。历史 actual 如果没有 `productionEquivalence`，只能作为开发调试证据，strict release gate 会以 authenticated-performance-shape blocker 阻断。默认产物为 `artifacts/ddd/performance/authenticated-runtime-actual.json`。发布前应把上一轮通过验收的生产等价 actual 晋级为 baseline；手动执行时：

```bash
DDD_AUTH_PERF_BASELINE_ACCEPTED_BY=release-operator \
DDD_AUTH_PERF_BASELINE_ENVIRONMENT=staging-production-equivalent \
DDD_AUTH_PERF_BASELINE_SOURCE_ARTIFACT=artifacts/ddd/performance/authenticated-runtime-actual.json \
node scripts/ddd-promote-performance-baseline.mjs
```

`scripts/ddd-promote-performance-baseline.mjs` 会拒绝 localhost actual、非 HTTPS actual、缺少结构化 `productionEquivalence.strict=true` 的 actual、失败样本、缺少上传耗时的 artifact、缺少 actual provenance 的 artifact、actual 环境和晋级环境不一致的 artifact、actual release candidate 和 `DDD_RELEASE_CANDIDATE` 不一致的 artifact，以及缺失或占位的 `acceptedBy`、`sourceEnvironment`、`sourceArtifact`；无论成功或失败，脚本都会写入 `artifacts/ddd/performance/authenticated-runtime-baseline-promotion.json`，记录源 actual 摘要、晋级 env、源文件 SHA-256、blockers 和输出路径，便于发布负责人审计为什么 baseline 没有生成。`scripts/ddd-promote-performance-baseline.test.mjs` 覆盖了非本地晋级、localhost 拒绝、非 HTTPS 拒绝、release candidate 不一致拒绝、失败样本拒绝、actual provenance 拒绝、环境不一致拒绝、占位验收人拒绝和仓库内路径脱敏，防止本地或不可追溯性能结果污染发布基线。authenticated performance contract 会要求 actual/baseline 带 ISO `checkedAt`、正数 duration/concurrency、整体和逐 endpoint p50/p95/p99 顺序正确、固定 9 个热路径 endpoint 精确覆盖、`ok + failed = samples`、perEndpoint samples/statusCounts 自洽、keepalive one-shot 成功，以及 `/api/v2/files/upload` 上传回包 200 且带 fileId，避免少测端点或缺少上传证据绕过性能门槛。`scripts/ddd-release-evidence-gate.mjs` 在 strict 模式会要求 baseline 非 localhost，`acceptedAt` 为 ISO 时间，`acceptedBy`、`sourceEnvironment`、`sourceArtifact` 为真实非占位值，`sourceSha256` 为 64 位 SHA-256，并比较整体 p95、逐 endpoint p95 和上传回包耗时，超过 baseline 10% 会阻断发布；actual 还必须覆盖 baseline 中所有 endpoint，避免少测端点绕过回退比较。可用 `DDD_RELEASE_MAX_P95_REGRESSION_RATIO` 调整阈值。`scripts/ddd-release-readiness-summary.mjs` 会展开 authenticated performance actual/baseline 诊断，显示 actual 是否 localhost、失败数、p95、upload 回包、端点数量、baseline 晋级 envKeys、最近一次 baseline promotion blockers、baseline metadata/shape 问题、缺失 endpoint 和回归项。

发布 workflow 默认不会自动晋级 baseline；只有显式设置 `promote_authenticated_baseline=true` 时，orchestrator 才会在 authenticated performance actual 生成后执行 `scripts/ddd-promote-performance-baseline.mjs`。首次建立基线或经发布负责人批准刷新基线时，应同时填写 `baseline_accepted_by`；否则该步骤会在报告中记录为 skipped，strict gate 仍会要求已有 `authenticated-runtime-baseline.json` 存在并通过回归比较。

File/Payment 业务 E2E smoke：

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
node scripts/ddd-file-processing-e2e-smoke.mjs

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
node scripts/ddd-payment-webhook-e2e-smoke.mjs
```

这两条 smoke 会写入 `productionEquivalence`：strict 发布要求 `LUMIRA_BASE_URL` 为 HTTPS 且非 localhost；`DDD_FILE_PROCESSING_DEPLOYMENT_EVIDENCE`、`DDD_PAYMENT_WEBHOOK_DEPLOYMENT_EVIDENCE` 或通用 `DDD_BUSINESS_E2E_DEPLOYMENT_EVIDENCE` 可记录部署单、CI artifact、provider sandbox 日志或对象存储证据引用。business E2E contract 在 strict 模式下会要求 artifact 自身携带结构化 `productionEquivalence`，避免旧的本地-only artifact 只在最终 release gate 才暴露环境缺口。File smoke 证明上传立即回包后，`SECURITY_SCAN`、`TEXT_EXTRACT`、`AI_PARSE` 都由任务处理完成并生成 artifact；Payment smoke 证明有效签名、重复 event、nonce replay 和坏签名四类 webhook 行为都落库可审计。

内部 job/outbox E2E smoke：

```bash
DDD_RELEASE_EVIDENCE_STRICT=true \
DDD_EVIDENCE_ENVIRONMENT=staging-production-equivalent \
DDD_RELEASE_CANDIDATE=replace-with-git-sha-or-tag \
DDD_EVIDENCE_OPERATOR=replace-with-operator \
DDD_JOB_SMOKE_DEPLOYMENT_EVIDENCE=change-or-deploy-artifact-link \
SAAS_JOB_INTERNAL_TOKEN=replace-with-runtime-token \
LUMIRA_BASE_URL=https://staging-api.example.internal \
node scripts/ddd-job-e2e-smoke.mjs
```

如需同时验证共用 `platform_event_outbox` 没有新增跨 owner 误处理，可打开 DB 诊断：

```bash
SAAS_JOB_INTERNAL_TOKEN=replace-with-runtime-token \
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
DDD_JOB_SMOKE_DB_CHECK=true \
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-job-e2e-smoke.mjs
```

该脚本必须证明两件事：未带 `X-Job-Token` 的内部任务请求被拒绝；带 token 后 platform/message/file/payment/plugin outbox relay、message/online heartbeat、file processing、AI knowledge index 均返回成功 envelope。Job E2E contract 会要求 artifact 带 `baseUrl` 和 ISO `checkedAt`，未授权探测路径必须是 `/internal/jobs/outbox/relay`；9 个 endpoint 结果必须精确覆盖清单，不能缺失、重复或出现未知 endpoint，`summary.total/failed/maxElapsedMs` 必须与明细一致，每个 endpoint 必须返回 200、正数 elapsedMs 和正确 data 类型。开启 DB 诊断时，还必须证明本次 smoke 前后 `source_type != MESSAGE` 且 `last_error` 为 payload 反序列化失败的 outbox 行数没有增长，避免 Message owner relay 误处理 File 等非 owner 事件。默认产物为 `artifacts/ddd/jobs/job-e2e-smoke.json`。
该脚本同样会写入 `productionEquivalence`；strict 发布下，内部 job endpoint 的 baseURL 也必须来自 HTTPS 且非 localhost 的准生产/生产等价环境。Job E2E contract 在 strict 模式下会要求 artifact 自身携带结构化 `productionEquivalence`，避免旧的 localhost job artifact 或手工生成的 JSON 只在最终 gate 才暴露环境缺口。旧的 localhost job artifact 可用于开发回归，但不会解除 strict gate 中的 `job-e2e-environment-strict` blocker。

Outbox replay/dead-letter 状态机 smoke：

```bash
DDD_OUTBOX_SMOKE_STRICT=true \
DDD_EVIDENCE_ENVIRONMENT=staging-prod-equivalent \
DDD_RELEASE_CANDIDATE=replace-with-git-sha-or-tag \
DDD_EVIDENCE_OPERATOR=replace-with-operator \
node scripts/ddd-outbox-replay-dead-letter-smoke.mjs
```

该脚本运行 System、Message、File、Plugin、Payment 的 owner outbox 聚焦测试，必须证明 claim、delivered、失败重试、`retry_count >= 8` 后进入 `DEAD_LETTER`、手动 replay 重置状态后重新投递，以及 relay disabled 时仍允许显式 replay。Outbox evidence contract 会要求 `testedContracts[]` 精确覆盖这六类状态机行为，不能缺失、重复或出现未知契约；每个必需 owner relay surefire report 必须存在，带 reportPath、正数 tests、非负 timeSeconds，且 failures/errors/skipped 均为 0。strict 模式会在 Maven 测试前拒绝缺失或占位的环境、候选版本和执行人 provenance。默认产物为 `artifacts/ddd/outbox/outbox-replay-dead-letter-test-evidence.json`。

共享 `platform_event_outbox` 污染审计和修复：

```bash
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-outbox-ownership-repair.mjs
```

默认 dry-run 只列出 `source_type != MESSAGE` 且 `last_error` 为 payload 反序列化失败的行。确认这些行确实是被 Message relay 误处理的非 Message owner 事件后，再执行：

```bash
DDD_OUTBOX_REPAIR_APPLY=true \
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-outbox-ownership-repair.mjs
```

修复动作只把命中的非 Message owner 事件重置为 `RECORDED`、`retry_count=0`、清空 `last_error/next_retry_at/delivered_at`，不删除事件。随后应调用对应 owner replay，例如 File 事件调用 `/file/internal/jobs/outbox/{id}/replay`，并再次执行带 `DDD_JOB_SMOKE_DB_CHECK=true` 的 `ddd-job-e2e-smoke`，确认 `crossOwnerPayloadFailuresDelta=0`。

文件处理端到端 smoke：

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
SAAS_JOB_INTERNAL_TOKEN=replace-with-runtime-token \
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-file-processing-e2e-smoke.mjs
```

该脚本通过 v2 登录后上传一个小型 `.txt` 文件，调用 `/file/internal/jobs/processing/run` 触发 owner job，再只读查询该 `fileId` 的 `file_processing_task` 和 `file_processing_artifact`。验收要求：`SECURITY_SCAN`、`TEXT_EXTRACT`、`AI_PARSE` 全部 `SUCCEEDED`，并生成 `SECURITY_SCAN_RESULT`、`TEXT_CONTENT`、`AI_PARSE_READY`。business E2E contract 还会校验 run timing、上传 txt/text/plain 元数据、任务 created/claimed/completed 时间、retryCount=0、lastError 为空、产物 created/updated 时间和 contentLength，以及 pending/failed/dead-letter backlog 不恶化。默认产物为 `artifacts/ddd/file/file-processing-e2e.json`。

支付 webhook sandbox E2E smoke：

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=3307 \
MYSQL_USER=root \
MYSQL_PASSWORD=replace-with-db-password \
DDD_EXPLAIN_DATABASE=saas \
node scripts/ddd-payment-webhook-e2e-smoke.mjs
```

该脚本通过 v2 登录配置 Stripe sandbox provider，创建支付订单，再对 `/api/v2/payment/webhooks/stripe` 发送真实 HMAC 签名事件、重复 eventId、同 nonce replay 和坏签名事件。验收要求：有效 webhook 将订单推进到 `PAID`，重复 eventId 幂等返回已处理，同 nonce replay 被拒绝，坏签名被拒绝，并从 `payment_order`、`payment_webhook_event` 读取最终状态。business E2E contract 还会校验 provider 包含 webhookSecret 配置字段、provider/order/webhook 场景耗时为正数、创建订单从 `PENDING` 流转到 `PAID`、四类 webhook eventType/processMessage 符合预期、valid 行写入 processedAt，避免只看最终订单状态而漏掉幂等或签名审计缺口。默认产物为 `artifacts/ddd/payment/payment-webhook-e2e.json`。

AI provider 和 owner gateway runtime drill：

```bash
LUMIRA_BASE_URL=http://127.0.0.1:8080 \
node scripts/ddd-ai-runtime-drill.mjs
```

在已配置真实或 sandbox LLM/vector provider、IAM/Platform/File owner base URL 和内部 token 的环境，必须开启强制远程验收：

```bash
LUMIRA_BASE_URL=https://staging.example.com \
DDD_AI_EXPECT_PROVIDER_REMOTE=true \
DDD_AI_EXPECT_OWNER_GATEWAY_REMOTE=true \
node scripts/ddd-ai-runtime-drill.mjs
```

该脚本读取 `/api/v2/ai/readiness`、`/api/v2/ai/health`、`/api/v2/ai/metrics`，必须证明 AI v2 chat/search/tool 契约存在，`ai.provider-runtime` 和 `ai.remote-owner-gateway` health check 存在，`ai.provider.remote_configured` 与 `ai.owner_gateway.configured` metrics 存在。AI runtime contract 要求 PASS artifact 的 readiness/health/metrics 三个 endpoint 都返回 200 且有正数耗时：readiness payload 必须包含 `/api/v2/ai/chat`，health payload 必须包含 provider runtime 和 remote owner gateway health check，metrics payload 必须包含 provider remote configured 与 owner gateway configured 指标。开启远程验收时，provider runtime 必须为 `UP` 且描述包含 `remoteConfigured=true`，owner gateway 必须为 `UP`。脚本会把 provider、chat/embedding model、`remoteConfigured`、是否本地 fallback、configured owner 数量写入 `remoteEvidence`；strict gate 会拒绝 `lumira-local` fallback、configured owner 数为 0 或 owner 列表重复的产物。失败时产物会写入 `failureDetails` 和 `summary.failureCategories`，`failures[]` 必须与 `failureDetails[].message` 一一对应，且每条 failure detail 必须带 category 和 owner，按 `endpoint`、`api-contract`、`health`、`metrics`、`provider-runtime`、`owner-gateway`、`provenance` 分类，并附带 owner 提示用于分派修复。默认产物为 `artifacts/ddd/ai/ai-runtime-drill.json`。
strict 模式下，AI drill 还会写入结构化 `productionEquivalence`，要求 `LUMIRA_AI_BASE_URL` 或 `LUMIRA_BASE_URL` 为 HTTPS 且非 localhost；可用 `DDD_AI_RUNTIME_DEPLOYMENT_EVIDENCE` 或通用 `DDD_DEPLOYMENT_EVIDENCE` 记录部署单、CI artifact、provider sandbox 日志或对象存储证据引用。AI runtime contract 在 strict 模式会要求 artifact 自身携带结构化 `productionEquivalence`，避免旧的 AI runtime JSON 只靠最终 gate 推断环境缺口。readiness summary 会展开 AI baseURL、localOnly、production-equivalence issues、provider remote 配置和 owner gateway configured owner 数，避免把 endpoint 不可达、provider 未远程化、owner gateway 未配置混在一起排查。

发布证据汇总门禁：

```bash
node scripts/ddd-release-evidence-orchestrator.mjs
```

默认 plan mode 只输出证据脚本执行计划到 `artifacts/ddd/release/orchestrator-report.json`，不会运行重型构建、Docker、性能或 runtime smoke。发布流水线需要真正采集证据时使用：

```bash
DDD_RELEASE_EVIDENCE_STRICT=true \
node scripts/ddd-release-evidence-orchestrator.mjs --run --strict
```

编排器会先执行发布 env 文件 lint，再执行发布配置矩阵预检，随后按构建、测试、runtime smoke、可选 authenticated performance baseline 晋级、Outbox replay/dead-letter、回滚校验、EXPLAIN、manifest provenance preflight、manifest、release gate、readiness summary 的顺序执行；即使中间步骤失败，也会继续尝试生成后续汇总 artifact，便于一次性看到完整缺口。运行中会持续刷新 `orchestrator-report.json`，避免 manifest 或 release gate 读取旧报告。传入 `--strict` 时，编排器会把 `DDD_RELEASE_EVIDENCE_STRICT=true` 传给所有子步骤，并为 Outbox 状态机 smoke 追加 `DDD_OUTBOX_SMOKE_STRICT=true`，让 env/config/build/test/runtime/manifest evidence 在生成阶段就执行 strict provenance 和生产等价校验。orchestrator preflight 会先检查 provenance、`DDD_RELEASE_ENV_FILE`、后端 runtime baseURL、AI runtime baseURL、前端 baseURL、`DDD_FRONTEND_EXPECT_DEPLOYED`、Docker CLI/daemon、AI remote 期望和迁移演练 env；后端、AI 和前端 baseURL 必须是 HTTPS 且非 localhost。preflight 契约固定包含 11 个 check ID：`release-provenance`、`release-config-env-file`、`backend-runtime-base-url`、`ai-runtime-base-url`、`frontend-runtime-base-url`、`frontend-deployed-expectation`、`docker-cli`、`docker-daemon`、`ai-provider-remote-expectation`、`ai-owner-gateway-remote-expectation`、`migration-runtime-evidence`，并要求每项不能缺失、重复或未知，`status` 只能是 `PASS`、`WARNING`、`BLOCKER`，且必须带有 `detail` 和非空 `envKeys`；`blockers/warnings` 计数和 preflight `status` 必须与明细一致。strict release gate 会要求该报告为 run 模式、带 provenance、包含 26 个预期步骤，并且每个 selected step 都有同序执行结果；selected step 和 executed result 不能重复或未知，step 必须有 `label`、`command` 和非空 `envKeys`，result 的 `status` 必须为数字，`skipped` 必须为布尔值；其中可选 baseline 晋级步骤即使未开启也必须以 skipped 结果入报告，最终 `release-gate` 和 `readiness-summary` 也必须出现在执行结果中。preflight 中的每个 `BLOCKER` 会被提升为 strict release gate 主 blocker，因此 Docker daemon、env file、部署 URL 或迁移演练 env 缺口都会在最终门禁里直接可见。Outbox 步骤还必须带有 `DDD_OUTBOX_SMOKE_STRICT`，manifest provenance preflight 步骤必须带有 `DDD_RELEASE_MANIFEST_CHECK_ENV`。readiness summary 会列出 orchestrator 的 selected/executed step 数、未执行步骤、strict env keys 和从 plan 进入 run 的恢复动作。

```bash
DDD_RELEASE_MANIFEST_STRICT=true \
node scripts/ddd-release-evidence-manifest.mjs
```

该脚本生成 `artifacts/ddd/release/evidence-manifest.json`，记录关键 artifact 的 SHA-256、大小和时间戳，并验证 EXPLAIN JSON 文件存在。设置 `DDD_RELEASE_MANIFEST_CHECK_ENV=true` 时只执行 provenance/artifact/EXPLAIN 预检，并写出 `artifacts/ddd/release/evidence-manifest-preflight.json`，报告只包含 key 是否存在、artifact 摘要、blocker 和脱敏 next action，不包含 secret。strict 模式会要求 manifest 自身带有环境、版本和执行人 provenance，并统计关键 artifact 的 provenance 缺失；manifest provenance 还会拒绝 `local-dev`、`local-worktree`、`local-operator` 等本地诊断值，防止本地证据被误当成生产等价发布证据。manifest contract 会复核 `summary.requiredArtifacts`、`presentArtifacts`、`optionalArtifacts`、`invalidJsonArtifacts`、`provenanceIssueArtifacts`、`explainFiles`、`blockers` 和 artifact/EXPLAIN/blockers 明细一致，并要求 `status` 与 blockers 明细一致。`blockers[]` 会由合同按缺失 artifact、无效 JSON、strict provenance、可选审计 artifact contract issue 和 EXPLAIN 状态重新计算并逐项比对；`artifacts[]` 不能包含未知 `relativePath`，避免人工追加或删除 blocker 掩盖缺失证据。每个 present artifact 还必须有正数 `bytes`、64 位 hex `sha256`、完整且可解析的 `timestamp.field/value`，且 `relativePath` 不能重复，避免人工编辑或旧脚本生成的 checksum 清单掩盖缺失证据。`artifacts/ddd/release/release-final-owner-queue-run-report.json` 和 `artifacts/ddd/release/explain-gate-report.json` 存在时会作为 optional checksum artifact 纳入 manifest，并分别校验 run report contract 和 EXPLAIN gate report contract；不存在不阻断。GitHub release evidence workflow 会在 capture preflight 并校验 capture contract 后、上传 artifacts 前再次刷新 manifest preflight、manifest、readiness summary 和 unblock brief，确保 preflight 期间重写的 redacted handoff contract report 和最终 provenance preflight report 都进入最终 checksum。manifest 不包含 release gate 自己的报告，也不包含运行中持续变化的 orchestrator report，避免循环依赖和过期 checksum；strict release gate 会单独要求 manifest 存在且 PASS，并单独验证 orchestrator report。

```bash
node scripts/ddd-release-evidence-gate.mjs
```

发布前必须使用 strict 模式；strict 模式要求 AI runtime drill、生产量级 EXPLAIN、带验收元数据的 authenticated performance baseline、部署环境前端 smoke 等关键证据存在，且 readiness、性能、File、Payment、Job、AI、Frontend 等运行时证据不能只来自 localhost。AI drill 必须是远程 provider 和远程 owner gateway 强制验收产物：

```bash
DDD_RELEASE_EVIDENCE_STRICT=true \
DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS=24 \
node scripts/ddd-release-evidence-gate.mjs
```

默认 advisory 模式会读取 `artifacts/ddd/**` 中已有证据并输出缺口，不阻断本地开发。strict 模式用于准生产或发布流水线，缺失 artifact、本地-only artifact、失败测试、不满足远程 provider/owner gateway 要求、orchestrator preflight `BLOCKER` 或证据超过 freshness 窗口都会变成 blocker。默认产物为 `artifacts/ddd/release/release-evidence-gate.json`。release gate contract 会复核 `summary.checks/blockers/warnings` 与 `checks[]`、`blockers[]`、`warnings[]` 明细一致，要求 check 状态只能是 `present`、`warning`、`blocker`，且每项必须有 `name` 和 `detail`；readiness summary 会把该 contract issue 展开到 diagnostics，避免手工编辑 gate JSON 掩盖真实 blocker。freshness 默认窗口是 24 小时，可用 `DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS` 调整；authenticated performance baseline 是已验收历史基线，不受 freshness 限制。

如果 strict gate 失败，生成面向执行者的行动摘要：

```bash
node scripts/ddd-release-readiness-summary.mjs
```

默认输出 `artifacts/ddd/release/readiness-summary.json` 和 `artifacts/ddd/release/readiness-summary.md`，会按 Docker、性能 baseline、回滚演练、配置、前端 smoke、AI runtime、迁移、运行时 provenance 等类别归纳 blocker 和建议命令。runtime readiness 会展开 baseURL、localOnly、30 个 owner readiness/health/metrics 覆盖和逐上下文 ready 状态；File/Payment/Job 业务 E2E 会展开上传处理任务、webhook 幂等/签名场景、内部 job endpoint 和跨 owner outbox 诊断；配置缺口会展开为 owner、group、envKeys 和 reason，并显示 `configContractIssue`；authenticated performance 会展开 actual/baseline 诊断和 baseline 晋级 envKeys；迁移运行证据会展开为 fresh DB drill、old DB upgrade drill、环境、版本、执行人和完成时间等 `runtimeDiagnostics`。摘要自身还会写入 `inputArtifacts`，记录 release gate、manifest、配置、迁移、Docker、runtime smoke、owner queue run report 等输入文件的路径、mtime、generatedAt 和 blocker/warning/status 计数；owner/source/priority/batch/missing-env 衍生工件也会携带同一份输入元数据，发布审计时应确认这些工件引用的是同一版 `release-evidence-gate.json`。摘要还会写入 `diagnostics.readinessSummary.contractIssues`，复核 `status`、gate blocker 计数、行动项和 owner/category 分组是否一致；release config contract issue 非空时摘要状态也会保持 `NOT_READY`，应先修复配置 artifact 或重新生成配置证据。Release Env Lint 和 Release Config Blockers 段会显示 `primaryBlockers`、`releaseConfigBlockersFromPlaceholders` 和 `releaseConfigBlockersAfterPlaceholders`：先修主阻塞和占位输入，再分派占位替换后仍存在的配置缺口；其中 repo 内 env 文件路径会显示为相对路径，home 目录下路径会显示为 `~`，避免 Markdown 泄漏本机用户名或 workspace 绝对路径。长 envKeys 会折成多行，便于复制到 secret store 或发布工单。该摘要只用于排障和分派，不替代 strict release gate。

`release-blocker-map.json`、`.csv` 和 `.md` 会把 strict release gate 的 raw blockers 同时按 category 和 owner 聚合，显示每类/每个 owner 的 blocker 分布、候选 batch、ready/blocked batch、命令和预期产物。CSV 一行一个 owner，适合导入工单或飞书表格快速分派。它用于把大批 blocker 拆成可派发的工作包，例如 production-equivalent runtime、release env/config、rollback drills、EXPLAIN 和 frontend smoke；它不会减少 blocker 数，也不替代 owner/action priority，只是把当前 gate 的 raw blocker 列表映射到执行队列。

快速上线解阻优先使用单页入口：

```bash
node scripts/ddd-release-unblock-brief.mjs
node scripts/ddd-release-unblock-brief-contract.mjs
```

默认输出 `artifacts/ddd/release/release-unblock-brief.json` 和 `.md`。该 brief 会把 final go/no-go、release env owner handoff、owner input receipt、performance baseline closure、cutover blocked items、execution waves 和前 5 个 `RUN_NOW` next action 合成一页；发布负责人先看它确认当前第一条 owner action、第一条 env owner action、owner 输入回执状态、性能 baseline 状态、P0/P1/P2/P3 波次依赖和 strict no-go 原因。brief 只显示 key 名、owner、criteria、相对路径和脱敏命令，不写 secret，也不允许本机绝对路径；它是执行入口和分派视图，不是 waiver。

当前波次执行时只复制 brief 中 `Wave operator commands` 的可运行波次。通常先处理 `P0`，例如：

```bash
DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_LIST_BATCHES=1 bash artifacts/ddd/release/release-execution-commands.sh
DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_CHECK_ENV_ONLY=1 bash artifacts/ddd/release/release-execution-commands.sh
DDD_RELEASE_PRIORITY=P0 DDD_RELEASE_DRY_RUN=1 bash artifacts/ddd/release/release-execution-commands.sh
DDD_RELEASE_ENV_FILE=<release-env-file> DDD_RELEASE_PRIORITY=P0 bash artifacts/ddd/release/release-execution-commands.sh
```

`P1`、`P2`、`P3` 若在 brief 中显示 blocked-until dependencies，不能提前执行；必须等前置 wave 的 expected artifacts 刷新、exit criteria 满足，并重新运行 strict gate/readiness summary 后再进入下一层。所有波次完成后仍必须调用硬门禁：

```bash
DDD_FINAL_GO_NO_GO_ENFORCE=1 bash artifacts/ddd/release/release-final-go-no-go-gate.sh
```

只有该脚本读取到 `cutoverAllowed=true` 才允许切流；NO-GO 会以退出码 10 阻断。

同一脚本还会输出 `release-fast-track.*`、`release-cutover-checklist.csv`、`release-cutover-owner-matrix.*`、`release-sprint-board.*`、`release-command-catalog.*`、`release-owner-handoff.*`、`release-owner-receipts.*`、`release-next-action-queue.*`、`release-blocker-closure-plan.*`、`release-env-owner-matrix.*`、`release-action-priority.*`、`release-action-batches.*`、`release-action-dependency-graph.*` 和 `release-execution-queue.*`。快速上线时先看 `release-fast-track.md`：它只给最短安全路径和 `NO_GO_STRICT`/`GO_STRICT` 决策，保持 `noAutoWaivers=true`，不会替代 strict release gate，也不会允许跳过安全、迁移、回滚、生产等价、性能或数据库计划证据；其中 `cutoverChecklist` 会把 strict gate、环境、镜像、生产等价、迁移、运行时/业务验收、回滚、EXPLAIN、manifest/orchestrator 逐项标成 `PASS` 或 `BLOCKED`，上线切流前必须全部为 `PASS`。`release-unblock-brief.json` 和 `.md` 是发布负责人当前最短路径的单页入口，会合并 final go/no-go、redacted env owner handoff、owner input receipt、authenticated performance baseline closure，以及 `release-next-action-queue` 中前 5 个 `RUN_NOW` owner action；它会展示 owner env blockers、owner input receipt pending owners 和 missing criteria、`Performance Baseline` 状态和 `Next Action Queue`，但只保留 key 名、owner、criteria、相对路径和脱敏命令，不包含 secret、不允许本机绝对路径，也不能作为 waiver。`release-cutover-checklist.csv` 是同一 checklist 的表格版，适合导入发布审批或切流会议清单；`release-cutover-owner-matrix.json`、`.csv` 和 `.md` 会把 checklist 再按 owner 反查到 batch、命令、env alias group、预期证据和退出标准，适合发布负责人直接按团队分派，且不新增人工状态，避免和 fast-track 决策漂移。`release-sprint-board.json`、`.csv` 和 `.md` 是发布战情看板，会把 priority、owner、ready/blocked 状态、依赖、cutover 项、命令和预期证据合并成 batch card，用于 standup、工单导入和下一波并行执行；`release-command-catalog.json`、`.csv` 和 `.md` 会把同一 next wave 生成 owner、priority、batch 三种粒度的 list、env-check、dry-run 和 execute 命令，适合直接复制到发布群或工单。`release-owner-handoff.json`、`.csv` 和 `.md` 会把 sprint board、command catalog 与 env owner matrix 合并成 owner 级交接包，逐个 owner 展示 ready/blocked batch、阻塞依赖、env keys、可复制命令、预期证据和退出标准，是快速上线分派工单或飞书任务的首选入口。`release-owner-receipts.json`、`.csv` 和 `.md` 是执行后的 owner 回执视图，会检查 handoff 中的预期 artifact 是否已落盘，并合并 owner action blocker，区分 `ARTIFACT_MISSING`、`CONTENT_BLOCKED`、`READY_FOR_STRICT_GATE_RERUN` 和 `WAITING_ON_DEPENDENCIES`；它用于快速定位下一步，不替代 strict release gate。`release-next-action-queue.json`、`.csv` 和 `.md` 会把 receipts 压成按 owner 排序的下一步队列，优先展示可立即执行的缺 artifact 和内容 blocker，适合发布负责人直接分派当前最短处理顺序，并通过 `executableCommands` 提供可复制执行入口；`release-next-action-commands.sh` 会按 RUN_NOW 队列生成安全默认 dry-run 脚本，支持 `DDD_RELEASE_NEXT_ACTION_LIST=1` 只列队列、`DDD_RELEASE_NEXT_ACTION_DETAIL=1` 查看单项详情，并支持 `DDD_RELEASE_NEXT_ACTION_ORDER`、`DDD_RELEASE_NEXT_ACTION_OWNER` 过滤，只有设置 `DDD_RELEASE_NEXT_ACTION_EXECUTE=1` 才实际执行；它同样不能作为 waiver。`release-blocker-closure-plan.json`、`.csv` 和 `.md` 会把每个 priority action 归类为 `RUN_NOW_LOCAL`、`RUN_NOW_WITH_REAL_ENV` 或 `WAIT_FOR_DEPENDENCIES`，并列出 owner、batch、依赖、env keys、命令、预期证据和退出标准，适合发布负责人快速区分本地可推进项与必须真实 HTTPS/CI/secret 环境采集的项；它只派生自 priority/batch，不新增 waiver。`release-env-owner-matrix.json`、`.csv` 和 `.md` 会把 `release-env-missing.json` 里的 canonical env key、alias mapping、ready/blocked batch、命令、预期证据和退出标准按 owner 聚合，适合把 `.env.release` 或 CI secret 补齐工作分派给 release-infra、database、release-performance 等 owner；它只列 key 和命令，不包含 secret 值。它们同样从 batches、execution queue、missing env 和 owner matrix 派生，不能作为 waiver。执行发布修复时再看 execution queue：`Ready Now` 只列当前 `canRunImmediately=true` 的批次，并直接带出命令、env keys、`expectedArtifacts` 和 `exitCriteria`；`Blocked Later` 列出被依赖阻塞的批次和仍需完成的前置 batch。`release-execution-queue.csv` 一行一个 batch，包含 `queueStatus`、`dependsOn`、`commands`、`expectedArtifacts` 和 `exitCriteria`，适合导入 Jira、飞书表格或发布排期表。`release-execution-commands.sh` 是从 Ready Now 批次生成的可复制命令清单，会先切到仓库根目录（可用 `LUMIRA_REPO_ROOT` 覆盖），`DDD_RELEASE_LIST_BATCHES=1` 可在不提供 secret 的情况下列出当前 ready batch，并支持同一组 `DDD_RELEASE_BATCH`、`DDD_RELEASE_OWNER`、`DDD_RELEASE_PRIORITY` 过滤器先预览执行范围；正式执行时会检查 `DDD_RELEASE_ENV_FILE` 已设置、文件存在且不是 `release-env-missing.template.env`，然后用 safe dotenv loader 加载真实发布配置；该 loader 只接受 `KEY=value` 或 `export KEY=value`，不会执行 env 文件中的 shell 语句；设置 `DDD_RELEASE_BATCH=<batch-id>` 时只执行匹配的 ready batch，设置 `DDD_RELEASE_OWNER=<owner>` 时只执行该 owner 当前 ready batch，设置 `DDD_RELEASE_PRIORITY=P0|P1|P2|P3` 时只执行该优先级当前 ready batch，这些过滤器可以组合使用且必须同时匹配，未匹配会失败并提示；设置 `DDD_RELEASE_DRY_RUN=1` 时只打印将执行的命令，不生成或刷新证据；设置 `DDD_RELEASE_CHECK_ENV_ONLY=1` 时只执行 env group 预检并跳过证据命令，适合在正式采集前先确认 secret 注入是否完整；缺少任何 env group 默认会失败，只有显式设置 `DDD_RELEASE_ALLOW_MISSING_ENV=1` 才会继续，这个开关只用于本地诊断，不能用于正式上线。env group 预检按 release config contract 的别名组判断，例如 `LUMIRA_BASE_URL=DEPLOY_CHECK_BASE_URL|LUMIRA_BASE_URL`，组内任意一个 key 存在即视为该组已覆盖，避免别名变量产生误报。它不会内嵌 secret，也不能替代真实证据 artifact，执行后仍要重跑 release gate 和 readiness summary。batches 则保留完整明细：每批都有稳定 `id`、`dependsOn`、`canRunImmediately`、命令提示、所需 env keys、`envCheckGroups`、`expectedArtifacts` 和 `exitCriteria`，JSON、CSV 和 Markdown 都会展示这些字段；`release-action-batches.csv` 是全量批次表，适合把 P0/P1/P2/P3 全部导入工单系统，和只展示当前 ready/blocked 队列的 `release-execution-queue.csv` 配套使用。依赖图工件从 batches 派生，JSON 给工单/自动化读取 `nodes`、`edges`、`readyBatchIds`、`blockedBatchIds`、`executionLevels`、`graphDensity` 和 `compressedEdges`，每个 node 也携带同批次的 `envKeys` 和 `envCheckGroups`；Markdown 会先输出按 P0/P1/P2/P3 聚合的压缩 Mermaid 图，再保留完整 DAG。发布负责人可先用 execution queue 分派当前可做事项，用压缩图理解后续层级，再进入 batch 明细确认命令和退出标准，自动化仍可读取完整边追踪每个 batch 的前置条件。P0 批次可并行准备；P1/P2/P3 会通过 `dependsOn` 指向前置批次，执行人应在依赖批次退出标准满足并重新运行 release gate 后再进入下一层。命令提示由 source/owner 的标准证据流程和行动项中的命令共同生成，不能只依赖 blocker 文案；例如 authenticated performance batch 会同时提示先跑 `ddd-authenticated-performance-smoke` 再晋级 baseline，frontend smoke batch 会同时提示 Playwright smoke 和 evidence 转换。`expectedArtifacts` 按 owner 精确到产物，File、Job、Payment 的 business E2E 批次分别对应 `file-processing-e2e.json`、`job-e2e-smoke.json`、`payment-webhook-e2e.json`，manifest 批次会同时列出 checksum manifest 和实际缺失 artifact（例如 authenticated runtime baseline 及其 promotion audit），避免执行人只重跑 manifest 而没有补根因证据。命令只是生成证据的入口；只有对应 artifact 已刷新、退出标准满足，且重新运行 release gate 后该批 blocker 消失，才进入下一批。P3 orchestrator batch 永远放在最后，用于 strict run 模式复核，不能用来替代前置 P0/P1/P2 证据采集。

`release-owner-input-receipt.json`、`.csv`、`-items.csv`、`-items.md` 和 `.md` 会把 owner input collection 的当前验收状态输出成脱敏回执：JSON 给 gate/contract 使用，`.csv` 用于导入工单或表格逐 owner 关闭，`-items.csv` 一行一个 canonical key，用于逐项关闭 34 个输入，`-items.md` 是同一逐项清单的 Markdown checklist，主 Markdown 给发布负责人阅读汇总。`release-owner-input-receipt-items/` 目录会再按 owner 拆出逐负责人 checklist，适合直接分派给 platform-events、platform-owners、release-infra、ai-owner 和 payment-owner。这些文件只列 owner、key 名、alias、输入数量、remaining placeholders/missing、packet/handoff 相对路径和 pass criteria，不包含 secret 或真实 env 值。

`release-next-action-queue.json`、`.csv` 和 `.md` 也会透出 owner input receipt 的 `PASS/PENDING_OWNER_INPUT`、pending owner、missing criteria、required owner input 数和逐 owner checklist 路径，保证最短行动队列与 final owner queue 使用同一份 owner 输入状态。该信息只用于分派和验收；owner input receipt 未 `PASS` 时仍不能切流。

`release-blocker-closure-plan.json`、`.csv` 和 `.md` 同样展示 owner input receipt 的 `PASS/PENDING_OWNER_INPUT`、pending owner、missing criteria 和 required owner input 数，并由 readiness summary contract 要求它与 next-action queue 的 receipt 摘要一致。它只用于分派和排障，不是 waiver；receipt 未 `PASS` 时 final go/no-go 仍必须保持 `NO_GO_STRICT`。

`release-next-action-commands.sh` 的 dry-run、list 和 detail 模式不需要 secret；设置 `DDD_RELEASE_NEXT_ACTION_CHECK_ENV=1` 时只用 safe dotenv loader 读取真实 `DDD_RELEASE_ENV_FILE` 并检查当前 RUN_NOW 项缺少哪些 key，不执行证据命令、不输出 secret 值。正式执行时必须设置 `DDD_RELEASE_NEXT_ACTION_EXECUTE=1` 且提供真实 `DDD_RELEASE_ENV_FILE`。脚本会拒绝缺失文件和 `release-env-missing.template.env`，再用同一 safe dotenv loader 解析 env 文件执行队列命令；该 loader 只接受 `KEY=value` 或 `export KEY=value`，不会执行 env 文件中的 shell 语句。执行模式会写出 `artifacts/ddd/release/release-next-action-run-report.json`，也可用 `DDD_RELEASE_NEXT_ACTION_REPORT` 覆盖；报告记录 order、owner、receiptStatus、command、status、durationMs 和 finishedAt，并自动运行 `node scripts/ddd-release-next-action-run-report-contract.mjs` 校验 PASS/FAIL、退出码和 summary 计数；`node scripts/ddd-release-next-action-run-report-summary.mjs` 可把报告追加到 GitHub Step Summary 或输出 Markdown。批量诊断时可显式加 `DDD_RELEASE_NEXT_ACTION_CONTINUE_ON_ERROR=1` 继续执行后续命令并收集更多失败信息；只要任一命令失败，脚本结尾仍会非零退出，不能作为上线成功依据。

`release-execution-commands.sh` 的真实执行模式会写出 `artifacts/ddd/release/release-execution-run-report.json`，也可用 `DDD_RELEASE_EXECUTION_REPORT` 覆盖；报告记录 batchId、owner、priority、command、status、durationMs 和 finishedAt，并自动运行 `node scripts/ddd-release-execution-run-report-contract.mjs` 校验 PASS/FAIL、退出码、summary 计数和命令脱敏。`node scripts/ddd-release-execution-run-report-summary.mjs` 可把报告追加到 GitHub Step Summary 或输出 Markdown，适合发布群快速查看哪个 batch/owner/priority 失败。dry-run 和 env-check-only 不写该报告，避免把预演误当成真实执行证据；该报告仍只是排障和审计辅助，不替代 expected artifacts、strict gate 或 final go/no-go。

`release-blocker-closure-commands.sh` 是 blocker closure plan 的命令入口，默认只列 `RUN_NOW_LOCAL` 和 `RUN_NOW_WITH_REAL_ENV` 项，不执行。可用 `DDD_RELEASE_CLOSURE_DETAIL=1` 查看单项详情，用 `DDD_RELEASE_CLOSURE_CHECK_ENV=1` 只检查所需 key，用 `DDD_RELEASE_CLOSURE_EXECUTE=1` 执行；支持 `DDD_RELEASE_CLOSURE_ORDER`、`DDD_RELEASE_CLOSURE_OWNER`、`DDD_RELEASE_CLOSURE_PRIORITY`、`DDD_RELEASE_CLOSURE_KIND` 过滤。env-check 和 execute 都必须提供真实 `DDD_RELEASE_ENV_FILE`，并拒绝模板文件；脚本只打印 key 名，不打印 secret 值。批量诊断时可显式加 `DDD_RELEASE_CLOSURE_CONTINUE_ON_ERROR=1` 继续执行后续命令；只要任一命令失败，最终仍会非零退出。

`release-execution-commands.sh` 和 `release-final-owner-queue-commands.sh` 也支持同样的诊断语义：分别设置 `DDD_RELEASE_CONTINUE_ON_ERROR=1` 或 `DDD_FINAL_OWNER_QUEUE_CONTINUE_ON_ERROR=1` 后，命令失败会被记录并继续尝试后续可执行项，但最终仍以失败退出。该能力只用于快速收集完整失败面，不能降低 strict gate、性能、迁移、回滚、生产等价或安全证据要求。

`release-closure-wave-env-matrix.json`、`.csv` 和 `.md` 会按 closure wave 汇总 owner、batch、env keys、命令和预期证据；`release-closure-wave-env.template.env` 是按 wave 分段的安全模板，只放 key 和 `__REQUIRED__` 占位符，不放 secret 值。真实环境执行前先按该模板补齐 `.env.release`，再用 `DDD_RELEASE_CLOSURE_CHECK_ENV=1` 预检。

`release-closure-wave-receipts.json`、`.csv` 和 `.md` 会按 wave 检查 expected artifacts 是否已经落盘，并标出 `ARTIFACT_MISSING`、`CONTENT_BLOCKED` 或 `READY_FOR_STRICT_GATE_RERUN`；`CONTENT_BLOCKED` 表示 artifact 已存在但 provenance、baseline 或内容合同仍未满足，`blockerHints` 会保留具体原因且不会被误列为缺失文件路径。每个 wave 都带 `node scripts/ddd-release-evidence-gate.mjs` 和 `node scripts/ddd-release-readiness-summary.mjs` 复核命令。它只帮助执行后验收，不替代 strict gate。`release-final-go-no-go.*` 和 `release-final-owner-queue.*` 会继续透传 content blockers，方便 CI 日志和 owner 分派直接定位内容问题。

`release-closure-wave-blocker-map.json`、`.csv` 和 `.md` 会把每个 closure wave 回连到 action id、source、owner、命令、预期证据和候选 strict blocker hint。候选 blocker 只用于追踪执行影响范围，真实放行仍以重新运行 strict release gate 后 blocker 消失为准。

`release-performance-baseline-closure.json` 和 `.md` 专门收敛 authenticated performance baseline：它会列出当前 actual、baseline promotion、baseline 缺口、不能晋级的 blocker、所需 env keys 和推荐命令。baseline 只能从生产等价、非本地、无失败的 actual 晋级，不能手工伪造。

`release-performance-baseline-commands.sh` 是同一闭环的安全执行入口：默认输出 detail/dry-run，`DDD_AUTH_PERF_BASELINE_CHECK_ENV=1` 只检查 env key，`DDD_AUTH_PERF_BASELINE_EXECUTE=1` 才执行性能采集、baseline 晋级、manifest、release gate 和 readiness summary。它必须使用真实 `DDD_RELEASE_ENV_FILE`，拒绝生成模板 env 文件，并要求真实 env 文件权限收紧到 `600`。

`release-final-go-no-go.json`、`.csv` 和 `.md` 是发布负责人最后看的单页包：它合并 strict gate、fast-track cutover checklist、closure wave receipts、closure blocker map 和 authenticated performance baseline closure。只有 strict gate 为 0、cutover checklist 全 PASS、closure wave 回执全 ready、性能 baseline READY 时才会给 `cutoverAllowed=true`；否则会列出 stop reasons 和下一批命令。它不提供 waiver。

`ddd-release-artifact-path-leak-contract.mjs` 默认扫描 21 个关键 release evidence 文件，覆盖 JSON、CSV 和发布负责人会阅读的 Markdown，包括 `readiness-summary.md`、`release-final-go-no-go.md`、`release-config-owner-input-reconciliation.json`、`release-owner-input-receipt.json`、`release-owner-input-receipt.csv`、`release-owner-input-receipt-items.csv`、`release-owner-input-receipt-items.md`、`release-owner-input-receipt.md`、`release-unblock-brief.md`、`evidence-manifest-preflight.json` 和 `release-performance-baseline-closure.md`。任何 repo root、runner home 或用户 home 绝对路径都会阻断 preflight；如需展示 env 文件或日志位置，应使用 repo-relative 路径、`~` 或外部可审计链接。`release-preflight-gate.sh` 会先运行 `DDD_RELEASE_MANIFEST_CHECK_ENV=true node scripts/ddd-release-evidence-manifest.mjs` 生成 manifest provenance preflight report，再运行 artifact path leak contract 扫描该 report；strict preflight 会在 path leak 通过后对 manifest provenance 缺口早停，避免继续执行较重的 env/final gate 而掩盖根因。随后它还会在 env readiness 前运行 `ddd-release-env-owner-handoff-redacted-contract.mjs`，校验 redacted owner handoff 的 JSON、CSV、总 Markdown 和逐 owner 目录文件完全一致，拒绝 stale owner 文件、路径穿越、具体 env 赋值、占位符、DSN 或 token，避免快速上线时按旧分派包执行；该 contract 会写出 `artifacts/ddd/release/release-env-owner-handoff-redacted-contract.json`，存在时会被 evidence manifest 作为 optional checksum artifact 收录并校验 `PASS`、`redacted=true`、`issueCount=0`。CI 会在 preflight capture 后执行最后一次 manifest refresh，防止该 JSON 在 preflight 中刷新后未被最终上传的 checksum manifest 覆盖。

`release-final-go-no-go-gate.sh` 是给 CI/CD 或发布切流步骤调用的硬门禁。默认运行只打印当前 go/no-go 决策；设置 `DDD_FINAL_GO_NO_GO_ENFORCE=1` 时，如果 `cutoverAllowed=false` 会返回退出码 10，从而阻止切流。需要先运行 `node scripts/ddd-release-readiness-summary.mjs` 刷新 final packet。

`release-final-owner-queue.json`、`.csv` 和 `.md` 会把 final go/no-go 的 stop owners 展开为 owner 级工单队列，包含 cutover item、ready/blocked batch、closure wave、缺失 artifact、首条命令和 rerun 命令；同时透出 owner input receipt 的 `PASS/PENDING_OWNER_INPUT`、pending owner 数、missing criteria、owner input 数和逐 owner checklist 路径，适合 CI 失败后直接分派。该队列只是执行入口；owner input receipt 未 `PASS` 时仍不能切流。

`release-final-owner-queue-commands.sh` 默认只列当前 `ACTIONABLE` owner，支持 `DDD_FINAL_OWNER_QUEUE_OWNER`、`DDD_FINAL_OWNER_QUEUE_STATUS`、`DDD_FINAL_OWNER_QUEUE_DETAIL` 过滤和查看明细；设置 `DDD_FINAL_OWNER_QUEUE_CHECK_ENV=1` 时会静态解析真实 `DDD_RELEASE_ENV_FILE` 中该 owner 所需 env key，不 `source` 文件、不执行其中内容；真实 env 文件权限必须收紧到 `600`，否则 check/execute 都会拒绝继续；只有设置 `DDD_FINAL_OWNER_QUEUE_EXECUTE=1` 才通过 safe dotenv loader 加载 env 并执行命令。

执行模式会写出机器可读审计报告，默认路径为 `artifacts/ddd/release/release-final-owner-queue-run-report.json`，也可用 `DDD_FINAL_OWNER_QUEUE_REPORT` 覆盖。报告包含 owner/status 过滤条件、整体退出码、`summary.totalEntries/succeededEntries/failedEntries` 汇总计数，以及每条已执行命令的 `commandIndex/commandCount`、状态和耗时；即使命令中途失败，也会通过 exit trap 写出 `FAIL` 报告。

执行脚本写出报告后会自动调用 `node scripts/ddd-final-owner-queue-run-report-contract.mjs` 校验 run report 的 schema、状态和退出码一致性，并复核 `summary` 计数必须与 `entries` 明细一致；每条 entry 还必须保留 `queueOrder`、`queueStatus`、`commandIndex` 和 `commandCount`，用于把实际执行命令追溯回 final owner queue 的优先级、可执行状态和 owner 内命令位置。也可以单独运行该 contract 复核历史报告。该 contract 只保证执行审计可被机器消费，不替代 owner evidence artifact、strict release gate 或最终 go/no-go gate。

`release-final-owner-queue-env.template.env` 会按 owner 分段列出上述命令入口需要的 env key，值只使用 `__REQUIRED__` 占位，不包含 secret。不要把填好真实值的文件提交到仓库。

可用 `release-final-owner-queue-env-init.sh` 从模板初始化本地真实 env 文件。默认目标为 `.env.release.local` 或 `DDD_RELEASE_ENV_FILE`，文件权限会设为 `600`，且默认拒绝覆盖已存在文件；只有在完成备份后才允许设置 `DDD_FINAL_OWNER_QUEUE_ENV_FORCE=1`。

发布流水线执行所有 runtime smoke 前应统一导出证据来源：

```bash
export DDD_EVIDENCE_ENVIRONMENT=staging-production-equivalent
export DDD_RELEASE_CANDIDATE="$(git rev-parse HEAD)"
export DDD_EVIDENCE_OPERATOR="${USER:-release-bot}"
```

backend/frontend build/test、readiness、authenticated performance、File、Payment、Job、AI、Docker 和 Frontend smoke artifact 会把这些值写入 `sourceEnvironment`、`releaseCandidate`、`evidenceOperator`。strict release gate 会要求这些证据具备三项真实非占位值，避免无法追溯证据来自哪个环境、哪个版本、由谁执行。Frontend smoke contract 也会要求 deployed smoke artifact 自身携带结构化 `productionEquivalence`，缺失该字段的旧前端 smoke JSON 不能作为 strict 发布证据。

`docs/36-ddd-release-env-template.env` 是发布 secret 填写模板，不是可直接放行的配置证据。`artifacts/ddd/release/release-env-missing.template.env` 是 readiness summary 根据当前缺口生成的临时填写清单，也不是证据；如果把它传给 lint，artifact 会标记 `inputKind=generated-missing-template` 和 `generatedMissingTemplate=true`，release gate 只会要求提供真实已填写的 `DDD_RELEASE_ENV_FILE`。未传 `DDD_RELEASE_ENV_FILE` 时，lint 会明确标记 `inputKind=process-environment-only`，只从当前进程环境读取可见变量；这适合本地诊断当前缺口，但不能替代真实 `.env.release`。lint artifact 会优先使用显式 `DDD_EVIDENCE_ENVIRONMENT`、`DDD_RELEASE_CANDIDATE`、`DDD_EVIDENCE_OPERATOR`，本地缺省时会推导 `local-dev`、当前 git commit 和当前用户，减少空 provenance 噪音；发布流水线仍应显式传入真实环境、版本和执行人。`release-env-missing.json` 的每个 group 会携带同批次的 `batchId`、`dependsOn`、`canRunImmediately`、`commands`、`expectedArtifacts` 和 `exitCriteria`，适合直接转成发布工单而不必再回查 `release-action-batches.json`；生成的 `.template.env` 也会把这些字段写成注释，方便填 env 文件的人同时看到每批的依赖、执行命令、预期产物和退出标准。当 lint 输入不是 `release-env-file`（包括缺失 env file、process environment only 或 generated missing template）时，readiness summary 会先把 release-config 的逐项缺失折叠到 env file 根因，避免在真实 env 文件出现前生成一串 owner 配置噪音批次。`scripts/ddd-release-config-template.test.mjs` 会验证模板覆盖所有必填 key，同时确认占位 URL/secret 会被 strict config evidence 拒绝；真实发布必须使用替换后的 `.env.release` 或 GitHub environment secrets。

历史 runtime artifact 如果已经采集成功但缺少来源元数据，或缺少由 `baseUrl` 推导的结构化 `productionEquivalence`，可以显式执行 metadata-only backfill。下面的 `local-*` 示例只适合本地诊断或历史证据标注，不能作为 strict manifest 自身的发布 provenance：

```bash
DDD_RUNTIME_PROVENANCE_BACKFILL=true \
DDD_EVIDENCE_ENVIRONMENT=local-dev \
DDD_RELEASE_CANDIDATE=local-worktree \
DDD_EVIDENCE_OPERATOR=codex \
node scripts/ddd-backfill-runtime-provenance.mjs
```

若只需要给旧 artifact 补结构化生产等价元数据，可执行：

```bash
DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL=true \
node scripts/ddd-backfill-runtime-provenance.mjs
```

该脚本只写入 `sourceEnvironment`、`releaseCandidate`、`evidenceOperator`、`provenanceBackfilledAt`、`provenanceBackfillReason`，以及可选的 `productionEquivalence`、`productionEquivalenceBackfilledAt` 和 `productionEquivalenceBackfillReason`，不会改变 `status`、性能数值、失败列表或业务结果。默认拒绝覆盖已有不同 provenance；只有显式设置 `DDD_RUNTIME_PROVENANCE_BACKFILL_OVERWRITE=true` 才允许覆盖 provenance，显式设置 `DDD_RUNTIME_PRODUCTION_EQUIVALENCE_BACKFILL_OVERWRITE=true` 才允许覆盖已有 `productionEquivalence`。它只能让既有本地证据可追溯、可被 strict contract 识别为本地证据，不能把 localhost 证据升级为生产等价证据。

后端测试证据归档：

```bash
./mvnw test
DDD_BACKEND_TEST_EVIDENCE_STRICT=true \
node scripts/ddd-backend-test-evidence.mjs
```

该脚本读取各 Maven module 的 `target/surefire-reports/TEST-*.xml`，汇总为 `artifacts/ddd/tests/backend-test-evidence.json`。strict 模式会要求 artifact 带有环境、版本和执行人 provenance；release gate 会要求关键架构边界、owner v2/readiness、AI provider/owner gateway、Payment webhook、File processing 和 Job adapter 测试存在且 failures/errors 为 0。backend test contract 还会复核 `summary.suites/tests/failures/errors/skipped/required/requiredPresent/requiredMissing` 与 Surefire `suites[]` 明细一致，并拒绝必需测试类 0 tests 或全部 skipped，避免旧报告、空跑配置或手工编辑隐藏失败测试。

后端构建产物证据归档：

```bash
DDD_BACKEND_BUILD_STRICT=true \
node scripts/ddd-backend-build-evidence.mjs
```

该脚本默认执行 `./mvnw -DskipTests package`，检查 `lumira-server`、Auth、Message、File、Plugin、Localization、Payment、AI、Job 的 Spring Boot 启动入口，以及各后端模块的 `target/classes` 和 jar 产物，默认产物为 `artifacts/ddd/build/backend-build-evidence.json`。strict 模式会要求 artifact 带有环境、版本和执行人 provenance。backend build contract 还会复核 `summary.modules/deployableModules/jars/missingEntrypoints/missingClasses/missingJars` 与 `modules[]` 明细一致，要求模块报告精确匹配 shared required backend module 清单，禁止缺失、重复或未知 module，且每个 module 的 `deployable`、`entrypoint` 必须与契约一致；jar metadata 必须包含文件路径、正数大小和 64 位 SHA-256，避免 package 证据 summary 和真实模块产物状态漂移。

Docker 镜像构建证据归档：

```bash
DDD_DOCKER_BUILD_STRICT=true \
DDD_EVIDENCE_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=2026.06.14-rc1 \
DDD_EVIDENCE_OPERATOR=release-operator \
node scripts/ddd-docker-build-evidence.mjs
```

该脚本使用 `deploy/docker/service.Dockerfile` 构建 `lumira-server` 镜像，使用 `deploy/docker/frontend.Dockerfile` 构建 `frontend` 镜像，并记录 Dockerfile checksum、image id、repo tag、size、entrypoint/cmd、exposed ports、运行用户和 inspect 结果，默认产物为 `artifacts/ddd/build/docker-image-evidence.json`。strict 模式会要求 artifact 带有环境、版本和执行人 provenance；strict gate 还会要求 `summary.images/passed/failed/skipped/blockers` 与 `images[]` 和 artifact blockers 明细一致，`status` 与镜像和 blockers 明细一致，镜像报告必须精确匹配 `lumira-server` 与 `frontend`，不能缺失、重复或包含未知镜像；每个镜像的 Dockerfile 路径、期望端口、non-root 要求、tag 和 Dockerfile SHA-256 必须与共享契约一致，`staticDockerfile` 与镜像顶层 checksum 也必须一致。顶层 `blockers[]` 必须由 skipped 镜像的 preflight/provenance blocker 或 failed 镜像的 `imageName: blocker` 精确推导，SKIPPED 镜像的 `skipReason` 必须等于 image blockers 串联结果，PASS 镜像不得残留 blocker。`lumira-server` 非 root 运行并暴露 `8080/tcp`，`frontend` 暴露 `80/tcp`，两个镜像都必须有 entrypoint 或 command。脚本会在 Docker build 前写入 `staticDockerfile` 静态合规结果，覆盖 Dockerfile 是否存在、端口声明、entrypoint/cmd、server 非 root 用户、server owner module build args、frontend frozen lockfile 和生产构建命令；Docker CLI 或 daemon 不可用时，artifact 会把每个镜像标记为 `SKIPPED`，并写入具体 `skipReason` 和 blockers，方便 CI 环境排障；readiness summary 会展开 CLI/daemon 状态、每个镜像的 Dockerfile、静态合规状态、tag、端口、non-root 要求、skip/build/inspect 状态和恢复动作。但 strict gate 仍会阻断发布。可通过 `DDD_DOCKER_TAG_PREFIX`、`DDD_DOCKER_TAG_SUFFIX` 和 `DDD_DOCKER_NO_CACHE=true` 控制镜像 tag 与缓存策略；`lumira-server` 默认不会下载 OpenTelemetry javaagent，只有设置 `DDD_DOCKER_OTEL_JAVAAGENT_URL` 或 compose build arg `OTEL_JAVAAGENT_URL` 时才把 agent 打入镜像，运行时开启 `OTEL_JAVAAGENT_ENABLED=true` 会先校验 agent 文件非空。

如果发布候选镜像已经由可信 CI 构建并拉取到当前 runner，可显式使用 inspect-only 证据路径，避免 Docker Hub 或上游 registry 抖动导致重复 build 阻塞：

```bash
DDD_DOCKER_BUILD_STRICT=true \
DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE=<ci-build-artifact-or-run-url> \
DDD_DOCKER_EXISTING_LUMIRA_SERVER_IMAGE=<registry>/lumira-server:<release-candidate> \
DDD_DOCKER_EXISTING_FRONTEND_IMAGE=<registry>/frontend:<release-candidate> \
node scripts/ddd-docker-build-evidence.mjs
```

该模式仍要求 Docker CLI/daemon 可用，strict 下还必须提供 `DDD_DOCKER_EXISTING_IMAGE_BUILD_EVIDENCE` 指向可信 CI 构建日志、制品清单或发布候选镜像 provenance；脚本仍读取当前 Dockerfile 做静态合同检查，并对指定镜像执行 `docker image inspect`。端口、non-root、entrypoint/cmd、size 和 required image 清单任何一项不满足都会失败。只有显式传入 `DDD_DOCKER_EXISTING_*_IMAGE` 时才启用该路径，默认仍执行真实 build。

前端静态与单元证据归档：

```bash
DDD_FRONTEND_STATIC_STRICT=true \
node scripts/ddd-frontend-static-evidence.mjs
```

该脚本依次执行 `corepack pnpm --dir frontend lint`、`typecheck` 和 `test`，记录退出码、耗时和输出尾部，默认产物为 `artifacts/ddd/frontend/frontend-static-evidence.json`。strict 模式会要求 artifact 带有环境、版本和执行人 provenance；release gate 会要求三项均通过，并复核 `summary.commands/passed/failed/skipped/durationMs` 与 `results[]` 明细一致、`status` 与命令结果一致，且 `results[]` 只能精确包含 lint/typecheck/unit，不能缺失、重复或出现未知命令；每项必须带 command、exitCode 和非负 durationMs，避免旧静态测试报告或手工 summary 掩盖 lint/typecheck/unit 失败。

前端生产构建证据归档：

```bash
DDD_FRONTEND_BUILD_STRICT=true \
node scripts/ddd-frontend-build-evidence.mjs
```

该脚本执行 `corepack pnpm --dir frontend build`，检查 `frontend/dist/index.html` 和静态资源产物，并记录文件数量、总大小、入口 HTML 与最大资源文件 checksum。默认产物为 `artifacts/ddd/frontend/frontend-build-evidence.json`。strict 模式会要求 artifact 带有环境、版本和执行人 provenance。frontend build contract 会要求 build command/exitCode/durationMs 完整，`summary.files/assets/totalBytes/indexHtmlPresent` 有效，entrypoint 指向 `frontend/dist/index.html` 且 bytes/SHA-256 合法，`largestFiles[]` 非空、按 bytes 降序、文件不重复并带 64 位 SHA-256，避免只凭构建退出码却缺少可审计部署产物。

发布环境配置证据归档：

```bash
DDD_RELEASE_ENV_FILE=.env.release \
DDD_RELEASE_CONFIG_STRICT=true \
node scripts/ddd-release-config-evidence.mjs
```

该脚本读取当前环境变量和可选 `DDD_RELEASE_ENV_FILE`，只归档变量存在性、脱敏 URL、secret 长度和 hash 前缀，不写出明文密钥。strict 发布要求准生产/生产等价的后端 baseURL、前端 baseURL、DB、Redis、JWT/FIELD secret、owner service URL、Job internal token、XXL-Job token、AI provider、AI owner gateway、Payment public URL 都存在；DB/Redis/owner/frontend/backend URL 不能指向 localhost，AI provider 和 IAM/Platform/File owner gateway 必须明确启用。release config contract 会复核 `summary`、`blockerDetails`、`blockersByGroup` 和 `blockersByOwner` 与 `groups[]`、`coverageMatrix`、`blockers[]`、`warnings[]` 明细一致，并要求 `coverageMatrix` 对每个配置要求精确出现一次，不能缺失、重复或包含未知 check，且每项必须列出至少一个 env key。`blockerDetails[]` 还必须逐项对应 `blockers[]`，并在指向已知配置 check 时与 `coverageMatrix` 的 owner、envKeys、required 保持一致，避免配置缺口被错误归属或用手工明细绕过。失败 artifact 会输出这些明细，用于把配置缺口分派给 release-infra、platform-owners、platform-events、file-owner、payment-owner 和 ai-owner。若失败来自 `__REQUIRED__`、`replace-with-*`、示例 URL 或其它占位值，先修 `primaryBlockers`；`placeholderDerivedConfigBlockers` 只是说明这些占位值会影响哪些配置检查，不能用来代替真实 `.env.release` 逐项审批。

可从 `docs/36-ddd-release-env-template.env` 复制变量名到 CI secret store 或受控 `.env.release`，模板中的占位符必须替换为真实准生产/生产值。

数据库迁移证据归档：

```bash
node scripts/ddd-migration-evidence.mjs
```

该脚本静态扫描 owner Flyway migration location，要求必需目录存在、SQL 文件非空、同一 location 下无重复版本，并输出 `artifacts/ddd/migration/migration-evidence.json`。发布前还必须在准生产或生产等价环境完成新库迁移和旧库升级演练，并用运行证据重新生成 artifact：

```bash
DDD_MIGRATION_CHECK_ENV=true node scripts/ddd-migration-evidence.mjs
```

上面的预检不会写入正式 `migration-evidence.json`，只生成 `artifacts/ddd/migration/migration-evidence-handoff.md`，按 database、release-infra、release-owner 列出 fresh DB drill、old DB upgrade drill、环境、release candidate、operator 和完成时间的必填变量。先用该 handoff 补齐真实 Flyway 日志、`flyway_schema_history` 导出、artifact/log 路径、对象 URI、HTTPS 链接或工单号，再运行 strict 生成命令：

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
node scripts/ddd-migration-evidence.mjs
```

strict 模式下，`scripts/ddd-migration-evidence.mjs` 和 release gate 都会要求 `freshDatabaseValidated` 和 `upgradeDatabaseValidated` 均为 `true`，并要求 `environment`、`releaseCandidate`、`operator`、ISO `completedAt`、`freshDatabaseEvidence`、`upgradeDatabaseEvidence` 全部为真实非占位证据。migration contract 会复核 `summary.locations/migrationFiles/duplicateVersionLocations/emptyFiles/runtimeReady` 与 `locations[]` 和 `runtime` 明细一致，并要求 `locations[]` 精确覆盖共享契约中的必需 owner Flyway location，不能缺失、重复或包含未知 location；每条 migration 还必须包含 `version`、`description`、`file`、正数 `bytes` 和 64 位 SHA-256，且 `migrationCount` 必须与 `migrations[]` 数量一致，避免旧扫描或手工 summary 掩盖缺失迁移目录、空迁移文件、异常文件元数据或未完成运行演练。`freshDatabaseEvidence` 和 `upgradeDatabaseEvidence` 必须分别指向不同的 Flyway 日志、`flyway_schema_history` 导出、artifact/log 路径、对象 URI、HTTPS 链接或工单号；不能用同一条笼统说明同时代表新库全量迁移和旧库升级，也不能用旧 release candidate 的迁移演练结果替代当前发布版本。artifact 会输出 `runtimeReady`、`runtimeProofs` 和 `runtimeDiagnostics`，逐项标记 fresh DB、old DB upgrade、环境、release candidate、operator、completedAt 的状态、owner、envKeys 和证据引用；非 strict 本地扫描即使 SQL 结构通过，也会用 `runtimeReady=false` 明确说明运行演练未完成。

回滚演练证据：

```bash
DDD_EVIDENCE_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=git-sha-or-build-id \
DDD_EVIDENCE_OPERATOR=release-owner@example.com \
node scripts/ddd-init-rollback-drill.mjs
```

将生成的 `artifacts/ddd/rollback/rollback-drill.json` 中每个上下文改成真实演练结果。`PASS` 必须提供 `rollbackAction`、`drillEvidence` 和 `validatedAt`，且 `drillEvidence` 需要包含可追溯的 HTTPS 链接、artifact/log 路径、对象存储 URI 或工单号，不能只是“已验证”一类描述。确实无法在本轮演练的上下文可以使用 `DEFERRED`，但必须提供 `notExercisableReason`、`riskAcceptedBy`、`deferralEvidence` 和未来的 `expiresAt`，其中 `deferralEvidence` 也必须指向审批单、会议纪要、artifact/log 路径、对象存储 URI 或工单号。`TODO`、`replace-with-*`、`Link or path...` 等占位文本都会被拒绝。strict 发布门禁会要求 artifact 总状态为 `PASS`，并要求 IAM、Auth、Platform、Message、File、Plugin、Localization、Payment、AI、Job 十个上下文全部有证据。

```bash
DDD_ROLLBACK_DRILL_CHECK_ENV=true node scripts/ddd-rollback-drill-evidence.mjs
DDD_ROLLBACK_DRILL_STRICT=true node scripts/ddd-rollback-drill-evidence.mjs
```

`DDD_ROLLBACK_DRILL_CHECK_ENV=true` 是安全预检模式，只写 `artifacts/ddd/rollback/rollback-drill-handoff.md` 或 `DDD_ROLLBACK_DRILL_HANDOFF_FILE` 指定的交接文件，不会改写 `rollback-drill.json`，也不会让 release gate 通过。正式脚本会独立校验 rollback artifact 的上下文覆盖、PASS/DEFERRED 必填字段、证据引用和时间格式，便于在汇总 release gate 前先把回滚演练证据修到可审计状态。校验通过时脚本会把 artifact 总状态写为 `PASS`；失败时写入 `blockers` 供 release readiness summary 归类。artifact 的 `summary` 由脚本生成，contract 会复核 `requiredContexts/contexts/passContexts/deferredContexts/readyContexts/missingRequiredContexts/unknownContexts/duplicateContexts/appliedDeferrals/blockers/warnings` 等统计与 contexts、diagnostics、blockers 明细一致，避免手工汇总掩盖缺失上下文或未演练条目。release gate 还会按 rollback 核心合同重新计算 `blockers[]` 并逐项比对，防止手工删除或改写 rollback blocker。`contextDiagnostics[]` 也必须精确覆盖十个上下文，owner/action 必须匹配共享 remediation 清单，status、ready、missingEvidence、deferralApplied 和 evidence 必须与 `contexts[]` 中 PASS/DEFERRED/MISSING 明细一致，避免行动摘要把 blocker 分派给错误 owner 或展示错误证据。

如果本轮发布无法安全演练某些上下文，可以通过显式审批文件批量合并 `DEFERRED` 记录：

```bash
DDD_ROLLBACK_DRILL_DEFERRAL_FILE=/secure/release/rollback-deferrals.json \
DDD_ROLLBACK_DRILL_RISK_ACCEPTED_BY=release-owner@example.com \
DDD_ROLLBACK_DRILL_DEFERRAL_EVIDENCE=CHANGE-12345 \
DDD_ROLLBACK_DRILL_DEFERRAL_EXPIRES_AT=2026-12-31T00:00:00.000Z \
node scripts/ddd-rollback-deferral-template.mjs
```

该脚本会同时生成 `artifacts/ddd/rollback/rollback-deferrals-owner-handoff/`，按 IAM/Auth/Platform/Message/File/Plugin/Localization/Payment/AI/Job 的 owner 拆分 Markdown 交接文件，方便并行找负责人确认真实延期原因、审批人、审批证据和到期时间。这个 handoff 只是签收入口，不是 waiver；填完模板中的真实延期原因、审批人、审批证据和到期时间后，再合并到 rollback drill artifact：

```bash
DDD_ROLLBACK_DRILL_DEFERRAL_FILE=/secure/release/rollback-deferrals.json \
DDD_ROLLBACK_DRILL_STRICT=true \
node scripts/ddd-rollback-drill-evidence.mjs
```

`rollback-deferrals.json` 必须包含 `contexts` 数组，每项提供 `context`、`notExercisableReason`、`riskAcceptedBy`、`deferralEvidence` 和未来的 `expiresAt`。模板脚本只生成待填写输入，不会让 gate 通过；`notExercisableReason` 中的 `replace-with-*` 占位、过期 `expiresAt` 或缺少真实证据引用都会被 `ddd-rollback-drill-evidence.mjs` 拒绝。脚本只接受真实证据引用；`deferralEvidence` 应指向审批单、变更单、会议纪要、artifact/log 路径或对象存储 URI。默认不会用 deferral 覆盖已经 `PASS` 的上下文，除非显式设置 `DDD_ROLLBACK_DRILL_DEFERRAL_OVERWRITE_PASS=true`。

前端浏览器 smoke：

```bash
PORT=8010 \
UMI_DEV_API_TARGET=http://127.0.0.1:8080 \
UMI_DEV_WS_TARGET=ws://127.0.0.1:8080 \
corepack pnpm --dir frontend dev

PLAYWRIGHT_BASE_URL=http://127.0.0.1:8010 \
PLAYWRIGHT_ADMIN_USER=admin \
PLAYWRIGHT_ADMIN_PASSWORD=123456 \
PLAYWRIGHT_NEW_PASSWORD=E2eAdmin123! \
corepack pnpm --dir frontend test:e2e:smoke
```

发布证据归档时使用 JSON reporter 生成可读 artifact，并转换为统一门禁格式：

```bash
corepack pnpm --dir frontend install --frozen-lockfile
corepack pnpm --dir frontend exec playwright install --with-deps chromium

mkdir -p artifacts/ddd/frontend

PLAYWRIGHT_BASE_URL=https://staging.example.com \
PLAYWRIGHT_ADMIN_USER=admin \
PLAYWRIGHT_ADMIN_PASSWORD=*** \
PLAYWRIGHT_NEW_PASSWORD=*** \
node scripts/ddd-frontend-playwright-smoke.mjs

PLAYWRIGHT_BASE_URL=https://staging.example.com \
DDD_FRONTEND_EXPECT_DEPLOYED=true \
DDD_EVIDENCE_ENVIRONMENT=staging \
DDD_RELEASE_CANDIDATE=2026.06.14-rc1 \
DDD_EVIDENCE_OPERATOR=release-operator \
DDD_FRONTEND_DEPLOYMENT_EVIDENCE=change-or-deploy-artifact-link \
node scripts/ddd-frontend-smoke-evidence.mjs
```

本地 smoke 应覆盖登录、强制改密、匿名跳转、核心页面、消息中心、session refresh 和登出；部署环境 smoke 还应保存录像、截图、控制台和网络日志。strict 发布要求 `DDD_FRONTEND_EXPECT_DEPLOYED=true`、HTTPS `PLAYWRIGHT_BASE_URL`，以及 `DDD_EVIDENCE_ENVIRONMENT`、`DDD_RELEASE_CANDIDATE`、`DDD_EVIDENCE_OPERATOR` 三项非占位来源元数据。`DDD_FRONTEND_DEPLOYMENT_EVIDENCE` 或通用 `DDD_DEPLOYMENT_EVIDENCE` 可记录部署单、CI artifact、Playwright HTML report、trace/video 存储路径或对象存储证据引用。`scripts/ddd-frontend-smoke-evidence.mjs` 在 `DDD_RELEASE_EVIDENCE_STRICT=true` 或 `DDD_FRONTEND_SMOKE_STRICT=true` 时会在转换阶段强制执行这些 deployed evidence 规则，并写入 `productionEquivalence`，避免最终 release gate 才发现本地或无来源前端 smoke。脚本默认要求 dashboard、download center、AI assistant、用户、角色、安全设置、支付设置、文件、插件、国际化、session refresh、消息中心和登出 smoke 全部通过，产物为 `artifacts/ddd/frontend/frontend-smoke.json`。该 artifact 还会写入 `flowCoverage`，逐条说明每个必需 flow 是否有 passed `@smoke` 测试覆盖；若 Playwright JSON 缺失或某个 flow 没有匹配通过用例，`flowCoverage.reason` 会给出具体原因。frontend smoke contract 会复核 `summary.requiredFlows/missingRequiredFlows` 与 required/coverage 明细一致，required flow 和 coverage 不能重复、缺失或出现未知项，passed coverage 必须记录匹配到的 Playwright 标题；`diagnostics.staticSpecCoverage` 也必须与 required flow 清单精确对应，避免测试标题或静态 spec 漂移。当 Playwright 报告存在时，也会复核 `summary.total/passed/failed/skipped` 与 `tests[]` 一致，并要求 `status` 与 blockers 明细一致。release gate 会按当前 strict/advisory 口径重新计算 `blockers[]` 并逐项比对，防止用旧的非 strict artifact 或手工 blocker 列表绕过部署 smoke 要求。`scripts/ddd-release-readiness-summary.mjs` 会展开前端 smoke 的 baseURL、HTTPS/localOnly、production-equivalence issues、`expectDeployed`、测试总数、必需 flow 覆盖和每个缺失 flow 的修复动作。

## 2. Readiness Drill Matrix

| Context | Readiness | Health | Metrics | 核心演练 | 回滚动作 | 必留证据 |
| --- | --- | --- | --- | --- | --- | --- |
| IAM | `/api/v2/iam/readiness` | `/api/v2/iam/health` | `/api/v2/iam/metrics` | 角色权限变更后权限快照版本推进、缓存失效、当前用户权限刷新 | 回切 v1 IAM adapter，保留 `sys_user/sys_role/sys_permission` owner 写入 | readiness JSON、permission snapshot version、权限变更审计、缓存失效日志 |
| Auth | `/api/v2/auth/readiness` | `/api/v2/auth/health` | `/api/v2/auth/metrics` | 登录、refresh token、current-user 热路径命中 session payload；损坏 session payload 自动移除 | 回切 v1 auth adapter，保留 Redis key schema 和 TTL | session hit/miss/save/remove/corrupt 指标、`auth.bootstrap_cache.alignment_rejects`、登录请求数、Redis key 样例 |
| Platform | `/api/v2/platform/readiness` | `/api/v2/platform/health` | `/api/v2/platform/metrics` | runtime appearance/config 版本推进、bootstrap 读取缓存命中、审计写入失败告警 | 回切 platform adapter，清理 runtime appearance cache | read-model version、config p95、bootstrap p95、审计失败率 |
| Message | `/api/v2/message/readiness` | `/api/v2/message/health` | `/api/v2/message/metrics` | 消息发布、可见列表分页、未读 capped count、WebSocket 投递、outbox replay | 暂停 relay job，v2 adapter 回切 MessageAppService 兼容路径 | outbox backlog、WebSocket 投递日志、capped count SQL explain |
| File | `/api/v2/files/readiness` | `/api/v2/files/health` | `/api/v2/files/metrics` | 上传立即返回、处理任务异步 claim、扫描/OCR/缩略图/TEXT_CONTENT artifact 生成、replay | 暂停 file processing job，保留原文件和 task，按 task id 重跑 | upload p95、processing backlog、artifact 记录、scan/OCR 结果 |
| Plugin | `/api/v2/plugins/readiness` | `/api/v2/plugins/health` | `/api/v2/plugins/metrics` | 插件启用、禁用、回滚、bootstrap 投影版本推进、outbox replay | 禁用租户插件或回滚版本，重建 plugin/bootstrap 投影 | pending/failed/dead-letter/dispatchable backlog、bootstrap 版本、回滚审计 |
| Localization | `/api/v2/localization/readiness` | `/api/v2/localization/health` | `/api/v2/localization/metrics` | 发布、回滚、runtime bundle 缓存命中与旧版本失效 | 回滚 active release，清空 runtime bundle cache | bundle cache size/hit/miss/hit-ratio、release id、回滚审计 |
| Payment | `/api/v2/payment/readiness` | `/api/v2/payment/health` | `/api/v2/payment/metrics` | webhook 签名失败、nonce replay、重复 event 幂等、outbox replay | 暂停 provider 回调入口，按 eventKey replay 或回切单体 endpoint | webhook p95、签名失败、重复拦截、outbox backlog、订单状态轨迹 |
| AI | `/api/v2/ai/readiness` | `/api/v2/ai/health` | `/api/v2/ai/metrics` | 知识库文档上传后异步索引、失败重试、DEAD_LETTER、向量检索重排、tool 调用审计 | 暂停 aiKnowledgeIndexJob，按 documentId 重建索引，禁用外部 provider/vector adapter | pending/retryable/failed/dead-letter、vector/local-hashing chunk、tool audit |
| Job | `/api/v2/job/readiness` | `/api/v2/job/health` | `/api/v2/job/metrics` | XXL-JOB handler 只调用 owner internal API，不读写业务表；owner relay 幂等 | 禁用对应 handler，BackendJobClient URL 回切单体 owner endpoint | configured target count、internal token 状态、handler 调用日志 |

## 3. Outbox Relay Drill

每个 owner outbox 至少演练以下路径：

1. 正常事件进入 `RECORDED` 或 owner 等价状态。
2. relay claim 后进入 dispatching 状态。
3. dispatcher 成功后标记 delivered。
4. dispatcher 抛错后增加 retry count，并写入 next retry 时间。
5. 超过重试阈值后进入 dead-letter。
6. 调用 owner replay endpoint，验证幂等 key 不产生重复业务副作用。

建议保留证据：

- relay 前后的 outbox 行。
- job-executor handler 日志。
- owner `/metrics` 响应。
- replay 请求和响应。
- 业务聚合状态快照。
- `artifacts/ddd/jobs/job-e2e-smoke.json`。

## 4. Performance Acceptance

性能验收以当前优化后数据为 baseline。新增或修改热路径时必须满足：

- 端点 p95 不得比 baseline 回退超过 10%，且 actual 必须覆盖 baseline 中所有热路径端点。
- 列表查询必须分页。
- 高频读不加载完整聚合。
- 跨上下文读取只走 API、事件投影或缓存快照。
- 新增热点 SQL 必须进入 `docs/28-ddd-hot-path-explain-plan.md`。
- `EXPLAIN FORMAT=JSON` artifact 必须带采集环境、版本、执行人、SQL checksum，不允许 `access_type=ALL`，非 `const/system/eq_ref` 访问必须命中索引 key。

建议保存：

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

- DDD 架构边界测试通过。
- `node scripts/ddd-backend-test-evidence.mjs` 通过，并保存后端关键测试 artifact。
- `node scripts/ddd-backend-build-evidence.mjs` 通过，并保存后端 package/jar artifact。
- `node scripts/ddd-docker-build-evidence.mjs` 通过，并保存 Docker image artifact。
- `node scripts/ddd-frontend-static-evidence.mjs` 通过，并保存前端 lint/typecheck/unit artifact。
- `node scripts/ddd-frontend-build-evidence.mjs` 通过，并保存前端 production build artifact。
- `node scripts/ddd-promote-performance-baseline.mjs` 通过，并保存非 localhost、带验收元数据的 authenticated performance baseline。
- `DDD_RELEASE_ENV_FILE=.env.release node scripts/ddd-release-config-evidence.mjs` 通过，并保存生产等价配置 artifact。
- `node scripts/ddd-release-evidence-manifest.mjs` 通过，并保存 checksum manifest。
- `node scripts/ddd-migration-evidence.mjs` 通过，并保存 Flyway 静态和运行演练 artifact。
- `node scripts/ddd-rollback-drill-evidence.mjs` 通过，并保存真实 rollback drill artifact。
- 十个上下文 readiness/health/metrics controller 和测试存在。
- `node scripts/ddd-readiness-gate.mjs` 通过。
- 运行环境 `node scripts/ddd-runtime-readiness-smoke.mjs` 通过，并保存 30 个 endpoint JSON artifact。
- Runtime smoke artifact 均包含 `sourceEnvironment`、`releaseCandidate`、`evidenceOperator`。
- Strict release gate 中所有关键 artifact 均在 `DDD_RELEASE_MAX_ARTIFACT_AGE_HOURS` 窗口内。
- `node scripts/ddd-explain-gate.mjs` 通过。
- `node scripts/ddd-job-e2e-smoke.mjs` 通过，并保存 job/internal E2E artifact。
- 性能 baseline/actual 比较通过。
- 每个 owner 的 outbox 或异步任务演练有证据。
- 每个上下文的回滚动作至少演练一次或有明确不可演练原因。
- `artifacts/ddd/rollback/rollback-drill.json` 覆盖十个上下文，并进入 release evidence gate。
- 前端 smoke 来自 HTTPS 部署 baseURL，设置 `DDD_FRONTEND_EXPECT_DEPLOYED=true`，并覆盖登录、首页、用户角色、消息、上传、插件、国际化、支付回调模拟、AI 会话。
