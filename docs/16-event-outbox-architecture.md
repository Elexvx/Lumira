# 事件与 Outbox 架构

## 1. 定位

Outbox 用于把本服务内已经提交的业务事实可靠地发布出去。它不是同步 RPC 的替代品，而是用于通知、索引、缓存失效、审计投影、补偿和弱一致流程。

当前仓库已有两条基础能力：

- `system-service`：平台级 outbox、relay、logging dispatcher、Redis Stream dispatcher、内部任务触发接口。
- `message-service`：消息事件 outbox、WebSocket 投递、重放和消息同步。

本轮补强了 `system-service` 自己的 `platform_event_outbox` migration，并增加了标准事件发布入口 `PlatformEventPublisher` 和事件常量 `PlatformEventTypes`。AI 知识库文档上传、重建索引、删除会发布平台事件。

随后 `file-service` 也补齐了 owner migration、`platform_event_outbox` migration、文件事件发布器和上传/删除事件记录。文件事件目前先记录到本服务 outbox，后续可接入 relay、Redis Stream 或专门的文件处理消费者。

## 2. 事件表

每个事件生产服务维护自己的 `platform_event_outbox` 表，不跨服务共享同一张物理表。

关键字段：

- `source_type`：事件来源域，例如 `AI`、`FILE`、`MESSAGE`、`SYSTEM`。
- `event_type`：事件类型，例如 `AI_KNOWLEDGE_DOCUMENT_INDEXED`。
- `event_key`：幂等键，格式为 `<eventType>:<tenantId>:<aggregateType>:<aggregateId>`。
- `payload_json`：事件负载。
- `dispatch_status`：`RECORDED`、`DISPATCHING`、`DELIVERED`、`FAILED`。
- `retry_count`、`next_retry_at`、`last_error`：失败重试信息。
- `trace_id`、`request_id`：链路追踪信息。

## 3. 标准 Payload

`system-service` 标准事件 payload 结构：

```json
{
  "schemaVersion": 1,
  "occurredAt": "2026-05-24T10:00:00",
  "tenantId": 1001,
  "userId": 2001,
  "aggregateType": "ai.knowledge-document",
  "aggregateId": 3001,
  "attributes": {
    "knowledgeBaseId": 9001,
    "documentId": 3001,
    "title": "example.pdf",
    "status": "READY",
    "chunkCount": 8
  }
}
```

## 4. 当前标准事件

| source | event | aggregate | 生产时机 |
| --- | --- | --- | --- |
| `AI` | `AI_KNOWLEDGE_DOCUMENT_INDEXED` | `ai.knowledge-document` | 知识库文档上传并完成 chunk 重建后 |
| `AI` | `AI_KNOWLEDGE_DOCUMENT_DELETED` | `ai.knowledge-document` | 知识库文档删除并软删除 chunk 后 |
| `FILE` | `FILE_OBJECT_UPLOADED` | `file.object` | 文件对象写入成功后 |
| `FILE` | `FILE_OBJECT_DELETED` | `file.object` | 文件对象删除后 |
| `MESSAGE` | `MESSAGE_NOTICE_CREATED` | `message.notice` | 站内信发布后，消息服务已有专用事件 |
| `MESSAGE` | `MESSAGE_NOTICE_RETRACTED` | `message.notice` | 站内信撤回后，消息服务已有专用事件 |

## 5. 生产规则

- 事件必须在业务事务提交后记录或投递。
- 事件 payload 只描述事实，不携带后续处理指令。
- 消费端必须幂等，优先使用 `event_key` 或业务 aggregate id 去重。
- 事件失败后进入 `FAILED`，由 relay 根据 `next_retry_at` 重试。
- 事件不替代审计日志；高风险操作仍需同步写审计。

## 6. 典型流程

### 6.1 AI 知识库索引

```text
用户上传知识库文档
  -> system-service 保存文档元数据
  -> 重建 ai_knowledge_chunk
  -> 发布 AI_KNOWLEDGE_DOCUMENT_INDEXED
  -> relay 投递到 logging 或 Redis Stream
  -> 后续可由向量化、缓存刷新、审计投影消费
```

### 6.2 文件处理

```text
文件上传到 file-service
  -> file_object 写入
  -> 发布 FILE_OBJECT_UPLOADED
  -> 后续消费：病毒扫描、OCR、缩略图、AI 知识库解析
```

`FILE_OBJECT_UPLOADED` 和 `FILE_OBJECT_DELETED` 已在 `file-service` 记录到本服务 outbox。下一步可增加 relay/dispatcher，把它们投递给病毒扫描、OCR、缩略图、知识库解析或审计投影。

### 6.3 消息通知

```text
message-service 写入 msg_notice
  -> 记录消息 outbox
  -> WebSocket 实时投递
  -> 失败后通过 relay 重试或重放
```

消息服务已经有专用 `MessageEventFactory`、`PlatformEventOutboxService` 和 WebSocket delivery。

## 7. Relay 运行

`system-service` 暴露内部任务接口：

```text
POST /internal/jobs/outbox/relay
Header: X-Job-Token
```

`job-executor` 通过 XXL-JOB 调用该接口。默认 dispatcher 是 logging；需要跨进程消费时可切到 Redis Stream：

```text
SAAS_EVENT_OUTBOX_RELAY_ENABLED=true
SAAS_EVENT_OUTBOX_DISPATCHER=redis-stream
SAAS_EVENT_REDIS_STREAM_KEY=saas:platform-events
```
