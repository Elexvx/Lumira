# 赛事工作流演练

## 试点赛事

使用独立测试赛事、测试团队和测试专家，依次验证：

1. 学生确认报名并提交初赛材料。
2. 管理员冻结候选并分配至少两名专家。
3. 专家分别提交评分，管理员汇总、终审并发布。
4. 学生收到一条站内信，能查看结果并提交申诉。
5. 晋级团队可提交下一阶段材料，未晋级团队被拒绝。
6. 管理员可导出报名数据和材料包。

## 性能演练

在预发布环境设置专用管理员凭据后运行：

```text
npm run test:competition-load
```

可通过 `COMPETITION_SMOKE_DURATION_MS`、`COMPETITION_SMOKE_CONCURRENCY`、
`COMPETITION_SMOKE_RPS`、`COMPETITION_SMOKE_MAX_P95_MS` 和
`COMPETITION_SMOKE_MAX_ERROR_RATE` 调整负载与门槛。脚本不会输出密码。

## 故障演练

1. 临时停止消息消费者，但保持 system outbox relay 运行。
2. 发布测试评审批次，确认主事务成功且 outbox/Redis pending 增长。
3. 恢复消费者，确认 30 秒内自动认领、生成站内信并 ACK。
4. 对预发布专用的无效身份事件执行 8 次投递，确认进入 dead-letter Stream。
5. 修复身份后按运维手册重放，确认幂等回执阻止重复通知。

自动化测试 `ReviewResultEventStreamConsumerTest` 覆盖临时失败保留 pending、
定时恢复和达到 8 次后转死信的行为。
