import { createHash } from "node:crypto";

export const explainQueries = [
  {
    name: "platform-runtime-appearance",
    sql: `
SELECT config_key, config_value
FROM sys_config
WHERE tenant_id = 1001
  AND config_key IN ('branding.website-name', 'watermark.enabled')
  AND deleted = 0`,
  },
  {
    name: "plugin-bootstrap",
    sql: `
SELECT d.plugin_code, d.plugin_name, t.plugin_version
FROM sys_plugin_tenant t
JOIN sys_plugin_definition d ON d.plugin_code = t.plugin_code AND d.deleted = 0
JOIN sys_plugin_version v ON v.plugin_code = t.plugin_code AND v.version = t.plugin_version AND v.deleted = 0
WHERE t.tenant_id = 1001
  AND t.enabled = 1
  AND t.deleted = 0
ORDER BY d.sort_no ASC, d.plugin_code ASC
LIMIT 200`,
  },
  {
    name: "message-visible-list",
    sql: `
SELECT n.id
FROM msg_notice n FORCE INDEX (idx_msg_notice_visible_recent)
WHERE n.tenant_id = 1001
  AND n.publish_status = 'PUBLISHED'
  AND n.deleted = 0
  AND (
    n.target_scope = 'TENANT'
    OR (n.target_scope = 'USER' AND n.target_user_id = 1001)
    OR (n.target_scope = 'ROLE' AND n.target_role_id IN (3001))
  )
ORDER BY n.id DESC
LIMIT 21`,
  },
  {
    name: "message-unread-count",
    sql: `
SELECT COUNT(*)
FROM (
  SELECT n.id
  FROM msg_notice n FORCE INDEX (idx_msg_notice_visible_recent)
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
) unread_candidates`,
  },
  {
    name: "message-archive-total",
    sql: `
SELECT COUNT(*)
FROM (
  SELECT n.id
  FROM msg_notice n FORCE INDEX (idx_msg_notice_visible_recent)
  WHERE n.tenant_id = 1001
    AND n.deleted = 0
    AND n.publish_status = 'PUBLISHED'
  ORDER BY n.id DESC
  LIMIT 1000
) archive_candidates`,
  },
  {
    name: "ai-knowledge-index-retry",
    sql: `
SELECT id
FROM ai_knowledge_document
WHERE status IN ('INDEXING', 'FAILED')
  AND is_deleted = 0
  AND (index_next_retry_at IS NULL OR index_next_retry_at <= NOW())
ORDER BY update_time ASC, id ASC
LIMIT 20`,
  },
  {
    name: "platform-outbox-owner-relay-message",
    sql: `
SELECT id
FROM platform_event_outbox FORCE INDEX (idx_platform_event_outbox_owner_queue)
WHERE deleted = 0
  AND source_type = 'MESSAGE'
  AND (
    dispatch_status = 'RECORDED'
    OR (dispatch_status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= NOW()))
  )
ORDER BY created_at ASC, id ASC
LIMIT 100`,
  },
  {
    name: "platform-outbox-owner-relay-file",
    sql: `
SELECT id
FROM platform_event_outbox FORCE INDEX (idx_platform_event_outbox_owner_queue)
WHERE deleted = 0
  AND source_type = 'FILE'
  AND (
    dispatch_status = 'RECORDED'
    OR (dispatch_status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= NOW()))
  )
ORDER BY created_at ASC, id ASC
LIMIT 100`,
  },
];

export const explainQueryByName = new Map(explainQueries.map((query) => [query.name, query]));

export function explainSqlSha256(sql) {
  return createHash("sha256").update(sql).digest("hex");
}

export const expectedExplainSqlSha256ByFile = new Map(
  explainQueries.map((query) => [`${query.name}.json`, explainSqlSha256(query.sql)]),
);
