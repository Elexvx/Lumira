# DDD Hot Path Explain Plan

本文档记录 DDD 迁移后的热路径 SQL、owner 边界和索引验收方式。新增或修改热路径时，必须补充本清单，并在可连接数据库的环境执行对应 `EXPLAIN`。

## 验收规则

1. 列表查询必须分页，禁止无界 `count(*)`。
2. 高频读必须按 owner 表查询，跨上下文只能走内部 API、事件投影或缓存快照。
3. `type` 不应为 `ALL`，`key` 应命中清单中的索引或更优索引。
4. `rows` 应随 `tenantId + scope + limit` 收敛，不能随全库数据线性增长。
5. 需要总量时使用 capped count、异步统计或读模型投影。
6. strict 发布采集必须设置 `DDD_EXPLAIN_STRICT=true` 或 `DDD_RELEASE_EVIDENCE_STRICT=true`，并提供 `DDD_EVIDENCE_ENVIRONMENT`、`DDD_RELEASE_CANDIDATE`、`DDD_EVIDENCE_OPERATOR`、`DDD_EXPLAIN_DATABASE` 和非 localhost 的 `MYSQL_HOST`；本地库 EXPLAIN 只能用于诊断，不能作为生产等价性能证据。

## Strict Rows 上限

正式上线的 `EXPLAIN FORMAT=JSON` artifact 不只要求命中索引，还要求每个 table plan node 的 `rows_examined_per_scan` 或 `rows` 不超过下列阈值。阈值按 capped limit、owner 过滤和预期选择性设置，超过阈值说明索引虽然存在但热路径仍可能退化。

| EXPLAIN 文件 | Strict max rows per scan |
| --- | ---: |
| `platform-runtime-appearance.json` | 20 |
| `plugin-bootstrap.json` | 1000 |
| `message-visible-list.json` | 100 |
| `message-unread-count.json` | 500 |
| `message-archive-total.json` | 2000 |
| `ai-knowledge-index-retry.json` | 200 |
| `platform-outbox-owner-relay-message.json` | 500 |
| `platform-outbox-owner-relay-file.json` | 500 |

## 热路径清单

| 热路径 | Owner | 查询入口 | 关键过滤 | 期望索引 | 说明 |
| --- | --- | --- | --- | --- | --- |
| Platform runtime appearance | Platform | `SystemPlatformSettingsAppService.loadConfigValuesByKeys` | `tenant_id + config_key + deleted` | `uk_sys_config_key` | 品牌、水印、悬浮窗通过 `platform/runtime-appearance` 版本失效。 |
| Plugin bootstrap | Plugin | `PluginPersistenceMapper.listTenantPlugins` | `tenant_id + enabled + deleted` | `uk_sys_plugin_tenant_rel`, `uk_sys_plugin_definition_code`, `uk_sys_plugin_version_code_version` | 插件启停/版本切换通过 System owner bump `plugin/bootstrap` 版本。 |
| Message visible list | Message | `MessageNoticeMapper.listVisiblePublished` | `tenant_id + publish_status + deleted + target_scope + target + id` | `idx_msg_notice_visible_recent` | 使用 `pageSize + 1` 判断 hasMore，不做总量 count；通用强制索引包含 `target_scope`，租户级广播消息可先收敛可见范围。 |
| Message unread count | Message | `MessageNoticeMapper.countUnread` | `tenant_id + publish_status + deleted + target_scope + target + limit` | `idx_msg_notice_visible_recent` | capped count，最大扫描 `UNREAD_COUNT_CAP`；`idx_msg_notice_visible_target_user_recent` 和 `idx_msg_notice_visible_target_role_recent` 也包含 `target_scope`，为用户/角色精确目标过滤和后续 UNION 分支查询预留稳定索引。 |
| Message archive total | Message | `MessageNoticeMapper.countArchive` | archive filters + `limit countLimit` | `idx_msg_notice_visible_recent` | capped total，避免历史归档全表统计；默认发布态归档路径复用可见消息 scope 索引。 |
| IAM permission snapshot | IAM | `PermissionSnapshotService.loadSnapshot` | `tenant_id + user_id + version` | Redis/read-model version | 权限变更 bump `iam/permission_snapshot`，消息查询携带 snapshot version。 |
| AI knowledge index retry | AI | `AiKnowledgeBaseAppService.processPendingIndexTasks` | `status + is_deleted + index_next_retry_at` | `idx_ai_knowledge_document_index_retry` | 到期失败任务重试，超过阈值进入 `DEAD_LETTER`。 |
| Message outbox owner relay | Message | `MessageOutboxRelayJobHandler` / `PlatformEventOutboxService.listDispatchable` | `source_type + created_at + id + dispatch_status + next_retry_at + deleted` | `idx_platform_event_outbox_owner_queue` | 共享物理表按 `source_type=MESSAGE` claim，禁止跨 owner 读取 File/System payload，按队列顺序 oldest-first 投递。 |
| File outbox owner relay | File | `FileOutboxRelayJobHandler` / `PlatformEventOutboxService.listDispatchable` | `source_type + created_at + id + dispatch_status + next_retry_at + deleted` | `idx_platform_event_outbox_owner_queue` | 共享物理表按 `source_type=FILE` claim，metrics/replay/relay 全部带 owner 过滤，按队列顺序 oldest-first 投递。 |
| File owner metadata lookup | File | `FileInternalApi.getFileForUser` | `tenant_id + uploader/user + file_id` | File owner index | AI 只通过 File owner contract 获取头像、附件和文件搜索。 |
| Payment webhook idempotency | Payment | `PaymentWebhookService` | `provider + event_id/nonce` | Payment owner unique/index | 签名失败、nonce replay、重复 webhook 已纳入测试。 |

## Explain 命令模板

以下 SQL 需在对应 profile 的数据库中执行，参数按压测租户替换。

```sql
EXPLAIN
SELECT config_key, config_value
FROM sys_config
WHERE tenant_id = 1001
  AND config_key IN ('branding.website-name', 'watermark.enabled')
  AND deleted = 0;

EXPLAIN
SELECT d.plugin_code, d.plugin_name, t.plugin_version
FROM sys_plugin_tenant t
JOIN sys_plugin_definition d ON d.plugin_code = t.plugin_code AND d.deleted = 0
JOIN sys_plugin_version v ON v.plugin_code = t.plugin_code AND v.version = t.plugin_version AND v.deleted = 0
WHERE t.tenant_id = 1001
  AND t.enabled = 1
  AND t.deleted = 0
ORDER BY d.sort_no ASC, d.plugin_code ASC
LIMIT 200;

EXPLAIN
SELECT n.id
FROM msg_notice n
WHERE n.tenant_id = 1001
  AND n.publish_status = 'PUBLISHED'
  AND n.deleted = 0
ORDER BY n.id DESC
LIMIT 21;

EXPLAIN
SELECT COUNT(*)
FROM (
  SELECT n.id
  FROM msg_notice n
  WHERE n.tenant_id = 1001
    AND n.deleted = 0
    AND n.publish_status = 'PUBLISHED'
    AND (
      n.target_scope = 'TENANT'
      OR (n.target_scope = 'USER' AND n.target_user_id = 1001)
      OR (n.target_scope = 'ROLE' AND n.target_role_id IN (3001))
    )
    AND NOT EXISTS (
      SELECT 1
      FROM msg_notice_read r
      WHERE r.notice_id = n.id
        AND r.tenant_id = n.tenant_id
        AND r.user_id = 1001
        AND r.deleted = 0
    )
  ORDER BY n.id DESC
  LIMIT 100
) unread_candidates;

EXPLAIN
SELECT COUNT(*)
FROM (
  SELECT n.id
  FROM msg_notice n
  WHERE n.tenant_id = 1001
    AND n.deleted = 0
    AND n.publish_status = 'PUBLISHED'
  ORDER BY n.id DESC
  LIMIT 1000
) archive_candidates;

EXPLAIN
SELECT id
FROM ai_knowledge_document
WHERE status IN ('INDEXING', 'FAILED')
  AND is_deleted = 0
  AND (index_next_retry_at IS NULL OR index_next_retry_at <= NOW())
ORDER BY update_time ASC, id ASC
LIMIT 20;

EXPLAIN
SELECT id
FROM platform_event_outbox FORCE INDEX (idx_platform_event_outbox_owner_queue)
WHERE deleted = 0
  AND source_type = 'MESSAGE'
  AND (
    dispatch_status = 'RECORDED'
    OR (dispatch_status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= NOW()))
  )
ORDER BY created_at ASC, id ASC
LIMIT 100;

EXPLAIN
SELECT id
FROM platform_event_outbox FORCE INDEX (idx_platform_event_outbox_owner_queue)
WHERE deleted = 0
  AND source_type = 'FILE'
  AND (
    dispatch_status = 'RECORDED'
    OR (dispatch_status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= NOW()))
  )
ORDER BY created_at ASC, id ASC
LIMIT 100;
```

## CI 接入建议

- `bin/ddd-performance-smoke.mjs` 负责 HTTP 热路径冒烟。
- `bin/ddd-performance-smoke.mjs` 支持 `DDD_PERF_BASELINE_FILE` 与 `DDD_PERF_ACTUAL_FILE`。提供 baseline 后，整体 p95 和逐端点 p95 超过基线 10%（可用 `DDD_PERF_MAX_P95_REGRESSION_RATIO` 调整）会直接失败。基线格式参考 `doc/30-ddd-performance-baseline.example.json`。
- `bin/ddd-performance-smoke.mjs` 支持 `DDD_SMOKE_SCENARIOS_FILE`，可用 JSON 场景声明 `method`、`path`、`headers`、`body`、`multipart` 和 `expectedStatuses`，用于登录、文件上传、支付 webhook、AI chat 等非 GET 热路径。场景格式参考 `doc/32-ddd-performance-scenarios.example.json`。
- 无运行服务的 CI 先执行 `node --check bin/ddd-performance-smoke.mjs`，有可访问环境时执行真实 smoke，并保存输出作为 `DDD_PERF_ACTUAL_FILE` 参与回归比较。
- `bin/ddd-collect-explain.mjs` 负责在可连接 MySQL 的环境执行 `EXPLAIN FORMAT=JSON`，默认写入 `tmp/ddd-explain`，可通过 `DDD_EXPLAIN_DIR` 指定产物目录。数据库名可用 `DDD_EXPLAIN_DATABASE` 或 `MYSQL_DATABASE` 指定，CLI 可用 `MYSQL_CLI` 覆盖，连接参数可用 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USER`、`MYSQL_PASSWORD` 指定。
- `bin/ddd-explain-gate.mjs` 已接入 CI，默认校验本清单的热点覆盖和字段完整性；当 `DDD_EXPLAIN_DIR` 指向 `EXPLAIN FORMAT=JSON` 产物目录时，会阻断 `access_type=ALL` 或未命中索引的执行计划，并要求 Message/File owner relay 产物命中 `idx_platform_event_outbox_owner_queue`。
- 数据库可用的 CI 环境增加 `node bin/ddd-collect-explain.mjs && node bin/ddd-explain-gate.mjs`，由 gate 统一判断失败条件。
- 每次新增 owner 表或 read model，先更新 `doc/27-ddd-owner-table-manifest.csv`，再补本文件的 explain 清单。
