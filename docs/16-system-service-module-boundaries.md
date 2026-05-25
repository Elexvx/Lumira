# system-service 模块边界

## 1. 定位

`system-service` 是当前平台核心服务，但不能继续无限吸收所有业务能力。它的职责是维护平台控制面：用户、组织、权限、菜单、配置、审计、AI 管理和系统级运行视图。

新增能力进入 `system-service` 前，必须先判断它是不是平台控制面能力；如果是独立业务域、独立数据生命周期或高流量处理链路，应优先规划为独立服务或至少保持内部模块边界清晰。

## 2. 内部边界

| 模块 | 目录 | Owner 职责 | 不应承担 |
| --- | --- | --- | --- |
| IAM / 用户中心 | `modules/iam`、`modules/user`、`modules/system/user` | 用户主数据、身份资料、用户安全设置、用户组织关系 | 登录协议细节、第三方认证回调 |
| 权限 / 角色 / 菜单 | `modules/system/permission`、`modules/system/role`、`modules/system/menu` | RBAC、权限快照、菜单树、数据范围 | 网关路由规则、前端私有权限字符串 |
| 系统配置 / 字典 | `modules/system/config`、`modules/system/dict` | 平台配置、租户配置、字典项、配置审计 | 各业务服务私有配置的直接表操作 |
| 审计 | `modules/audit`、`modules/system/audit` | 操作日志、登录日志、高风险动作审计 | 业务流程编排和消息实时投递 |
| AI | `modules/ai` | 数字员工、LLM 服务、AI 技能、知识库、会话、检索日志 | 通用文件存储、消息投递、长任务执行器 |
| 监控 | `modules/system/monitor` | 服务健康、Redis、网关和基础运行视图 | 替代专业日志/指标平台 |
| 在线会话 | `modules/system/online` | 在线用户、踢下线、会话同步 | 登录协议本身 |
| 插件视图 | `modules/system/plugin`、`modules/plugin` | 平台菜单、权限展示和兼容视图 | 插件包安装、运行时隔离、插件网关主流程 |

## 3. 允许的依赖方向

```text
controller -> app/service -> domain -> infrastructure/mapper
```

允许：

- `controller` 做参数接收、权限入口和统一响应。
- `app` 编排用例、调用基础设施、写审计和发布事件。
- `domain` 放业务规则和状态约束。
- `infrastructure` 封装数据库、Redis、外部 API、Outbox、文件、任务。

不允许：

- controller 直接操作 mapper。
- AI、IAM、审计等模块互相直接改表。
- 为了一个页面在模块里复制权限判断、菜单判断或用户解析逻辑。
- 新模块绕开 `CurrentUser`、`TraceContext`、`ApiResponse`、统一异常和审计约定。

## 4. 四个重点模块的边界

### 4.1 IAM

IAM 是用户和身份的主数据域。它维护用户、账号身份、安全设置、组织归属和数据权限基础。

IAM 可以提供：

- 当前用户摘要。
- 用户权限快照。
- 组织和数据范围。
- 用户状态校验。

IAM 不负责：

- 登录协议本身。
- JWT 签发和刷新。
- 文件、消息、AI 等业务资源权限的细节实现。

### 4.2 AI

AI 模块是可演进为独立 `ai-service` 的业务增强域。当前留在 `system-service` 中，但必须保持边界。

AI 可以拥有：

- `ai_employee`
- `ai_llm_service`
- `ai_knowledge_base`
- `ai_knowledge_document`
- `ai_knowledge_chunk`
- `ai_conversation`
- `ai_message`

AI 只能通过 `file-service` 接收文件对象，通过 Outbox 发布索引、删除、重建等异步事件。AI 不直接维护文件服务的表。

### 4.3 系统配置

系统配置是平台控制面，不是每个业务服务的私有配置中心。

规则：

- 运行密钥和环境配置走环境变量或密钥系统。
- 业务可配置项走配置表，但读取入口要统一。
- 敏感字段必须使用字段级加密，并记录修改审计。
- 其他服务需要配置时，通过 API、配置快照或 Nacos 读取，不直接查 `sys_config`。

### 4.4 审计

审计模块记录事实，不编排业务。

应记录：

- 用户、角色、权限、菜单、配置变化。
- AI 知识库、LLM 服务、数字员工配置变化。
- 文件删除、插件安装、消息撤回等高风险动作。

审计事件来源可以是同步调用，也可以是 Outbox 消费投影。

## 5. 拆分判断

模块满足以下任意两项时，应进入独立服务评估：

- 拥有独立表族和生命周期。
- 有独立的高频读写或异步处理链路。
- 需要独立扩容或独立发布。
- 与 `system-service` 的依赖主要通过用户、权限、配置等平台能力完成。
- 故障不应该影响系统管理主链路。

当前最接近拆分评估的是 AI、插件、本地化和文件存储空间治理。
