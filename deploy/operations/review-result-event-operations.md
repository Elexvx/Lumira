# 评审结果事件运维手册

## 关键对象

- Outbox 表：`platform_event_outbox`
- Redis Stream：`saas:platform-events`
- 消费组：`message-review-result-v1`
- 死信 Stream：`saas:platform-events:dead-letter`
- 幂等回执表：`event_consumer_receipt`

## 告警处置

1. 查看 `platform_event_outbox` 中 `FAILED`、`DEAD_LETTER` 的事件及 `last_error`。
2. 查看 Redis 消费组的 pending 数量和最老消息空闲时间。
3. 查看死信 Stream 的 `failureReason`、`eventKey` 和 `originalStreamId`。
4. 修复数据库、Redis、用户身份或消息写入故障后，再执行重放。

## 安全重放

Outbox 事件优先使用平台已有的按 ID 重放能力。Redis 死信仅在确认 payload 未被篡改、
目标用户 ID 与 UUID 仍然匹配后，复制回主 Stream。不要修改 `eventKey` 或事件 ID；
`event_consumer_receipt` 会阻止已经成功处理的事件重复生成站内信。

重放后确认：

1. 原事件被消费组 ACK。
2. `event_consumer_receipt.result_status = 'SUCCEEDED'`。
3. 目标账号只新增一条 `source_type = 'SYSTEM_EVENT'` 的站内信。
4. pending、失败与死信指标停止增长。

## 故障恢复

消费者每 30 秒认领一次空闲 pending 事件；连续投递达到 8 次仍失败时进入死信。
不可解析或身份字段不合法的事件会立即进入死信并 ACK 原事件，防止毒消息阻塞队列。
