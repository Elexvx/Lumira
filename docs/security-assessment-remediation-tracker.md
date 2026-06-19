# Lumira 安全评估整改跟踪表

评估批次：`495458a5_20260619-134359`

基线提交：`89381581`

已完成整改提交：`f9ff9e3a`、`dc3d17e1`、`72c08f54`、`d2b31294`、`982576f1`、`5310a365`、`c327aa6a`、`d23d4580`、`5c4a5501`、`d8392d64`、`d4322be6`、`9d63573e`、`ec75d416`、`c2da00ef`、`8b487153`、`982964d8`、`d51b4c5e`、`79cbdccc`、`94781f29`、`d203483f`

当前补充证据：本轮补充报告结构合约测试、lane completion receipt 自动填充辅助命令、P0 release env 填写清单、交接包内 release env 填写清单和第一波 env 脱敏回执样例合约，自动校验 UTF-8 中文、21 条详细测评项、剩余阻断责任矩阵、关键标准和证据编号；发布汇总和生产证据就绪门禁仍显示 Docker 证据通道通过、总门禁 5/6 阻断、5 个生产证据门仍阻断，机器判定为 `NO_GO_STRICT`。

## 范围与依据

本跟踪表用于承接 GB/T 22239-2019、GB/T 28449-2018、GB/T 20984-2022、GB/T 30279-2020、OWASP Testing Guide V4、MITRE ATT&CK Enterprise、NIST SP 800-115 的条款/测试项映射整改闭环。范围覆盖仓库内后端服务、前端、脚本门禁、部署脚本、发布证据与配置基线；不替代需要真实授权环境的生产渗透测试、互联网暴露面扫描、动态应用安全测试和第三方平台现场核验。

## 发现项状态

| ID | 控制域 | 风险 | 标准/测试项映射 | 当前状态 | 复测证据 | 生产验证 |
|---|---|---:|---|---|---|---|
| DEPLOY-OPS-ADMIN-DEFAULT-001 | 身份鉴别/安全运维 | 严重 | GB/T 22239 身份鉴别；OWASP WSTG-ATHN；MITRE T1078 | 已修复并回归通过 | `DefaultAdminPasswordBaselineTest`、E-BE-01 | 待现场验证 |
| SQL-DATA-001 | 访问控制 | 高 | GB/T 22239 访问控制；OWASP WSTG-ATHZ；MITRE T1068 | 已修复并回归通过 | `SystemRoleManagementAppServiceTest`、E-BE-01 | 待端到端验证 |
| EXT-CALLBACK-001 | 访问控制/内部接口 | 高 | GB/T 22239 访问控制；OWASP WSTG-ATHZ/API；MITRE T1059 | 已修复并回归通过 | `AiCommandServiceTest`、E-BE-01 | 待运行态验证 |
| EXT-CALLBACK-002 | 安全运维/供应链 | 高 | GB/T 22239 安全运维；OWASP WSTG-CONF；MITRE T1105 | 已修复并回归通过 | `PlatformUpdateAppServiceTest`、E-REG-01 | 待真实更新源验证 |
| CAND-PLUGIN-PATH-001 | 文件上传/插件管理 | 高 | GB/T 22239 入侵防范；OWASP WSTG-INPV/FILE；MITRE T1059 | 已修复并回归通过 | `PluginArtifactLoaderTest`、E-BE-01 | 待恶意样本验证 |
| AUTH-IAM-001 | 身份鉴别/会话管理 | 高 | GB/T 22239 身份鉴别；OWASP WSTG-SESS | 已修复并回归通过 | `AuthAppServiceTest`、E-BE-01 | 待端到端验证 |
| AUTH-IAM-002 | 身份鉴别/登录防护 | 高 | GB/T 22239 身份鉴别；OWASP WSTG-ATHN | 已修复并回归通过 | `AuthAppServiceTest`、E-BE-01 | 待浏览器验证 |
| DEPLOY-OPS-UPDATER-AUTH-001 | 安全运维/管理接口 | 高 | GB/T 22239 安全运维；OWASP WSTG-CONF；MITRE T1190 | 已修复并回归通过 | `node scripts/lumira-updater-auth.test.mjs` | 待端口暴露验证 |
| FILE-PLUGIN-FILE-ROOT-001 | 文件存储边界 | 中高 | GB/T 22239 数据完整性；OWASP WSTG-INPV/FILE | 已修复并回归通过 | `FileManagementAppServiceTest`、E-BE-01 | 待上传端到端验证 |
| PLUGIN-CLEANUP-BOUNDARY-001 | 插件存储边界 | 中高 | GB/T 22239 入侵防范；OWASP WSTG-INPV/FILE；MITRE T1059 | 已修复并回归通过 | `PluginArtifactLoaderTest`、E-REG-01 | 待运行态验证 |
| UPDATE-SOURCE-TRUST-001 | 安全运维/供应链 | 中 | GB/T 22239 安全运维；OWASP WSTG-CONF；MITRE T1105 | 已修复并回归通过 | `PlatformUpdateAppServiceTest`、E-REG-01 | 待真实源验证 |
| CAND-FORWARDED-IP-001 | 边界防护/审计 | 中 | GB/T 22239 安全审计；OWASP WSTG-CONF | 已修复并回归通过 | `ClientIpResolverTest`、E-REG-01 | 待代理链验证 |
| DEPLOY-OPS-BACKUP-ENV-001 | 备份/机密保护 | 中 | GB/T 22239 数据保密；GB/T 20984 风险处置；MITRE T1552 | 已修复并静态复核通过 | `deploy/backup-platform.sh`、`.gitignore` | 待备份恢复演练 |
| FORWARDED-IP-VALIDATION-001 | 边界防护/审计 | 低中 | GB/T 22239 安全审计；OWASP WSTG-CONF | 已修复并回归通过 | `ClientIpResolverTest`、E-REG-01 | 待代理链验证 |

## 风险分级依据

| ID | GB/T 20984 风险要素 | GB/T 30279 分级理由 | 残余风险 |
|---|---|---|---|
| DEPLOY-OPS-ADMIN-DEFAULT-001 | 资产：管理员账号；威胁：首个访问者接管；影响：全局控制。 | 可远程利用、影响全系统、默认部署条件下危害高。 | 生产首启流程需现场确认。 |
| SQL-DATA-001 | 资产：权限与租户数据；威胁：通配权限提升。 | 需认证但影响范围大，可造成越权。 | 需端到端验证租户与对象级访问。 |
| EXT-CALLBACK-001 | 资产：内部工具 API；威胁：AI 工具越权调用。 | 需权限但可扩大访问面。 | 需运行态工具链验证。 |
| EXT-CALLBACK-002/UPDATE-SOURCE-TRUST-001 | 资产：更新链路；威胁：恶意 manifest/镜像。 | 供应链路径影响部署制品。 | 需真实签名源和镜像仓库验证。 |
| CAND-PLUGIN-PATH-001/PLUGIN-CLEANUP-BOUNDARY-001 | 资产：插件目录/主机文件；威胁：路径穿越或误删。 | 需插件管理路径但可影响文件系统边界。 | 需恶意样本和运行态插件隔离验证。 |
| AUTH-IAM-001/AUTH-IAM-002 | 资产：会话与登录入口；威胁：token 重放/验证码绕过。 | 可影响身份鉴别可靠性。 | 需真实浏览器登录链路验证。 |
| DEPLOY-OPS-UPDATER-AUTH-001 | 资产：更新管理接口；威胁：未授权更新/回滚。 | 端口误暴露时影响高。 | 需部署端口暴露和网络 ACL 验证。 |
| FILE-PLUGIN-FILE-ROOT-001 | 资产：上传文件与存储根；威胁：越界读写。 | 需管理权限但影响文件存储边界。 | 需上传、下载、删除端到端验证。 |
| CAND-FORWARDED-IP-001/FORWARDED-IP-VALIDATION-001 | 资产：审计 IP 与限流 key；威胁：伪造客户端地址。 | 依赖代理链配置，影响审计准确性。 | 需真实反向代理链验证。 |
| DEPLOY-OPS-BACKUP-ENV-001 | 资产：env secret；威胁：备份泄露。 | 本地/运维路径影响机密性。 | 需备份恢复演练和权限检查。 |

## 运行记录

- 2026-06-19：提交现有工作树作为整改基线 `89381581`。
- 2026-06-19：完成原 11 项报告问题的第一轮代码整改并提交 `f9ff9e3a`。
- 2026-06-19：补充安全评估证据与生产门禁证据提交 `dc3d17e1`、`72c08f54`。
- 2026-06-19：使用子代理完成后端服务与公共库只读复核，新增 3 项发现并完成整改，提交 `d2b31294`。
- 2026-06-19：补充标准逐项矩阵、测试证据索引和中文报告修订，提交 `982576f1`。
- 2026-06-19：通过后端全量回归：`.\mvnw.cmd clean test`，17 个 Maven 模块全部 `BUILD SUCCESS`，完成时间 2026-06-19 15:18:55。
- 2026-06-19：通过后端发行包构建：`.\mvnw.cmd -pl services/lumira-server -am -DskipTests package`，16 个模块 `BUILD SUCCESS`，完成时间 2026-06-19 15:29:25。
- 2026-06-19：通过前端回归：依赖锁定安装、代码规范检查、类型检查、单元测试、冒烟测试均通过；`test` 中 12 个测试文件、87 个用例通过。
- 2026-06-19：通过前端覆盖率统计：12 个测试文件、87 个用例通过；语句 34.7%、分支 12.53%、函数 23.46%、行 35.22%，覆盖率偏低，列入质量改进项。
- 2026-06-19：通过前端生产构建：`corepack pnpm --dir frontend run build`；旧落盘证据显示输出 90 个资源文件，本轮以命令输出记录作为当前通过证据。
- 2026-06-19：通过静态/合约门禁：`ddd-dockerfile-contract.test.mjs`、`ddd-docker-evidence-contract.test.mjs`、`ddd-backend-evidence-contract.test.mjs`、`ddd-frontend-evidence-contract.test.mjs`、`ddd-frontend-smoke-contract.test.mjs`。
- 2026-06-19：Docker 构建证据更新为更准确的阻断结论：`docker --version` 与 `docker info` 成功，Docker 版本 29.5.3；`node scripts/ddd-docker-build-evidence.mjs` 写入 `artifacts/ddd/build/docker-image-evidence.json`，状态为失败，2 个镜像构建均失败，阻断为基础镜像层 `unexpected EOF`、`short read`、`ETIMEDOUT`。
- 2026-06-19：修复 Windows 下部署脚本 Docker/Compose 调用问题：共享执行工具可正确调用 `docker.cmd`，`deploy-container.mjs` 改用 Node 原生目录创建并向 Compose 传递相对路径；`node scripts/deploy-container.mjs --ps --local-mysql` 通过。
- 2026-06-19：`node scripts/ddd-docker-build-evidence.mjs --check` 通过，发布汇总和下一步队列中 Docker 镜像证据通道转为通过；但 `node scripts/start-platform.mjs --skip-build --local-mysql --skip-check` 仍受基础镜像拉取超时阻断，未形成运行态服务。
- 2026-06-19：部署运行态检查失败：`node scripts/check-deployment.mjs` 对 `127.0.0.1:8000` 与 `127.0.0.1:8080` 均无 HTTP 响应。
- 2026-06-19：前端端到端冒烟测试所需 Chromium 已安装；因 `127.0.0.1:8000`/`127.0.0.1:8080` 未提供运行态，未形成新的 2026-06-19 端到端运行态通过证据。
- 2026-06-19：主报告补充 21 条详细测评项清单，覆盖 GB/T 22239、GB/T 28449、GB/T 20984、GB/T 30279、OWASP、ATT&CK、NIST 的本地证据、执行结果和未闭环条件。
- 2026-06-19：再次复核 `node scripts/ddd-staging-execution-checklist.mjs --rollup` 与 `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness`；结果仍为 Docker 证据通道通过、总门禁 5/6 阻断、5 个生产证据门阻断。
- 2026-06-19：主报告补充剩余阻断责任矩阵；`--blocking-inputs-markdown` 显示 32 个阻断输入、5 个阻断 gate，`--production-unblock-plan-markdown` 显示 3 条并行解阻工作流和 5 个阻断审计项。
- 2026-06-19：补充报告结构合约测试 `node scripts/security-assessment-report-contract.test.mjs`，覆盖 UTF-8 中文、21 条详细测评项、剩余阻断责任矩阵、标准名称、关键证据 ID 和阻断摘要，并接入 CI。
- 2026-06-19：补充 lane completion receipt 自动填充辅助命令 `node scripts/ddd-lane-completion-receipt-autofill.mjs --receipt-file=<receipt-file> --output=<autofilled-receipt-file>`，并接入命令列表、提交计划、交接包模板和 owner packet；该命令只根据 owner evidence intake 中 PASS 的 lane 预填 receipt，不替代 `--lane-completion-submission-check` 和最终 go/no-go。
- 2026-06-19：补充 P0 release env 填写清单 `node scripts/ddd-release-env-fill-checklist.mjs`，从 release env lint 与 config evidence 中提取阻断 key 并按责任域分组输出 Markdown/JSON；当前真实落盘证据为 55 个 primary blocker key、63 个 config blocker。该清单不包含 secret 值，不替代 `ddd-release-env-file-lint.mjs` 和 `ddd-release-config-evidence.mjs`。
- 2026-06-19：复核第一波 env 脱敏回执样例合约 `node scripts/ddd-staging-execution-checklist.mjs --next-action-env-receipt-contract --next-action-env-receipt-file=artifacts/ddd/release/staging-handoff-bundle/next-action-env-receipt.sample.json`；结构合约通过，样例 redacted=true、3 条 lane、5 条 pass criteria，但 receiptStatus 仍为 BLOCKED，仅用于展示安全回执形态。
- 2026-06-19：脚本门禁复核：
  - `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness`：输出“阻断/严格不放行”。
  - `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce`：证据未齐时按设计非零阻断。
  - `node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle`：重新生成发布交接包，写入 113 个文件，Docker 镜像证据通道为通过，阻断门为 5/6。
  - `node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify`：通过，检查 112 个文件，问题列表为空。
  - `node scripts/ddd-production-evidence-readiness.test.mjs`：通过，覆盖生产解阻计划、生产证据就绪 JSON 和交接包完整性。
  - `node scripts/ddd-release-artifact-integrity-gate-contract.test.mjs`：通过。
  - `node scripts/ddd-release-config-sync.test.mjs`：通过。

## 阻断项清单

| 阻断项 | 优先级 | 当前证据 | 完成条件 |
|---|---|---|---|
| Docker 镜像构建/拉取受镜像仓库、网络或构建缓存异常阻断 | P0 | `artifacts/ddd/build/docker-image-evidence.json`：状态为失败，Docker 预检成功，2 个镜像构建失败，错误包括 `unexpected EOF`、`short read`、`ETIMEDOUT`；`deploy-container --ps --local-mysql` 已通过，`start-platform --skip-build --local-mysql --skip-check` 卡在 busybox/base image 拉取。 | 使用稳定镜像仓库、本地镜像缓存或可信持续集成 Docker 执行器，重新生成通过的构建/启动/检查证据。 |
| 本地 API proxy 与后端未运行 | P0 | `node scripts/check-deployment.mjs` 全部健康检查无 HTTP 响应。 | 启动隔离环境，确保 `127.0.0.1:8000`、`127.0.0.1:8080` 或指定目标可访问。 |
| Playwright 端到端测试/动态应用安全测试未完成 | P0 | 当前无 2026-06-19 运行态端到端通过证据；无动态应用安全测试报告。 | 提供运行态 URL、测试账号、测试数据和授权边界，执行端到端测试与动态应用安全测试。 |
| 生产证据严格不放行 | P0 | production evidence readiness 输出 5 个阻断证据门。 | 补齐第一波环境回执、通道完成回执、责任人证据、生产切换审计、最终放行/不放行结论。 |
| 前端覆盖率偏低 | P1 | 行覆盖率 35.22%、分支覆盖率 12.53%。 | 增加关键登录、请求、权限、文件、系统/AI 页面测试。 |

## 尚未闭环

- 生产环境渗透测试、真实外部资产扫描、第三方服务现场核验尚未执行，需要明确授权环境、目标资产、时间窗口和测试边界。
- 真实部署后的代理链、updater 端口暴露、真实更新源签名、备份目录权限、日志留存与告警联动仍需现场复核。
- Playwright 端到端冒烟测试、运行态动态应用安全测试和业务流攻击路径验证仍需可访问的本地或隔离运行环境。
- 发布证据仍处于严格不放行状态，不能作为生产上线放行依据。
