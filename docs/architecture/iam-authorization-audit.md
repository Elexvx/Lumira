# Lumira 权限体系全面审计与整改方案

审计日期：2026-08-30  
审计方式：只读源码、已提交生产 Compose 装配与定向单元测试；未改动任何业务代码、数据库迁移、配置或现有工作区文件。  
证据等级：**已确认**表示已从生产装配和调用链闭环；**高概率**表示源码链路成立但需隔离集成验证；**需验证**表示缺少运行时、数据库或产品模型证据；**已反驳**表示源码中有相关代码但不在默认生产路径中。

## 1. 执行摘要

Lumira 已具备不少成熟的基础能力：默认认证边界、JWT 与 Redis 会话绑定、用户启用状态检查、权限快照、数据范围解析、AI 的二次确认/审批、插件声明权限校验，以及异步任务的可信用户重建。它不是“完全没有权限体系”的项目。

但当前体系尚未形成唯一的授权语义与唯一的最终执行面。最严重的是：默认单体生产路径中，角色、角色权限、用户角色或数据范围变更只推进 IAM 快照版本，却不会同步撤销或刷新已存在的 AuthSession。常规请求的 AuthJwtAuthFilter 仍从旧会话快照创建 CurrentUser，导致已撤销权限在旧 access token 有效期内仍可用于部分受保护接口。默认窗口为 1,800 秒，且可被部署配置放大。这是已确认的 P0。

三个根因如下：

1. **会话授权快照与权威权限版本脱节。** 权限变更路径更新的是 PermissionSnapshot 版本，而生产认证快路径没有比较该权威版本。
2. **授权语义分散。** AuthorizationService、PermissionGuard fallback、业务私有 exact 检查、DataPermissionResolver、菜单树推导、前端 access.ts、AI/Plugin 包装层各自解释部分规则。
3. **权限元数据和管理员规则不是唯一事实源。** 菜单/路由/前端合成节点参与权限定义；管理员专属列表在至少三处后端和一处前端重复，且已出现内容差异。

建议采用**增量式系统性整改**，而不是替换现有框架或一次性重写。第一刀应先修复 AuthJwtAuthFilter 与 AuthSession 的撤权生效链路，并为角色、用户角色、DataScope 变更建立受影响会话的同步失效或 fail-closed 机制。此项完成并通过集成测试前，应冻结涉及角色、权限、会话、DataScope 与新受保护接口的功能扩展。

应保留并收敛的能力：AuthorizationService/AuthorizationRequest/AuthorizationDecision、PermissionGuard 作为 PEP 门面、PermissionSnapshot 的物化与缓存、DataPermissionResolver 的纯计算部分、AI 的风险决策、插件命名空间校验、异步可信用户快照重建，以及操作审计。

## 2. 审计范围与基线

### 2.1 基线

| 项目 | 值 |
| --- | --- |
| 分支 | main |
| 审计基准提交 | 02b64dbfea84a9b5ba2da16e0aa7b61862e8588d |
| 最近提交 | 02b64dbf — fix(system): map maintenance login roles as beans |
| 工作区状态 | 已有大量用户修改和未跟踪文件；均保持原样。本文是唯一新增文件。 |
| 生产默认假设 | 已提交 deploy/docker-compose.prod.yml 中的 lumira-server 模块化单体；LUMIRA_MONOLITH 默认 true。 |
| 审计范围 | lumira-admin、lumira-auth、lumira-system、lumira-common-security、AI、Plugin、Async、File、Competition、Activity、UI、SQL 基线与部署装配。 |
| 排除目录 | node_modules、target、dist、build、coverage、generated。 |

已执行的定向验证：

~~~text
./mvnw -pl services/lumira-system -am \
  -Dtest=JwtAuthFilterTest,SessionAuthenticationServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

./mvnw -pl services/lumira-auth -am \
  -Dtest=AuthAppServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
~~~

两组测试通过：前者共 49 项，后者共 84 项。它们只能证明当前单元行为，不替代实际生产装配、Redis、数据库和浏览器链路验证。

### 2.2 未验证边界

1. 未启动服务、未读取实际密钥/环境变量、Redis 内容、数据库行、生成后的 Nginx upstream 或在线会话。
2. 未逐条穷举全部约 2,577 个文件和所有 Controller 的资源级授权；报告以生产装配和代表性链路为依据。
3. 未确认产品究竟是每客户独立部署还是共享数据库多租户，因此不把“无 tenant_id”机械定性为现时漏洞。
4. 内部令牌查询参数重复的范围混淆已有源码证据，但需要 MockMvc/Servlet 集成测试确认参数绑定顺序后才可称为运行时复现。

## 3. 当前实际架构图

默认生产路径不是多个 Servlet Filter 同时叠加认证：系统 JwtAuthFilter 在存在 AccessTokenAuthenticationPort 时委托 auth 侧 AuthJwtAuthFilter；只有端口缺失时才走旧 SessionAuthenticationService。这个差别至关重要，因为两条路径对权限快照新鲜度的处理不同。

~~~mermaid
flowchart LR
  B[浏览器/API 客户端] --> UI[UI 会话与菜单 Bootstrap]
  UI --> SC[SecurityConfig 默认拒绝边界]
  SC --> JF[JwtAuthFilter]
  JF -->|生产单体优先| AF[AuthJwtAuthFilter]
  JF -.端口缺失回退.-> SF[SessionAuthenticationService]
  AF --> JWT[JWT Claims]
  AF --> AS[Redis AuthSession]
  AS --> CU[CurrentUser]
  PS[PermissionSnapshotService] -->|角色/权限/Scope 物化与版本| AS
  RM[角色/用户角色/DataScope 变更] --> PS
  AF -.未比较当前 PS 版本.-> CU
  CU --> PG[PermissionGuard]
  PG --> AZ[AuthorizationService / AuthorizationDecision]
  CU --> DP[DataPermissionResolver]
  DP --> RES[领域资源：用户、文件、报名、项目等]
  AZ --> AI[AI Tool Policy / 委托 / 风险确认]
  PG --> PL[Plugin Gateway / 声明权限]
  UI --> MT[后端菜单 Bootstrap]
  MT --> FT[frontend access.ts / 菜单过滤]
  FT -.仅 UX，不是安全边界.-> UI
  AJ[Async / Job] --> IT[InternalServiceTokenAuthFilter]
  IT --> TS[SessionTrustedUserSnapshotResolver]
  TS --> PS
  AZ --> AUD[Operation / Authorization Audit]

  classDef danger fill:#ffe3e3,stroke:#c92a2a,color:#6b0000;
  class AF,AS danger;
~~~

关键生产装配证据：

- 【LumiraAdminRuntimeAssemblyConfiguration.java:28-55】导入 Auth、System、IAM、AI、Plugin 等控制面模块；【deploy/docker-compose.prod.yml:395-474】构建该单体。
- 【SecurityConfig.java:56-95】对 permit path 外请求要求认证，且先放置 InternalServiceTokenAuthFilter、再放置 JwtAuthFilter。
- 【JwtAuthFilter.java:138-161】优先使用 AccessTokenAuthenticationPort；【AuthJwtAuthFilter.java:109-140,172-238】读取 AuthSession 并创建 CurrentUser。
- 【AuthAppService.java:635-663,832-847,1852-1915】只在 refresh-token、current-user、bootstrap 等流程懒刷新 session 快照。

## 4. 实际权限决策入口清单

### 4.1 入口与是否统一

| 入口 | 默认生产有效性 | 主要语义 | 是否与统一内核一致 | 安全边界判断 |
| --- | --- | --- | --- | --- |
| SecurityConfig + JwtAuthFilter | 已确认 | 非 permit 请求必须认证 | 认证边界统一 | 真正后端边界 |
| AuthJwtAuthFilter | 已确认 | JWT 与 AuthSession、用户状态、闲置过期、旧 permissionsVersion 比较 | 不调用当前 PermissionSnapshot 版本 | 真正后端边界；P0 所在 |
| SessionAuthenticationService | 条件回退/可信重解析路径 | 会话票据和当前快照版本校验、必要时刷新 | 语义优于 AuthJwt 快路径，但不是单体默认认证实现 | 纵深防护/回退 |
| PermissionGuard（注入 AuthorizationService） | 已确认 | 委托 AuthorizationService | 属于同一 PDP 门面 | PEP |
| DefaultAuthorizationService | 已确认 | exact、全局 *、尾随前缀 wildcard、AI/委托/风险 | 是当前最接近 PDP 的内核，但未覆盖全部业务 | PDP 候选 |
| PermissionGuard 无参 fallback | 源码存在，默认生产未发现构造 | 仅 exact 与全局 * | 与 DefaultAuthorizationService 不同 | 不应成为运行时边界 |
| 业务私有 require/hasPermission | 已确认 | 常见为 exact 与全局 * | 与 PDP wildcard 语义不同 | 各领域 PEP，未统一 |
| DataPermissionResolver | 已确认 | ALL、部门、下级部门、CUSTOM、SELF | 功能权限之外的独立数据策略计算 | 资源数据 PEP 的输入 |
| AI Tool Orchestration/Policy | 已确认 | 用户 RBAC + agent grant + delegation + 风险/确认/审批 + 执行前复核 | 是 PDP 的额外策略层，不是普通 Web 统一实现 | 真正 AI 边界 |
| Plugin Gateway/RuntimePolicy | 已确认 | 重建可信用户、声明权限属于插件命名空间、再走 Guard | 比普通 Web 更主动刷新 | 真正插件边界 |
| InternalServiceTokenAuthFilter | 已确认 | 内部路径的 scoped token → generic internal principal | 与人类 JWT 模型不同，符合主体不同 | 真正内部服务边界 |
| SessionTrustedUserSnapshotResolver | 已确认 | 异步工作按当前身份/角色/权限重新构建用户 | 与普通 Web 快路径不一致，但安全性更强 | 异步用户绑定任务边界 |
| SystemPermissionTreeAssembler | 已确认 | 菜单、按钮、suffix、前缀、legacy alias、settings 路径推导权限树 | 不等于 Permission Catalog | 管理配置/展示 |
| UI access.ts、authenticatedMenuTree.ts | 已确认 | 前端数组、OR 组合、exact/*、管理员特殊逻辑、菜单二次过滤 | 独立解释器 | UX，不是安全边界 |

结论：后端有全局认证边界，但没有覆盖所有业务资源的唯一授权 PEP；AuthorizationService 是可复用的核心，而非当前唯一决策点。

### 4.2 权限唯一事实源评估

| 信息 | 当前事实源 | 是否唯一 | 冲突风险 |
| --- | --- | ---: | ---: |
| Permission 定义 | sys_permission、插件声明/注册、Controller/Service 字符串、sys_menu.permission_key、前端常量 | 否 | 高 |
| Permission 名称 | sys_permission.permission_name、菜单名、前端标签映射 | 否 | 中 |
| Permission 分组 | sys_permission.permission_group、菜单树/前端分组逻辑 | 否 | 中 |
| Permission 风险等级 | AI tool/policy 元数据与调用侧风险参数；普通 Web 无统一字段 | 否 | 高 |
| 管理员专属权限 | RoleService、PermissionSnapshotService、InternalSystemController、adminAccess.ts 的常量 | 否 | 高 |
| 菜单显示条件 | sys_menu、插件 bootstrap、authenticatedMenuTree、access.ts | 否 | 高 |
| 页面访问条件 | 路由、access.ts、菜单 path、后端 API permission | 否 | 高 |
| API 操作权限 | PermissionGuard/AuthorizationService、Controller/Service 私有检查、字符串常量 | 否 | 高 |
| 数据范围 | sys_role_data_scope、DataPermissionResolver、领域 owner/member 条件、导出/查询代码 | 否 | 高 |
| 超级管理员身份 | 后端受保护 userId=1001/UUID、前端 userId/username/role code/role name | 否 | 高 |
| 插件权限 | sys_permission、Plugin Definition、PluginRuntimeSecurityPolicy、Gateway | 否 | 中 |
| AI 工具权限 | 工具声明、agent grant、delegation、AuthorizationService、AiToolPolicy | 否 | 中 |
| 权限版本 | IAM read-model/cache、PermissionSnapshot、AuthSession、JWT claims | 否 | 高 |

**明确结论：当前没有真正的 Permission Catalog，也没有唯一 Authorization Decision Point。**  
sys_permission 是最接近目录的持久化表，但它不拥有完整的风险、scope、assignable、protected 和页面/API 定义；AuthorizationService 是最接近 PDP 的服务，但业务私有检查、认证快路径、DataScope、AI/Plugin 包装器和前端仍会自行解释规则。

### 4.3 安全属性裁决

| 属性 | 结论 | 证据与边界 |
| --- | --- | --- |
| 默认拒绝 | **部分已确认** | 【SecurityConfig.java:63-86】对非 permit 请求认证；【DefaultAuthorizationService.java:45-75】缺请求、主体、权限均拒绝。DataScope 无规则则为 SELF 而非拒绝，且领域是否接入 DataPermissionResolver 需逐资源证明。 |
| 依赖异常 | **认证路径 fail-closed** | 【AuthJwtAuthFilter.java:119-123,195-200】Session store 或用户信任服务异常抛出 dependency unavailable，不能继续构建主体。PermissionSnapshot 的回退/缓存异常不应被误读为全局放行，尚需故障注入验证。 |
| 显式 DENY | **AI 强、普通 Web 弱** | AuthorizationDecision 支持 DENY/REQUIRE_CONFIRM/REQUIRE_APPROVAL；AI 使用风险/委托判定。普通业务 Guard 多为布尔权限检查，不存在全局 deny-overrides 规则。 |
| 最小权限 | **有控制但存在漂移** | 角色管理拒绝全局 * 和管理员专属项；但保护清单多处复制且存在差异，不能作为长期可靠事实源。 |
| 权限回收 | **P0 不满足** | 用户禁用会 revokeUserSessions；角色、角色权限、用户角色、DataScope 变更不保证常规 Web 下一请求失效。 |
| 资源级授权 | **抽样正向、全量未证** | Competition、File、Activity、System User 使用 owner/dept/scope 的证据存在；未发现全仓通用拦截器，不能声称每个详情、导出和批量接口都已覆盖。 |
| 高风险操作 | **不一致** | AI 对 HIGH/CRITICAL 有确认/审批和执行前复核；配置回滚、角色分配、安全配置等普通 Web 路径目前看到的是权限检查，未证实存在统一 MFA/审批策略。 |

## 5. 已确认问题清单

| ID | 问题 | 严重级别 | 状态 | 证据 | 实际影响 | 根因 |
| -- | -- | ---: | -- | -- | ---- | -- |
| IAM-01 | 权限、角色、用户角色或 DataScope 撤回后，旧 AuthSession 仍被生产认证快路径接受 | P0 | 已确认 | AuthJwtAuthFilter:109-140,172-238；RoleService:308-375；UserService:417-431；PermissionSnapshotService:459-494 | 旧令牌在有效期内可继续调用未重新解析的敏感接口，例如配置回滚；默认约 30 分钟 | 会话版本只与令牌自比，未与权威 IAM 版本比较 |
| IAM-02 | scoped internal token 的选择和 Controller 参数绑定可能被重复查询参数混淆 | P1 | 高概率，需集成验证 | InternalServiceTokenAuthFilter:87-121；InternalServiceTokenPolicy:210-253；InternalSystemController:506-525；ReadModelVersionService:108-147 | 持有较低范围内部令牌的调用方可能操作不属于该 token 的 read-model 版本，造成越范围缓存失效/可用性影响 | 以原始 query 做策略匹配，后续以标量参数做业务绑定，且只验证 generic internal principal |
| IAM-03 | 同一 wildcard 授权在 PDP 与业务私有检查结果不同 | P1 | 已确认 | DefaultAuthorizationService:120-132；CompetitionRegistrationAppService:2417-2431,2474-2477；SystemRoleManagementAppService:442-451,731-761 | 同一权限集合可在不同模块被允许或拒绝，产生菜单/API/领域行为不一致 | 多个授权解释器、没有统一匹配函数 |
| IAM-04 | 权限树由菜单、路径、后缀、前缀和前端 synthetic/inferred 节点共同定义 | P1 | 已确认 | SystemPermissionTreeAssembler:20-70,72-129,153-165；rolesPermissionTree/normalize.tsx | 新权限/新页面可能漏配、误展示或角色树与 API 失配 | sys_permission 不是唯一 Catalog |
| IAM-05 | 管理员专属权限清单和管理员识别规则分散 | P1 | 已确认 | RoleService:53-76；PermissionSnapshotService:57-81；InternalSystemController:101-125；adminAccess.ts:3-53 | 保存、读取和前端展示可能不同；前端 username=admin 可能误判 UI 权限 | 硬编码复制；例如 RoleService 列表缺少 payment:，Snapshot/Internal 列表包含 |
| IAM-06 | DataScope 的默认与无规则行为会静默落为 SELF，功能 * 又隐式给 ALL 数据 | P1 | 已确认 | DataPermissionResolver:13-70；DataPermissionRule:11-25；DataScopeType:12-20；RoleService:826-860 | 错误配置可能变成“只能看自己”而非可观测拒绝；功能和数据超级权限耦合 | Role → DataScope 直接绑定、缺少 scope_required 与策略元数据 |
| IAM-07 | PermissionSnapshotService 和 CurrentUser 负载过多且全局版本 bump 影响面大 | P2 | 已确认 | PermissionSnapshotService:42-95,150-207,459-575,578-643；CurrentUser:8-25 | 变更容易牵动身份、缓存、数据范围和管理员策略；全员缓存扰动 | IAM 投影、缓存、策略、管理员特例和展示状态耦合 |
| IAM-08 | 核心 IAM 仅表达 User → Role → RoleDataScope，缺少正式 scoped RoleBinding | P1 | 部分已确认 | saas.sql:1699-1731,1853-1867；Team/Project/Competition 有各自成员关系 | 同一人跨项目/部门拥有不同角色依赖领域私有模型，易出现角色爆炸 | 无统一 Subject–Role–Scope 绑定模型 |

未列为已确认漏洞的项目：匿名 JWT 伪造、插件网关绕过、AI 原生工具绕过、抽样的文件/报名跨资源 IDOR 均未发现可证实链路；这不代表全量无问题，只代表本次证据不足以报告为已发生漏洞。

## 6. 对核心怀疑逐项裁决

### A. 存在多套权限决策引擎

**结论：已确认。证据等级：已确认。**  
AuthorizationService/注入式 PermissionGuard 是一组内核入口；业务私有检查、DataPermissionResolver、AI/Plugin 附加策略、会话认证快路径、菜单树和前端解释器是不同层次的实现。UI/menu 是展示层，不是后端安全边界；AI/Plugin 是有意的附加策略，但普通业务私有检查与 PDP 确实可能产生不同结论。  
**影响：** 新模块需要自行选择入口，难以证明所有请求遵守相同语义。  
**整改：** 将 AuthorizationService 定位为唯一 PDP；业务、AI、Plugin、System Job 通过统一 AuthorizationRequest 表达差异，保留专用策略作为 PDP provider。

### B. 权限匹配语义不一致

**结论：已确认。证据等级：已确认。**  
DefaultAuthorizationService 支持尾随 * 前缀匹配；CompetitionRegistrationAppService 和 SystemUserManagementAppService 的私有检查只接受 exact 或全局 *。Role 管理的 validatePermissionKeys 仅要求非空，且 assignable 过滤不禁止普通前缀 wildcard，因此例如 aiadc:competition:* 可以进入角色并被不同入口不同解释。  
**影响：** 当前主要表现为 P1 一致性/漏配风险；在同一功能有不同 PEP 时可能演化为实际错误放行或错误拒绝。  
**整改：** 把匹配算法收敛为 Catalog 定义的 permission matcher；禁止业务模块直接读 permissions 集合。

### C. PermissionGuard 仍有 fallback

**结论：部分成立；“默认生产会走 fallback”已反驳。证据等级：已确认。**  
无参构造器会退化为 exact/* 检查，但默认单体 CommonRuntimeAssembly 注入 AuthorizationService，且未发现生产代码 new PermissionGuard()。  
**影响：** 当前不是已证实生产绕过，仍可能在测试、未来错误装配或新模块中引入语义漂移。  
**整改：** 移除 public 无参构造器或仅限测试可见；启动期断言授权服务存在。

### D. Role 与 Scope 没有正确解耦

**结论：部分成立。证据等级：已确认（核心 IAM），需验证（各领域完整关系）。**  
核心表是 sys_user_role 与 sys_role_data_scope，表达的是用户全局角色、角色默认数据范围；项目、团队、赛事存在各自成员关系，但不是统一 RoleBinding。  
**影响：** “A 项目管理员、B 项目查看者、C 部门审批人”不能由 IAM 一处表达，促成私有授权模型和角色爆炸。  
**整改：** 引入 scoped RoleBinding，保留现有项目/团队成员关系作为 ResourceRelation 适配器。

### E. 菜单、路由和权限目录耦合

**结论：已确认。证据等级：已确认。**  
SystemPermissionTreeAssembler 使用菜单、BUTTON、suffix、action prefix、legacy alias 和 settings 路径；前端 normalize 又过滤、推导和注入 synthetic 节点。  
**影响：** sys_permission 不是唯一事实源；改路由或页面可能改变角色配置呈现。  
**整改：** Catalog 定义 resource/action/metadata；菜单只引用 permission id，不再创造或补全权限。

### F. 前端存在独立权限解释器

**结论：已确认，但不是服务端提权证据。证据等级：已确认。**  
access.ts、authenticatedMenuTree.ts、adminAccess.ts 保存权限数组、OR 组合、管理员逻辑和菜单过滤。后台 API 仍有认证/授权，不应把前端当安全边界。  
**影响：** UX 与 API 易不一致，前端升级需要同步理解后端规则。  
**整改：** 后端返回 capabilities；前端保留极薄的 can(capability) 和资源 capabilities 消费逻辑。

### G. 超级管理员硬编码分散

**结论：已确认规则分散；未确认后端因 username=admin 提权。证据等级：已确认。**  
后端以受保护 userId=1001 及关联 UUID 为主要锚点；前端还以 username=admin、roleCode/roleName 推断超级管理员。  
**影响：** 普通账号名为 admin 可能得到错误 UI 行为，但后台敏感接口仍由后端拦截。  
**整改：** 使用受保护 Principal/Role metadata 与服务端 capabilities；前端不再自行识别管理员。

### H. 管理员专属权限列表重复

**结论：已确认。证据等级：已确认。**  
Role 管理、Snapshot、InternalSystemController、UI 均保有规则；Role 管理列表遗漏 payment:，另两处包含。  
**影响：** 保存时、投影时、内部注册时和前端显示时可不一致。  
**整改：** 将 assignable、system_protected、risk_level 等迁入 Permission Metadata，删除复制清单。

### I. DataScope 默认行为可能隐藏配置错误

**结论：已确认。证据等级：已确认。**  
无匹配规则、空/非法 scope 或无法解析的主体数据会落到 SELF；全局功能权限 * 直接得到 DataPermissionDecision.ALL；多个角色规则为并集；CUSTOM 使用 CSV ID。  
**影响：** 这是相对安全的默认收敛而非 fail-open，但会隐藏漏配；且并非所有业务都自动强制数据范围。  
**整改：** Catalog 声明 scope_required；缺 Scope 时拒绝并审计；将 CUSTOM 迁为关系表；列表、详情、导出共用 DataPolicy。

### J. PermissionSnapshotService 职责过重

**结论：已确认。证据等级：已确认。**  
该服务同时负责角色/权限/部门/下级部门/DataScope/主页、保护管理员、缓存、全局版本、失效、模拟角色和指标。  
**影响：** P0 以外还增加维护风险和全局缓存扰动。  
**整改：** 拆分 IdentityProjection、AuthorizationSnapshot、DataScopeProjection、Version/Invalidation、ProtectedPrincipalPolicy。

### K. CurrentUser 过度承载权限状态

**结论：已确认。证据等级：已确认。**  
CurrentUser 同时含身份、会话、令牌版本、密码状态、权限、角色、部门、DataScope、模拟角色、主页。  
**影响：** 领域模块自然倾向直接解释其字段，推动私有引擎。  
**整改：** 分离 IdentityContext、AuthorizationContext、PresentationContext；业务只能消费授权决策或受限 capability。

### L. AuthJwt 与旧 Jwt 双认证模型

**结论：部分成立。证据等级：已确认。**  
不是“生产一次请求叠加两套 Filter”；默认单体由 JwtAuthFilter 委托 AuthJwtAuthFilter。问题是 auth 侧与 system 侧会话模型同时存在，前者不做当前快照版本校验，后者会校验/刷新，造成语义分叉。  
**影响：** 同一 Session 逻辑随运行模式或调用入口变化。  
**整改：** 指定单一 AuthenticationSessionVerifier；旧 system 实现仅作为 adapter，删除重复实体/缓存语义。

### M. 缺少真正多租户权限边界

**结论：核心 IAM 缺少 tenant 语义已确认；是否构成当前漏洞需验证。证据等级：需验证。**  
CurrentUser、sys_user_role、sys_role_permission、sys_role_data_scope、sys_permission、sys_menu 没有 tenant 字段；定向搜索未建立通用 TenantContext。  
**影响：** 若共享数据库多租户部署，当前模型不足以证明隔离；若每客户独立部署，问题是企业 SaaS 演进缺口而非立即漏洞。  
**整改：** 先由产品/部署做边界决策，再决定是否把 TENANT 作为强制 Scope；不要仅凭 SaaS 名称迁表。

### N. 近期提交显示权限体系长期不稳定

**结论：部分成立。证据等级：高概率。**  
历史中确有围绕 token/session、permissionsVersion、role switch、menu、data scope、protected admin、AI 审计和 plugin menu 的连续修复主题。提交数量本身不证明质量差，但与当前多事实源、会话快照分叉相互印证。  
**影响：** 若不先收敛语义，修复仍会散落在各模块。  
**整改：** 将下一轮变更按本报告阶段计划纳入同一 ADR、Catalog 和契约测试；不要继续以局部字符串判断修补。

## 7. 权限一致性风险场景

| 场景 | 前置条件 | 调用路径 | 预期结果 | 当前结果 | 安全影响 |
| --- | --- | --- | --- | --- | --- |
| 1. 撤销后继续回滚配置 | 用户已有 system:config:update、有效旧 token；管理员移除该权限 | AuthJwtAuthFilter → SecurityContext → SystemConfigVersionController → PermissionGuard | 下一次请求拒绝 | 旧 AuthSession 仍带旧权限，可在 token 生命周期内继续通过 | P0：权限回收不生效 |
| 2. DataScope 收窄后继续访问 | 旧 session 内含 ALL/部门范围；管理员收窄角色 DataScope | 同上，再由领域服务读取 CurrentUser.dataScopes | 下一请求按新范围过滤 | 常规 AuthJwt 路径仍提供旧 DataScope | P0：范围回收延迟 |
| 3. 前缀 wildcard 语义分叉 | 角色被授予 aiadc:competition:* | DefaultAuthorizationService 与 Competition 私有 exact 检查 | 两处一致允许或一致拒绝 | PDP 允许前缀，私有检查拒绝 | P1：同一权限集在不同入口结论不同 |
| 4. 目录/菜单/前端补全不一致 | 新增 sys_permission 或新路由/按钮但未同步各推导点 | PermissionTreeAssembler → normalize.tsx → access.ts → 菜单 | Catalog 一处注册即可稳定展示 | 菜单、前端 synthetic/inferred、后端 API 可相互失配 | P1：漏配、误展示、角色树误导 |
| 5. 用户名 admin 的前端误判 | 普通用户 username 为 admin 或数据异常导致该值出现 | adminAccess.ts → 设置页/菜单展示 → 后端 API | 仅服务端保护身份决定管理员 UX | UI 将其视为 protected admin；后端仍拒绝敏感 API | P1：行为不一致，非已证实后端提权 |
| 6. 内部 token 重复参数 | 攻击者持有 plugin scoped token；请求带重复 contextName/scope | Filter 原始 query token policy → generic internal principal → Controller 标量参数 | token 的 scope 与实际 context/scope 必须相同 | 源码显示可能先选 plugin token、后绑定 IAM 等前值 | P1 高概率：跨范围版本 bump/缓存扰动，需 MockMvc 验证 |
| 7. 队列导出中的正向对照 | 导出已排队，后续撤权/禁用用户 | Worker → SessionTrustedUserSnapshotResolver → PermissionSnapshot | 执行时重新校验 | 该路径会重建当前快照并拒绝失效用户 | 可保留的正确模式 |

## 8. 目标架构

### 8.1 核心模型与 PDP/PEP

目标不是引入 Casbin、OPA、OpenFGA 或 SpiceDB，而是把现有内核收敛为可解释的企业授权平台：

~~~text
Principal
  └─ RoleBinding（谁、何时、在哪个 Scope 具有什么角色）
       └─ Role
            └─ Permission（resource + action + metadata）

AuthorizationRequest
  = principal + effective bindings + resource reference + action
    + scope + relation + data policy + risk/approval/delegation context

AuthorizationService（唯一 PDP）
  → AuthorizationDecision（ALLOW / DENY / REQUIRE_CONFIRM / REQUIRE_APPROVAL）
  → reasons、matchedBindings、matchedPolicy、version、audit id

PEP
  = HTTP/Controller Guard、领域资源服务、Plugin Gateway、AI Orchestrator、Async Worker
~~~

| 组件 | 目标职责 |
| --- | --- |
| Principal | USER、GROUP、SERVICE_ACCOUNT、AGENT、INTERNAL_SERVICE；不以 username/role name 判断管理员。 |
| Permission Catalog | 唯一注册 resource、action、名称、组、assignable、system_protected、risk_level、scope_required、source_type、plugin_code。 |
| Role | 表达“能做什么”，不直接绑定用户范围。 |
| RoleBinding | 表达“谁在 GLOBAL/TENANT/ORG/DEPARTMENT/PROJECT/RESOURCE 范围内拥有该 Role”，支持有效期、来源与撤销。 |
| DataPolicy | 表达可见记录/字段；列表、详情、写入、导出、异步使用同一 policy。 |
| ResourceRelation | owner、member、editor、reviewer 等领域关系；Team/Project/Competition 通过 adapter 提供。 |
| AuthorizationService | 唯一 PDP，负责匹配、deny 优先级、scope、relation、risk、委托以及理由。 |
| PermissionGuard | 唯一轻量 PEP 门面，只构建 request 并要求 decision；无 fallback。 |
| PermissionSnapshot/AuthSession | 缓存与会话投影，不是独立真相。每个受保护请求以 O(1) 当前版本或失效标记保证新鲜度。 |
| Menu/Frontend | 消费服务端 capability 与资源 capabilities，不再推导/创造 permission。 |
| Audit | 每次高风险/拒绝/委托决策记录主体、Binding、Scope、Policy、版本、原因和关联请求。 |

### 8.2 会话撤权目标流程

1. 角色/用户角色/DataScope/插件禁用/模拟角色变更在同一事务中写入版本事件。
2. Version/Invalidation 服务按用户、角色、绑定建立反向索引，原子标记受影响 AuthSession 失效；投递失败时请求认证 fail-closed，而不是继续信任旧 session。
3. AuthJwtAuthFilter 在构建 CurrentUser 前验证 session.authzVersion 与权威 subject/binding version；失配后同步 rehydrate 或返回需要刷新/重新登录。
4. AI、Plugin、Async 不自建另一套版本语义，而通过同一个 TrustedAuthorizationSnapshotResolver。

### 8.3 Capability 与资源级授权

服务端 bootstrap 返回全局 capabilities；资源详情/列表返回 resource capabilities，例如 canUpdate、canExport、canViewSensitive。前端只渲染，不把隐藏按钮当授权。领域服务在每个读/写/下载/导出入口调用相同的资源 PEP：

~~~text
require(currentUser, action, ResourceRef, requestedFields)
  -> function permission
  -> scoped role binding
  -> relation / ownership
  -> data policy / field policy
  -> risk/approval
  -> decision + audit explanation
~~~

## 9. 数据库迁移方案

### 9.1 建议模型

以下为兼容性目标表，不建议一次删除 sys_* 表：

| 表 | 关键字段/用途 |
| --- | --- |
| iam_principal | id、principal_type、subject_id、status；建立 USER/GROUP/SERVICE_ACCOUNT/AGENT 的统一主体。 |
| iam_role | 可复用 sys_role id 或建立映射；role_code、role_name、status。 |
| iam_permission | permission_key、resource、action、display_name、group、assignable、system_protected、risk_level、scope_required、source_type、plugin_code、status。 |
| iam_role_permission | role_id、permission_id、effect；初期仅 ALLOW，预留 DENY/约束。 |
| iam_role_binding | id、subject_type、subject_id、role_id、scope_type、scope_id、valid_from、valid_until、grant_source、status、created_by、revoked_by、revoked_at。 |
| iam_data_policy | id、permission_id/role_binding_id、effect、scope kind、field policy、priority、status。 |
| iam_policy_target | policy_id、target_type、target_id；替代 CUSTOM CSV。 |
| iam_resource_relation | resource_type、resource_id、subject_type、subject_id、relation、valid_until。 |
| iam_authorization_audit | request_id、principal、resource、action、decision、reason、binding ids、policy ids、snapshot/version、risk/approval、created_at。 |

### 9.2 旧表兼容映射

| 旧表 | 过渡含义 | 新模型映射 |
| --- | --- | --- |
| sys_permission | 现有权限键、名称、组、source_type、plugin_code | iam_permission 的初始 Catalog；补齐 metadata 后作为唯一发布源 |
| sys_role_permission | Role 的 allow 权限键 | iam_role_permission |
| sys_user_role | 用户全局角色 | iam_role_binding，scope_type=GLOBAL、scope_id=null |
| sys_role_data_scope | 角色默认数据范围 | iam_data_policy + iam_policy_target；先保留 Compatibility Adapter |
| sys_menu | 页面入口及引用的 permission_key | 只读 Catalog 引用；不得反向创建 Permission |

### 9.3 分步迁移与回滚

1. **扩表与回填：** 新增表、索引和审计列；从 sys_* 回填 GLOBAL binding 与现有 role permission。仅新增，不删旧数据。
2. **Catalog 影子校验：** 新 Catalog 与旧 sys_permission 双读，比对 permission key、protected/assignable 元数据和菜单引用；差异阻断发布。
3. **双写：** Role、RoleBinding、DataPolicy 写入同时写 legacy adapter；使用 outbox 保证失效事件和审计。
4. **影子决策：** AuthorizationService 同时计算旧/新结果，只记录差异；不得影响请求。
5. **按领域切换：** 先选 Project 或 Competition 试点，feature flag 决定新 PEP；成功后推广。
6. **回滚：** feature flag 回到 legacy adapter；新表与审计保留，不执行破坏性回滚。双写失败应拒绝敏感授权变更而非静默偏离。

建议索引：

- iam_permission：唯一 permission_key；按 source_type、plugin_code、status 索引。
- iam_role_binding：subject_type/subject_id/status、role_id/status、scope_type/scope_id/status、valid_until 索引；按产品决定是否加入 tenant_id。
- iam_policy_target：policy_id/target_type/target_id 唯一索引。
- iam_resource_relation：resource_type/resource_id/relation、subject_type/subject_id/relation 索引。
- iam_authorization_audit：request_id、principal、resource/action、created_at 分区/归档策略。

### 9.4 租户隔离决策门

若确认共享数据库多租户：iam_role_binding、iam_data_policy、iam_policy_target、iam_resource_relation、业务资源和审计必须带 tenant_id，复合唯一键和所有 repository predicate 必须含 tenant_id；GLOBAL Catalog 可以是平台全局但需明确 owner。若确认每客户独立部署：保留 TENANT scope 类型的模型预留，但不要无证据地给现有所有表强行加 tenant_id。

## 10. 分阶段整改方案

| 阶段 | 目标与文件范围 | 数据变化/兼容策略 | 测试与完成标准 | 风险、回滚、工作量 |
| --- | --- | --- | --- | --- |
| 0：P0 热修与扩散冻结 | AuthJwtAuthFilter、AuthSessionStore、PermissionSnapshotService、Role/User 管理、SecurityConfig；禁止新增私有权限检查 | 先不迁表；增加 session authzVersion 或失效标记、受影响 session 反向索引/事件 | 单体集成：撤权、角色删除、DataScope 修改后**下一请求**拒绝；Redis/版本依赖失败 fail-closed | 风险是会话批量失效和性能；feature flag 可回退旧验证但仅用于紧急止血。完成前冻结相关功能。**中** |
| 1：统一权限执行面 | PermissionGuard、DefaultAuthorizationService、私有 require/hasPermission、SessionTrustedUserSnapshotResolver | 保留 Compatibility Adapter；删除/封闭无参 Guard | 权限语义契约：exact、*、prefix、空、角色合并、可信主体；所有新领域只走 Guard | 迁移遗漏导致错误拒绝；按领域灰度，adapter 回退。**大** |
| 2：正式 Permission Catalog | sys_permission 管理、SystemPermissionTreeAssembler、Plugin 权限注册、菜单管理 | 扩展/映射 iam_permission；sys_permission 暂作 adapter | Catalog 完整性：每 API action/菜单引用/插件声明均可解析；禁止路由推导新 permission | 历史数据缺 metadata；先影子比对、再阻断新建。**中** |
| 3：Scoped RoleBinding 试点 | IAM、Project/Competition/Team 任选一域 | 回填 sys_user_role 为 GLOBAL binding；领域成员关系通过 relation adapter | 同人多项目不同角色、有效期、撤销、模拟角色、资源范围测试 | 模型与现有成员表冲突；只试点并保留旧路径。**大** |
| 4：DataScope 重构 | DataPermissionResolver、导出、File、Competition、System User | CSV CUSTOM 拆入 iam_policy_target；scope_required metadata；功能 * 与数据 ALL 解耦 | 列表/详情/编辑/删除/导出/批量/异步一致；缺 Scope 拒绝并审计 | 查询性能与历史范围差异；双算比对、按资源切换。**特大** |
| 5：收拢前端权限 | access.ts、adminAccess.ts、authenticatedMenuTree.ts、rolesPermissionTree/normalize.tsx | 无需破坏性数据迁移；bootstrap 提供 capabilities | 菜单、路由、按钮与 API 组合测试；username=admin、roleName=超级管理员 不改变安全身份 | UX 短期变化；双渲染监测、旧 UI adapter 回退。**中** |
| 6：解释与审计 | AuthorizationDecision、OperationAudit、AI/Plugin/Job PEP | 新增 iam_authorization_audit，敏感操作强制写入 | 能回答允许/拒绝原因、命中 Binding/Scope/Relation/DataPolicy、批准/确认状态 | 审计量与隐私；异步落库/脱敏/采样。**中** |
| 7：企业增强 | Group、Delegation、SoD、MFA step-up、DENY、Service Account、SCIM、访问复核 | 以 Principal/Binding/Catalog 为前提逐项增加 | 过期授权、冲突 deny、MFA、SCIM 回收、定期复核 | 不应在 P0/统一语义未完成前启动。**特大** |

## 11. 文件级改造清单

| 模块或文件 | 当前职责 | 问题 | 建议调整 | 优先级 |
| --- | --- | --- | --- | --- |
| AuthJwtAuthFilter | 生产 AuthSession → CurrentUser | 不校验当前权限版本 | 接入唯一 TrustedAuthorizationSnapshotVerifier；失配 rehydrate 或拒绝 | P0 |
| JwtAuthFilter | 选择 auth port 或旧 system 路径 | 两条路径语义不同 | 只保留一个 verifier，旧实现做 adapter | P1 |
| SessionAuthenticationService | 旧会话票据和可信刷新 | 更安全但非默认快路径 | 复用其版本校验能力，禁止独立模型继续扩张 | P1 |
| PermissionSnapshotService | 快照、DataScope、缓存、版本、管理员策略 | IAM God Service；全局 bump | 拆 projection/version/invalidation/protected policy；提供反向失效索引 | P0/P2 |
| PermissionGuard | 业务 PEP 门面 | 无参 fallback 语义不同 | 删除/限制无参构造，强制委托 PDP | P1 |
| DefaultAuthorizationService | PDP 候选 | 未覆盖所有业务，scope 仅消费调用方参数 | 定义统一 matcher、resource/scope/relation policy provider | P1 |
| SystemRoleManagementAppService | 角色、权限、DataScope 写入 | 只 invalidate，不撤 session；本地 protected list | 写 Binding/Policy 并同步发布精确失效事件 | P0/P1 |
| SystemUserManagementAppService | 用户/角色写入 | role 变更不撤 session | 用户角色变更同步失效；绑定化 | P0 |
| DataPermissionResolver | 纯数据范围决策 | 默认 SELF、*→ALL、CSV/领域显式接入 | 保持纯函数；转为 DataPolicy evaluator，支持 scope_required | P1 |
| InternalServiceTokenAuthFilter | 内部 token 鉴别 | 原始 query 用于 scoped token 选择 | 解析规范化参数、拒绝重复、写入 verified caller context | P1 |
| InternalServiceTokenPolicy | 内部路径/范围表 | query 成对匹配与 Controller 脱节 | 使用 typed capability/path registry，避免字符串扫描 | P1 |
| InternalSystemController | 通用内部 service principal | read-model version 接受调用者自报 scope | 要求 verified scope binding 或拆固定端点 | P1 |
| SystemPermissionTreeAssembler | 角色授权树 | 从菜单/路由/alias 推导权限 | 只消费 Catalog 与菜单引用，移除 inference | P1 |
| LumiraAuthPostLoginBootstrapProvider | 登录后插件/菜单 bootstrap | capability 投影正确但需 Catalog 驱动 | 返回版本化 capabilities，避免 UI 再解释 | P1 |
| PluginGatewayController/RuntimePolicy | 插件执行 PEP | 比普通 API 更新鲜，语义重复 | 迁入统一 request/decision，保留命名空间 policy provider | P1 |
| AiToolOrchestrationService/AiNativeToolRuntimeService | AI 计划、确认、执行 | 正确的多次复核不应成为独立 RBAC 方言 | 使用统一 PDP 输出并写解释审计 | P1 |
| SessionTrustedUserSnapshotResolver | 异步任务可信用户重建 | 这是正向实现但与 Web 分叉 | 成为所有主体的统一 snapshot resolver | P1 |
| access.ts | UI 条件判断 | 数组、OR、权限语义自实现 | 只消费 capability，不实现 matcher | P1 |
| adminAccess.ts | 前端管理员判断 | userId/username/role code/name 硬编码 | 删除安全身份推断；服务端返回 protected capability | P1 |
| rolesPermissionTree/normalize.tsx | 角色树补全 | synthetic/inferred permission | 删除补全，后端 Catalog 返回完整树 | P1 |
| authenticatedMenuTree.ts | 前端菜单二次过滤 | 与后端菜单能力重复 | 只处理布局；以服务端 visible/capability 为准 | P2 |

## 12. 测试与验收标准

### 12.1 测试矩阵

| 类别 | 必测用例 |
| --- | --- |
| 功能权限 | 无权限、exact、全局 *、prefix wildcard、多个角色合并、角色删除、权限撤回、模拟角色；所有 PEP 结果完全一致。 |
| Scope/Binding | GLOBAL、TENANT（启用后）、DEPARTMENT、DEPARTMENT_AND_CHILDREN、PROJECT、RESOURCE、SELF、CUSTOM、缺 Scope、过期 binding、资源不属于 scope。 |
| 数据权限 | 列表、详情、编辑、删除、批量、导出、敏感字段、文件下载、支付/审核、异步任务必须使用同一 DataPolicy。 |
| 管理员 | 真实受保护管理员、普通角色名“超级管理员”、普通 username=admin、非 1001 管理员、1001 模拟普通角色、管理员角色撤销。 |
| 缓存与版本 | 角色权限、用户角色、DataScope、用户禁用、插件禁用、模拟角色、多实例、本地/Redis cache、旧 session、旧 token、并发撤权、Redis/权限库不可用。 |
| 前后端一致性 | 菜单可见/API 禁止、菜单不可见/API 允许、按钮可见/操作禁止、直达路由、动态插件菜单、实时刷新、角色切换。 |
| AI Agent | 用户有权限/Agent 无授权，反向场景，只读执行写操作，HIGH 未确认，CRITICAL 未审批，委托过期，SELF Scope 访问他人数据。 |
| Internal token | 每个 scoped token 的允许/拒绝 matrix；路径编码、重复参数、参数顺序、空值、多个 context/scope；Controller 只接受经验证 scope。 |

### 12.2 必须新增的验收用例

1. 以完整 LumiraAdminRuntimeAssemblyConfiguration 启动，用户持有旧 token。
2. 删除其角色权限、移除其用户角色、修改 DataScope 三次分别验证。
3. 对 SystemConfigVersionController、Alerting 写接口、至少一个 File/Competition/Export 接口发送下一请求。
4. 断言均返回拒绝或要求重新登录，且不会执行业务副作用；不能以“调用 bootstrap 后才失效”作为通过条件。
5. 用 MockMvc 构造重复 query 参数的 scoped token 请求，覆盖所有参数顺序；若可跨范围，则将 IAM-02 升为运行时确认并先修复。
6. 新旧 AuthorizationService 影子决策连续发布周期内差异为零，或每一个差异都有白名单、到期时间和责任人。

### 12.3 发布门槛

- IAM-01 修复后，撤权下一请求生效的集成测试必须是 release gate。
- 所有新 permission 必须在 Catalog 存在，菜单/API/插件引用均通过完整性测试。
- 所有新高风险普通 Web 操作必须声明 risk_level 与是否需要 confirmation/MFA/approval。
- 不以单元测试、curl、菜单隐藏或前端按钮禁用替代资源级/生产装配证明。

## 13. CI 防回退规则

规则应先以报告模式建立基线，再逐域切为失败门槛，避免一次性把现有遗留全部阻塞。

| 规则 | 实现方式 | 允许例外 |
| --- | --- | --- |
| 业务模块不得直接读取 CurrentUser.permissions 或 contains(*) | ArchUnit：除 common-security、iam、authorization、compatibility adapter 外禁止；补充 rg contract test | 带注释的短期 adapter 白名单，必须有到期日期 |
| 业务模块不得直接查询 sys_role_permission/sys_user_role/sys_role_data_scope | ArchUnit/JDBC SQL 字符串扫描/Maven 测试 | IAM repository 与明确 adapter |
| 不得 new PermissionGuard() | ArchUnit 禁止构造器调用；编译期收紧构造器可见性 | 测试 fixture |
| 不得自行比较 roleCode/roleName/userId=1001 | 后端 ArchUnit；前端 ESLint 自定义 rule | ProtectedPrincipalPolicy 和测试 |
| 前端不得自行 wildcard、推导 action、以 admin 名称鉴权 | ESLint AST rule，限制 access/admin/route helper 外的权限字段访问 | 过渡期 capability adapter |
| Catalog 完整性 | Maven/SQL 基线测试：每 permission_key 唯一、菜单/API/插件声明均指向 Catalog；protected/assignable 规则无重复来源 | migration fixture |
| 授权语义 | 参数化 contract test：所有 PEP 对 exact/*/prefix/空/deny 的结果一致 | 无；差异必须被建模为独立 policy |
| DataPolicy | 集成测试：列表、详情、导出、异步共享同一个 policy trace | 无 |
| 会话撤权 | 生产单体 SpringBootTest + Redis Testcontainer/等价隔离实现 | 无，作为 P0 release gate |
| 内部 token | MockMvc contract：拒绝重复 query、path/query 编码歧义、caller scope 不匹配 | 无 |

推荐在仓库中新建 architecture-test Maven 模块或现有 system 测试包，集中保存 allowlist 和 Catalog contract；不要把简单 rg 当作唯一安全控制，但它适合发现新增的直接 contains 与硬编码。

## 14. 最终裁决

**主结论：C. 已形成多套权限体系，需要系统性整改。**  
**限定：IAM-01 达到 P0，必须按 D 的处置优先级先修复。** 这不要求停止整个产品所有功能，但应停止新增或扩大 IAM、会话、角色、DataScope、内部 token 与受保护接口的变更，直到撤权下一请求生效的生产装配测试通过。

| 项目 | 结论 |
| --- | --- |
| 结论置信度 | P0 撤权缺陷：高；权限语义/菜单/管理员分散：高；内部 token 重复参数：中，需集成验证；多租户风险：需产品部署验证。 |
| 最高优先级动作 | 在 AuthJwtAuthFilter 构建 CurrentUser 前验证当前授权版本，并在角色/用户角色/DataScope 变更时精确失效受影响 AuthSession；用完整单体集成测试证明。 |
| 不建议现在做的事情 | 不要直接更换为外部授权引擎；不要一次性删除 sys_* 表；不要继续用前端隐藏、全局 cache bump 或缩短 token TTL 充当撤权修复；不要将未证实 tenant 缺失误报为当前跨租户漏洞。 |
| 最值得保留的能力 | AuthorizationDecision 的风险/确认/审批表达、PermissionSnapshot 物化、AuthSession 服务器端状态、DataPermissionResolver 的纯计算、Plugin 命名空间限制、AI 二次确认、Async trusted snapshot resolver、操作审计。 |

真正的完成标准不是“权限相关文件更少”，而是：同一 Principal 在任意 Web、AI、Plugin、Async、菜单和资源入口上由同一语义做出可解释的决定；权限/范围撤销后下一次受保护请求立即失效；新增 Permission 只注册一次；前端只展示后端已经决定的 capability。
