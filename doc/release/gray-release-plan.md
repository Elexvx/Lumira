# Lumira Gray Release Plan

## 发布前检查清单

- 确认 GitHub Actions `backend-test`, `frontend-build`, `migration-check`, `security-scan` 已在 `main` push 上触发并通过或生成已审批的 advisory 风险接受记录。
- 确认 `artifacts/tests/backend-test-evidence.json`, `artifacts/frontend/frontend-build-evidence.json`, `artifacts/migration/migration-check-evidence.json`, `artifacts/staging/staging-smoke-evidence.json`, `artifacts/release/production-readiness-evidence.json` 的 `gitCommitSha` 等于发布 commit。
- 确认生产环境变量已由发布负责人和安全确认人双人复核。
- 确认本次不包含大规模权限、租户、支付或文件存储架构改动。

## 数据库备份

- 发布前对生产 MySQL 做全量快照。
- 导出 `flyway_schema_history`、支付 webhook 事件表、文件对象表、AI 工具授权表和安全审计表。
- 记录备份位置、校验和、恢复演练负责人和恢复预计耗时。

## Migration 执行顺序

1. 在 staging 执行 `scripts/migration-check.sh` 或 `scripts/migration-check.ps1`。
2. 确认空库、旧库升级和重复执行均通过。
3. 生产进入只读或低写入窗口。
4. 执行 Flyway migration。
5. 校验关键表/字段：`ai_employee_tool_grant`, `payment_provider_config`, `file_processing_task.claim_token`, `platform_event_outbox.claim_token`, `security_audit_event`, `sys_department_closure`。

## 环境变量检查

- `JWT_SECRET`, `FIELD_SECRET`, `PLUGIN_SIGNATURE_SECRET`, `SAAS_JOB_INTERNAL_TOKEN`, `DB_PASSWORD` 必填且为强随机值。
- Redis password 必填，或有明确的内网隔离和安全组证据。
- CORS 不允许 `*`、`localhost` 或非生产域名。
- Swagger UI 和 api-docs 生产默认关闭或受保护。
- Grafana、XXL-JOB、对象存储和第三方支付凭据禁止默认弱口令。

## 镜像构建

- 使用 CI 从发布 commit 构建后端和前端镜像。
- 镜像 tag 包含完整 commit sha 或不可变 build id。
- 镜像扫描结果归档到 release evidence。

## 部署步骤

1. 发布后端镜像到 staging。
2. 发布前端镜像到 staging。
3. 执行 staging smoke。
4. 生产更新环境变量和镜像 tag。
5. 滚动部署后端，再部署前端/api proxy。
6. 保留上一稳定镜像 tag，直到 100% 灰度完成后再清理。

## 健康检查

- `/api/health`
- `/api/version`
- 登录和 `current-user`
- refresh cookie
- 文件上传/下载
- AI 工具 list/propose/confirm
- 支付 webhook invalid signature 拒绝
- 插件网关无权限拒绝

## 灰度比例

1. 内部账号。
2. 5%。
3. 20%。
4. 50%。
5. 100%。

## 每阶段观察指标

- 登录成功率。
- 5xx 错误率。
- API P95/P99。
- Redis 错误。
- DB 慢查询。
- AI 工具拒绝、确认、执行数量。
- webhook 拒绝数量。
- 文件上传失败率。

## 回滚触发条件

- 登录成功率低于基线 2 个百分点且持续 10 分钟。
- 5xx 错误率超过 1% 且持续 5 分钟。
- API P99 超过基线 2 倍且持续 10 分钟。
- 支付 webhook 签名校验或租户解析出现误拒/误处理。
- 文件上传失败率超过基线 2 倍。
- AI 工具出现未授权执行或高风险操作绕过确认/审批。

## 回滚步骤

- 停止扩大灰度，固定当前流量比例。
- 切回上一稳定后端和前端镜像 tag。
- 禁用高风险 AI 写工具和数字员工写操作。
- 支付 webhook 降级为只记录不处理。
- 保留新增数据表和上传文件，必要时从备份恢复业务数据。
- 执行 rollback smoke：health、login、current-user、file center、webhook、AI chat。

## 发布责任人和确认人

- 发布负责人：待填写。
- 数据库确认人：待填写。
- 安全确认人：待填写。
- 业务确认人：待填写。
- 最终上线确认人：待填写。
