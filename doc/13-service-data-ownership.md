# 服务与数据归属

本文回答两个问题：一张表由哪个模块负责，以及其他模块如何安全地使用这些数据。机器可读的权威清单位于 [`27-ddd-owner-table-manifest.csv`](27-ddd-owner-table-manifest.csv)，后端架构测试会读取它。

## 1. 核心规则

- 每张业务表只有一个长期 owner 模块。
- 只有 owner 可以写入该表；临时兼容写入必须在清单中显式声明。
- 新迁移脚本放在 owner 模块中。
- 非 owner 通过 Internal API、事件、缓存快照或读模型读取数据。
- 禁止 import 其他模块的 Mapper/Entity，也禁止直接写其他模块的表。
- 跨域报表或搜索使用 owner 提供的查询 Facade，或建立有明确 owner 的读模型。

`lumira-server` 只是聚合运行入口，不拥有业务表。物理上同库同进程不代表模块可以绕过归属边界。

## 2. 当前归属

下表便于人工阅读；精确表模式和兼容写入者以 CSV 清单为准。

| 上下文 | owner 模块 | 主要数据 |
| --- | --- | --- |
| AUTH | `lumira-auth` | Passkey、微信绑定、验证绑定与挑战 |
| IAM | `lumira-system` | 用户、角色、菜单、权限、部门和数据范围 |
| PLATFORM | `lumira-system` | 系统配置、字典、审计、平台治理和读模型版本 |
| MESSAGE | `lumira-message` | `msg_*` 与消息事件 |
| FILE | `lumira-file` | 文件对象、存储空间、处理任务和产物 |
| PLUGIN | `lumira-plugin` | 插件目录、租户插件和插件事件 |
| LOCALIZATION | `lumira-localization` | 语言、词条、发布和回滚数据 |
| PAYMENT | `lumira-payment` | 支付配置、订单、退款和回调事件 |
| AI | `lumira-ai` | AI 助手、知识库、会话、消息和工具 |
| TEAM | `lumira-team` | 团队、成员、邀请和加入申请 |
| JOB | `lumira-quartz` | 不拥有业务表，只负责调度与 relay 适配 |

部分历史迁移仍由 `lumira-system` 承载，以保证聚合运行兼容。这是受控历史债务，不代表 `lumira-system` 获得相应业务数据的长期所有权。

## 3. 预留业务域

以下是未来拆分方向，不表示对应模块或表已经存在：

| 计划上下文 | 计划负责的数据 |
| --- | --- |
| COMPETITION | 竞赛、规则、阶段和分组 |
| REGISTRATION | 报名、参与者资料和审核 |
| SCHEDULE | 比赛、轮次和赛果 |
| SCORE | 分数与排名 |
| CERTIFICATE | 证书模板与颁发 |
| PROJECT | 项目、成员和任务 |

在独立 owner 模块建立前，不得仅依据本表创建跨模块写入；必须先完成架构决策和归属清单变更。

## 4. 跨模块访问方式

### 同步查询或命令

调用 owner 提供的 Internal API 或应用 Facade。共享契约放在 `libs/lumira-*-api`，调用方只依赖契约，不依赖 owner 的实现、Entity 或 Mapper。

### 异步协作

owner 在业务事务中记录 Outbox 事件，消费者幂等处理并维护自己的投影。事件规则见 [事件与 Outbox](16-event-outbox-architecture.md)。

### 报表与搜索

优先调用 owner 查询 Facade。高频跨域查询可通过事件构建专用读模型；读模型必须注明 owner、刷新方式和一致性语义。

## 5. 变更清单

新增表或迁移 owner 时，必须同时处理：

1. 更新 `27-ddd-owner-table-manifest.csv`。
2. 在 owner 模块添加或迁移数据库脚本。
3. 更新本文的人工说明。
4. 删除旧模块的写路径，或登记有截止条件的临时兼容写入。
5. 建立调用方所需的 API、事件或读模型契约。
6. 运行表归属和模块依赖架构测试。

## 6. 常见错误

- 因为共享数据库就直接跨模块 Join 并写回结果。
- 在消费模块复制 owner 表的 Entity/Mapper。
- 让调度器、AI Tool 或 Controller 直接写业务表。
- 只移动 Java 代码，不移动迁移、测试和数据归属。
- 在 Markdown 表中改了 owner，却忘记更新机器读取的 CSV 清单。
