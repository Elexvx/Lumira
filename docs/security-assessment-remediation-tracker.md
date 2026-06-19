# Lumira 安全评估整改跟踪表

评估批次：`495458a5_20260619-134359`

基线提交：`89381581`

已完成整改提交：`f9ff9e3a`、`dc3d17e1`、`72c08f54`

当前补充整改：待提交

## 范围与依据

本跟踪表用于承接 GB/T 22239-2019、GB/T 28449-2018、GB/T 20984-2022、GB/T 30279-2020、OWASP Testing Guide V4、MITRE ATT&CK Enterprise、NIST SP 800-115 对照评估后的整改闭环。范围覆盖仓库内后端服务、前端、脚本门禁、部署脚本、发布证据与配置基线；不替代需要真实授权环境的生产渗透测试、互联网暴露面扫描和第三方平台现场核验。

## 发现项状态

| ID | 控制域 | 风险 | 当前状态 | 验证方式 |
|---|---|---:|---|---|
| DEPLOY-OPS-ADMIN-DEFAULT-001 | 身份鉴别/安全运维 | Critical | 已修复并回归通过 | `DefaultAdminPasswordBaselineTest` |
| SQL-DATA-001 | 访问控制 | High | 已修复并回归通过 | `SystemRoleManagementAppServiceTest` |
| EXT-CALLBACK-001 | 访问控制/内部接口 | High | 已修复并回归通过 | `AiCommandServiceTest` |
| EXT-CALLBACK-002 | 安全运维/供应链 | High | 已修复并回归通过 | `PlatformUpdateAppServiceTest` |
| CAND-PLUGIN-PATH-001 | 文件上传/插件管理 | High | 已修复并回归通过 | `PluginArtifactLoaderTest` |
| AUTH-IAM-001 | 身份鉴别/会话管理 | High | 已修复并回归通过 | `AuthAppServiceTest` |
| AUTH-IAM-002 | 身份鉴别/登录防护 | High | 已修复并回归通过 | `AuthAppServiceTest` |
| DEPLOY-OPS-UPDATER-AUTH-001 | 安全运维/管理接口 | High | 已修复并回归通过 | `node scripts/lumira-updater-auth.test.mjs` |
| FILE-PLUGIN-FILE-ROOT-001 | 文件存储边界 | Medium-High | 已修复并回归通过 | `FileManagementAppServiceTest` |
| PLUGIN-CLEANUP-BOUNDARY-001 | 插件存储边界 | Medium-High | 已修复并回归通过 | `PluginArtifactLoaderTest` |
| UPDATE-SOURCE-TRUST-001 | 安全运维/供应链 | Medium | 已修复并回归通过 | `PlatformUpdateAppServiceTest` |
| CAND-FORWARDED-IP-001 | 边界防护/审计 | Medium | 已修复并回归通过 | `ClientIpResolverTest` |
| DEPLOY-OPS-BACKUP-ENV-001 | 备份/机密保护 | Medium | 已修复并静态复核通过 | `deploy/backup-platform.sh`、`.gitignore` |
| FORWARDED-IP-VALIDATION-001 | 边界防护/审计 | Low-Medium | 已修复并回归通过 | `ClientIpResolverTest` |

## 运行记录

- 2026-06-19：提交现有工作树作为整改基线 `89381581`。
- 2026-06-19：完成原 11 项报告问题的第一轮代码整改并提交 `f9ff9e3a`。
- 2026-06-19：补充安全评估证据与生产门禁证据提交 `dc3d17e1`、`72c08f54`。
- 2026-06-19：使用子代理 `Raman` 完成后端服务与公共库只读复核，新增 3 项发现并完成整改。
- 2026-06-19：通过后端全量回归：
  - `.\mvnw.cmd clean test`
  - 结果：17 个 Maven 模块全部 `BUILD SUCCESS`，完成时间 2026-06-19 15:18:55，耗时 01:51；`system-service` 有 4 个既有 integration skip。
- 2026-06-19：通过前端回归：
  - `corepack pnpm --dir frontend install --frozen-lockfile`
  - `corepack pnpm --dir frontend run lint`
  - `corepack pnpm --dir frontend run typecheck`
  - `corepack pnpm --dir frontend run test`：12 个测试文件、37 个用例通过。
  - `corepack pnpm --dir frontend run test:smoke`
- 2026-06-19：前端 E2E smoke 安装 Chromium 后执行，因 `127.0.0.1:8000`/`127.0.0.1:8080` 未提供运行态而连接拒绝，未完成。
- 2026-06-19：脚本门禁复核：
  - `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness`：输出 `BLOCKED/NO_GO_STRICT`。
  - `node scripts/ddd-staging-execution-checklist.mjs --production-evidence-readiness-enforce`：证据未齐时按设计非零阻断。
  - `node scripts/ddd-staging-execution-checklist.mjs --handoff-bundle-verify`：通过，检查 112 个文件，`issues=[]`。
  - `node scripts/ddd-release-artifact-integrity-gate-contract.test.mjs`：通过。
  - `node scripts/ddd-release-config-sync.test.mjs`：通过。

## 尚未闭环

- 生产环境渗透测试、真实外部资产扫描、第三方服务现场核验尚未执行，需要明确授权环境、目标资产、时间窗口和测试边界。
- 真实部署后的代理链、updater 端口暴露、真实更新源签名、备份目录权限、日志留存与告警联动仍需现场复核。
- Playwright E2E smoke、运行态 DAST 和业务流攻击路径验证仍需可访问的本地或隔离运行环境。
- 发布证据仍处于 `NO_GO_STRICT`：first-wave env receipt、lane completion receipt、owner evidence、production audit、final go/no-go 证据未齐。
