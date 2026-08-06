# ADR-0004：建立真实租户上下文和资源作用域

## 状态

Proposed

## 背景

Lumira 的架构文档要求租户上下文贯穿认证、授权、SQL、缓存、文件、任务和审计，但当前 `CurrentUser` 没有有效 tenant 字段，部分兼容构造参数被忽略，前端请求层也没有租户上下文。活动、赛事和报名表缺少 `tenant_id`，管理查询主要依赖功能权限和“本人/全部”判断。

未来多个主办方入驻后，功能权限无法单独证明某条资源属于当前主办方，也无法限制同一租户运营人员只访问被分配的赛事。

## 决策

- 入驻方定义为 Tenant；一个 Tenant 可以拥有多个 Activity 和 Competition。
- 新增 tenant、membership、tenant role 和 resource assignment 数据模型。
- `CurrentUser`、token/session 和权限快照必须包含可信的当前 tenant 上下文。
- 租户切换由服务端验证 membership 后重新签发上下文，不信任前端任意 tenant 参数。
- tenant-owned 聚合根和高频独立查询子表增加 `tenant_id`。
- 管理查询必须显式接收 `TenantScope` 或 `ResourceScope`；非平台态禁止隐式全表查询。
- 平台跨租户操作必须进入显式 platform/proxy 模式并审计。

## 影响

### 正面

- 从身份到数据层形成可验证的隔离链。
- 支持一个入驻方多赛事、一个运营人员被分配到指定赛事。
- 导出、缓存和异步任务可以绑定可靠 tenant/resource scope。

### 负面

- IAM、Token、权限快照、前端请求和大量业务表都需要渐进迁移。
- 租户切换后必须处理缓存清理、在途请求取消和权限刷新。
- 回填期间存在新旧 scope 并行，需要影子校验和双写。

### 中性

- 当前单租户数据统一回填到 legacy tenant。
- URL 中可以表达资源层级，但 tenant 仍以可信会话为准。

## 备选方案

### 仅使用 created_by 或 owner_user_id

无法表示组织共有资源、租户管理员和资源运营人员，拒绝。

### 仅使用 organizer 文本字段

展示文本不是稳定身份，没有成员关系和授权语义，拒绝。

### 每个租户独立数据库

当前运维成本过高；共享库共享表加逻辑隔离足以满足近期需求，重点租户未来仍可物理隔离。

## 参考

- [完整架构设计](../../docs/plans/2026-07-20-lumira-platform-domain-architecture-design.md)
- [权限与多租户规范](../05-permission-rbac.md)
