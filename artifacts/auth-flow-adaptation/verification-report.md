# 认证入口适配验证报告

- 日期：2026-08-21（Asia/Shanghai）
- 范围：登录、短信注册、找回密码、注册策略开关移除、数据库迁移与国际化契约
- 本地地址：`http://127.0.0.1:8000/user/login`
- 本地运行模式：原生 `dev`，验收结束后已停止

## 实现结论

- 注册和找回密码都在 `/user/login` 的现有认证面板原位切换，没有新增注册页、找回密码页或业务路由。
- 注册表单复用短信验证码登录的 `challenge` / `complete` 链路；新手机号在验证码通过后仍由现有控制面创建普通用户并登录。
- 注册策略始终开放；`security.registration-enabled` 已从运行时代码、管理端安全设置、全新数据库基线中移除，在线迁移会删除旧配置。
- 短信服务是否可用仍由验证码配置决定；本地短信未启用时，注册入口保持可见并给出准确的依赖提示，不伪造成功。
- 找回密码继续复用统一验证码响应适配器，模拟短信响应仍可触发统一调试弹窗。

## 自动化测试结果

| 轮次 | 命令/范围 | 结果 |
| --- | --- | --- |
| 后端第一轮 | `InternalSystemControllerTest, SecuritySettingsRequestTest, SecuritySettingsServiceTest, SystemVerificationAppServiceTest` | 首次 122 项中 1 项失败：移除注册配置后持久化数量从 17 变为 16；断言已修复，并新增旧 key 不再写入的断言 |
| 后端第二轮 | 同上 | 122/122 通过 |
| Auth 服务 | `AuthAppServiceTest, SecuritySettingsServiceTest` | 87/87 通过 |
| 前端全量第一轮 | `pnpm test` | 136 个文件、659/659 通过 |
| 前端全量第二轮 | `pnpm test` | 136 个文件、659/659 通过 |
| 验证码响应适配 | `challengeResponseAdapter`、`requestSuccessAdapters` | 7/7 通过 |
| 认证入口契约 | `bin/auth-entry-adapter-contract.test.mjs` | 4/4 通过 |
| 数据库/国际化契约 | 认证、平台设置、数据库国际化契约 | 16/16 通过 |
| 仓库 Node 全量 | `bin/*.test.mjs` | 215 项：212 通过、3 项因宿主环境能力跳过、0 失败 |
| 数据库迁移链 | `node bin/check-database-migrations.mjs` | 通过，基线与最新迁移均为 `202608210007` |
| 类型检查 | `pnpm run typecheck` | 通过 |
| 样式检查 | `pnpm run stylelint` | 通过 |
| 生产构建 | `pnpm run build` | 通过，134 个输出资源 |
| 差异检查 | `git diff --check` | 通过，仅有既有 CRLF 提示 |

`pnpm run test:database-i18n` 严格审计仍报告工作区既有的 17 个缺失数据库 key 和多处历史硬编码基线回归；本次新增的 `page.login.backToLogin`、`page.login.registerAndLogin` 不在缺失列表，且数据库目录完整性契约通过。

## 浏览器验收结果

1. 登录入口保持 `/user/login`，登录表单本身未增加重复标题。
2. 点击“注册账号”后 URL 不变，面板状态变为 `registration`。
3. 注册状态展示手机号、验证码、发送验证码、注册并登录和返回登录；不展示密码字段。
4. 当前本地短信未启用，点击发送验证码后仍停留在注册面板，并提示“当前未启用短信验证码登录”；没有弹出独立注册页或对话框。
5. 点击“忘记密码”后 URL 不变，面板状态变为 `password-reset`，页面中存在一个找回密码表单、对话框数量为 0。
6. 找回密码从“账号”进入“验证”步骤后，绑定邮箱/手机号选择、上一步和下一步均正常显示。
7. 最终 DOM 断言：注册使用手机号字段为 `true`、注册使用密码字段为 `false`、找回密码内嵌表单数量为 `1`、对话框数量为 `0`。

浏览器运行时固定为 1280px 视口，未提供可用的视口缩放接口，因此本轮没有伪造 390px 浏览器证据；移动端媒体查询已通过类型检查、样式检查和两轮前端全量测试。

## 环境清理

- 浏览器验收标签页已全部关闭。
- 本地原生环境已执行 `npm run stop:local` 并停止。
- 未创建测试用户，未修改本地插件启停状态或短信 provider 配置。
