# Lumira 安全评估与全量测试报告

报告日期：2026-06-19

评估对象：`C:\Users\Administrator\Documents\GitHub\Lumira`

评估基线：`89381581`

已完成整改提交：`f9ff9e3a`、`dc3d17e1`、`72c08f54`

当前补充整改：待提交

## 1. 结论

本轮已完成仓库代码层、自动化测试门禁层和关键安全控制的全量排查：覆盖后端 17 个 Maven 模块、前端依赖锁定安装、lint、类型检查、单元测试、smoke 测试、发布脚本门禁、交接包完整性、以及一次子代理只读复核。已发现的原 11 项问题已闭环；子代理补充发现的 3 项问题也已完成代码整改并通过针对性回归和后端全量回归。

本结论不等同于“生产环境全量渗透测试已完成”。真实生产网络边界、外部暴露资产、云账号权限、第三方服务、日志平台、备份介质、真实更新源签名链和运行态 DAST，需要在授权的预生产或生产窗口继续执行。

## 2. 依据标准

| 标准/方法 | 本轮对应工作 |
|---|---|
| GB/T 22239-2019 | 复核身份鉴别、访问控制、安全审计、入侵防范、恶意代码防范、数据完整性和安全运维控制。 |
| GB/T 28449-2018 | 按测评准备、方案执行、证据留存、结果判定、整改复测组织本轮评估。 |
| GB/T 20984-2022 | 按资产、威胁、脆弱性、影响和现有控制进行风险判定与优先级排序。 |
| GB/T 30279-2020 | 按利用条件、影响范围和危害程度对漏洞分类分级。 |
| OWASP Testing Guide V4 | 覆盖认证、会话管理、访问控制、输入校验、文件处理、配置管理、客户端与接口行为测试。 |
| MITRE ATT&CK Enterprise | 将发现项映射到凭据访问、权限提升、防御规避、初始访问和命令控制相关技术。 |
| NIST SP 800-115 | 按规划、发现、攻击面验证、报告和整改复测的技术测试流程留存证据。 |

## 3. 范围说明

已完成范围：

- 后端：`services/*`、`libs/*` 的 Maven 全量测试与安全回归。
- 前端：`frontend` 依赖锁定安装、lint、typecheck、单元测试、smoke 测试。
- 脚本门禁：发布证据就绪、强制阻断、交接包完整性、发布制品完整性、配置同步门禁。
- 运维与发布：CI 门禁、updater 认证、发布证据完整性、镜像摘要、环境证据生成。
- 子代理复核：后端服务与公共库安全复核，补充发现 3 项风险。
- 文档：整改跟踪表、全量测试证据、国家标准对照说明。

未完成或受环境限制范围：

- 未对真实生产环境执行授权渗透测试。
- 未对互联网暴露资产执行外部扫描。
- 未连接真实第三方服务、云账号、日志平台或密钥管理平台做现场核验。
- 前端 Playwright E2E smoke 已安装 Chromium，但本机 `127.0.0.1:8000` 与 `127.0.0.1:8080` 未提供可访问运行态，E2E smoke 因连接拒绝未完成。
- `scripts/ddd-staging-execution-checklist.test.mjs` 全量脚本测试受重复 Node 进程干扰，已改用关键门禁命令复核并记录限制。

## 4. 问题闭环摘要

| 风险级别 | 数量 | 状态 |
|---|---:|---|
| Critical | 1 | 已修复并复测通过 |
| High | 7 | 已修复并复测通过 |
| Medium-High | 2 | 已修复并复测通过 |
| Medium | 3 | 已修复并复测通过 |
| Low-Medium | 1 | 已修复并复测通过 |

本轮补充整改：

- 更新清单源增加 HTTPS 校验和可配置主机 allowlist，镜像仍要求 `@sha256:` 摘要钉扎。
- 插件目录递归清理增加根目录边界校验，禁止删除插件存储根、暂存根或任意外部路径。
- `Forwarded/X-Forwarded-For` 只接受受信代理 CIDR 且客户端值必须为 IP 字面量，拒绝主机名、伪造值和 `unknown`。
- 发布证据就绪增加强制门禁命令：证据未齐时按设计返回阻断。

## 5. 测试证据

| 类别 | 命令 | 结果 |
|---|---|---|
| 后端全量测试 | `.\mvnw.cmd clean test` | 17 个 Maven 模块全部 `BUILD SUCCESS`，完成时间 2026-06-19 15:18:55，耗时 01:51；`system-service` 有 4 个既有 integration skip。 |
| IP 解析回归 | `.\mvnw.cmd -pl libs/common-web -Dtest=ClientIpResolverTest test` | 5 个用例通过，覆盖受信代理、非法值、可解析主机名拒绝、IPv6 `Forwarded`。 |
| 插件清理边界回归 | `.\mvnw.cmd -pl services/plugin-service -Dtest=PluginArtifactLoaderTest test` | 3 个用例通过。 |
| 更新源校验回归 | `.\mvnw.cmd -pl services/system-service -Dtest=PlatformUpdateAppServiceTest test` | 3 个用例通过。 |
| 前端依赖锁定安装 | `corepack pnpm --dir frontend install --frozen-lockfile` | 通过。 |
| 前端 lint | `corepack pnpm --dir frontend run lint` | 通过。 |
| 前端类型检查 | `corepack pnpm --dir frontend run typecheck` | 通过。 |
| 前端单元测试 | `corepack pnpm --dir frontend run test` | 12 个测试文件、37 个用例通过。 |
| 前端 smoke | `corepack pnpm --dir frontend run test:smoke` | 通过。 |
| 前端 E2E smoke | `corepack pnpm --dir frontend run test:e2e:smoke` | Chromium 已安装；因 `http://127.0.0.1:8000/user/login` 连接拒绝未完成。 |
| 发布证据就绪 | `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness` | 正常输出 `BLOCKED/NO_GO_STRICT`，列出 5 个缺失/阻断证据门。 |
| 发布证据强制门禁 | `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce` | 按设计在证据未齐时非零阻断。 |
| 交接包完整性 | `node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify` | 通过，检查 112 个文件，`issues=[]`。 |
| 发布制品完整性门禁 | `node scripts/ddd-release-artifact-integrity-gate-contract.test.mjs` | 通过。 |
| 发布配置同步门禁 | `node scripts/ddd-release-config-sync.test.mjs` | 通过。 |

## 6. 子代理复核

已使用子代理 `Raman` 对后端服务与公共库进行只读复核。补充发现与处置如下：

| ID | 风险 | 处置 |
|---|---|---|
| UPDATE-SOURCE-TRUST-001 | 更新清单源缺少显式可信源校验。 | 已增加 HTTPS 强制校验和主机 allowlist，保留镜像摘要钉扎。 |
| PLUGIN-CLEANUP-BOUNDARY-001 | 插件清理方法可对传入路径执行递归删除，缺少根边界。 | 已限制只能删除插件存储根/暂存根内的子路径，且禁止删除根本身。 |
| FORWARDED-IP-VALIDATION-001 | 受信代理头中的客户端 IP 未严格校验。 | 已要求 IP 字面量，拒绝伪造字符串和可解析主机名。 |

## 7. 风险保留与建议

| 风险 | 建议动作 |
|---|---|
| 生产环境未现场验证 | 在授权预生产或生产窗口执行 NIST SP 800-115 风格动态测试，覆盖认证、越权、文件上传、管理接口、代理链与日志告警。 |
| 外部资产未扫描 | 建立资产清单，明确域名、IP、端口、云资源和第三方回调地址后执行授权扫描。 |
| 运行态 E2E/DAST 未完成 | 启动本地或隔离环境的前端入口和后端 API，再执行 Playwright E2E、OWASP ZAP/等价 DAST 与业务流攻击路径验证。 |
| 生产证据仍为阻断 | 补齐 first-wave env receipt、lane completion receipt、owner evidence、production audit、final go/no-go 证据。 |
| 安全运营证据仍需现场补齐 | 补充日志留存、告警闭环、备份恢复演练、权限审计和应急响应记录。 |

## 8. 全量排查判定

可以判定：仓库代码、自动化测试门禁、发布证据门禁和已知安全发现整改的全量排查已完成。

不能判定：生产环境全量安全测评已完成。该结论仍需要真实环境授权、资产清单、外部扫描结果、运行态 E2E/DAST、现场访谈和安全运营证据支持。
