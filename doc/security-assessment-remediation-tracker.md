# Lumira 安全评估整改跟踪表

评估批次：`20260620-latest`

代码基线：`210188b8 chore: snapshot current workspace changes` 之后继续完成本轮修复和回归。

## 范围与依据

本跟踪表承接 GB/T 22239-2019、GB/T 28449-2018、GB/T 20984-2022、GB/T 30279-2020、OWASP Testing Guide V4、MITRE ATT&CK Enterprise、NIST SP 800-115 的条款/测试项映射整改闭环。范围覆盖仓库内后端服务、前端、脚本门禁、部署脚本、发布证据与配置基线；不替代真实授权环境中的生产渗透测试、外部扫描、动态应用安全测试和现场核验。

## 发现项状态

| ID | 控制域 | 风险 | 标准/测试项映射 | 当前状态 | 复测证据 | 生产验证 |
|---|---|---:|---|---|---|---|
| DEPLOY-OPS-ADMIN-DEFAULT-001 | 身份鉴别/安全运维 | 严重 | GB/T 22239；OWASP WSTG-ATHN；MITRE T1078 | 已修复，待生产复核 | `DefaultAdminPasswordBaselineTest`、E-BE-01 | 待现场验证 |
| SQL-DATA-001 | 访问控制 | 高 | GB/T 22239；OWASP WSTG-ATHZ；MITRE T1068 | 已修复，待端到端复核 | `SystemRoleManagementAppServiceTest`、E-BE-01 | 待端到端验证 |
| EXT-CALLBACK-001 | 访问控制/内部接口 | 高 | GB/T 22239；OWASP WSTG-ATHZ/API；MITRE T1059 | 已修复，待运行态复核 | `AiCommandServiceTest`、E-BE-01 | 待运行态验证 |
| EXT-CALLBACK-002 | 安全运维/供应链 | 高 | GB/T 22239；OWASP WSTG-CONF；MITRE T1105 | 已修复，待真实源复核 | `PlatformUpdateAppServiceTest`、E-BE-01 | 待真实更新源验证 |
| CAND-PLUGIN-PATH-001 | 文件上传/插件管理 | 高 | GB/T 22239；OWASP WSTG-INPV/FILE；MITRE T1059 | 已修复，待恶意样本复核 | `PluginArtifactLoaderTest`、E-BE-01 | 待恶意样本验证 |
| AUTH-IAM-001 | 身份鉴别/会话管理 | 高 | GB/T 22239；OWASP WSTG-SESS | 已修复，待浏览器复核 | `AuthAppServiceTest`、E-BE-01 | 待端到端验证 |
| AUTH-IAM-002 | 身份鉴别/登录防护 | 高 | GB/T 22239；OWASP WSTG-ATHN | 已修复，待浏览器复核 | `AuthAppServiceTest`、E-BE-01 | 待浏览器验证 |
| DEPLOY-OPS-UPDATER-AUTH-001 | 安全运维/管理接口 | 高 | GB/T 22239；OWASP WSTG-CONF；MITRE T1190 | 已修复，待端口暴露复核 | `node bin/lumira-updater-auth.test.mjs` | 待端口暴露验证 |
| FILE-PLUGIN-FILE-ROOT-001 | 文件存储边界 | 中高 | GB/T 22239；OWASP WSTG-INPV/FILE | 已修复，待上传链路复核 | `FileManagementAppServiceTest`、E-BE-01 | 待上传端到端验证 |
| PLUGIN-CLEANUP-BOUNDARY-001 | 插件存储边界 | 中高 | GB/T 22239；OWASP WSTG-INPV/FILE；MITRE T1059 | 已修复，待运行态复核 | `PluginArtifactLoaderTest`、E-BE-01 | 待运行态验证 |
| UPDATE-SOURCE-TRUST-001 | 安全运维/供应链 | 中 | GB/T 22239；OWASP WSTG-CONF；MITRE T1105 | 已修复，待真实源验证 | `PlatformUpdateAppServiceTest`、E-BE-01 | 待真实源验证 |
| CAND-FORWARDED-IP-001 | 边界防护/审计 | 中 | GB/T 22239；OWASP WSTG-CONF | 已修复，待代理链验证 | `ClientIpResolverTest`、E-BE-01 | 待代理链验证 |
| DEPLOY-OPS-BACKUP-ENV-001 | 备份/机密保护 | 中 | GB/T 22239；GB/T 20984；MITRE T1552 | 已静态闭环，待备份恢复演练 | `deploy/backup-platform.sh`、`.gitignore` | 待备份恢复演练 |
| FORWARDED-IP-VALIDATION-001 | 边界防护/审计 | 低中 | GB/T 22239；OWASP WSTG-CONF | 已修复，待代理链验证 | `ClientIpResolverTest`、E-BE-01 | 待代理链验证 |

## 运行记录

- 2026-06-20：已提交当前工作区快照 `210188b8`，作为本轮测试基线。
- 2026-06-20：官网/CMS 类残留在非构建产物范围内未发现命中。
- 2026-06-20：修复主报告和整改跟踪表乱码，重建为中文 UTF-8，并通过 `node bin/security-assessment-report-contract.test.mjs`。
- 2026-06-20：修复 Java BOM/源码编码、插件加载器异常文案编码、DDD 表归属旧模块名和报告合约旧路径。
- 2026-06-20：`lumira-backend\mvnw.cmd test` 全量通过，17 个 reactor 模块 SUCCESS。
- 2026-06-20：`corepack pnpm --dir lumira-ui run test`、`typecheck`、`test:coverage`、`build`、`lint`、`test:smoke` 通过；修复 `adapt-cdn-assets.mjs` 和 smoke 脚本从旧 `bin` 到 `scripts` 的路径引用。
- 2026-06-20：`node bin/ddd-staging-execution-checklist.mjs --production-evidence-readiness` 返回 `BLOCKED`/`NO_GO_STRICT`，blockedAuditItemCount=6，readyEvidenceCount=1。
- 2026-06-20：补齐 E-REL-05 至 E-REL-09 证据编号，覆盖生产证据就绪轻量回归、lane completion receipt 自动填充、P0 release env 填写清单、第一波 env 脱敏回执样例合约和生产解锁本地尝试。

## 阻断项清单

| 阻断项 | 优先级 | 当前证据 | 完成条件 |
|---|---|---|---|
| 容器镜像构建/启动证据不足 | P0 | 镜像构建和运行态健康检查缺少稳定通过证据 | 使用可信 CI 或稳定镜像仓库重新生成证据 |
| 本地 API proxy 与后端未运行 | P0 | 缺少运行态 URL | 启动隔离环境或提供目标 URL |
| Playwright 端到端测试和动态应用安全测试未完成 | P0 | 缺少运行态端到端通过证据 | 提供测试账号、测试数据和授权边界 |
| 生产证据严格不放行 | P0 | `NO_GO_STRICT`，blockedAuditItemCount=6 | 补齐环境回执、lane 回执、生产审计和最终放行 |
| 前端覆盖率偏低 | P1 | Statements 34.7%、Branches 12.53%、Functions 23.46%、Lines 35.22% | 增加登录、请求、权限、文件、系统和 AI 页面测试 |

## 尚未闭环

- 生产核验尚未执行，需要明确授权环境、目标资产、时间窗口和测试边界。
- 真实部署后的代理链、updater 端口暴露、真实更新源签名、备份目录权限、日志留存与告警联动仍需现场复核。
- Playwright 端到端冒烟、动态应用安全测试和业务流攻击路径验证仍需可访问的本地或隔离运行环境。
- 发布证据仍处于 `NO_GO_STRICT`，不能作为生产上线放行依据。
