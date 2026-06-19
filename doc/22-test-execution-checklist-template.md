# Lumira 测试执行台账模板

## 1. 使用说明

本文档用于测试执行阶段的逐条记录。建议每次提测复制一份，按版本单独保存，例如：

- `doc/test-runs/2026-06-11-v0.1.0-rc1-test-checklist.md`
- `doc/test-runs/2026-06-18-v0.1.0-release-test-checklist.md`
- `doc/test-runs/2026-06-18-v0.1.0-release-page-workbook.md`

执行方式：

1. 先填写版本、环境、执行人、测试范围
2. 逐页手工测试时，先复制 [`doc/24-page-manual-test-workbook.md`](/Users/johntao/Documents/GitHub/lumira/doc/24-page-manual-test-workbook.md)
3. 在逐页工作簿里填写每条用例的实际结果、状态、缺陷编号和备注
4. 本文档用于汇总页面执行结论、冒烟结果、自动化结果和风险项
5. 失败项必须关联缺陷编号
6. 阻塞项必须写明阻塞原因和责任人
7. 测试结束后汇总通过率与剩余风险

状态建议统一使用：

- `PASS`
- `FAIL`
- `BLOCKED`
- `SKIP`
- `NOT RUN`

优先级建议统一使用：

- `P0`
- `P1`
- `P2`
- `P3`

## 2. 测试轮次信息

| 字段 | 内容 |
| --- | --- |
| 测试版本 |  |
| 构建时间 |  |
| Git 提交号 |  |
| 测试环境 |  |
| 前端地址 |  |
| 后端地址 |  |
| 数据库实例 |  |
| Redis 实例 |  |
| 执行人 |  |
| 开始时间 |  |
| 结束时间 |  |
| 覆盖范围 |  |
| 备注 |  |

## 3. 环境检查清单

| 编号 | 检查项 | 检查结果 | 备注 |
| --- | --- | --- | --- |
| ENV-001 | 前端可访问 |  |  |
| ENV-002 | 后端健康检查正常 |  |  |
| ENV-003 | 数据库连接正常 |  |  |
| ENV-004 | Redis 连接正常 |  |  |
| ENV-005 | 测试账号已准备 |  |  |
| ENV-006 | 测试租户与角色已准备 |  |  |
| ENV-007 | 测试文件样本已准备 |  |  |
| ENV-008 | AI/插件/支付测试配置已准备 |  |  |
| ENV-009 | 日志与监控可查看 |  |  |
| ENV-010 | 自动化命令可执行 |  |  |

## 4. 冒烟测试执行台账

| 用例编号 | 用例名称 | 优先级 | 执行结果 | 缺陷编号 | 执行人 | 执行时间 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SMK-001 | 登录页打开正常，验证码正常 | P0 |  |  |  |  |  |
| SMK-002 | 账号密码登录成功 | P0 |  |  |  |  |  |
| SMK-003 | 当前用户信息与菜单正常返回 | P0 |  |  |  |  |  |
| SMK-004 | 用户列表页可打开并翻页 | P0 |  |  |  |  |  |
| SMK-005 | 角色管理页可打开并查看权限树 | P0 |  |  |  |  |  |
| SMK-006 | 文件上传和下载正常 | P0 |  |  |  |  |  |
| SMK-007 | 通知中心可查看未读消息 | P0 |  |  |  |  |  |
| SMK-008 | AI 助手页面可打开 | P0 |  |  |  |  |  |
| SMK-009 | 国际化管理页可查询 | P1 |  |  |  |  |  |
| SMK-010 | 系统监控页可打开 | P1 |  |  |  |  |  |
| SMK-011 | 健康检查接口返回 UP | P0 |  |  |  |  |  |
| SMK-012 | 部署检查脚本通过 | P0 |  |  |  |  |  |

## 5. 页面测试执行汇总

说明：

- 逐页详细操作、预期结果和留空填写位请使用 [`doc/24-page-manual-test-workbook.md`](/Users/johntao/Documents/GitHub/lumira/doc/24-page-manual-test-workbook.md)
- 本节用于按页面汇总本轮执行结论，方便提测、回归和上线汇报

| 页面分组 | 页面/路由 | 计划用例数 | 已执行 | 通过 | 失败 | 阻塞 | 页面结论 | 缺陷编号 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 认证入口 | `/user/login` |  |  |  |  |  |  |  |  |
| 工作台 | `/dashboard/home` |  |  |  |  |  |  |  |  |
| 下载中心 | `/download-center` |  |  |  |  |  |  |  |  |
| AI | `/ai/assistant` |  |  |  |  |  |  |  |  |
| AI | `/ai/knowledge` |  |  |  |  |  |  |  |  |
| AI | `/ai/share/:token` |  |  |  |  |  |  |  |  |
| 用户中心 | `/user-center/users` |  |  |  |  |  |  |  |  |
| 用户中心 | `/user-center/departments` |  |  |  |  |  |  |  |  |
| 用户中心 | `/user-center/online-users` |  |  |  |  |  |  |  |  |
| 用户中心 | `/user-center/roles` |  |  |  |  |  |  |  |  |
| 用户中心 | `/user-center/personal-center/profile` |  |  |  |  |  |  |  |  |
| 用户中心 | `/user-center/files` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/menus` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/dicts` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/profile-fields` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/personalization` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/security` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/verification` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/payment` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/notifications` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/ai-employees` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/plugins` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/localization` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/files/all` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/monitoring` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/api-docs` |  |  |  |  |  |  |  |  |
| 系统设置 | `/settings/audit` |  |  |  |  |  |  |  |  |
| 插件页 | `/plugins/sensitive-words` |  |  |  |  |  |  |  |  |
| 插件页 | `/plugins/:pluginCode` |  |  |  |  |  |  |  |  |
| 异常页 | `/403` |  |  |  |  |  |  |  |  |
| 异常页 | `/404` |  |  |  |  |  |  |  |  |

## 6. 自动化执行记录

| 编号 | 命令 | 结果 | 执行时间 | 备注 |
| --- | --- | --- | --- | --- |
| AUTO-001 | `./mvnw test` |  |  |  |
| AUTO-002 | `./mvnw -pl services/lumira-system -am test` |  |  |  |
| AUTO-003 | `./mvnw -pl services/lumira-auth -am test` |  |  |  |
| AUTO-004 | `./mvnw -pl services/lumira-file -am test` |  |  |  |
| AUTO-005 | `./mvnw -pl services/lumira-message -am test` |  |  |  |
| AUTO-006 | `corepack pnpm --dir lumira-ui test` |  |  |  |
| AUTO-007 | `corepack pnpm --dir lumira-ui test:coverage` |  |  |  |
| AUTO-008 | `corepack pnpm --dir lumira-ui test:smoke` |  |  |  |
| AUTO-009 | `node bin/check-deployment.mjs` |  |  |  |

## 7. 缺陷汇总

| 缺陷编号 | 标题 | 模块 | 等级 | 状态 | 是否阻塞上线 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |  |

## 8. 风险与阻塞项

| 编号 | 风险/阻塞描述 | 影响范围 | 当前处理人 | 预计解决时间 | 备注 |
| --- | --- | --- | --- | --- | --- |
| RISK-001 |  |  |  |  |  |

## 9. 测试结论

### 9.1 结果汇总

| 统计项 | 数值 |
| --- | --- |
| 总用例数 |  |
| 已执行 |  |
| 通过 |  |
| 失败 |  |
| 阻塞 |  |
| 跳过 |  |
| 通过率 |  |

### 9.2 结论建议

可直接填写以下其一：

- 建议上线
- 有条件上线
- 不建议上线

### 9.3 结论说明

请填写：

- 是否存在 P0/P1 缺陷
- 是否存在未验证高风险场景
- 是否完成自动化和冒烟
- 是否完成部署检查与回滚确认
