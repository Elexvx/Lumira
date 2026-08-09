# ADR-0006：活动和赛事独立写模型，公共查询使用目录投影

## 状态

Accepted / Implemented

## 背景

活动和赛事都有标题、时间、地点、图片和发布状态，但报名、赛程、材料、收费、评审和证书生命周期差异明显。当前数据库主表已经分开，混合主要发生在前端路由、模块归属和无作用域查询。

产品仍需要首页、公开搜索、推荐和日历能够统一展示活动与赛事。

## 决策

- Activity 和 Competition 保留独立聚合、表、管理 API 和报名表。
- 不创建通用 `event` 写入主表，不在管理查询中做活动/赛事 UNION。
- 新增 `event_catalog_item` 只读投影，保存公开查询需要的最小公共字段。
- Activity/Competition 发布、更新、撤回和归档时写 Outbox，由幂等消费者更新投影。
- 公共目录允许秒级最终一致；管理、报名确认和支付始终读取 owner 数据。
- 投影支持按 source 重建，失败不阻塞业务写入。

## 影响

### 正面

- 统一展示不污染业务写模型。
- 公共查询可以独立优化索引和缓存。
- Activity 与 Competition 的生命周期保持清晰。

### 负面

- 需要 Outbox consumer、重建工具和投影延迟监控。
- 用户可能短暂看到旧的公开摘要。

### 中性

- 目录不是 source of truth，可以删除重建。

## 备选方案

### 通用 event 主表加子类型

迁移大、类型分支多，且当前主表已经独立，拒绝。

### 公共查询实时 UNION 两个 owner 表

数据量小时可用，但会把公开查询耦合到两个写模型和权限条件，长期不采用。

## 参考

- [完整架构设计](../../docs/plans/2026-07-20-lumira-platform-domain-architecture-design.md)
- [事件与 Outbox](../16-event-outbox-architecture.md)

## Implementation ordering contract

- Projection ordering uses the positive, database-monotonic `platform_event_outbox.id` carried as `outboxSequence`; it never compares a DomainEvent UUID.
- A source rebuild reads this high-water mark before its owner snapshot and writes it into every rebuilt row. Only a strictly greater outbox sequence may subsequently replace that row, so delayed and equal-sequence redelivery cannot overwrite the snapshot.
- Operations trigger a source-scoped rebuild through the stateless `eventCatalogRebuildJob` XXL-JOB handler (parameter: `ACTIVITY` or `COMPETITION`), which calls the control-plane's token-protected `/internal/jobs/event-catalog/rebuild/{sourceType}` endpoint.
