# Lumira 架构收敛实施方案

状态：Complete

日期：2026-08-09

适用范围：当前 Lumira 仓库的后端模块边界、三运行时装配、Transactional Outbox、Redis Streams、任务调度、架构守卫、部署默认值和后续业务域拆分。

## 1. 决策结论

Lumira 的正式架构名称统一为：

> DDD 多运行时模块化单体，使用事务 Outbox 和 Redis Streams 支撑需要最终一致性的跨模块协作。

当前阶段不把 Auth、File、Message、Payment、Team 等模块直接拆成物理微服务。保留三个正式后端运行时：

1. `lumira-server`：唯一同步 API 和控制面入口。
2. `lumira-async`：Outbox relay、Redis Streams 消费者和后台处理入口。
3. `lumira-job-executor`：XXL-JOB 调度与补偿触发适配器。

代码模块、运行时和物理服务是三种不同边界，禁止继续混用“service”目录名、Spring Boot 启动类或网关 upstream 名称来证明已经微服务化。

## 2. 当前事实基线

- `lumira-server` 在同一 Spring 容器中装配 system、ai、activity、competition、event-catalog、expert、project、workflow、export、auth、file、message、plugin、localization、payment 和 team。
- `lumira-async` 是瘦异步运行时：只保留 Redis、relay、消费者、owner control-plane HTTP 适配器与可观测性，不再装配业务 owner 实现、数据库、MyBatis 或 Flyway。
- 生产只构建 `lumira-server`、`lumira-async`、`lumira-job-executor` 三个后端镜像。
- 数据 owner 仍共享同一 MySQL 底座，且生产仍不是物理拆库的微服务架构；但 async 与 job 不再持有数据库凭据或业务存储卷。
- 同步事务仍是主要业务执行模型；Outbox 和 Redis Streams 只用于跨边界副作用、后台任务和允许最终一致的投影。
- Activity、Competition（含 Registration、Review、Certificate）、Project、Expert、Workflow、Export、AI 与事件目录均有独立 owner 模块；`lumira-system` 只保留 IAM、平台治理、审计、共享事件桥和受控内部适配器。

### 2.1 2026-08-09 执行进度

- 阶段 0/1 的基础守卫和可靠性切片已完成：Message 与 File 的事务 Outbox 缺口已修复，Payment consumer 已具备周期 pending 恢复、投递上限、DLQ、幂等和指标，生产 dispatcher 默认值已统一。
- 正式生产拓扑已固定为 `lumira-server`、`lumira-async`、`lumira-job-executor`；API proxy 不再接受指向未部署 owner 容器的 upstream 覆盖，Job Executor 只保留 Async 与 Control Plane 两类真实目标。
- async 已改为通过 common-api 契约和带 scoped token 的内部 endpoint 触发 owner relay/replay 与赛事支付处理；其 POM 不再依赖 system、file、message、plugin、payment 或 competition 的完整实现。
- System/IAM、AI、Competition、Registration、Workflow 与 InternalSystemController 的历史直接持久化均已下沉到 owner repository、adapter 或受控 port；历史 debt register 已清空而非扩大 allowlist。
- Activity、Competition、Project、Expert、Workflow、Export 与 AI 已迁入独立 owner 模块，旧 `lumira-system` 对应业务源码目录已清零；每个模块仍由 `lumira-server` 聚合运行。
- Activity/Competition 公共查询已使用可重建的 `event_catalog_item` 读模型，基于事务 Outbox 投影并带事件序号水位保护。
- 已完成最终串行编译、测试、Compose 与架构扫描验证；完整 29 模块 Maven 测试于 2026-08-09 通过。未提交、未推送、未部署。

## 3. 本次收口的问题和结论

### 3.1 架构语义与部署事实不一致

仓库存在多个 `*-service` 模块、独立 Application、Internal API、服务令牌和可配置 Base URL，但生产同步请求仍聚合到一个 `lumira-server`。本次已删除指向未部署 owner 容器的 upstream 覆盖，固定 updater 与 Job Executor 的真实目标，并以三运行时配置契约锁定部署事实；模块名不再被表述为已部署微服务。

### 3.2 `lumira-system` 业务职责过宽

平台治理、IAM、活动、赛事、报名、项目、专家、证书、工作流、导出与 AI 曾集中在一个模块。业务上下文已迁为独立 Maven owner，`lumira-system` 只保留平台/IAM 职责；历史 debt register 现为空，且守卫同时扫描 Java、注解、文本块和 mapper SQL 的跨 owner 读写。

### 3.3 事件可靠性语义不统一

- Message 与 File 已改为业务事务内先写 Outbox，提交后才做低延迟即时投递；即时投递失败仍保留 relayable Outbox。
- Payment Redis Stream 消费者具备周期 pending reclaim、投递上限、DLQ、幂等回执和指标；非赛事付款事件会被明确过滤并 ACK。
- `.env.example`、Compose 与 Spring YAML 现在以 `redis-stream` 为一致的生产 dispatcher 契约，生产 dispatcher 覆盖只传给真正执行 owner Outbox 的 server。

### 3.4 架构守卫存在覆盖缺口

`DddArchitectureBoundaryTest` 已改用目录到实际 `artifactId` 的受控映射，并通过反向夹具验证它会拦截实现依赖；manifest 将 bootstrap schema 与运行期 writer 分离，迁出的 owner 不再由 `lumira-system` 宽泛兼容写入放行。

### 3.5 本地权威文档不完整

外部知识库可保存叙述性材料，但当前 revision 必须保留可执行规则所需的最小权威事实。本计划、owner manifest、ADR-0005/0006/0009、SQL bootstrap 与架构测试共同构成仓库内可审计的依据。

## 4. 目标架构规则

### 4.1 模块边界

- 每个业务 owner 模块拥有自己的 Application、Domain、Repository/Mapper、Infrastructure、权限和表清单。
- 模块之间只能通过 `*-api` 契约、明确的本地端口接口、Internal HTTP、Integration Event 或只读投影协作。
- 禁止模块导入其他 owner 的 Mapper、Entity 或内部 Service 实现。
- 同一 `lumira-server` 内优先使用本地端口实现；只有真实跨进程调用才使用 HTTP 适配器。

### 4.2 事务与事件

- 单个 owner 内需要强一致的数据必须在一个本地数据库事务完成。
- 需要可靠投递的 Integration Event 必须与 owner 状态在同一事务写入 owner Outbox。
- Redis Streams 只作为投递通道，MySQL Outbox 是可恢复事实来源。
- 消费语义统一为 at-least-once；所有副作用必须具备幂等键或 durable receipt。
- 所有消费者必须具备周期 pending 恢复、最大投递次数、DLQ、人工 replay、积压/失败指标。
- 即时 WebSocket/SSE 推送只能作为提交后的低延迟优化，不能替代 Outbox。

### 4.3 运行时边界

- `lumira-server` 不运行批量 relay 或后台处理循环。
- `lumira-async` 可以装配 owner 提供的异步适配器，但业务写入仍由 owner 包内的应用服务或消费处理器完成。
- `lumira-job-executor` 不拥有业务表，只触发幂等内部任务接口。
- 正式发布清单只包含三个运行时；独立业务模块 Application 不得被文档或 CI 表述为已部署微服务。

### 4.4 配置边界

- 正式部署默认使用 `redis-stream` dispatcher；`logging` 仅用于明确的本地诊断配置。
- 消费者启用、dispatcher、stream key、group、DLQ 和 relay 开关必须组成可验证的一致配置。
- 配置契约测试必须覆盖 `.env.example`、Compose 和 Spring YAML 的最终解析结果。

## 5. 分阶段实施

### 阶段 0：基线保护

1. 保留现有用户工作树，不覆盖无关前端、部署脚本和设计 QA 修改。
2. 修复服务模块依赖守卫的 artifactId 映射，并增加可故意触发失败的测试夹具或解析断言。
3. 增加三运行时发布清单契约，确保 CI 和 Dockerfile 不静默增加伪微服务镜像。
4. 把本文件作为当前 revision 的架构收敛权威入口。

完成标准：架构测试能真实识别跨服务实现依赖；当前合法模块全部通过；`git diff --check` 通过。

### 阶段 1：事件可靠性收敛

1. Message：事务内先写 Outbox，事务提交后只做低延迟即时投递；无事务调用仍先持久化再投递。
2. Payment：增加周期 pending 恢复、最小 idle time、投递次数上限、DLQ 和对应指标。
3. 对齐 Message、Payment 消费器的失败分类、ACK 时机、幂等与 replay 规则。
4. 将正式部署 dispatcher 默认值统一为 `redis-stream`，并添加配置契约测试。
5. 验证 Redis 不可用、消费者异常、重复投递、进程重启和 poison message 场景。

完成标准：业务状态和 Outbox 原子提交；失败事件无需重启即可恢复；超过阈值进入 DLQ；默认配置能够完成真实 Redis Stream 投递。

### 阶段 2：运行时装配收敛

1. 清点每个业务模块的 Control Plane、Async 和 Standalone 装配。
2. 明确本地端口实现与远程 HTTP 适配器的条件，删除生产单体模式中永远不会使用或会产生歧义的装配。
3. 建立启动上下文测试：server 不加载 async loop；async 不暴露公共控制面；job 不直接写业务表。
4. 统一健康检查、OTel service name、版本端点和 readiness 所表达的真实运行时身份。

完成标准：三个运行时的 Bean、端点、后台线程和持久化权限清单可由自动化测试证明，且不存在双重消费或双重 relay。

### 阶段 3：从 `lumira-system` 拆出业务限界上下文

迁移顺序：

1. `lumira-competition`：Competition、Registration、Review、Certificate 相关 owner 能力。
2. `lumira-activity`：Activity 及 Activity Registration。
3. `lumira-workflow`：Workflow 定义、实例、任务与状态迁移。
4. `lumira-export`：用户、报名和平台导出的统一流式任务能力。

每个模块按相同切片迁移：

1. 建立 API 契约和 Maven 模块。
2. 移动 Domain 和应用服务，不改变公开 API。
3. 移动 Repository/Mapper/Entity 和 owner manifest。
4. 旧 Controller 保留临时 facade，只调用新应用端口。
5. 将跨域同步写改为 owner API 或事务 Outbox。
6. 删除对应直接 SQL allowlist。
7. 完成模块、架构、SQL bootstrap、升级路径和端到端回归。

完成标准：`lumira-system` 不再拥有上述业务表，不再包含对应业务直接 SQL；公开 API 和历史数据兼容；每个模块可独立测试但仍由三运行时装配。

### 阶段 4：读模型与查询边界

1. Activity 与 Competition 保持独立写模型。
2. 公共搜索、首页、推荐和日历使用可重建的 `event_catalog_item` 投影。
3. 管理、报名确认、支付和评审始终读取 owner 数据。
4. 为投影延迟、版本、重建和失败积压增加观测与运维入口。

完成标准：公共查询不跨 owner 表做实时 UNION；投影可从 owner 事件重建；管理路径不存在陈旧投影决策。

### 阶段 5：是否物理拆分的决策门

只有满足至少一项才提议拆为微服务：

- 某业务域需要独立扩缩容且三运行时无法经济满足。
- 需要独立故障隔离或发布节奏。
- 有稳定团队 owner 和稳定版本化契约。
- 共享数据库已成为明确瓶颈，且已具备数据迁移、补偿和可观测能力。

物理拆分前必须先证明模块边界、数据 owner、事件契约和运维能力已经在模块化单体中成立。

## 6. 首批并行执行单元

### 工作流 A：Message Transactional Outbox

负责路径：`services/lumira-message`。

- 调整 Message 写入与推送顺序。
- 保证事务内 Outbox 原子性。
- 保留提交后即时 WebSocket 优化。
- 增加事务提交、回滚、无事务和即时投递失败测试。

### 工作流 B：Payment Consumer Recovery

负责路径：`services/lumira-async` 中的 Payment consumer 及其测试。

- 增加周期 pending reclaim。
- 增加最大投递次数和 DLQ。
- 保持 handler 幂等和成功后 ACK。
- 增加运行期失败恢复、poison message、DLQ 和重复消息测试。

### 工作流 C：Architecture Guard 与部署默认值

负责路径：`DddArchitectureBoundaryTest`、相关测试、`deploy/.env.example` 和配置契约测试。

- 修复 artifactId 假绿。
- 对齐 Redis Stream 正式默认值。
- 增加消费者与 dispatcher 配置一致性断言。
- 不修改用户当前无关的部署脚本和前端工作。

## 7. 并行集成规则

- 每个工作流只修改自己的负责路径；发现跨路径需要时先记录并通知主协调者。
- 不格式化或重写无关文件，不覆盖当前 dirty worktree。
- 不提交、不推送、不部署，除非用户另行明确授权。
- 每个工作流必须返回修改文件、行为变化、测试命令、测试结果和未解决风险。
- 主协调者负责统一审查 ACK/事务边界、配置优先级、测试覆盖和最终差异。

## 8. 验证矩阵

### 架构与依赖

- `DddArchitectureBoundaryTest`
- `ArchitecturePersistenceBoundaryTest`
- 三运行时 Docker/CI 契约测试
- owner manifest 与 SQL 写入扫描

### Message

- 提交：业务数据与 Outbox 同时存在，随后即时投递。
- 回滚：业务数据与 Outbox 都不存在，也不即时投递。
- 即时投递失败：Outbox 保留并可由 relay 重试。
- 无事务：先持久化 Outbox，再尝试即时投递。

### Payment Consumer

- 正常事件只执行一次有效副作用并 ACK。
- handler 临时失败后无需重启即可 reclaim。
- 重复事件被 durable receipt/业务幂等键拒绝。
- 超过投递上限进入 DLQ 并 ACK 原消息。
- 非目标事件按明确规则跳过并 ACK。

### 配置

- `.env.example`、Compose 和 Spring YAML 对 dispatcher/consumer 的解释一致。
- 正式默认值产生 Redis Stream 消息。
- logging 模式必须显式选择且不会错误启用依赖 Redis 的消费者。

### 综合门槛

- 相关 Maven 测试全部通过。
- 部署契约测试通过。
- `git diff --check` 通过。
- `git status --short` 中用户原有修改保持不丢失。

## 9. 回滚策略

- Message 改造可按服务文件回退；数据库表结构不变。
- Payment consumer 改造只增加恢复与 DLQ 行为；可通过 consumer 配置关闭，但不得删除原始 Stream/Outbox 数据。
- Dispatcher 默认值变更如需回退，只回退配置，不清理 MySQL Outbox 或 Redis Stream。
- 架构守卫修复不应通过扩大 allowlist 回滚；若发现合法依赖，应先补契约层或记录明确迁移计划。

## 10. 完成定义

本方案整体完成必须同时满足：

1. 三运行时身份和装配边界由自动化测试证明。
2. 所有可靠事件路径满足事务 Outbox、at-least-once、幂等、周期恢复、DLQ 和可观测要求。
3. Activity、Competition、Registration、Review、Certificate、Project、Expert、Workflow、Export 和 AI 不再由 `lumira-system` 宽泛承载，且 System 不再直连 `ai_*` 表。
4. 对应直接 SQL 债务从 allowlist 删除；非 System owner 不得直连平台/IAM/receipt 表或其他 owner 表。
5. `lumira-async` 不依赖业务 owner 实现、不持有 datasource/MyBatis/Flyway/业务卷；生产 Outbox dispatcher 只在 owner server 运行。
6. 公共跨域查询通过可重建投影实现。
7. 仓库内存在足够的权威架构事实，当前 revision 可独立审计。
8. 全部架构、模块、SQL、部署契约和关键端到端测试通过。

截至 2026-08-09，阶段 0 至阶段 4 的源代码、owner 清单、bootstrap SQL、模块装配与定向验证均已落地；完整 29 模块 Maven 串行测试已经通过，本方案状态标为完成。
