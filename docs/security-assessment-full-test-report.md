# Lumira 安全评估与全量测试报告

报告日期：2026-06-19

评估对象：`C:\Users\Administrator\Documents\GitHub\Lumira`

评估基线：`89381581`

整改提交：`f9ff9e3a`

## 1. 结论

本轮已完成仓库代码层面与自动化门禁层面的全量排查：覆盖后端 Maven 模块、前端依赖/静态检查/类型检查/单元测试/smoke 测试、脚本门禁测试、部署脚本与发布证据完整性。已发现的 11 项安全问题均已完成代码整改，并通过针对性回归与仓库级测试验证。

本结论不等同于生产环境全量渗透测试。生产网络边界、真实域名和端口、第三方服务配置、云资源权限、日志平台、备份介质、真实更新源签名链等，需要在授权的现场或预生产环境继续执行动态测试。

## 2. 依据标准

| 标准/方法 | 本轮对应工作 |
|---|---|
| GB/T 22239-2019 | 对身份鉴别、访问控制、安全审计、入侵防范、恶意代码和数据完整性相关控制进行代码与配置复核。 |
| GB/T 28449-2018 | 按测评准备、方案执行、证据留存、结果判定、整改复测组织本轮评估。 |
| GB/T 20984-2022 | 结合资产、威胁、脆弱性、影响和现有控制进行风险判定与整改优先级排序。 |
| GB/T 30279-2020 | 对漏洞按利用条件、影响范围和危害程度进行分级，形成 Critical、High、Medium-High、Medium 风险队列。 |
| OWASP Testing Guide V4 | 覆盖认证、会话管理、访问控制、输入校验、文件处理、配置管理、客户端与接口行为测试。 |
| MITRE ATT&CK Enterprise | 将发现项映射到凭据访问、权限提升、防御规避、初始访问和命令控制相关攻击技法。 |
| NIST SP 800-115 | 按规划、发现、攻击面验证、报告和整改复测的技术测试流程留存证据。 |

## 3. 范围说明

已完成范围：

- 后端：`services/*`、`libs/*` 的 Maven 测试与安全相关回归。
- 前端：`frontend` 依赖锁定安装、lint、typecheck、单元测试、smoke 测试。
- 脚本门禁：`scripts/*.test.mjs` 顺序执行。
- 运维与发布：CI 门禁、updater 认证测试、发布证据完整性、交付包完整性、镜像摘要与环境证据生成。
- 文档：整改跟踪表、全量测试证据、国家标准对照说明。

未完成范围：

- 未对真实生产环境执行授权渗透测试。
- 未对互联网暴露资产执行外部扫描。
- 未连接真实第三方服务、云账号、日志平台或密钥管理平台做现场核验。
- 未使用子代理完成方差降低型深度扫描；当前会话没有可调用的子代理调度工具，因此本报告不冒充多子代理结论。

## 4. 问题闭环摘要

| 风险级别 | 数量 | 状态 |
|---|---:|---|
| Critical | 1 | 已修复并复测通过 |
| High | 7 | 已修复并复测通过 |
| Medium-High | 1 | 已修复并复测通过 |
| Medium | 2 | 已修复或静态复核通过 |

主要整改内容：

- 默认管理员口令不再使用可预测基线，登录逻辑拒绝不安全默认值。
- 数据权限 SQL 通配符策略收紧，阻断 `*` 造成的越权查询风险。
- AI 外部工具回调执行前强制校验所需权限。
- 发布清单镜像引用要求使用 `@sha256:` 摘要。
- 插件包加载修复路径穿越风险，限制固定包名、插件代码格式、存储根目录和元数据一致性。
- 刷新令牌校验补齐 `jti/tokenId`、`userId`、`sessionVersion`。
- 启用验证码或提交验证码证据时失败关闭。
- updater 管理接口拒绝空 token。
- 文件存储根目录必须位于上传根目录之内。
- 仅信任配置的代理 CIDR 传入的 `Forwarded/X-Forwarded-For`。
- 备份默认目录移出仓库，环境快照按 `600` 权限写入，仓库忽略 `backups/`。

## 5. 测试证据

| 类别 | 命令 | 结果 |
|---|---|---|
| 针对性 Java 安全回归 | `.\mvnw.cmd "-pl" "services/auth-service,services/system-service,services/ai-service,services/plugin-service,services/file-service,libs/common-web" "-Dtest=AuthAppServiceTest,DefaultAdminPasswordBaselineTest,SystemRoleManagementAppServiceTest,PlatformUpdateAppServiceTest,AiCommandServiceTest,PluginArtifactLoaderTest,FileManagementAppServiceTest,ClientIpResolverTest" test` | 通过 |
| updater 认证回归 | `node scripts/lumira-updater-auth.test.mjs` | 通过 |
| 后端全量测试 | `.\mvnw.cmd clean test` | 15 个模块通过；`lumira-server` 清理阶段因历史 Java 进程锁定 jar 中断 |
| 后端补充验证 | `.\mvnw.cmd -pl services/lumira-server,services/ai-service -am test` | 17 个模块通过 |
| 前端依赖锁定安装 | `corepack pnpm --dir frontend install --frozen-lockfile` | 通过 |
| 前端 lint | `corepack pnpm --dir frontend run lint` | 通过 |
| 前端类型检查 | `corepack pnpm --dir frontend run typecheck` | 通过 |
| 前端单元测试 | `corepack pnpm --dir frontend run test` | 12 个测试文件、37 个用例通过 |
| 前端 smoke | `corepack pnpm --dir frontend run test:smoke` | 通过 |
| 脚本门禁 | `scripts/*.test.mjs` 顺序执行 | 全部通过 |
| 发布证据生成 | `node scripts/ddd-release-readiness-summary.mjs` | 生成成功；发布业务状态为 `NOT_READY`，仍有 94 个 owner/环境输入阻塞 |
| 发布完整性门禁 | `node scripts/ddd-release-artifact-integrity-gate-contract.test.mjs` | 通过 |
| 发布配置同步门禁 | `node scripts/ddd-release-config-sync.test.mjs` | 通过 |

## 6. 本轮新增修复

| 文件 | 修复内容 | 原因 |
|---|---|---|
| `.github/workflows/ci.yml` | 将 `scripts/lumira-updater-auth.test.mjs` 纳入 CI 语法检查与测试步骤 | 防止 updater 认证回归只在本地执行，CI 漏检 |
| `frontend/scripts/captcha-load-smoke.ts` | 使用本地 mock 请求与失败图片加载模拟，修复中文错误信息编码 | 让 smoke 测试不依赖外部网络，并验证预加载失败不影响验证码挑战 |
| `frontend/scripts/theme-official-alignment-smoke.ts` | 修复 Windows 路径解析，允许 Ant Design radius token fallback 形式 | 保证 Windows 环境下主题一致性 smoke 稳定运行 |
| `artifacts/ddd/release/**` | 重新生成发布证据和完整性清单 | 修复发布证据完整性门禁中陈旧 hash/size 导致的失败 |

## 7. 风险保留与建议

| 风险 | 建议动作 |
|---|---|
| 生产环境未现场验证 | 在预生产或生产授权窗口执行 NIST SP 800-115 风格动态测试，覆盖认证、越权、文件上传、管理接口、代理链与日志告警。 |
| 外部资产未扫描 | 建立资产清单，明确域名、IP、端口、云资源和第三方回调地址后执行授权扫描。 |
| 供应链与镜像运行态未验证 | 在真实发布流水线中强制镜像摘要、签名校验、SBOM、依赖漏洞扫描和制品完整性门禁。 |
| 安全运营证据仍需现场补齐 | 补充日志留存、告警闭环、备份恢复演练、权限审批和应急响应记录。 |

## 8. 全量排查判定

本轮可以判定为“仓库代码与自动化测试门禁全量排查已完成”。不能判定为“生产环境全量安全测评已完成”，因为该结论需要真实环境授权、资产清单、外部扫描结果、现场访谈和运行态证据支持。
