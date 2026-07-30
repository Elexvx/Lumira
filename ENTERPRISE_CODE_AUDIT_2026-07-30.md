# Lumira 企业级代码审计报告

- 审计日期：2026-07-30（Asia/Shanghai）
- 审计提交：`33dd4dca184d3a9bb5453f8a835a55bc2209ada4`
- 分支状态：本地 `main` 与 `origin/main` 一致
- 审计规模：1,898 个受版本控制文件；1,842 个代码、配置、SQL 与文档文件；约 357,480 行
- 审计结论：**NO-GO，当前版本不应直接升级到生产环境**

## 1. 结论摘要

当前代码具备较完整的模块化、鉴权、支付回调、Outbox、蓝绿升级和数据库迁移基础，但仍存在会影响身份安全、授权数据完整性、存量数据库一致性和多实例缓存一致性的上线阻断项。

本次确认：

| 级别 | 数量 | 上线含义 |
| --- | ---: | --- |
| P0 / Critical | 2 | 必须修复后才能部署 |
| P1 / High | 7 | 必须进入本次上线阻断清单 |
| P2 / Medium | 10 | 应在近期版本完成，涉及安全边界和可维护性 |
| P3 / Low | 3 | 纳入持续治理 |

最重要的阻断项是：

1. 全量基线 SQL 包含两个启用账号的公开初始口令，普通账号没有首次登录强制改密；在已有库重放基线还会覆盖已修改的口令。
2. 菜单 ID `-1074` 同时分配给“评审结果与申诉”和“删除赛事”，可能覆盖授权节点。
3. 维护模式、菜单新增/停用等改动只进入全量基线，没有对应在线迁移；这不符合“数据库优先、存量库可升级”的要求。
4. 多处把固定“操作类型”当作幂等事件 ID，第二次相同类型的设置、权限、菜单或插件变更不会推进跨实例缓存版本。
5. 安全、短信、Passkey、微信登录设置由多个独立 upsert 组成但没有事务，失败时可产生混合配置。
6. 安全扫描对依赖审计、OSV 和 Semgrep 全部软失败，当前仍有未处置的高危依赖报告。
7. 插件以共享 HMAC 密钥签名并在主进程加载执行，示例密钥还能通过生产校验，供应链信任边界不足。

## 2. 审计范围与方法

本次执行了仓库级静态审计、迁移契约复核、定向单元测试、前端全量单元测试、Lint、类型检查、GitHub CI/分支治理状态检查和当前提交的依赖风险报告复核。

覆盖范围：

- 数据库基线、在线迁移、种子数据、运行时自动补数据
- 身份认证、权限、数据隔离、会话和跳转
- 事务、一致性、缓存版本、并发与 Outbox
- 文件、插件、支付、AI 外部端点等高风险边界
- 前端富文本和路由
- CI/CD、安全扫描、分支保护、依赖风险和质量门禁
- 代码规模、异常处理、审计日志和仓库卫生

限制：

- 未连接或修改生产数据库，未执行生产渗透测试、云资源/IAM 审计和真实支付渠道联调。
- 自动安全审计框架在 Windows 上因 GBK 解码中文提交标题失败；本报告使用当前提交的 GitHub 安全扫描制品和人工代码复核补足。
- 依赖漏洞是否可被实际利用仍需按运行路径逐项确认；“存在报告”不等于所有漏洞均可利用，但在完成风险接受或升级前不能视为已关闭。

## 3. 数据库优先专项结论

### 3.1 结论

**不能确认当前所有功能改动都已并入核心数据库迁移。**

当前状态是：

- 新库：大多数定义可通过 [`lumira-backend/sql/saas.sql`](lumira-backend/sql/saas.sql) 获得。
- 存量库：部分近期配置和菜单改动没有对应 `deploy/migrations` 在线迁移。
- 后端启动：未发现启动时向 MySQL 自动插入静态业务定义的 Runner。
- 运行期：管理员保存设置时会正常写入业务配置；这属于业务写入，但不能代替版本化迁移。
- [`FieldEncryptionMigrationRunner`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/infrastructure/security/FieldEncryptionMigrationRunner.java#L27) 只检查明文并在发现问题时拒绝启动，不会自动修改数据库。

### 3.2 已确认的存量库缺口

| 变更 | 全量基线 | 在线迁移 | 现有库行为 |
| --- | --- | --- | --- |
| 维护模式 3 个配置项 | 有，[`saas.sql:4125`](lumira-backend/sql/saas.sql#L4125) | 无 | 首次保存时由后端 upsert 创建，[`SystemPlatformSettingsAppService:243`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/app/SystemPlatformSettingsAppService.java#L243) |
| 报名团队资料菜单 | 有，[`saas.sql:3569`](lumira-backend/sql/saas.sql#L3569) | 无 | 存量库不会自动获得菜单 |
| 项目、团队、查询中心等菜单停用 | 有，[`saas.sql:3559`](lumira-backend/sql/saas.sql#L3559) | 无 | 存量库主要依赖前端隐藏/重定向 |
| 报名数据集与异步导出 | 有 | 有，[`V202607290001`](deploy/migrations/V202607290001__competition_registration_datasets.sql) | 合格 |
| 版本化评审域 | 有 | 有，[`V202607290002`](deploy/migrations/V202607290002__competition_review_domain.sql) | 存在菜单 ID 冲突，见 P0-02 |
| 内置插件路由迁移 | 有 | 有，[`V202607280001`](deploy/migrations/V202607280001__relocate_builtin_plugin_routes.sql) | 合格 |

### 3.3 运行时自动写库结论

在 `ApplicationRunner` / `CommandLineRunner` 中未发现向核心 MySQL 自动插入菜单、配置、角色或权限定义的逻辑。发现的 Runner 为：

- 生产密钥校验；
- 字段明文检查；
- Redis 可选清理；
- 插件密钥校验。

因此，问题不是“启动时偷偷补核心数据”，而是“新增静态定义只改全量 SQL、漏写在线迁移”，以及普通设置保存承担了本应由迁移完成的定义创建。

## 4. P0 / Critical

### P0-01 已启用的已知初始账号与可重复重置口令

**证据**

- 基线明确声明初始口令为公开固定值：[`saas.sql:3352`](lumira-backend/sql/saas.sql#L3352)。
- `admin` 和普通 `user` 都以相同口令哈希启用：[`saas.sql:4080`](lumira-backend/sql/saas.sql#L4080)、[`saas.sql:4294`](lumira-backend/sql/saas.sql#L4294)。
- 用户表和凭证表的 `ON DUPLICATE KEY UPDATE` 都会覆盖已有口令：[`saas.sql:4090`](lumira-backend/sql/saas.sql#L4090)、[`saas.sql:4261`](lumira-backend/sql/saas.sql#L4261)、[`saas.sql:4304`](lumira-backend/sql/saas.sql#L4304)、[`saas.sql:4343`](lumira-backend/sql/saas.sql#L4343)。
- 首次改密保护只覆盖 ID 1001 的默认管理员，不覆盖普通账号：[`InitialPasswordChangeGuard:23`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/infrastructure/security/service/InitialPasswordChangeGuard.java#L23)、[`InitialPasswordChangeGuard:55`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/infrastructure/security/service/InitialPasswordChangeGuard.java#L55)。
- 测试主动断言基线中存在该公开口令的哈希：[`DefaultAdminPasswordBaselineTest:19`](lumira-backend/services/lumira-system/src/test/java/com/lumira/saas/infrastructure/db/DefaultAdminPasswordBaselineTest.java#L19)。
- SQL 敏感信息门禁把全量基线列为允许文件，因此不会阻断这个哈希：[`check-sensitive-sql-dumps.mjs:12`](bin/check-sensitive-sql-dumps.mjs#L12)。

**影响**

- 新部署若未立即改密，两个已知账号可被直接尝试登录。
- 普通账号具备文件上传、赛事报名、支付报名和活动创建等权限。
- 在已有数据库误重放全量基线会把已改口令重置为已知值。

**整改**

1. 删除内置普通账号或默认设为禁用。
2. 管理员口令在部署时使用一次性随机值，通过秘密管理系统注入并只存哈希。
3. 基线 SQL 不得在冲突更新时覆盖口令、状态、UUID 或凭证。
4. 首次改密策略改为凭证级状态，而不是硬编码用户 ID。
5. 修改测试，禁止出现可公开验证的固定口令哈希。

**验收**

- 新库中不存在可用的固定口令。
- 重放允许的幂等迁移不改变现有凭证。
- 所有引导账号均有一次性凭证、到期时间和强制改密状态。
- CI 对可验证固定口令哈希失败关闭。

### P0-02 菜单主键冲突会覆盖授权节点

**证据**

- `competition.review-results` 使用 ID `-1074`：[`saas.sql:3572`](lumira-backend/sql/saas.sql#L3572)。
- `competition.management.delete` 同样使用 ID `-1074`：[`saas.sql:3588`](lumira-backend/sql/saas.sql#L3588)。
- 在线评审迁移再次用 `-1074` 写入评审结果菜单：[`V202607290002:332`](deploy/migrations/V202607290002__competition_review_domain.sql#L332)。
- 完整性测试先把 ID 写入 `Map`，重复 ID 被静默覆盖，造成假阴性：[`SaasSqlBootstrapCompletenessTest:194`](lumira-backend/services/lumira-system/src/test/java/com/lumira/saas/infrastructure/db/SaasSqlBootstrapCompletenessTest.java#L194)。

**影响**

- 删除赛事按钮节点可能被评审菜单覆盖。
- 角色权限绑定、菜单父子关系和前端权限树可能产生不可预测结果。
- 新库与存量库执行迁移后的授权状态可能不同。

**整改**

1. 分配全局唯一、不可复用的菜单 ID。
2. 新增修复迁移：按 `menu_code` 校正 ID、父子关系和所有关联表外键。
3. 完整性测试分别检测重复 ID、重复 `menu_code`、悬空父节点和权限键冲突，不能先放入 Map。

**验收**

- 基线和每条迁移分别通过唯一性检查。
- 修复迁移在空库、旧库和已执行错误迁移的库上均有可重复测试。
- 升级前后角色菜单快照一致，删除赛事权限仍存在。

## 5. P1 / High

### P1-01 静态业务定义缺少在线迁移

维护模式、报名团队资料菜单和多个菜单停用只进入全量基线，存量数据库无法通过正式升级获得相同状态。详见第 3 节。

**整改与验收**

- 每次新增/修改菜单、权限、字典、配置定义必须同时提供基线和版本化迁移。
- 建立“空库最终状态 = 旧版本逐条迁移最终状态”的结构与种子快照对比。
- 配置保存服务只更新值，不负责首次创建平台定义；缺少定义时应明确报错。

### P1-02 固定操作类型被误用为幂等事件 ID

**证据**

- 版本服务在 `last_event_key` 相同时不递增：[`ReadModelVersionService:108`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/infrastructure/readmodel/ReadModelVersionService.java#L108)。
- 测试明确固化这一行为：[`ReadModelVersionServiceTest:19`](lumira-backend/services/lumira-system/src/test/java/com/lumira/saas/infrastructure/readmodel/ReadModelVersionServiceTest.java#L19)。
- 安全设置固定使用 `security-update`：[`SecuritySettingsService:337`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/infrastructure/security/service/SecuritySettingsService.java#L337)。
- 权限快照固定使用 `iam.permission.invalidate`：[`PermissionSnapshotService:448`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/iam/service/PermissionSnapshotService.java#L448)。
- 验证、短信、Passkey 和微信设置使用固定事件名：[`SystemVerificationSettingsAppService:323`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/verification/SystemVerificationSettingsAppService.java#L323)、[`WechatLoginSettingsService:221`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/verification/WechatLoginSettingsService.java#L221)。
- 插件启停/版本切换也传固定事件名：[`PluginManagementAppService:238`](lumira-backend/services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/app/PluginManagementAppService.java#L238)、[`PluginManagementAppService:302`](lumira-backend/services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/app/PluginManagementAppService.java#L302)。

**影响**

同一类型第二次变更可能不推进共享版本，多实例节点继续使用旧的权限、菜单、登录方式、安全策略或插件缓存。

**整改**

- 将“事件唯一 ID”和“事件类型”分离。
- 每个业务提交生成 UUID/Outbox event ID；同一次提交涉及多个 scope 时复用该唯一 ID。
- 只有消息重放才使用同一个事件 ID。

**验收**

- 连续两次相同类型变更版本连续递增。
- 同一事件重放不重复递增。
- 两节点集成测试验证第二次变更可见。

### P1-03 多配置更新缺少事务

**证据**

- 安全策略一次执行 16 个独立 upsert，没有 `@Transactional`：[`SecuritySettingsService:223`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/infrastructure/security/service/SecuritySettingsService.java#L223)。
- 验证、短信和 Passkey 更新/重置由 3–8 个独立 upsert 组成，没有事务：[`SystemVerificationSettingsAppService:310`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/verification/SystemVerificationSettingsAppService.java#L310)。
- 微信登录更新/重置由 5 个独立 upsert 组成，没有事务：[`WechatLoginSettingsService:199`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/verification/WechatLoginSettingsService.java#L199)。
- 查询配置 ID 时广泛捕获异常并按“不存在”处理：[`SystemVerificationSettingsAppService:503`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/verification/SystemVerificationSettingsAppService.java#L503)。

**影响**

中途数据库错误会留下混合配置；例如登录开关已更新但凭据、允许来源或排序尚未更新。

**整改与验收**

- 每个设置聚合在单一事务内更新。
- 缓存版本和审计事件在提交后发布。
- 只把明确的“未找到”当作不存在，数据库异常必须失败关闭。
- 注入第 N 条写入失败，验证所有值回滚且版本不递增。

### P1-04 安全扫描软失败且存在未处置依赖风险

**证据**

- `pnpm audit`、OSV、Semgrep 均配置 `continue-on-error: true`：[`security-scan.yml:53`](.github/workflows/security-scan.yml#L53)。
- `pnpm audit` 最终显式 `exit 0`：[`security-scan.yml:56`](.github/workflows/security-scan.yml#L56)。
- 文件系统镜像扫描被禁用：[`security-scan.yml:29`](.github/workflows/security-scan.yml#L29)。
- 当前提交的安全扫描虽然显示成功，但制品报告为前端生产依赖：3 low、17 moderate、12 high、0 critical。
- 当前 OSV 报告包括：
  - `jackson-core 2.21.2`：HIGH；
  - `jackson-databind 2.22.0`：2 个 MODERATE；
  - `logback-core 1.5.32`：2 个 LOW；
  - 前端/工具链中的 Axios、brace-expansion、fast-uri、immutable、js-yaml、postcss、svgo 等 HIGH。

GitHub 证据：Security Scan run `30540376421`，提交与本报告一致。

**整改与验收**

- 建立严重度、可达性、SLA 和例外到期日策略；未知 HIGH 默认阻断。
- 恢复镜像/文件系统扫描。
- 安全扫描必须以策略结果决定 Job 成败。
- 逐项升级或记录带负责人、到期日和补偿控制的风险接受。

### P1-05 插件供应链与运行隔离不足

**证据**

- 示例环境提供的占位密钥长度足够，且不在禁止列表中：[`deploy/.env.example:100`](deploy/.env.example#L100)、[`PluginSecurityPropertiesValidator:15`](lumira-backend/services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/runtime/PluginSecurityPropertiesValidator.java#L15)。
- 插件签名实际是共享密钥 HMAC-SHA256，并使用普通字符串忽略大小写比较：[`PluginArtifactLoader:254`](lumira-backend/services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/loader/PluginArtifactLoader.java#L254)。
- 校验只验证 `checksums` 中列出的文件，没有证明关键可执行文件全部被覆盖：[`PluginArtifactLoader:241`](lumira-backend/services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/loader/PluginArtifactLoader.java#L241)。
- 插件 JAR 通过父类加载器加载到应用进程并可注册 HTTP 处理器和定时任务：[`PluginRuntimeLoader:59`](lumira-backend/services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/loader/PluginRuntimeLoader.java#L59)。

**影响**

占位密钥泄漏或任一签名方失陷可签发任意插件；插件获得与主进程相同的文件、网络、内存和数据库能力。

**整改与验收**

- 改为离线私钥签名、平台只持有公钥；支持密钥 ID、轮换和吊销。
- 清单必须覆盖所有文件，并拒绝额外文件。
- 使用常量时间字节比较。
- 非内置插件移入独立进程/容器，使用最小权限服务账号、网络白名单和资源限制。
- 生产环境拒绝所有文档占位密钥。

### P1-06 主分支缺少组织级治理保护

2026-07-30 查询结果：

- GitHub `main` 分支保护 API 返回 `Branch not protected`。
- Repository rulesets 为空。
- 仓库缺少 `README.md`、`SECURITY.md`、`CONTRIBUTING.md` 和 `CODEOWNERS`。

**影响**

即使 CI 存在，仍可绕过评审、状态检查、签名或所有者批准直接写入主分支。

**整改与验收**

- 启用 ruleset：禁止直接推送、至少 1–2 人评审、CODEOWNERS、高风险目录额外审批、必须通过 CI/安全扫描、禁止 force-push。
- 增加漏洞披露、贡献、发布和回滚政策。

### P1-07 团队域生产审计日志被静默丢弃

**证据**

- 生产组件 [`NoopTeamAuditPort`](lumira-backend/services/lumira-team/src/main/java/com/lumira/team/app/NoopTeamAuditPort.java#L6) 的实现为空，并保留 TODO。
- 该空实现直接进入团队控制面装配：[`TeamControlPlaneAssemblyConfiguration:33`](lumira-backend/services/lumira-team/src/main/java/com/lumira/team/TeamControlPlaneAssemblyConfiguration.java#L33)。

**影响**

团队成员、角色和控制面变更缺少可追溯审计证据，不满足企业审计和事故调查要求。

**整改与验收**

- 接入不可篡改的审计 Outbox/平台审计 API。
- 审计失败策略明确：高风险管理操作失败关闭，异步场景持久化后重试。
- 集成测试验证操作者、目标、前后值、时间、请求 ID 和结果均可查询。

## 6. P2 / Medium

### P2-01 工单富文本只在前端使用自制清洗器

- 后端仅做非空和长度处理后直接持久化 HTML：[`WorkOrderFeedbackService:189`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/workorder/app/WorkOrderFeedbackService.java#L189)。
- 前端自制清洗器只删除部分标签和 `on*`/`javascript:` 属性：[`WorkOrderFeedbackPage:79`](lumira-ui/src/pages/plugins/WorkOrderFeedbackPage.tsx#L79)。
- 最终仍使用 `dangerouslySetInnerHTML`：[`WorkOrderFeedbackPage:248`](lumira-ui/src/pages/plugins/WorkOrderFeedbackPage.tsx#L248)。

整改：服务端采用经过维护的严格 allowlist 清洗；保存原文与安全渲染值分离；增加 SVG、CSS、URL 编码和 DOM clobbering 回归用例。

### P2-02 登录重定向允许协议相对路径边界

- 重定向只检查以 `/` 开头，因此 `//example.com` 也会通过：[`loginRedirect.ts:23`](lumira-ui/src/auth/loginRedirect.ts#L23)。
- 登录广播监听器最终调用 `window.location.replace(target)`：[`useLoginFlowRuntime.ts:1565`](lumira-ui/src/pages/user/login/hooks/useLoginFlowRuntime.ts#L1565)。
- 现有 10 个重定向测试没有覆盖 `//`、反斜杠和编码变体：[`loginRedirect.test.ts`](lumira-ui/src/auth/loginRedirect.test.ts)。

整改：仅接受单斜杠同源绝对路径；通过 `new URL(target, location.origin)` 验证 origin；拒绝反斜杠、控制字符和二次编码。

### P2-03 AI 端点存在 DNS 重绑定时间差

- 保存/创建客户端前会 DNS 解析并拒绝本地地址：[`AiChatModelFactory:542`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/ai/app/AiChatModelFactory.java#L542)。
- 实际 HTTP 连接时会再次解析，存在验证与使用之间的 DNS 重绑定窗口。
- Ollama 默认地址是 `localhost`，但同一校验拒绝 loopback：[`AiChatModelFactory:557`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/ai/app/AiChatModelFactory.java#L557)、[`AiChatModelFactory:580`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/ai/app/AiChatModelFactory.java#L580)。

整改：连接时固定已验证 IP，校验证书主机名；每次重定向重新校验；本地 Ollama 使用单独、明确授权的策略。

### P2-04 缺少统一浏览器安全响应头

- Nginx 主要设置缓存头，没有统一 CSP、HSTS、`X-Content-Type-Options`、`Referrer-Policy`、`Permissions-Policy`。
- Spring Security 仅显式配置 frame options：[`SecurityConfig:82`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/infrastructure/security/SecurityConfig.java#L82)。

整改：在边缘层统一配置安全头；CSP 先 report-only 再强制；HSTS 仅在确认全站 HTTPS 后启用并逐步扩大。

### P2-05 内部文件读取边界依赖调用方先鉴权

- 文件服务的业务引用读取只校验引用类型、正数 ID 和文件存在，没有校验 `fileId` 与 `referenceId` 的绑定，也没有自行校验业务权限：[`FileManagementAppService:660`](lumira-backend/services/lumira-file/src/main/java/com/lumira/file/app/FileManagementAppService.java#L660)。
- 当前赛事调用方先执行材料访问检查，这是正向控制，但内部 API 自身仍是 confused-deputy 边界。

整改：由文件服务验证不可伪造的授权票据，或查询受信业务引用映射；增加“正确用户 + 错误 referenceId/fileId”拒绝用例。

### P2-06 异常吞噬规模过大

静态统计发现生产代码中有 186 个捕获 `Exception/Throwable` 的位置，其中 45 个直接命名为 `ignored`。并非每个捕获都错误，但当前没有静态规则区分允许场景。

高风险实例：配置 ID 查询把任意数据库异常当作不存在：[`SystemVerificationSettingsAppService:503`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/verification/SystemVerificationSettingsAppService.java#L503)。

整改：建立允许清单；禁止在认证、权限、配置、支付和审计路径无日志吞异常；记录结构化错误并保留 cause。

### P2-07 可信用户刷新逻辑大面积复制

至少 33 个生产类各自实现 `refreshTrustedCurrentUser` / `requireTrustedCurrentUser` / `requireTrustedUser`，包含重复的会话刷新、UUID 校验、权限快照复制和异常回退。

影响：不同模块容易出现鉴权行为漂移，安全修复需要同步修改大量副本。

整改：下沉为统一 `TrustedCurrentUserResolver`/拦截器，在控制器边界生成不可变安全主体；业务服务只消费可信主体。

### P2-08 超大文件和职责聚合

代表性文件：

- [`CompetitionPage.tsx`](lumira-ui/src/pages/competition/CompetitionPage.tsx)：7,719 行。
- [`SystemManagementAppService.java`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/app/SystemManagementAppService.java)：3,814 行。
- [`CompetitionRegistrationAppService.java`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/competition/app/CompetitionRegistrationAppService.java)：3,189 行。
- [`InternalSystemController.java`](lumira-backend/services/lumira-system/src/main/java/com/lumira/saas/modules/system/controller/InternalSystemController.java)：2,334 行。

整改：按聚合、用例和端口拆分；控制器只做协议适配；前端按页面子域、状态机和可复用面板拆分。新增复杂度和文件行数增量门禁。

### P2-09 缺少覆盖率、复杂度和 Java 静态质量门禁

- 前端存在 `test:coverage`，但 CI 只运行普通测试且无阈值。
- Maven 未配置 JaCoCo、SpotBugs、PMD、Checkstyle 或 PIT。
- 根 [`package.json:10`](package.json#L10) 的默认 `test` 仍是占位失败脚本。

整改：建立变更覆盖率与核心模块覆盖率基线；启用 SpotBugs/Checkstyle/复杂度门禁；根脚本统一编排实际测试。

### P2-10 配置契约和仓库制品漂移

- 文档声明 `LUMIRA_INITIAL_ADMIN_PASSWORD` 可设置首登口令并传入容器：[`deploy/.env.example:159`](deploy/.env.example#L159)、[`docker-compose.prod.yml:389`](deploy/docker-compose.prod.yml#L389)，但代码中没有消费者。
- [`build-identity.env`](build-identity.env) 内容损坏，包含多个无效 `FRONTEND_VERSION` 行。
- 仓库跟踪了两份 SHA256 完全相同的编译 JAR：
  - `artifacts/boot-jar-patch/BOOT-INF/lib/system-service-0.1.0.jar`
  - `artifacts/boot-jar-patch-2/BOOT-INF/lib/system-service-0.1.0.jar`
- 仓库还跟踪多份临时 QA 截图。

整改：配置必须有启动时契约测试；构建制品进入制品库而不是 Git；清理损坏身份文件和一次性 QA 产物。

## 7. P3 / Low

### P3-01 Lint 仍有警告

本地 Lint 为 0 error、2 warning：

- [`ActivityRegistrationPage.tsx:51`](lumira-ui/src/pages/competition/ActivityRegistrationPage.tsx#L51) 未使用 `initialState`。
- [`CompetitionPage.tsx:6843`](lumira-ui/src/pages/competition/CompetitionPage.tsx#L6843) 未使用 `CompetitionStageWindowsPanel`。

建议将生产 CI 配置为零警告或显式预算。

### P3-02 GitHub Actions 未全部固定到不可变提交

Actions 使用 `@v4`、`@v1` 等浮动主版本；Gitleaks 二进制通过 `curl` 下载后未校验 checksum：[`security-scan.yml:18`](.github/workflows/security-scan.yml#L18)、[`security-scan.yml:24`](.github/workflows/security-scan.yml#L24)。

建议固定完整 commit SHA，并校验外部二进制签名或摘要。

### P3-03 动态 SQL虽有白名单，但仍应消除字符串替换

两处 MyBatis `${}` 经复核未发现可利用注入：

- 消息排序字段由固定 `<when>` 选择，方向归一化为 `asc/desc`。
- 本地化排序列来自固定映射，方向归一化为 `asc/desc`：[`LocalizationManagementAppService:200`](lumira-backend/services/lumira-localization/src/main/java/com/lumira/localization/app/LocalizationManagementAppService.java#L200)。

建议改为枚举分支生成固定 SQL，避免后续调用者绕开白名单。

## 8. 已确认的正向控制

以下控制在本次审计中表现良好：

- 客户端 IP 只在受信代理 CIDR 后解析转发头。
- JWT、字段加密、数据库、CORS 和内部令牌有生产环境启动校验。
- 内部服务令牌按路径/服务收敛并使用常量时间比较。
- Refresh Cookie 为 Secure、HttpOnly、SameSite=Lax，并配套双提交 CSRF。
- 工单数据隔离正确：管理员范围可查看全部，普通用户查询和详情均按 `userId + userUuid` 限制。
- 插件网关会剥离敏感头。
- Nginx 有基础限流和缓存策略。
- 支付回调具备请求体上限、签名与渠道身份校验、金额/币种核对、唯一事件、重放 nonce、CAS、事务和 Outbox。
- 更新器默认只监听 loopback，要求令牌、镜像 digest、仓库白名单、扩展型迁移、锁和恢复流程。
- 数据库迁移先于应用容器启动，蓝绿升级与真实 Docker 回滚演练在当前 CI 通过。
- `FieldEncryptionMigrationRunner` 为只读检查并失败关闭，不会在启动时篡改数据库。

## 9. 测试与证据

本地复核：

| 命令/门禁 | 结果 |
| --- | --- |
| `pnpm test` | 66 个文件、312 个测试全部通过 |
| `pnpm typecheck` | 通过 |
| `pnpm lint` | 通过，2 个 warning |
| `node bin/database-migration-contract.test.mjs` | 13/13 通过 |
| 后端定向测试 | 48/48 通过 |
| `node bin/check-sensitive-sql-dumps.mjs` | 36 个 SQL，0 finding；但全量基线被允许跳过哈希检查 |

后端定向测试范围：

- `ReadModelVersionServiceTest`
- `SecuritySettingsServiceTest`
- `SystemVerificationSettingsAppServiceTest`
- `WechatLoginSettingsServiceTest`
- `DefaultAdminPasswordBaselineTest`
- `SaasSqlBootstrapCompletenessTest`

当前提交的 GitHub CI、Frontend Build 和 Security Scan 均显示 success。但 Security Scan 的核心依赖/代码扫描是软失败，不能把绿色结果解释为风险已关闭。

## 10. 整改顺序

### 阶段 A：上线阻断修复

1. 移除固定账号口令和覆盖凭证的基线逻辑。
2. 修复 `-1074` 菜单冲突并提供关联数据修复迁移。
3. 为所有近期静态定义补在线迁移。
4. 修复 read-model 唯一事件 ID 语义。
5. 为安全/登录设置增加事务和提交后事件。
6. 将 HIGH 依赖和安全扫描策略改为可阻断。

### 阶段 B：高风险边界加固

1. 插件改为非对称签名并进程隔离。
2. 接通团队域审计。
3. 服务端富文本清洗、严格同源跳转、AI 端点连接期校验。
4. 文件服务验证业务引用绑定。
5. 启用分支 ruleset、CODEOWNERS 和安全政策。

### 阶段 C：工程治理

1. 统一可信用户解析。
2. 拆分超大类和页面。
3. 增加覆盖率、复杂度、静态分析和零警告门禁。
4. 清理制品、QA 截图、损坏版本文件和无效配置。

## 11. 上线验收门槛

只有同时满足以下条件，结论才能从 NO-GO 调整为 GO：

1. P0 全部关闭，并提供空库、存量库和异常迁移回滚证据。
2. P1 全部关闭或由安全负责人书面接受，包含负责人和到期日。
3. 安全扫描按严重度策略失败关闭，当前 HIGH 有升级或正式风险接受。
4. 两节点环境连续修改两次同类设置后，权限、菜单、登录方式和插件状态立即一致。
5. 配置更新故障注入证明原子回滚。
6. 主分支保护和必需检查实际启用。
7. 预发布环境完成完整登录、报名、材料、评审、支付、导出、插件和升级回滚验收。
