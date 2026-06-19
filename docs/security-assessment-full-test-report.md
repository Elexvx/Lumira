# Lumira 安全评估与全量测试报告

报告日期：2026-06-19

评估对象：`C:\Users\Administrator\Documents\GitHub\Lumira`

评估基线：`89381581`

已完成整改提交：`f9ff9e3a`、`dc3d17e1`、`72c08f54`、`d2b31294`、`982576f1`

当前补充证据：待提交

## 1. 结论

本轮已完成仓库内静态审计、自动化测试门禁、发布证据门禁和已知安全发现整改的全量排查。覆盖范围包括后端 17 个 Maven 模块、前端依赖锁定安装、lint、类型检查、单元测试、coverage、生产构建、smoke 测试、Dockerfile 静态契约、发布脚本门禁、交接包完整性，以及多轮子代理只读复核。原 11 项发现和子代理补充的 3 项发现均已完成代码整改；除 `DEPLOY-OPS-BACKUP-ENV-001` 属静态复核闭环外，其余发现项均有针对性回归测试。

本报告不能判定“生产环境全量安全测评已完成”。Docker CLI 与 daemon 已可用，`docker --version` 和 `docker info` 均成功；但镜像 build evidence 仍失败，失败点为基础镜像层拉取/缓存读取异常，包括 `unexpected EOF`、`short read` 和 `ETIMEDOUT`。因此容器运行态、Playwright E2E、DAST、外部资产扫描、真实第三方服务、云账号、日志平台、备份介质、生产更新源签名链等仍需在授权的本地隔离、预生产或生产窗口继续执行。相关条款在本报告中判定为“受限/未验证/阻断”。

## 2. 标准逐项对照矩阵

| 标准/方法 | 条款/测试项 | 要求摘要 | 适用性 | 验证方法 | 证据 ID | 判定 | 发现项/残余风险 |
|---|---|---|---|---|---|---|---|
| GB/T 22239-2019 | 身份鉴别 | 默认账号、登录、会话、令牌、二次校验应具备安全控制。 | 适用 | 代码审计、单元测试、后端全量测试 | E-BE-01、E-REG-01 | 部分符合 | `DEPLOY-OPS-ADMIN-DEFAULT-001`、`AUTH-IAM-001`、`AUTH-IAM-002` 已修复；生产登录策略需现场验证。 |
| GB/T 22239-2019 | 访问控制 | 用户、角色、数据权限、AI 工具、内部接口应按最小权限控制。 | 适用 | 子代理审计、权限回归测试 | E-BE-01、E-REG-01 | 部分符合 | `SQL-DATA-001`、`EXT-CALLBACK-001` 已修复；生产租户数据隔离需 E2E 验证。 |
| GB/T 22239-2019 | 安全审计 | 高危管理操作、认证事件、内部任务应留痕并可追溯。 | 适用 | 代码审计、测试检索 | E-SUB-01 | 受限 | 缺少所有高危管理接口均产生审计记录的系统化运行态证据。 |
| GB/T 22239-2019 | 入侵防范/恶意代码防范 | 上传、插件、更新链路应限制恶意输入和供应链污染。 | 适用 | 单元测试、插件/更新审计、Dockerfile 契约 | E-REG-01、E-DOCKER-01 | 部分符合 | `CAND-PLUGIN-PATH-001`、`PLUGIN-CLEANUP-BOUNDARY-001`、`UPDATE-SOURCE-TRUST-001` 已修复；真实恶意样本和 DAST 未完成。 |
| GB/T 22239-2019 | 数据完整性与保密性 | 机密配置、备份、字段加密、日志脱敏应防泄露。 | 适用 | 静态复核、配置测试 | E-REG-01、E-DOC-02 | 部分符合 | `DEPLOY-OPS-BACKUP-ENV-001` 静态闭环；日志平台和备份恢复需现场验证。 |
| GB/T 28449-2018 | 测评准备 | 明确范围、依据、资产和限制。 | 适用 | 文档审查 | E-DOC-01 | 部分符合 | 资产清单和授权边界仍需现场补齐。 |
| GB/T 28449-2018 | 方案执行与证据留存 | 每项测试应有命令、结果、环境和限制。 | 适用 | 命令执行、证据索引 | E-BE-01 到 E-DEPLOY-FAIL | 部分符合 | 本地证据完整；运行态证据受镜像构建和服务环境阻断。 |
| GB/T 28449-2018 | 结果判定与整改复测 | 发现项应有风险、整改、复测和残余风险。 | 适用 | 跟踪表、回归测试 | E-DOC-02、E-REG-01 | 部分符合 | 代码层闭环；生产验证状态仍为未验证。 |
| GB/T 20984-2022 | 资产识别 | 识别账号、数据、插件、文件、更新、部署和发布证据资产。 | 适用 | 架构/代码审计 | E-SUB-01 | 部分符合 | 外部资产、云资源、第三方服务资产清单缺失。 |
| GB/T 20984-2022 | 威胁/脆弱性/影响分析 | 结合攻击路径和影响判定风险等级。 | 适用 | 子代理审计、风险表 | E-SUB-01、E-DOC-02 | 部分符合 | 代码风险已评估；运行态威胁仍需 DAST/渗透。 |
| GB/T 20984-2022 | 风险处置 | 应给出整改、接受、规避或转移策略。 | 适用 | 整改跟踪表 | E-DOC-02 | 部分符合 | 阻断项需指定现场责任人与完成窗口。 |
| GB/T 30279-2020 | 漏洞分类分级 | 按利用条件、影响范围、危害程度和现有控制分级。 | 适用 | 发现项分级、复核 | E-DOC-02 | 部分符合 | 已按 Critical/High/Medium 等分级；需补生产可利用性验证。 |
| OWASP Testing Guide V4 | WSTG-ATHN/SESS | 认证、会话、令牌、验证码和强制改密。 | 适用 | 单元测试、代码审计 | E-BE-01、E-REG-01 | 部分符合 | E2E 登录链路未完成。 |
| OWASP Testing Guide V4 | WSTG-ATHZ | 角色、租户、对象级访问控制。 | 适用 | 单元测试、代码审计 | E-BE-01、E-SUB-01 | 部分符合 | 运行态越权测试未完成。 |
| OWASP Testing Guide V4 | WSTG-INPV/FILE | 输入校验、文件上传、插件包、zip 路径。 | 适用 | 单元测试、插件审计 | E-REG-01 | 部分符合 | polyglot、畸形 zip、压缩炸弹等运行态样本未完成。 |
| OWASP Testing Guide V4 | WSTG-CONF | 配置、默认密钥、CORS、管理接口、更新源。 | 适用 | 配置审计、测试 | E-REG-01、E-DOCKER-01 | 部分符合 | 真实部署配置未现场验证。 |
| OWASP Testing Guide V4 | WSTG-CLNT/API | 前端构建、接口形态、客户端状态与 API smoke。 | 适用 | 前端测试、构建、smoke | E-FE-01、E-FE-02 | 部分符合 | Playwright E2E 受运行态阻塞。 |
| MITRE ATT&CK Enterprise | T1190 初始访问 | Web/API 暴露面和管理接口滥用。 | 适用 | 代码审计、部署检查 | E-DEPLOY-FAIL | 受限 | 外部暴露面扫描和 DAST 未完成。 |
| MITRE ATT&CK Enterprise | T1059/T1105 执行与传输 | 插件、更新、内部任务可能成为执行/下载链路。 | 适用 | 插件/更新审计、测试 | E-REG-01、E-DOCKER-01 | 部分符合 | 真实更新源和插件沙箱运行态未验证。 |
| MITRE ATT&CK Enterprise | T1552 凭据暴露 | env、备份、日志、配置可能泄露凭据。 | 适用 | 静态审计、备份脚本复核 | E-DOC-02 | 部分符合 | 日志平台、备份介质现场验证未完成。 |
| NIST SP 800-115 | Planning | 规划范围、授权边界、测试目标。 | 适用 | 文档审查 | E-DOC-01 | 部分符合 | 缺真实授权窗口和资产清单。 |
| NIST SP 800-115 | Discovery | 识别服务、代码、脚本、发布制品和攻击面。 | 适用 | 子代理、搜索、测试清单 | E-SUB-01 | 部分符合 | 外部资产发现未完成。 |
| NIST SP 800-115 | Attack/Validation | 验证漏洞、攻击路径、运行态影响。 | 适用 | 单元/合约测试、尝试 E2E | E-BE-01、E-DEPLOY-FAIL | 受限 | E2E/DAST/渗透未完成。 |
| NIST SP 800-115 | Reporting/Remediation | 报告、整改、复测、残余风险。 | 适用 | 报告、跟踪表、提交记录 | E-DOC-01、E-DOC-02 | 部分符合 | 生产验证和风险接受待现场闭环。 |

## 3. 范围说明

已完成范围：

- 后端：`services/*`、`libs/*` 的 Maven 全量测试、安全回归和发行包构建。
- 前端：`frontend` 依赖锁定安装、lint、typecheck、单元测试、coverage、生产构建、smoke 测试。
- 脚本门禁：发布证据就绪、强制阻断、交接包完整性、发布制品完整性、配置同步门禁、Dockerfile 静态契约。
- 运维与发布：CI 门禁、updater 认证、发布证据完整性、镜像摘要要求、Docker build evidence 阻断证据。
- 子代理复核：后端、IAM、文件/插件、外部回调、部署运维、数据访问、标准矩阵和测试缺口复核。

未完成或受环境限制范围：

- 未对真实生产环境执行授权渗透测试。
- 未对互联网暴露资产执行外部扫描。
- 未连接真实第三方服务、云账号、日志平台或密钥管理平台做现场核验。
- Docker CLI/daemon 已可用，但 `node scripts/ddd-docker-build-evidence.mjs` 记录镜像构建证据 `FAIL`，失败点为 registry/network/build cache 的基础镜像层读取异常。
- 前端 Playwright E2E smoke 已安装 Chromium，但本机 `127.0.0.1:8000` 与 `127.0.0.1:8080` 未提供可访问运行态，E2E smoke 因连接拒绝未完成。
- `scripts/ddd-staging-execution-checklist.test.mjs` 全量脚本测试受重复 Node 进程干扰，已改用关键门禁命令复核并记录限制。

## 4. 问题闭环摘要

| 风险级别 | 数量 | 状态 |
|---|---:|---|
| Critical | 1 | 已修复并回归通过 |
| High | 7 | 已修复并回归通过 |
| Medium-High | 2 | 已修复并回归通过 |
| Medium | 3 | 2 项回归通过，1 项静态复核通过 |
| Low-Medium | 1 | 已修复并回归通过 |

## 5. 测试证据索引

| 证据 ID | 类别 | 命令 | 结果 |
|---|---|---|---|
| E-BE-01 | 后端全量测试 | `.\mvnw.cmd clean test` | 17 个 Maven 模块全部 `BUILD SUCCESS`，完成时间 2026-06-19 15:18:55，耗时 01:51，`system-service` 有 4 个既有 integration skip。 |
| E-BE-02 | 后端发行包 | `.\mvnw.cmd -pl services/lumira-server -am -DskipTests package` | 16 个模块全部 `BUILD SUCCESS`，完成时间 2026-06-19 15:29:25，耗时 33.622 s，生成 repackaged boot jar。 |
| E-REG-01 | 安全回归 | `ClientIpResolverTest`、`PluginArtifactLoaderTest`、`PlatformUpdateAppServiceTest` 等 | IP 校验 5 例、插件清理 3 例、更新源 3 例均通过。 |
| E-FE-01 | 前端基础测试 | `install --frozen-lockfile`、`lint`、`typecheck`、`test`、`test:smoke` | 均通过；单元测试 12 个文件、87 个用例通过。 |
| E-FE-02 | 前端 coverage | `corepack pnpm --dir frontend run test:coverage` | 12 个文件、87 个用例通过；语句 34.7%、分支 12.53%、函数 23.46%、行 35.22%，覆盖率偏低，列为质量改进项。 |
| E-FE-03 | 前端生产构建 | `corepack pnpm --dir frontend run build` | 通过，输出 `dist`，87 个 assets。 |
| E-DOCKER-01 | Docker 静态契约 | `node scripts/ddd-dockerfile-contract.test.mjs`、`node scripts/ddd-docker-evidence-contract.test.mjs` | 均通过。 |
| E-DOCKER-02 | Docker build evidence | `node scripts/ddd-docker-build-evidence.mjs` | 写入 `artifacts/ddd/build/docker-image-evidence.json`；Docker 29.5.3 preflight 通过，但 2 个镜像 build 均 `FAIL`，阻断为 `unexpected EOF`、`short read`、`ETIMEDOUT` 等 registry/network/build cache 异常。 |
| E-DDD-01 | DDD 证据合约 | `ddd-backend-evidence-contract.test.mjs`、`ddd-frontend-evidence-contract.test.mjs`、`ddd-frontend-smoke-contract.test.mjs` | 均通过。 |
| E-REL-01 | 发布证据就绪 | `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness` | 正常输出 `BLOCKED/NO_GO_STRICT`，列出 5 个缺失/阻断证据门。 |
| E-REL-02 | 发布证据强制门禁 | `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce` | 按设计在证据未齐时非零阻断。 |
| E-REL-03 | 交接包完整性 | `node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify` | 通过，检查 112 个文件，`issues=[]`。 |
| E-REL-04 | 发布制品/配置门禁 | `ddd-release-artifact-integrity-gate-contract.test.mjs`、`ddd-release-config-sync.test.mjs` | 均通过。 |
| E-DEPLOY-FAIL | 部署运行态检查 | `node scripts/check-deployment.mjs` | 失败：`127.0.0.1:8000/health`、`/api/health`、`127.0.0.1:8080/actuator/health` 均无 HTTP 响应。 |
| E-DOC-01 | 主报告 | `docs/security-assessment-full-test-report.md` | UTF-8 中文检查通过，无问号串、无替换字符、无 mojibake。 |
| E-DOC-02 | 整改跟踪表 | `docs/security-assessment-remediation-tracker.md` | UTF-8 中文检查通过，无问号串、无替换字符、无 mojibake。 |
| E-SUB-01 | 子代理审计 | Raman、Ampere、Poincare 等只读复核 | 发现并验证 3 项补充风险；指出标准逐项矩阵和测试缺口并已纳入本报告。 |

## 6. OWASP/MITRE/NIST 专项覆盖

| 专项 | 已覆盖 | 未覆盖/受限 |
|---|---|---|
| OWASP 认证与会话 | 默认管理员、refresh token、验证码、JWT/session 回归测试。 | 真实浏览器登录、强制改密、会话失效 E2E 未完成。 |
| OWASP 访问控制 | 角色权限、数据权限、AI 工具权限、内部接口令牌审计。 | 租户越权、对象级越权运行态测试未完成。 |
| OWASP 文件/输入 | 文件根路径、插件包路径、插件清理、更新源校验测试。 | polyglot、畸形 zip、压缩炸弹、恶意插件运行态样本未完成。 |
| OWASP 配置 | 生产配置校验、Dockerfile 静态契约、CORS/secret 规则审计。 | 真实部署配置、WAF、TLS、日志平台未现场验证。 |
| MITRE ATT&CK | T1190、T1059、T1105、T1552 的相关攻击路径已在代码层映射。 | 外部攻击面扫描、DAST、真实供应链与更新通道攻击演练未完成。 |
| NIST SP 800-115 | Planning、Discovery、Reporting、Remediation 在仓库范围有文档和证据。 | Attack/Validation 的运行态验证受镜像构建和环境阻塞。 |

## 7. 子代理复核发现

| ID | 风险 | 处置 |
|---|---|---|
| UPDATE-SOURCE-TRUST-001 | 更新清单源缺少显式可信源校验。 | 已增加 HTTPS 强制校验和主机 allowlist，保留镜像摘要钉扎。 |
| PLUGIN-CLEANUP-BOUNDARY-001 | 插件清理方法可对传入路径执行递归删除，缺少根边界。 | 已限制只能删除插件存储根/暂存根内的子路径，且禁止删除根本身。 |
| FORWARDED-IP-VALIDATION-001 | 受信代理头中的客户端 IP 未严格校验。 | 已要求 IP 字面量，拒绝伪造字符串和可解析主机名。 |

## 8. 环境限制与阻断项

| 阻断项 | 影响标准/测试 | 当前证据 | 完成条件 |
|---|---|---|---|
| Docker 镜像构建受 registry/network/build cache 异常阻断 | 容器部署、镜像 build/inspect、运行态健康检查 | E-DOCKER-02、E-DEPLOY-FAIL；Docker CLI/daemon 可用，但基础镜像层读取出现 `unexpected EOF`、`short read`、`ETIMEDOUT`。 | 使用稳定镜像仓库/本地镜像缓存/可信 CI Docker runner，重新执行 build evidence 与部署检查。 |
| 本地 8000/8080 无服务 | Playwright E2E、API smoke、DAST | E-DEPLOY-FAIL | 启动隔离环境或提供 `PLAYWRIGHT_BASE_URL`/API 地址。 |
| 生产授权缺失 | NIST Attack/Validation、外部扫描、DAST | 本报告范围声明 | 提供授权窗口、资产清单、测试边界、应急联系人。 |
| 发布证据 NO_GO_STRICT | 发布前证据门禁 | E-REL-01、E-REL-02 | 补齐 first-wave env receipt、lane receipt、owner evidence、production audit、final go/no-go。 |
| 前端覆盖率偏低 | 质量/回归充分性 | E-FE-02 | 为登录、请求层、权限、文件、AI/系统关键页面增加测试，提升覆盖率基线。 |

## 9. 全量排查判定

可以判定：仓库代码、自动化测试门禁、发布证据门禁、标准映射矩阵和已知安全发现整改的全量排查已完成到本机可验证边界。

不能判定：生产环境全量安全测评已完成。该结论仍需要真实环境授权、资产清单、外部扫描结果、运行态 E2E/DAST、镜像 build/inspect、现场访谈和安全运营证据支持。
