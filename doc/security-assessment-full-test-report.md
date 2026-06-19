# Lumira 安全评估与全量测试报告

报告日期：2026-06-20

评估对象：`C:\Users\Administrator\Documents\GitHub\Lumira`

代码基线：已先提交快照 `210188b8 chore: snapshot current workspace changes`，本报告记录该基线之后本轮修复和回归结果。

评估依据：GB/T 22239-2019、GB/T 28449-2018、GB/T 20984-2022、GB/T 30279-2020、OWASP Testing Guide V4、MITRE ATT&CK Matrix for Enterprise、NIST SP 800-115。

## 1. 结论

本轮已完成仓库内可自动化验证范围的一轮全量排查：后端全量 Maven 测试通过，前端单元测试、类型检查、覆盖率测试和生产构建通过，报告合约通过，官网/CMS 类残留在非构建产物范围内未发现命中。已修复报告乱码、Java BOM/源码编码、DDD 表归属旧模块名、前端构建后处理脚本路径和发布证据合约断言问题。

不能判定生产环境全量安全测评已完成。生产授权、外部资产清单、真实运行环境、动态应用安全测试、端到端业务攻击路径、日志平台、备份介质、云账号和第三方服务仍需要在授权环境中补齐。当前机器判定保持 `NO_GO_STRICT`，不作为生产上线放行依据。

## 2. 标准逐项对照矩阵

| 标准/方法 | 条款/测试项 | 验证方法 | 证据 | 判定 | 残余风险 |
|---|---|---|---|---|---|
| GB/T 22239-2019 | 身份鉴别 | 代码审计、后端测试 | E-BE-01 | 部分符合 | 真实浏览器登录、强制改密和会话失效需运行态验证 |
| GB/T 22239-2019 | 访问控制 | 权限回归、架构边界测试 | E-BE-01、E-SUB-01 | 部分符合 | 租户隔离和对象级越权需端到端验证 |
| GB/T 22239-2019 | 安全审计 | 日志设计与测试复核 | E-BE-01 | 受限 | 真实日志平台、告警和留存证据缺失 |
| GB/T 22239-2019 | 入侵防范/恶意代码防范 | 文件、插件、更新链路测试 | E-BE-01、E-REG-01 | 部分符合 | 恶意样本和动态应用安全测试未完成 |
| GB/T 28449-2018 | 测评准备、实施、复核 | 报告、证据索引、命令回归 | E-DOC-01、E-DOC-02、E-DOC-03 | 部分符合 | 授权书、资产清单和测试窗口待补齐 |
| GB/T 20984-2022 | 风险识别、分析、处置 | 风险表、整改跟踪、阻断矩阵 | E-DOC-02、E-REL-01 | 部分符合 | 生产可利用性和影响需现场复核 |
| GB/T 30279-2020 | 漏洞分类分级 | 严重/高/中高/中/低中分级 | E-DOC-02 | 部分符合 | 最终等级需结合真实暴露面 |
| OWASP Testing Guide V4 | WSTG-ATHN/SESS/ATHZ/INPV/CONF/CLNT | 单元、集成、构建和静态审计 | E-BE-01、E-FE-01、E-FE-03 | 部分符合 | Playwright、DAST 和运行态越权测试未完成 |
| MITRE ATT&CK | T1190、T1059、T1105、T1552 | 攻击路径映射与代码复核 | E-DOC-02 | 部分符合 | 外部攻击面扫描和供应链演练未完成 |
| NIST SP 800-115 | Planning、Discovery、Attack、Reporting | 规划、发现、报告和整改回归 | E-DOC-01、E-DOC-02 | 部分符合 | Attack/Validation 阶段需授权环境 |

## 3. 详细测评项清单

| 序号 | 依据 | 测评项 | 当前执行结果 | 证据 | 未闭环条件 |
|---:|---|---|---|---|---|
| 1 | GB/T 22239-2019 | 身份鉴别：默认管理员、登录、验证码、刷新令牌、会话失效 | 代码层回归通过 | E-BE-01 | 浏览器端到端验证 |
| 2 | GB/T 22239-2019 | 访问控制：角色、租户、对象级授权、内部接口令牌 | 代码层回归通过 | E-BE-01 | 运行态越权测试 |
| 3 | GB/T 22239-2019 | 安全审计：认证事件、管理操作、内部任务留痕 | 静态复核覆盖 | E-DOC-02 | 日志平台验证 |
| 4 | GB/T 22239-2019 | 入侵防范：上传、插件包、zip 路径和更新源 | 插件与文件回归通过 | E-BE-01 | 恶意样本验证 |
| 5 | GB/T 22239-2019 | 数据保密：机密配置、备份、日志脱敏 | 静态闭环 | E-DOC-02 | 备份恢复演练 |
| 6 | GB/T 22239-2019 | 安全运维：部署脚本、更新器认证、发布门禁 | 发布门禁仍阻断 | E-REL-01 | 补齐生产证据 |
| 7 | GB/T 28449-2018 | 测评准备：范围、依据、工具、人员分工 | 仓库范围已记录 | E-DOC-01 | 正式授权和资产清单 |
| 8 | GB/T 28449-2018 | 方案编制：命令、预期、证据保全 | 本地命令已记录 | E-DOC-03 | 现场方案确认 |
| 9 | GB/T 28449-2018 | 测评实施：静态审计、自动化测试、部署检查 | 仓库内自动化通过 | E-BE-01、E-FE-03 | 运行态验证 |
| 10 | GB/T 28449-2018 | 结果判定：风险、整改、复测 | 整改跟踪表已更新 | E-DOC-02 | 生产复核签署 |
| 11 | GB/T 20984-2022 | 资产识别：账号、数据、插件、文件、发布证据 | 仓库资产已覆盖 | E-DOC-01 | 外部资产清单 |
| 12 | GB/T 20984-2022 | 威胁识别：初始访问、越权、供应链、凭据泄露 | 已映射 ATT&CK | E-DOC-02 | 真实攻击路径验证 |
| 13 | GB/T 20984-2022 | 脆弱性识别：代码、配置、部署、证据缺口 | 已知代码项回归 | E-BE-01、E-REL-05 | release env 和运行态证据 |
| 14 | GB/T 20984-2022 | 风险处置：整改、接受、规避、转移 | 已给出阻断条件 | E-DOC-02 | 责任人与窗口确认 |
| 15 | GB/T 30279-2020 | 漏洞分类：身份、访问、文件、供应链、运维 | 已分类 | E-DOC-02 | 现场可利用性复核 |
| 16 | GB/T 30279-2020 | 漏洞分级：利用条件、影响范围、危害程度 | 已分级 | E-DOC-02 | 真实暴露面确认 |
| 17 | OWASP Testing Guide V4 | 认证与会话测试 | 服务层通过 | E-BE-01 | Playwright 登录链路 |
| 18 | OWASP Testing Guide V4 | 授权测试 | 权限服务层通过 | E-BE-01 | 租户和对象级运行态测试 |
| 19 | OWASP Testing Guide V4 | 输入、文件和配置测试 | 插件、文件、配置回归通过 | E-BE-01 | DAST 与恶意样本 |
| 20 | MITRE ATT&CK | 初始访问、执行、传输、凭据暴露路径 | 已映射 | E-DOC-02 | 外部攻击面扫描 |
| 21 | NIST SP 800-115 | 规划、发现、攻击/验证、报告和整改 | 仓库内规划、发现、报告、整改已覆盖 | E-DOC-01、E-DOC-02 | 授权环境攻击验证 |

## 4. 测试范围

已完成的本机范围包括后端 Maven 全量测试、前端依赖安装、单元测试、类型检查、覆盖率测试、生产构建、发布证据脚本门禁、报告合约和关键关键词扫描。未完成范围包括真实生产授权渗透测试、互联网暴露面扫描、动态应用安全测试、Playwright 运行态端到端、日志平台核验、备份恢复演练、云资源和第三方服务现场核验。

## 5. 问题闭环摘要

| 风险级别 | 数量 | 状态 |
|---|---:|---|
| 严重 | 1 | 已修复并需生产复核 |
| 高 | 7 | 已修复并需生产复核 |
| 中高 | 2 | 已修复并需运行态复核 |
| 中 | 3 | 已修复或静态闭环，需现场复核 |
| 低中 | 1 | 已修复并需代理链验证 |

## 6. 测试证据索引

| 证据 ID | 类别 | 命令/来源 | 当前结论 |
|---|---|---|---|
| E-DOC-01 | 主报告 | `doc/security-assessment-full-test-report.md` | 中文 UTF-8，报告合约通过 |
| E-DOC-02 | 整改跟踪表 | `doc/security-assessment-remediation-tracker.md` | 中文 UTF-8，报告合约通过 |
| E-DOC-03 | 报告结构合约 | `node bin/security-assessment-report-contract.test.mjs` | 通过 |
| E-BE-01 | 后端测试 | `lumira-backend\mvnw.cmd test` | 通过：17 个 reactor 模块 SUCCESS |
| E-FE-01 | 前端测试 | `corepack pnpm --dir lumira-ui run test` | 通过：12 个测试文件，37 个测试 |
| E-FE-02 | 前端覆盖率 | `corepack pnpm --dir lumira-ui run test:coverage` | 通过；Statements 34.7%、Branches 12.53%、Functions 23.46%、Lines 35.22% |
| E-FE-03 | 前端构建 | `corepack pnpm --dir lumira-ui run build` | 通过；生成 `dist` 87 个文件 |
| E-FE-04 | 前端 lint | `corepack pnpm --dir lumira-ui run lint` | 通过 |
| E-FE-05 | 前端 smoke | `corepack pnpm --dir lumira-ui run test:smoke` | 通过；修复 smoke 脚本从旧 `bin` 到 `scripts` 的路径引用 |
| E-REL-01 | 发布证据就绪 | `node bin/ddd-staging-execution-checklist.mjs --production-evidence-readiness` | `BLOCKED`/`NO_GO_STRICT`，blockedAuditItemCount=6，readyEvidenceCount=1 |
| E-REL-02 | 发布证据强制门禁 | `node bin/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce` | 证据未齐时应非零阻断 |
| E-REL-05 | 生产证据就绪轻量回归 | `node bin/ddd-production-evidence-readiness.test.mjs` | 覆盖生产解阻计划、生产证据就绪 JSON 和交接包阻断缺口 |
| E-REL-06 | lane completion receipt 自动填充 | `node bin/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>` | 仅预填回执，不绕过最终检查 |
| E-REL-07 | P0 release env 填写清单 | `node bin/ddd-release-env-fill-checklist.test.mjs` | 校验 release env 填写清单和安全占位模板 |
| E-REL-08 | 第一波 env 脱敏回执样例合约 | `node bin/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract` | 样例不作为真实 env 通过证据 |
| E-REL-09 | 生产解锁本地尝试 | `node bin/ddd-production-unblock-attempt.mjs` | 生成 `release-env-lint.attempt.json`，最终仍为 `NO_GO_STRICT` |

## 7. OWASP/MITRE/NIST 专项覆盖

| 专项 | 已覆盖 | 未覆盖/受限 |
|---|---|---|
| OWASP 认证与会话 | 默认管理员、刷新令牌、验证码、会话回归 | 浏览器登录和会话生命周期 |
| OWASP 访问控制 | 角色、数据权限、内部接口令牌 | 租户和对象级运行态越权 |
| OWASP 文件/输入 | 文件根路径、插件包路径、插件清理、更新源 | polyglot、畸形 zip、压缩炸弹 |
| OWASP 配置 | 生产配置、Dockerfile 静态契约、CORS/secret 规则 | 真实 TLS、WAF、日志平台 |
| MITRE ATT&CK | T1190、T1059、T1105、T1552 映射 | 外部扫描和真实供应链演练 |
| NIST SP 800-115 | Planning、Discovery、Reporting、Remediation | Attack/Validation 运行态验证 |

## 8. 本轮修复摘要

| 类别 | 修复 |
|---|---|
| 文档 | 重建中文 UTF-8 报告和整改表，加入乱码回归合约 |
| 后端 | 移除 Java BOM，修复插件加载器编码，更新 DDD 表归属模块名 |
| 前端 | 修正 `adapt-cdn-assets.mjs` 和 smoke 脚本从旧 `bin` 到 `scripts` 的路径 |
| 发布证据 | 将证据合约改为如实断言 `BLOCKED`/`NO_GO_STRICT`，不绕过生产门禁 |

## 9. 环境限制与阻断项

| 阻断项 | 影响标准/测试 | 当前证据 | 完成条件 |
|---|---|---|---|
| 容器镜像构建/启动证据不足 | 容器部署和健康检查 | E-REL-01 | 可信 CI 或稳定镜像仓库重新生成证据 |
| 本地运行态服务未提供 | Playwright、接口冒烟、DAST | 本报告范围声明 | 提供 `PLAYWRIGHT_BASE_URL`、测试账号和目标 URL |
| 生产授权缺失 | NIST Attack/Validation | 本报告范围声明 | 提供授权窗口、资产清单和应急联系人 |
| 发布证据严格不放行 | 发布门禁 | E-REL-01、E-REL-02 | 补齐环境回执、lane 回执、生产审计和最终放行 |
| 前端覆盖率偏低 | 质量充分性 | E-FE-02 | 增加登录、请求、权限、文件、系统和 AI 页面测试 |

## 10. 剩余阻断责任矩阵

| 阻断域 | 责任域 | 关键缺口 | 下一条命令 | 完成信号 |
|---|---|---|---|---|
| release-env | release-infra | 生产等价安全环境文件缺失 | `node bin/ddd-release-env-file-lint.mjs` | release env lint 无阻断项 |
| runtime-business | runtime-owner | 部署 URL、认证、AI、文件、任务、支付证据缺失 | `node bin/ddd-staging-runtime-check.mjs` | 业务检查通过 |
| rollback | bounded-context-owner | 回滚演练或延期文件缺失 | `node bin/ddd-staging-data-safety-check.mjs` | 回滚证据通过 |
| migration | database | fresh/upgrade 迁移验证缺失 | `node bin/ddd-staging-data-safety-check.mjs` | 迁移验证通过 |
| explain | database | explain 计划产物缺失 | `node bin/ddd-staging-data-safety-check.mjs` | explain 证据通过 |
| first-wave-env-receipt | release-infra | 第一波环境输入文件和脱敏回执缺失 | `node bin/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract` | 回执结构合约通过 |
| lane-completion-receipt | release-owner | owner lane 完成回执缺失 | `node bin/ddd-staging-execution-checklist.mjs --lane-completion-submission-check` | 覆盖全部 lane |
| owner-evidence | platform-owner | owner evidence intake 仍需持续复核 | `node bin/ddd-staging-execution-checklist.mjs --owner-evidence-intake` | 必需 artifact 缺失为 0 |
| production-audit | release-owner | 生产切换审计仍有阻断项 | `node bin/ddd-staging-execution-checklist.mjs --production-cutover-audit` | blockedAuditItems 为 0 |
| final-go-no-go | release-owner | final review 未放行 | `node bin/ddd-staging-execution-checklist.mjs --final-go-no-go` | `GO_STRICT` 且 `cutoverAllowed=true` |

## 11. 全量排查判定

可以判定：仓库内可自动化验证范围已完成本轮全量排查和回归，后端全量、前端测试/构建、文档合约和发布证据阻断语义均已复核。

不能判定：生产环境全量安全测评已完成。当前最终机器判定为 `NO_GO_STRICT`，仍需真实授权环境、资产清单、外部扫描、运行态端到端测试、动态应用安全测试、镜像构建/启动证据、现场访谈和安全运营证据支持。
