# Lumira 安全评估与全量测试报告

报告日期：2026-06-19

评估对象：`C:\Users\Administrator\Documents\GitHub\Lumira`

评估基线：`89381581`

已完成整改提交：`f9ff9e3a`、`dc3d17e1`、`72c08f54`、`d2b31294`、`982576f1`、`5310a365`、`c327aa6a`、`d23d4580`、`5c4a5501`、`d8392d64`、`d4322be6`、`9d63573e`、`ec75d416`、`c2da00ef`、`8b487153`、`982964d8`、`d51b4c5e`、`79cbdccc`

当前补充证据：本轮补充报告结构合约测试、lane completion receipt 自动填充辅助命令、P0 release env 填写清单和第一波 env 脱敏回执样例合约，自动校验 UTF-8 中文、21 条详细测评项、剩余阻断责任矩阵、关键标准和证据编号；发布汇总和生产证据就绪门禁仍显示 Docker 证据通道通过、总门禁 5/6 阻断、5 个生产证据门仍阻断，机器判定为 `NO_GO_STRICT`。

## 1. 结论

本轮已完成仓库内静态审计、自动化测试门禁、发布证据门禁和已知安全发现整改的全量排查。覆盖范围包括后端 17 个 Maven 模块、前端依赖锁定安装、代码规范检查、类型检查、单元测试、覆盖率统计、生产构建、冒烟测试、Dockerfile 静态契约、发布脚本门禁、交接包完整性，以及多轮子代理只读复核。原 11 项发现和子代理补充的 3 项发现均已完成代码整改；除 `DEPLOY-OPS-BACKUP-ENV-001` 属静态复核闭环外，其余发现项均有针对性回归测试。

本报告不能判定“生产环境全量安全测评已完成”。Docker 命令行与守护进程已可用，`docker --version`、`docker info`、Docker 只读证据检查和 `deploy-container --ps --local-mysql` 均成功；但镜像构建/启动证据仍受阻，失败点为基础镜像层拉取/缓存读取异常，包括 `unexpected EOF`、`short read`、`ETIMEDOUT` 和 busybox/base image pull timeout。因此容器运行态、Playwright 端到端测试、动态应用安全测试、外部资产扫描、真实第三方服务、云账号、日志平台、备份介质、生产更新源签名链等仍需在授权的本地隔离、预生产或生产窗口继续执行。相关条款在本报告中判定为“受限/未验证/阻断”。

## 2. 标准逐项对照矩阵

说明：下表按本仓库可验证范围，将国家标准和国际测试方法拆解到测评项级别。因缺少生产授权、真实资产清单和运行态环境，涉及现场访谈、外部扫描、渗透验证、生产安全运营证据的条目均保留为“部分符合”“受限”或“未验证”，不作为生产等保测评通过结论。

| 标准/方法 | 条款/测试项 | 要求摘要 | 适用性 | 验证方法 | 证据 ID | 判定 | 发现项/残余风险 |
|---|---|---|---|---|---|---|---|
| GB/T 22239-2019 | 身份鉴别 | 默认账号、登录、会话、令牌、二次校验应具备安全控制。 | 适用 | 代码审计、单元测试、后端全量测试 | E-BE-01、E-REG-01 | 部分符合 | `DEPLOY-OPS-ADMIN-DEFAULT-001`、`AUTH-IAM-001`、`AUTH-IAM-002` 已修复；生产登录策略需现场验证。 |
| GB/T 22239-2019 | 访问控制 | 用户、角色、数据权限、AI 工具、内部接口应按最小权限控制。 | 适用 | 子代理审计、权限回归测试 | E-BE-01、E-REG-01 | 部分符合 | `SQL-DATA-001`、`EXT-CALLBACK-001` 已修复；生产租户数据隔离需端到端验证。 |
| GB/T 22239-2019 | 安全审计 | 高危管理操作、认证事件、内部任务应留痕并可追溯。 | 适用 | 代码审计、测试检索 | E-SUB-01 | 受限 | 缺少所有高危管理接口均产生审计记录的系统化运行态证据。 |
| GB/T 22239-2019 | 入侵防范/恶意代码防范 | 上传、插件、更新链路应限制恶意输入和供应链污染。 | 适用 | 单元测试、插件/更新审计、Dockerfile 契约 | E-REG-01、E-DOCKER-01 | 部分符合 | `CAND-PLUGIN-PATH-001`、`PLUGIN-CLEANUP-BOUNDARY-001`、`UPDATE-SOURCE-TRUST-001` 已修复；真实恶意样本和动态应用安全测试未完成。 |
| GB/T 22239-2019 | 数据完整性与保密性 | 机密配置、备份、字段加密、日志脱敏应防泄露。 | 适用 | 静态复核、配置测试 | E-REG-01、E-DOC-02 | 部分符合 | `DEPLOY-OPS-BACKUP-ENV-001` 静态闭环；日志平台和备份恢复需现场验证。 |
| GB/T 28449-2018 | 测评准备 | 明确范围、依据、资产和限制。 | 适用 | 文档审查 | E-DOC-01 | 部分符合 | 资产清单和授权边界仍需现场补齐。 |
| GB/T 28449-2018 | 方案执行与证据留存 | 每项测试应有命令、结果、环境和限制。 | 适用 | 命令执行、证据索引 | E-BE-01 到 E-DEPLOY-FAIL | 部分符合 | 本地证据完整；运行态证据受镜像构建和服务环境阻断。 |
| GB/T 28449-2018 | 结果判定与整改复测 | 发现项应有风险、整改、复测和残余风险。 | 适用 | 跟踪表、回归测试 | E-DOC-02、E-REG-01 | 部分符合 | 代码层闭环；生产验证状态仍为未验证。 |
| GB/T 20984-2022 | 资产识别 | 识别账号、数据、插件、文件、更新、部署和发布证据资产。 | 适用 | 架构/代码审计 | E-SUB-01 | 部分符合 | 外部资产、云资源、第三方服务资产清单缺失。 |
| GB/T 20984-2022 | 威胁/脆弱性/影响分析 | 结合攻击路径和影响判定风险等级。 | 适用 | 子代理审计、风险表 | E-SUB-01、E-DOC-02 | 部分符合 | 代码风险已评估；运行态威胁仍需动态应用安全测试和渗透验证。 |
| GB/T 20984-2022 | 风险处置 | 应给出整改、接受、规避或转移策略。 | 适用 | 整改跟踪表 | E-DOC-02 | 部分符合 | 阻断项需指定现场责任人与完成窗口。 |
| GB/T 30279-2020 | 漏洞分类分级 | 按利用条件、影响范围、危害程度和现有控制分级。 | 适用 | 发现项分级、复核 | E-DOC-02 | 部分符合 | 已按严重、高、中高、中、低中分级；需补生产可利用性验证。 |
| OWASP Testing Guide V4 | WSTG-ATHN/SESS | 认证、会话、令牌、验证码和强制改密。 | 适用 | 单元测试、代码审计 | E-BE-01、E-REG-01 | 部分符合 | 端到端登录链路未完成。 |
| OWASP Testing Guide V4 | WSTG-ATHZ | 角色、租户、对象级访问控制。 | 适用 | 单元测试、代码审计 | E-BE-01、E-SUB-01 | 部分符合 | 运行态越权测试未完成。 |
| OWASP Testing Guide V4 | WSTG-INPV/FILE | 输入校验、文件上传、插件包、zip 路径。 | 适用 | 单元测试、插件审计 | E-REG-01 | 部分符合 | polyglot、畸形 zip、压缩炸弹等运行态样本未完成。 |
| OWASP Testing Guide V4 | WSTG-CONF | 配置、默认密钥、CORS、管理接口、更新源。 | 适用 | 配置审计、测试 | E-REG-01、E-DOCKER-01 | 部分符合 | 真实部署配置未现场验证。 |
| OWASP Testing Guide V4 | WSTG-CLNT/API | 前端构建、接口形态、客户端状态与接口冒烟测试。 | 适用 | 前端测试、构建、冒烟测试 | E-FE-01、E-FE-02 | 部分符合 | Playwright 端到端测试受运行态阻塞。 |
| MITRE ATT&CK Enterprise | T1190 初始访问 | Web/API 暴露面和管理接口滥用。 | 适用 | 代码审计、部署检查 | E-DEPLOY-FAIL | 受限 | 外部暴露面扫描和动态应用安全测试未完成。 |
| MITRE ATT&CK Enterprise | T1059/T1105 执行与传输 | 插件、更新、内部任务可能成为执行/下载链路。 | 适用 | 插件/更新审计、测试 | E-REG-01、E-DOCKER-01 | 部分符合 | 真实更新源和插件沙箱运行态未验证。 |
| MITRE ATT&CK Enterprise | T1552 凭据暴露 | env、备份、日志、配置可能泄露凭据。 | 适用 | 静态审计、备份脚本复核 | E-DOC-02 | 部分符合 | 日志平台、备份介质现场验证未完成。 |
| NIST SP 800-115 | Planning | 规划范围、授权边界、测试目标。 | 适用 | 文档审查 | E-DOC-01 | 部分符合 | 缺真实授权窗口和资产清单。 |
| NIST SP 800-115 | Discovery | 识别服务、代码、脚本、发布制品和攻击面。 | 适用 | 子代理、搜索、测试清单 | E-SUB-01 | 部分符合 | 外部资产发现未完成。 |
| NIST SP 800-115 | Attack/Validation | 验证漏洞、攻击路径、运行态影响。 | 适用 | 单元/合约测试、尝试端到端测试 | E-BE-01、E-DEPLOY-FAIL | 受限 | 端到端测试、动态应用安全测试和渗透验证未完成。 |
| NIST SP 800-115 | Reporting/Remediation | 报告、整改、复测、残余风险。 | 适用 | 报告、跟踪表、提交记录 | E-DOC-01、E-DOC-02 | 部分符合 | 生产验证和风险接受待现场闭环。 |

## 3. 详细测评项清单

| 序号 | 依据 | 测评项 | 当前执行结果 | 证据 | 未闭环条件 |
|---:|---|---|---|---|---|
| 1 | GB/T 22239-2019 | 身份鉴别：默认管理员、登录、验证码、刷新令牌、会话失效。 | 代码层已整改并回归通过。 | E-BE-01、E-REG-01、E-DOC-02 | 需真实浏览器登录、强制改密、会话失效端到端验证。 |
| 2 | GB/T 22239-2019 | 访问控制：角色、租户、对象级授权、AI 工具权限、内部接口令牌。 | 代码层已整改并回归通过。 | E-BE-01、E-SUB-01、E-DOC-02 | 需运行态越权样本、租户隔离和对象级访问验证。 |
| 3 | GB/T 22239-2019 | 安全审计：认证事件、管理操作、内部任务、更新操作留痕。 | 静态复核覆盖关键风险点。 | E-SUB-01 | 需接入真实日志平台并验证告警、留存、追溯链路。 |
| 4 | GB/T 22239-2019 | 入侵防范和恶意代码防范：上传、插件包、更新清单、压缩包路径。 | 插件路径、清理边界、更新源信任已整改。 | E-REG-01、E-DOCKER-01 | 需恶意样本、动态应用安全测试和插件隔离运行态验证。 |
| 5 | GB/T 22239-2019 | 数据完整性与保密性：密钥、配置、备份、日志脱敏。 | 备份脚本和配置泄露风险已静态闭环。 | E-DOC-02 | 需备份恢复演练、介质权限和日志脱敏现场验证。 |
| 6 | GB/T 22239-2019 | 安全运维：部署脚本、更新器认证、容器镜像、发布门禁。 | Windows Docker/Compose 调用已修复；发布门禁仍严格不放行。 | E-DOCKER-03、E-DEPLOY-PS、E-REL-01 | 需生产等价环境、镜像构建/启动证据和最终放行证据。 |
| 7 | GB/T 28449-2018 | 测评准备：测评对象、边界、依据、工具、人员分工。 | 仓库范围和依据已在报告记录。 | E-DOC-01 | 需正式授权书、资产清单、测试窗口和应急联系人。 |
| 8 | GB/T 28449-2018 | 方案编制：测试路径、命令、预期结果、证据保全方式。 | 本地命令和证据索引已形成。 | E-DOC-01、E-REL-03 | 需现场版本、生产等价环境和外部扫描方案确认。 |
| 9 | GB/T 28449-2018 | 测评实施：静态审计、自动化测试、部署检查、运行态验证。 | 仓库内静态和自动化测试已执行；运行态验证阻断。 | E-BE-01 到 E-DEPLOY-START-BLOCKED | 需可访问部署环境、测试账号和目标 URL。 |
| 10 | GB/T 28449-2018 | 结果判定：发现项、风险等级、证据、复测结论。 | 已形成整改跟踪表和风险分级。 | E-DOC-02 | 需生产验证结果和风险接受签署。 |
| 11 | GB/T 20984-2022 | 资产识别：账号、数据、插件、文件、更新、部署、发布证据。 | 仓库资产已覆盖，外部资产缺失。 | E-SUB-01、E-DOC-01 | 需云资源、域名、第三方服务、日志平台和密钥平台清单。 |
| 12 | GB/T 20984-2022 | 威胁识别：初始访问、越权、供应链、凭据泄露、文件边界。 | 已结合代码审计和 ATT&CK 路径识别。 | E-SUB-01、E-DOC-02 | 需真实暴露面和攻击路径验证。 |
| 13 | GB/T 20984-2022 | 脆弱性识别：代码缺陷、配置缺陷、部署缺陷、证据缺口。 | 14 项发现已整改或静态闭环；发布证据缺口保留。 | E-DOC-02、E-REL-05 | 需补齐 release env、运行态、回滚、迁移和 explain 证据。 |
| 14 | GB/T 20984-2022 | 风险分析与处置：影响、可能性、残余风险、处置策略。 | 已给出风险等级和完成条件。 | E-DOC-02 | 需现场责任人和完成窗口确认。 |
| 15 | GB/T 30279-2020 | 漏洞分类：身份鉴别、访问控制、文件边界、供应链、运维配置。 | 已按发现项归类。 | E-DOC-02 | 需生产可利用性复测支撑最终等级。 |
| 16 | GB/T 30279-2020 | 漏洞分级：利用条件、影响范围、危害程度、现有控制。 | 已分为严重、高、中高、中、低中。 | E-DOC-02 | 需结合真实部署暴露面复核等级。 |
| 17 | OWASP Testing Guide V4 | 认证与会话测试：登录、验证码、令牌、会话生命周期。 | 单元和服务层回归通过。 | E-BE-01、E-REG-01 | 需浏览器端到端测试。 |
| 18 | OWASP Testing Guide V4 | 授权测试：角色、对象级、租户级、内部 API。 | 代码层回归通过。 | E-BE-01、E-SUB-01 | 需运行态越权测试。 |
| 19 | OWASP Testing Guide V4 | 输入、文件和配置测试：上传、zip、插件、CORS、默认配置。 | 关键代码缺陷已修复。 | E-REG-01、E-DOCKER-01 | 需动态样本、真实配置和动态应用安全测试。 |
| 20 | MITRE ATT&CK Enterprise | 初始访问、执行、命令控制、凭据暴露相关技术路径。 | 已映射 T1190、T1059、T1105、T1552。 | E-SUB-01、E-DOC-02 | 需外部攻击面和运行态攻击路径验证。 |
| 21 | NIST SP 800-115 | 规划、发现、攻击/验证、报告和整改。 | 规划、发现、报告、整改在仓库范围已覆盖；攻击/验证受限。 | E-DOC-01、E-DOC-02、E-DEPLOY-FAIL | 需授权环境、端到端测试、动态应用安全测试和渗透验证。 |

## 4. 范围说明

已完成范围：

- 后端：`services/*`、`libs/*` 的 Maven 全量测试、安全回归和发行包构建。
- 前端：`frontend` 依赖锁定安装、代码规范检查、类型检查、单元测试、覆盖率统计、生产构建、冒烟测试。
- 脚本门禁：发布证据就绪、强制阻断、交接包完整性、发布制品完整性、配置同步门禁、Dockerfile 静态契约。
- 运维与发布：持续集成门禁、更新器认证、发布证据完整性、镜像摘要要求、Docker 构建证据阻断记录。
- 子代理复核：后端、IAM、文件/插件、外部回调、部署运维、数据访问、标准矩阵和测试缺口复核。

未完成或受环境限制范围：

- 未对真实生产环境执行授权渗透测试。
- 未对互联网暴露资产执行外部扫描。
- 未连接真实第三方服务、云账号、日志平台或密钥管理平台做现场核验。
- Docker 命令行/守护进程已可用，`node scripts/ddd-docker-build-evidence.mjs --check`、`node scripts/deploy-container.mjs --ps --local-mysql` 已通过；但 `node scripts/ddd-docker-build-evidence.mjs` 记录镜像构建证据为失败，`node scripts/start-platform.mjs --skip-build --local-mysql --skip-check` 仍受镜像拉取超时阻断。
- 前端 Playwright 端到端冒烟测试所需 Chromium 已安装；当前本机 `127.0.0.1:8000` 与 `127.0.0.1:8080` 未提供可访问运行态，因此 2026-06-19 未形成新的端到端运行态通过证据。仓库中旧的 Playwright 成功结果仅能证明 2026-06-16 的旧环境结果。
- `scripts/ddd-staging-execution-checklist.test.mjs` 全量脚本测试受重复 Node 进程干扰，已改用关键门禁命令复核并记录限制。

## 5. 问题闭环摘要

| 风险级别 | 数量 | 状态 |
|---|---:|---|
| 严重 | 1 | 已修复并回归通过 |
| 高 | 7 | 已修复并回归通过 |
| 中高 | 2 | 已修复并回归通过 |
| 中 | 3 | 2 项回归通过，1 项静态复核通过 |
| 低中 | 1 | 已修复并回归通过 |

## 6. 测试证据索引

| 证据 ID | 类别 | 命令 | 结果 |
|---|---|---|---|
| E-BE-01 | 后端全量测试 | `.\mvnw.cmd clean test` | 命令输出显示 17 个 Maven 模块全部构建成功，完成时间 2026-06-19 15:18:55，耗时 01:51，`system-service` 有 4 个既有集成测试跳过；现有落盘证据文件生成时间较早，需以命令输出记录为本轮证据。 |
| E-BE-02 | 后端发行包 | `.\mvnw.cmd -pl services/lumira-server -am -DskipTests package` | 命令输出显示 16 个模块全部构建成功，完成时间 2026-06-19 15:29:25，耗时 33.622 s，生成重新打包的启动 jar；现有落盘证据文件生成时间较早，需以命令输出记录为本轮证据。 |
| E-REG-01 | 安全回归 | `ClientIpResolverTest`、`PluginArtifactLoaderTest`、`PlatformUpdateAppServiceTest` 等 | IP 校验 5 例、插件清理 3 例、更新源 3 例均通过。 |
| E-FE-01 | 前端基础测试 | `install --frozen-lockfile`、代码规范检查、类型检查、单元测试、冒烟测试 | 本轮命令输出显示通过；单元测试 12 个文件、87 个用例通过。旧证据文件生成时间较早，不能单独证明 2026-06-19 结果。 |
| E-FE-02 | 前端覆盖率 | `corepack pnpm --dir frontend run test:coverage` | 本轮命令输出显示 12 个文件、87 个用例通过；语句 34.7%、分支 12.53%、函数 23.46%、行 35.22%，覆盖率偏低，列为质量改进项。 |
| E-FE-03 | 前端生产构建 | `corepack pnpm --dir frontend run build` | 本轮命令输出显示通过；旧落盘构建证据显示输出 90 个资源文件，不能用 87 个资源文件作为当前证据。 |
| E-DOCKER-01 | Docker 静态契约 | `node scripts/ddd-dockerfile-contract.test.mjs`、`node scripts/ddd-docker-evidence-contract.test.mjs` | 均通过。 |
| E-DOCKER-02 | Docker 构建证据 | `node scripts/ddd-docker-build-evidence.mjs` | 写入 `artifacts/ddd/build/docker-image-evidence.json`；Docker 29.5.3 预检通过，但 2 个镜像构建均失败，阻断为 `unexpected EOF`、`short read`、`ETIMEDOUT` 等镜像仓库/网络/构建缓存异常。 |
| E-DOCKER-03 | Docker 只读检查 | `node scripts/ddd-docker-build-evidence.mjs --check` | 通过；Docker 可用，两个 Dockerfile 静态检查通过，推荐模式为“构建并检查”。 |
| E-DDD-01 | DDD 证据合约 | `ddd-backend-evidence-contract.test.mjs`、`ddd-frontend-evidence-contract.test.mjs`、`ddd-frontend-smoke-contract.test.mjs` | 均通过。 |
| E-REL-01 | 发布证据就绪 | `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness` | 正常输出“阻断/严格不放行”，列出 5 个缺失/阻断证据门；当前 Docker 证据通道已在汇总和队列中转为通过，总门禁为 5/6 阻断。 |
| E-REL-02 | 发布证据强制门禁 | `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce` | 按设计在证据未齐时非零阻断。 |
| E-REL-03 | 交接包完整性 | `node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle`、`--handoff-bundle-verify` | 本轮重新生成交接包，写入 113 个文件；验证通过，检查 112 个文件，问题列表为空。 |
| E-REL-04 | 发布制品/配置门禁 | `ddd-release-artifact-integrity-gate-contract.test.mjs`、`ddd-release-config-sync.test.mjs` | 均通过。 |
| E-REL-05 | 生产证据就绪轻量回归 | `node scripts/ddd-production-evidence-readiness.test.mjs` | 通过；覆盖生产解阻计划、生产证据就绪 JSON、交接包完整性，并确认当前 5 个生产证据门仍处于阻断状态。 |
| E-REL-06 | lane completion receipt 自动填充辅助 | `node scripts/ddd-lane-completion-receipt-autofill.test.mjs`、`node --check scripts/ddd-staging-execution-checklist.mjs` | 通过；owner evidence intake 中 PASS 的 lane 可预填 receipt，并在命令列表、提交计划、交接包模板和 owner packet 中展示下一步命令，但不绕过最终提交检查。 |
| E-REL-07 | P0 release env 填写清单 | `node scripts/ddd-release-env-fill-checklist.test.mjs`、`node scripts/ddd-release-env-fill-checklist.mjs --json` | 通过；当前真实落盘证据提取到 55 个 primary blocker key、63 个 config blocker，并按 runtime、database、security、evidence、AI、jobs、other 分组生成 Markdown/JSON 填写清单，帮助 release-infra 补齐 `release-env`，但不包含 secret 值且不替代 lint/config evidence。 |
| E-REL-08 | 第一波 env 脱敏回执样例合约 | `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=artifacts/ddd/release/staging-handoff-bundle/next-action-env-receipt.sample.json` | 结构合约通过；样例为 redacted=true、3 条 lane、5 条 pass criteria，receiptStatus 仍为 BLOCKED，用于展示安全回执形态，不作为真实 env 通过证据。 |
| E-DEPLOY-FAIL | 部署运行态检查 | `node scripts/check-deployment.mjs` | 失败：`127.0.0.1:8000/health`、`/api/health`、`127.0.0.1:8080/actuator/health` 均无 HTTP 响应。 |
| E-DEPLOY-PS | Compose 部署脚本探测 | `node scripts/deploy-container.mjs --ps --local-mysql` | 通过；修复 Windows 下 Docker/Compose 调用、目录准备和 compose 相对路径后，可正常输出 compose `ps`。 |
| E-DEPLOY-START-BLOCKED | 本地 compose 启动尝试 | `node scripts/start-platform.mjs --skip-build --local-mysql --skip-check` | 启动进入 Docker volume/image 阶段，但 busybox/base image 拉取超时，未形成可访问运行态。 |
| E-DOC-01 | 主报告 | `docs/security-assessment-full-test-report.md` | UTF-8 中文检查命令通过，无连续问号串、无替换字符、无常见乱码片段。 |
| E-DOC-02 | 整改跟踪表 | `docs/security-assessment-remediation-tracker.md` | UTF-8 中文检查命令通过，无连续问号串、无替换字符、无常见乱码片段。 |
| E-DOC-03 | 报告结构合约 | `node scripts/security-assessment-report-contract.test.mjs` | 通过；验证 UTF-8 中文、21 条详细测评项、剩余阻断责任矩阵、标准名称、关键证据 ID 和阻断摘要。 |
| E-SUB-01 | 子代理审计 | 多轮只读复核 | 发现并验证 3 项补充风险；本轮再次指出标准映射、证据时间、交接包状态和中文化缺口，已纳入本报告。 |

## 7. OWASP/MITRE/NIST 专项覆盖

| 专项 | 已覆盖 | 未覆盖/受限 |
|---|---|---|
| OWASP 认证与会话 | 默认管理员、刷新令牌、验证码、令牌/会话回归测试。 | 真实浏览器登录、强制改密、会话失效端到端测试未完成。 |
| OWASP 访问控制 | 角色权限、数据权限、AI 工具权限、内部接口令牌审计。 | 租户越权、对象级越权运行态测试未完成。 |
| OWASP 文件/输入 | 文件根路径、插件包路径、插件清理、更新源校验测试。 | polyglot、畸形 zip、压缩炸弹、恶意插件运行态样本未完成。 |
| OWASP 配置 | 生产配置校验、Dockerfile 静态契约、CORS/secret 规则审计。 | 真实部署配置、WAF、TLS、日志平台未现场验证。 |
| MITRE ATT&CK | T1190、T1059、T1105、T1552 的相关攻击路径已在代码层映射。 | 外部攻击面扫描、动态应用安全测试、真实供应链与更新通道攻击演练未完成。 |
| NIST SP 800-115 | Planning、Discovery、Reporting、Remediation 在仓库范围有文档和证据。 | Attack/Validation 的运行态验证受镜像构建和环境阻塞。 |

## 8. 子代理复核发现

| ID | 风险 | 处置 |
|---|---|---|
| UPDATE-SOURCE-TRUST-001 | 更新清单源缺少显式可信源校验。 | 已增加 HTTPS 强制校验和主机 allowlist，保留镜像摘要钉扎。 |
| PLUGIN-CLEANUP-BOUNDARY-001 | 插件清理方法可对传入路径执行递归删除，缺少根边界。 | 已限制只能删除插件存储根/暂存根内的子路径，且禁止删除根本身。 |
| FORWARDED-IP-VALIDATION-001 | 受信代理头中的客户端 IP 未严格校验。 | 已要求 IP 字面量，拒绝伪造字符串和可解析主机名。 |

## 9. 环境限制与阻断项

| 阻断项 | 影响标准/测试 | 当前证据 | 完成条件 |
|---|---|---|---|
| Docker 镜像构建/拉取受镜像仓库、网络或构建缓存异常阻断 | 容器部署、镜像构建/检查、运行态健康检查 | E-DOCKER-02、E-DOCKER-03、E-DEPLOY-PS、E-DEPLOY-START-BLOCKED；Docker 命令行/守护进程与 compose `ps` 可用，但基础镜像层读取/拉取出现 `unexpected EOF`、`short read`、`ETIMEDOUT`。 | 使用稳定镜像仓库/本地镜像缓存/可信持续集成 Docker 执行器，重新执行构建/启动证据与部署检查。 |
| 本地 8000/8080 无服务 | Playwright 端到端测试、接口冒烟测试、动态应用安全测试 | E-DEPLOY-FAIL | 启动隔离环境或提供 `PLAYWRIGHT_BASE_URL`/接口地址。 |
| 生产授权缺失 | NIST 攻击/验证阶段、外部扫描、动态应用安全测试 | 本报告范围声明 | 提供授权窗口、资产清单、测试边界、应急联系人。 |
| 发布证据严格不放行 | 发布前证据门禁 | E-REL-01、E-REL-02 | 补齐第一波环境回执、通道完成回执、责任人证据、生产切换审计、最终放行/不放行结论。 |
| 前端覆盖率偏低 | 质量/回归充分性 | E-FE-02 | 为登录、请求层、权限、文件、AI/系统关键页面增加测试，提升覆盖率基线。 |

## 10. 剩余阻断责任矩阵

| 阻断门 | 责任方 | 关键缺口 | 下一条命令 | 完成信号 |
|---|---|---|---|---|
| release-env | release-infra | `DDD_RELEASE_ENV_FILE` 指向的生产等价安全环境文件缺失或不安全。 | `DDD_RELEASE_ENV_FILE=<release-env-file> node scripts/ddd-release-env-file-lint.mjs` | release env lint 无阻断项，第一波环境回执合约通过。 |
| runtime-business | release-infra、frontend、ai、file-owner、job-owner、payment-owner | `LUMIRA_BASE_URL`、`PLAYWRIGHT_BASE_URL`、部署证据、前端/AI/认证性能部署证据缺失。 | `node scripts/ddd-staging-runtime-check.mjs` | 运行态业务检查通过，接口、前端、AI、文件、任务、支付证据均被接收。 |
| rollback | bounded-context owners | 缺少回滚演练文件或正式延期文件，以及环境、候选版本、操作者证据。 | `node scripts/ddd-staging-data-safety-check.mjs` | 回滚演练或延期证据通过数据安全检查。 |
| migration | database | 缺少 fresh/upgrade 数据库迁移验证、证据文件、环境、操作者和完成时间。 | `node scripts/ddd-staging-data-safety-check.mjs` | 迁移验证项全部通过，fresh/upgrade 证据可追溯。 |
| explain | database | 缺少 `DDD_EXPLAIN_DATABASE`、MySQL 连接信息和 explain 产物。 | `node scripts/ddd-staging-data-safety-check.mjs` | explain 计划与性能证据生成并通过检查。 |
| first-wave-env-receipt | release-infra | 第一波环境输入文件和脱敏回执缺失。 | `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt --next-action-env-file=<env-file> --next-action-env-receipt-output=<receipt-file>` | `--next-action-env-receipt-contract` 通过。 |
| lane-completion-receipt | release-owner | 5 条 owner lane 的完成回执缺失，当前覆盖率 0/5。 | `node scripts/ddd-staging-execution-checklist.mjs --lane-completion-receipt-init --lane-completion-receipt-output=<receipt-file>` | `--lane-completion-submission-check` 显示 dispatchReady=true 且覆盖 5/5。 |
| owner-evidence | platform-owners | owner evidence intake 仍有 2 个缺失 artifact、54 个阻断输入。 | `node scripts/ddd-staging-execution-checklist.mjs --owner-evidence-intake-markdown` | owner evidence intake 无必需 artifact 缺失。 |
| production-audit | release-owner | 生产切换审计仍有 5 个阻断审计项。 | `node scripts/ddd-staging-execution-checklist.mjs --production-cutover-audit` | production cutover audit 的 blockedAuditItems 为 0。 |
| final-go-no-go | release-owner | final review 和严格 go/no-go 均未放行。 | `node scripts/ddd-staging-execution-checklist.mjs --final-review-enforce --lane-completion-receipt-file=<receipt-file>` | final review 输出 GO_STRICT 且 `cutoverAllowed=true`。 |

当前阻断输入汇总：`node scripts/ddd-staging-execution-checklist.mjs --blocking-inputs-markdown` 输出 32 个阻断输入、5 个阻断 gate；`node scripts/ddd-staging-execution-checklist.mjs --production-unblock-plan-markdown` 输出 3 条并行工作流和 5 个阻断审计项。

## 11. 全量排查判定

可以判定：仓库代码、自动化测试门禁、发布证据门禁、标准映射矩阵和已知安全发现整改的全量排查已完成到本机可验证边界。

不能判定：生产环境全量安全测评已完成。该结论仍需要真实环境授权、资产清单、外部扫描结果、运行态端到端测试、动态应用安全测试、镜像构建/检查、现场访谈和安全运营证据支持。
