# 模块边界与新模块模板

Lumira 以模块化单体运行：`services/lumira-admin` 聚合业务模块，但每个模块仍独立拥有代码、数据、契约和业务规则。本文是新增模块与跨模块协作的统一准则。

## 1. 三层模块

| 层 | 组成 | 职责 |
| --- | --- | --- |
| 基础层 | `libs/lumira-common-*`、`libs/lumira-*-api` | 稳定契约、安全原语、通用类型和基础能力；不得依赖业务模块 |
| 平台层 | auth、system、file、message、plugin、localization、payment、ai、quartz | 认证、IAM、配置、文件、消息、插件等平台能力 |
| 业务层 | 当前 Team；未来 Competition、Registration、Schedule、Score、Certificate、Project 等 | 产品业务规则、用例、数据、事件和权限策略 |

`lumira-system` 只负责 IAM、权限、菜单、配置、审计和平台治理，不是所有业务代码的默认容器。

## 2. 当前运行约束

- `services/lumira-admin` 是同步请求的唯一 Spring Boot 启动入口。
- 应用级端口、数据库、Redis、Flyway、Actuator 等运行配置集中在聚合入口。
- `lumira-async` 与 `lumira-job-executor` 是独立后台运行时，不拥有被处理业务的数据。
- 前端、Nginx、Docker 和脚本只面向 `lumira-server`，不直接绑定内部模块地址。
- 聚合运行不放宽 Maven 依赖、表 owner、包可见性或权限边界。

## 3. 允许和禁止的协作方式

允许：

- 同步查询或命令：Internal API / Facade。
- 异步协调：领域事件、集成事件和 Outbox。
- 跨域搜索或报表：owner 查询接口或专用读模型。

禁止：

- import 其他模块的 Mapper、Entity 或 Service 实现。
- 直接读写其他模块的 owner 表。
- 从 `common-*` 或 `*-api` 反向依赖业务实现。
- 让 Quartz、AI Tool Runtime、Controller 或消息消费者绕过 owner 的应用服务写表。

例如，竞赛模块需要团队成员信息时，应调用 `TeamInternalApi` 或读取 Team 投影，不能查询 `team_member`。

## 4. 新业务模块模板

```text
services/lumira-{domain}/
├─ pom.xml
└─ src/
   ├─ main/java/com/lumira/{domain}/
   │  ├─ controller/    HTTP 适配
   │  ├─ app/           用例、事务、权限、审计
   │  ├─ domain/        聚合、值对象和领域规则
   │  ├─ repository/    持久化端口与实现
   │  ├─ entity/        模块私有持久化实体
   │  ├─ mapper/        模块私有数据访问
   │  ├─ dto/           命令和请求数据
   │  ├─ vo/            响应对象
   │  ├─ event/         领域/集成事件
   │  └─ security/      业务权限策略
   ├─ main/resources/db/migration/
   └─ test/java/com/lumira/{domain}/
```

共享契约确有多个消费者时，可增加 `libs/lumira-{domain}-api`。不要为了形式完整创建没有职责的空层。

## 5. 新模块必须先定义

- 业务边界：模块解决什么问题，不解决什么问题。
- 表归属：拥有哪些表，如何从旧 owner 迁移。
- API 契约：哪些同步能力向其他模块开放。
- 权限：permission key、租户、角色和数据范围。
- 审计：哪些写操作需要记录 actor、resource、result 和 requestId。
- 事件：哪些事实需要可靠发布，消费者如何幂等。
- 测试：应用服务、Controller、架构边界和关键工作流测试。

## 6. 何时物理拆分

模块满足以下至少两项时，才进入独立服务评估：

- 有独立表族和生命周期。
- 有独立的高频读写或异步链路。
- 需要独立扩容或发布。
- 与平台模块主要通过稳定契约协作。
- 故障不应影响系统管理主链路。

物理拆分的最小步骤：

1. 确认 API、数据和事件边界已经稳定。
2. 增加独立启动类与运行配置。
3. 分离数据库迁移、缓存和可观测配置。
4. 将聚合进程内调用替换为内部 API 或消息协作。
5. 在 Docker Compose 和代理层增加服务与路由。
6. 保持前端 `/api` 路径不变，并完成回滚演练。

物理拆分是运行决策，不应成为清理代码边界的前置条件。
