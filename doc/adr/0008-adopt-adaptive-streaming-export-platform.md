# ADR-0008：采用全站自适应流式导出平台

## 状态

Proposed

## 背景

用户管理已有按 5,000 条切换同步/异步的导出基础，但异步路径仍会把全部记录加载为 List，并用 `XSSFWorkbook` 和 byte array 在内存中生成完整文件。其他管理页面尚未统一接入这一能力。

赛事报名字段动态、包含个人信息，大批量导出同时要求资源范围、字段授权、稳定内存、任务恢复、审计和文件过期。

## 决策

- 建立 `lumira-export` 平台模块，拥有导出任务、租约、进度和结果引用。
- 各业务 owner 实现 ExportProvider，负责字段、权限、count、cursor 和行映射；Export 平台不直接读业务表。
- 默认阈值沿用 5,000 条并按 provider 配置；小数据同步流式下载，大数据异步执行。
- 读取使用 keyset cursor 和固定批次；XLSX 使用流式 workbook/临时文件，超大数据使用 CSV/ZIP。
- 不在 JSON 中返回 Base64 文件，不把全部记录或完整工作簿保存在 JVM heap。
- 任务与 Outbox 同事务创建；async worker 使用 lease、heartbeat、cursor、retry、cancel 和 expiry。
- 每个任务绑定 resource/filter/field/permission snapshot，并执行字段脱敏和审计。

## 影响

### 正面

- 全站导出行为一致，避免大量数据卡死请求或拖垮进程。
- 业务 owner 保留查询和敏感字段权限控制。
- 任务可以重试、续跑、取消和在下载中心统一管理。

### 负面

- 需要改造现有用户导出和文件上传方式。
- 流式 XLSX、临时文件和任务清理增加实现复杂度。
- 每个领域都需要实现并测试 provider。

### 中性

- XXL-JOB 只做兜底扫描和清理，实时任务由 Outbox/async runtime 驱动。

## 备选方案

### 所有导出都同步

实现简单，但无法控制大数据量内存和超时，拒绝。

### 所有导出都异步

小数据体验差且增加无意义任务，拒绝。

### Export 平台直接查询所有业务表

破坏 owner 和数据权限边界，拒绝。

## 参考

- [完整架构设计](../../docs/plans/2026-07-20-lumira-platform-domain-architecture-design.md)
- [接口规范](../04-interface-spec.md)
