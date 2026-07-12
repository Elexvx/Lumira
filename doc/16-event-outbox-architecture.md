# 事件与 Outbox 架构

## 1. 定位

Outbox 用于把本服务内已经提交的业务事实可靠地发布出去。它不是同步 RPC 的替代品，而是用于通知、索引、缓存失效、审计投影、补偿和弱一致流程。

Lumira 使用三类事件：

| 类型 | 范围 | 用途 |
| --- | --- | --- |
| 领域事件 | 单个 owner 模块内部 | 表达聚合或领域状态变化 |
| 集成事件 | 跨模块 | 通知其他模块建立投影或触发后续处理 |
| Outbox 事件 | 需要可靠投递的集成事件 | 与业务写入同事务记录，异步重试和重放 |

基本边界：事件由完成业务写入的 owner 发布；消费者必须幂等；Payload 只包含完成协作所需的最少事实，不得包含密码、原始邀请 Token 或敏感身份数据。高风险命令仍需同步鉴权，不能用事件替代。

当前仓库已有两条基础能力：

- `system-service`：平台级 outbox、relay、logging dispatcher、Redis Stream dispatcher、内部任务触发接口。
- `message-service`：消息事件 outbox、WebSocket 投递、重放和消息同步。
- `plugin-service`：插件生命周期 outbox、内部 relay、logging dispatcher、失败退避、死信标记和手动 replay。

本轮补强了 `system-service` 自己的 `platform_event_outbox` migration，并增加了标准事件发布入口 `PlatformEventPublisher` 和事件常量 `PlatformEventTypes`。AI 知识库文档上传、重建索引、删除会发布平台事件。

随后 `file-service` 也补齐了 owner migration、`platform_event_outbox` migration、文件事件发布器和上传/删除事件记录。文件事件目前先记录到本服务 outbox，后续可接入 relay、Redis Stream 或专门的文件处理消费者。

## 2. 事件表

聚合部署阶段，`system-service`、`message-service`、`file-service` 暂共享 `platform_event_outbox` 物理表，并通过 `source_type` 划分 owner。所有 owner relay、metrics、manual replay 和污染修复脚本都必须带上自己的 `source_type` 过滤，禁止跨 owner claim、失败标记或重放。物理拆分后，每个 owner 可迁移为独立 outbox 表，但迁移前仍以 `source_type` 作为强边界。

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
| `PLUGIN` | `plugin.*` | `plugin.tenant-plugin` | 插件启用、禁用和生命周期变更后 |

## 5. 生产规则

- Outbox 记录必须与业务写入处于同一事务；事务提交后再由 relay 投递。
- 事件 payload 只描述事实，不携带后续处理指令。
- 消费端必须幂等，优先使用 `event_key` 或业务 aggregate id 去重。
- 事件失败后进入 `FAILED`，由 relay 根据 `next_retry_at` 重试。
- 事件不替代审计日志；高风险操作仍需同步写审计。
- 跨模块事件至少包含 `eventId`、`eventType`、`occurredAt`、`sourceModule`、`tenantId`、`aggregateType` 和 `aggregateId`。

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

`FILE_OBJECT_UPLOADED` 和 `FILE_OBJECT_DELETED` 已在 `file-service` 记录到本服务 outbox。`file-service` 已提供 owner relay/replay、logging dispatcher 和 `/file/internal/jobs/outbox/relay|replay` 内部任务接口，并由 `job-executor` 的 `fileOutboxRelayJob` 作为纯 adapter 触发。下一步把病毒扫描、OCR、缩略图、知识库解析或审计投影接成真实消费者。

### 6.3 消息通知

```text
message-service 写入 msg_notice
  -> 记录消息 outbox
  -> WebSocket 实时投递
  -> 失败后通过 relay 重试或重放
```

消息服务已经有专用 `MessageEventFactory`、`PlatformEventOutboxService` 和 WebSocket delivery。

### 6.4 插件生命周期

```text
plugin-service 启用/禁用租户插件
  -> 领域模型发布插件生命周期事件
  -> 写入 plugin_event_outbox
  -> pluginOutboxRelayJob 调用 Plugin owner 内部 relay
  -> 成功标记 DELIVERED，失败指数退避，超过重试上限进入 DEAD_LETTER
  -> 管理员可通过内部 replay 接口重放指定事件
```

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

插件 outbox 使用 owner 内部任务接口：

```text
POST /plugin/internal/jobs/outbox/relay
POST /plugin/internal/jobs/outbox/{id}/replay
Header: X-Job-Token
```

`job-executor` 中的 `pluginOutboxRelayJob` 只调用 Plugin owner API，不读取或写入插件业务表。
