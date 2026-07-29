# 赛事报名与评审流程切换手册

## 目标

将赛事报名、材料、评审和结果发布统一切换到 `/api/v2/aiadc/registrations` 与
`/api/v2/reviews`，旧的阶段人工评审接口仅作为短期回退开关。

## 切换前

1. 备份数据库，并确认最近一次备份可恢复。
2. 执行 `competition-workflow-cutover.sql`。
3. 对存在 `missing_new_publication_count > 0` 的历史赛事保留只读归档；不要把旧结果
   直接写成新评审批次，以免伪造专家评分明细。
4. 确认 `platform_event_outbox` 没有长期停留在 `FAILED` 或 `DEAD_LETTER` 的评审发布事件。
5. 用一个试点赛事走完：报名、材料、冻结候选、分配专家、评分、汇总、终审、发布、
   学生查看结果和晋级材料上传。

## 切换

设置以下环境变量并滚动发布：

```text
SAAS_WORKFLOW_LEGACY_STAGE_REVIEW_ENABLED=false
LUMIRA_EVENT_REVIEW_RESULT_CONSUMER_ENABLED=true
SAAS_EVENT_OUTBOX_DISPATCHER=redis-stream
```

旧接口 `/api/v2/aiadc/stages/{stageId}/review-candidates`、
`/review-candidates/{registrationId}` 和 `/apply-promotion-rule` 将拒绝调用。

## 验证

1. 新评审工作台可创建批次并发布。
2. 每个团队收到一条结果通知；重复投递不会产生重复站内信。
3. 晋级团队可进入下一阶段材料窗口，未晋级团队被拒绝。
4. Prometheus 中没有持续增长的 outbox 重试、死信或消费失败。

## 回滚

若新评审入口不可用，先停止发布新结果，再临时设置
`SAAS_WORKFLOW_LEGACY_STAGE_REVIEW_ENABLED=true` 并滚动发布。回滚只恢复旧接口访问，
不会删除新流程数据。恢复后仍需处理未投递事件，再重新执行切换检查。
