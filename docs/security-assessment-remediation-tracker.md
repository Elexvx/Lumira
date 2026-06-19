# Lumira 安全评估整改跟踪表

评估批次：`495458a5_20260619-134359`

基线提交：`89381581`

整改提交：`f9ff9e3a`

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
| CAND-FORWARDED-IP-001 | 边界防护/审计 | Medium | 已修复并回归通过 | `ClientIpResolverTest` |
| DEPLOY-OPS-BACKUP-ENV-001 | 备份/机密保护 | Medium | 已修复并静态复核通过 | `deploy/backup-platform.sh`、`.gitignore` |

## 运行记录

- 2026-06-19：已提交现有工作树作为整改基线 `89381581`。
- 2026-06-19：完成 11 项报告问题的第一轮代码整改并提交 `f9ff9e3a`。
- 2026-06-19：通过针对性 Java 回归测试：
  - `.\mvnw.cmd "-pl" "services/auth-service,services/system-service,services/ai-service,services/plugin-service,services/file-service,libs/common-web" "-Dtest=AuthAppServiceTest,DefaultAdminPasswordBaselineTest,SystemRoleManagementAppServiceTest,PlatformUpdateAppServiceTest,AiCommandServiceTest,PluginArtifactLoaderTest,FileManagementAppServiceTest,ClientIpResolverTest" test`
  - 结果：`BUILD SUCCESS`。
- 2026-06-19：通过 updater 脚本认证回归：
  - `node scripts/lumira-updater-auth.test.mjs`
- 2026-06-19：执行仓库级回归验证：
  - `.\mvnw.cmd clean test`：15 个模块通过，`lumira-server` 清理阶段因历史 Java 进程锁定 `target/lumira-server-0.1.0.jar` 中断。
  - `.\mvnw.cmd -pl services/lumira-server,services/ai-service -am test`：17 个模块回归通过。
  - `corepack pnpm --dir frontend install --frozen-lockfile`：通过。
  - `corepack pnpm --dir frontend run lint`：通过。
  - `corepack pnpm --dir frontend run typecheck`：通过。
  - `corepack pnpm --dir frontend run test`：12 个测试文件、37 个用例通过。
  - `corepack pnpm --dir frontend run test:smoke`：通过。
  - `scripts/*.test.mjs` 顺序执行：全部通过。

## 尚未闭环

- 生产环境渗透测试、真实外部资产扫描、第三方服务现场核验尚未执行，需要明确授权环境、目标资产、时间窗口和测试边界。
- 真实部署后的代理链、updater 端口暴露、真实更新源签名、备份目录权限、日志留存与告警联动仍需在现场环境复核。
- 本轮未使用多子代理深度扫描能力；当前会话没有可调用的子代理调度工具，因此结论按主代理全仓库复核与自动化测试证据出具。
