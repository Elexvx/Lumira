# Lumira 安全评估整改跟踪表

评估批次：`495458a5_20260619-134359`

基线提交：`89381581`

## 范围与依据

本跟踪表用于承接 GB/T 22239-2019、GB/T 28449-2018、GB/T 20984-2022、GB/T 30279-2020、OWASP Testing Guide V4、MITRE ATT&CK Enterprise、NIST SP 800-115 对照评估后的整改闭环。

## 发现项状态

| ID | 控制域 | 风险 | 当前状态 | 验证方式 |
|---|---|---:|---|---|
| DEPLOY-OPS-ADMIN-DEFAULT-001 | 身份鉴别/安全运维 | Critical | 已修复，待全量复测 | `DefaultAdminPasswordBaselineTest` |
| SQL-DATA-001 | 访问控制 | High | 已修复，待全量复测 | `SystemRoleManagementAppServiceTest` |
| EXT-CALLBACK-001 | 访问控制/内部接口 | High | 已修复，待全量复测 | `AiCommandServiceTest` |
| EXT-CALLBACK-002 | 安全运维/供应链 | High | 已修复，待全量复测 | `PlatformUpdateAppServiceTest` |
| CAND-PLUGIN-PATH-001 | 文件上传/插件管理 | High | 已修复，待全量复测 | `PluginArtifactLoaderTest` |
| AUTH-IAM-001 | 身份鉴别/会话管理 | High | 已修复，待全量复测 | `AuthAppServiceTest` |
| AUTH-IAM-002 | 身份鉴别/登录防护 | High | 已修复，待全量复测 | `AuthAppServiceTest` |
| DEPLOY-OPS-UPDATER-AUTH-001 | 安全运维/管理接口 | High | 已修复，待全量复测 | `node scripts/lumira-updater-auth.test.mjs` |
| FILE-PLUGIN-FILE-ROOT-001 | 文件存储边界 | Medium-High | 已修复，待全量复测 | `FileManagementAppServiceTest` |
| CAND-FORWARDED-IP-001 | 边界防护/审计 | Medium | 已修复，待全量复测 | `ClientIpResolverTest` |
| DEPLOY-OPS-BACKUP-ENV-001 | 备份/机密保护 | Medium | 已修复，待全量复测 | 静态检查：`deploy/backup-platform.sh`、`.gitignore` |

## 运行记录

- 2026-06-19：已提交现有工作树作为整改基线 `89381581`。
- 2026-06-19：完成 11 项报告问题的第一轮代码整改。
- 2026-06-19：通过针对性 Java 回归测试：
  - `.\mvnw.cmd "-pl" "services/auth-service,services/system-service,services/ai-service,services/plugin-service,services/file-service,libs/common-web" "-Dtest=AuthAppServiceTest,DefaultAdminPasswordBaselineTest,SystemRoleManagementAppServiceTest,PlatformUpdateAppServiceTest,AiCommandServiceTest,PluginArtifactLoaderTest,FileManagementAppServiceTest,ClientIpResolverTest" test`
  - 结果：BUILD SUCCESS，36 个 Java 测试通过。
- 2026-06-19：通过 updater 脚本认证回归：
  - `node scripts/lumira-updater-auth.test.mjs`

## 尚未闭环

- 尚未运行仓库全量测试。
- 尚未逐条关闭 `deep_review_input.csv` 的全部工作项。
- 尚未执行生产环境代理链、updater 端口暴露、真实更新源签名、备份目录权限等现场复核。
