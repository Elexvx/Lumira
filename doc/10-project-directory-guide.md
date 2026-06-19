# 项目目录结构说明

> 说明：这是一份当前仓库的目录快照说明，重点解释“每个文件夹里面有什么、负责什么”。
> `node_modules/`、`target/`、`lumira-ui/src/.umi/`、`lumira-ui/src/.umi-production/` 属于生成产物，不作为主源码结构重点。

## 1. 根目录

```text
lumira/
├─ README.md                 仓库总说明、启动方式、架构概览
├─ pom.xml                   根 Maven 父 POM，聚合 services / libs
├─ services/                 聚合后端与各模块目录
├─ lumira-ui/                 前端管理端
├─ libs/                     共享库与内部契约
├─ doc/                     架构、设计、迁移与规范文档
├─ sql/                 数据库脚本与初始化文件
├─ deploy/                   部署相关文件
├─ .codex/                   Codex 本地配置
├─ .vscode/                  编辑器配置
└─ node_modules/             前端依赖缓存，不属于源码
```

### 根目录各项说明

- `README.md`：项目的整体入口说明，包含仓库定位、技术栈和启动方式。
- `pom.xml`：根 Maven 聚合文件，定义了多模块结构和统一依赖版本。
- `services/`：聚合后端入口 `lumira-server` 和各业务模块目录。
- `lumira-ui/`：前端管理台，负责页面、布局、权限、登录态和 API 调用。
- `libs/`：共享能力和内部接口契约，供后端各模块与服务复用。
- `doc/`：架构设计、目录规范、数据库设计、权限设计、微服务重构说明等。
- `sql/`：独立数据库脚本，例如 `saas.sql`。
- `deploy/`：部署说明和 Docker Compose 相关文件。

## 2. services/ 与聚合后端入口

当前正式运行入口是 `services/lumira-admin/`。它在运行时聚合 `system-service`、`auth-service`、`file-service`、`message-service`、`plugin-service`、`localization-service`、`payment-service` 和 `job-executor` 模块。

`services/lumira-system/` 不再是独立对外主入口，而是聚合后端中的核心业务模块之一。

### 2.1 services 顶层

```text
services/
├─ lumira-server/         当前默认后端启动入口
├─ system-service/           系统、权限、AI、审计等核心模块
├─ auth-service/             认证模块
├─ file-service/             文件模块
├─ message-service/          消息模块
├─ plugin-service/           插件模块
├─ localization-service/     国际化模块
├─ payment-service/          支付模块
└─ job-executor/             作业执行器
```

- `lumira-server/`：Spring Boot 聚合启动类和正式打包入口。
- `*-service/`：按业务边界拆分的 Maven 模块，既是逻辑边界，也是未来再次拆成物理微服务时的基础。

### 2.2 lumira-server

```text
services/lumira-admin/
├─ pom.xml
└─ src/main/java/com/lumira/server/
   ├─ LumiraServerApplication.java
   └─ config/
```

作用说明：

- `LumiraServerApplication.java`：当前默认启动类。
- `lumira-server` 自身不承载大量业务实现，它的核心职责是把各模块聚合成一个正式运行进程。

### 2.3 system-service 模块结构

`services/lumira-system/` 仍然是平台核心模块，主要承载系统配置、权限、用户体系、AI、审计和监控能力。

```text
services/lumira-system/src/main/java/com/lumira/saas/
├─ common/                   通用响应、异常、分页、常量
├─ infrastructure/           安全、Redis、Trace、任务、事件等基础设施
└─ modules/                  业务模块
```

#### `common/`

- `annotation/`：通用注解，例如重复提交控制。
- `api/`：统一响应结构，例如 `ApiResponse`。
- `constant/`：Header、缓存 key 等常量。
- `enums/`：错误码、状态枚举等。
- `exception/`：业务异常、全局异常处理。
- `util/`：请求上下文等通用工具。
- `vo/`：分页结果等通用返回对象。

#### `infrastructure/`

- 这一层放技术基础设施，不放业务规则。
- 包含 `event/`、`job/`、`redis/`、`security/`、`config/` 等公共实现。

#### `modules/`

业务模块按领域拆分，每个模块内部再按职责分层。当前重点包括：

- `ai/`：AI 员工、模型服务、知识库、工具治理、对话和分享。
- `audit/`：登录审计、操作审计等日志记录。
- `iam/`：角色、菜单、按钮权限、权限快照。
- `system/`：用户、角色、菜单、配置、个人中心、监控、公开接口等。
- `user/`：用户基础数据与领域模型。

### 2.4 system-service 资源目录

```text
services/lumira-system/src/main/resources/
├─ application.yml           主配置
├─ application-dev.yml       开发环境配置
├─ application-test.yml      测试环境配置
├─ application-prod.yml      生产环境配置
├─ banner.txt                启动 Banner
├─ db/migration/             Flyway 数据库迁移
└─ logback-spring.xml        日志配置
```

- `application*.yml`：不同环境的配置入口。
- `db/migration/`：数据库版本脚本，记录表结构和初始化数据演进。
- `logback-spring.xml`：日志输出格式与级别控制。

## 3. lumira-ui/

`lumira-ui/` 是前端管理端，负责登录、路由、布局、权限、设置中心、页面、服务请求和多语言。

### 3.1 lumira-ui 顶层

```text
lumira-ui/
├─ package.json              前端依赖和脚本
├─ pnpm-lock.yaml            pnpm 锁文件
├─ .umirc.ts                 Umi 配置入口
├─ src/                      前端源码
└─ .turbopack/               构建缓存/生成内容
```

- `package.json`：脚本、依赖、烟雾测试命令。
- `pnpm-lock.yaml`：锁定前端依赖版本。
- `.umirc.ts`：Umi 运行配置。
- `.turbopack/`：构建缓存，属于生成内容。

### 3.2 lumira-ui/src 顶层文件

```text
lumira-ui/src/
├─ app.tsx / app.*           全局启动、布局、面包屑、bootstrap
├─ access.ts                 路由访问控制
├─ loading.tsx               全局加载页
├─ global.css                全局样式
├─ routes/meta.ts            路由元数据
├─ services/                 API 封装
├─ pages/                    路由页面
├─ layouts/                  布局壳层
├─ components/               通用组件
├─ auth/                    登录态、token、验证码、会话
├─ i18n/                    多语言
├─ theme/                   主题与配置
├─ utils/                   工具函数
└─ ...
```

### 3.3 `.umi/` 与 `.umi-production/`

这两个目录都是 Umi 自动生成内容。

- `.umi/`：开发态生成文件，负责路由、插件、初始化逻辑。
- `.umi-production/`：生产构建生成文件。
- 这两个目录一般不手工编辑，只看生成结果，不把它们当成源码主结构。

### 3.4 lumira-ui/src 主要功能目录

#### `agreement/`

- 放协议或设置相关的基础配置。

#### `assets/`

- 放静态资源，如图片、图标、通用素材。

#### `auth/`

- `session.ts`、`token.ts`、`captcha.ts`、`loginEncryption.ts` 等。
- 负责登录态、token 生命周期、验证码、错误反馈、会话活动控制。

#### `bootstrap/`

- 放启动阶段的状态存储和初始化逻辑。

#### `branding/`

- 放品牌配置、站点视觉信息和品牌设置相关逻辑。

#### `cache/`

- 放前端缓存封装和存储策略。

#### `components/`

通用可复用组件目录，里面有：

- `ActionBar/`：操作栏
- `DataTable/`：表格封装
- `DetailDrawer/`：详情抽屉
- `DetailForm/`：详情表单
- `DetailSection/`：详情区块
- `EmptyState/`：空状态
- `ManagementPageContainer/`：管理页容器
- `PageDetailDescriptions/`：详情描述区
- `PermissionButton/`：权限按钮
- `QueryPanel/`：查询区
- `ResponsiveTable/`：响应式表格
- `captcha/`：验证码组件
- `common/`：通用小组件
- `message-center/`：消息中心相关组件

#### `constants/`

- 放前端常量，如 HTTP 常量、UI 常量等。

#### `enums/`

- 放前端枚举定义。

#### `features/`

这是通用能力层，里面有：

- `crud/`：CRUD 状态管理
- `detail/`：详情页配置
- `form/`：表单配置
- `management/`：管理页壳层
- `permissions/`：权限 hook 和权限动作
- `table/`：表格操作和封装

#### `hooks/`

- 放复用型 React hooks。

#### `i18n/`

- `antdLocale.ts`：Ant Design 语言包映射
- `locale.ts`：语言切换和归一化
- `runtimeLocalization.ts`：运行时语言包加载

#### `layouts/`

布局壳层目录，里面有：

- `AiLayout/`：AI 助手页布局
- `DashboardLayout/`：登录后主后台布局
- `SettingsLayout/`：设置中心布局
- `components/`：顶部动作区、消息中心抽屉等布局组件

#### `locales/`

- `zh-CN.ts`、`en-US.ts` 等本地化文案。

#### `navigation/`

- 放导航相关配置，例如设置中心导航。

#### `pages/`

这是页面目录，按业务域分层。

- `ai/`：AI 助手与分享页
- `dashboard/`：首页仪表盘
- `exception/`：异常页，包括无权限、未找到等
- `files/`：文件中心
- `plugins/`：插件运行容器
- `profile/`：个人中心
- `settings/`：系统设置中心
- `system/`：系统管理页面
- `user/`：登录页

##### `pages/profile/`

- `Center.tsx`：个人中心主体
- `center/components/`、`center/hooks/`：个人资料卡、绑定弹窗、二次验证弹窗等

##### `pages/settings/`

系统设置中心的主页面集合，里面包括：

- `ai-employees/`：AI 员工管理
- `dicts.tsx`：字典管理
- `localization/`：本地化管理
- `menus.tsx`：菜单管理
- `monitoring/`：监控中心、审计页
- `notifications/`：通知中心
- `payment.tsx`：支付设置
- `personalization.tsx`：品牌、协议、水印等个性化设置
- `plugins/`：插件设置
- `profile-fields.tsx`：个人资料字段设置
- `security.tsx`：安全设置
- `verification.tsx`：验证设置

##### `pages/system/`

- `departments.tsx`：部门管理
- `online-users.tsx`：在线用户管理
- `roles.tsx`：角色管理
- `users.tsx`：用户管理

##### `pages/user/`

- `Login.tsx`、`Login.css`：登录页
- `login/`：登录表单组件、验证码逻辑、hooks 和样式拆分

#### `plugins/`

- 前端运行时插件、错误边界、manifest、registry 等。

#### `query/`

- 请求查询客户端和统一数据请求层。

#### 响应式能力

- 当前未单独拆出 `responsive/` 顶级目录，断点、设备适配与布局收敛在 `layouts/`、`hooks/`、`theme/` 等目录中。

#### `services/`

接口封装目录，按业务域拆分：

- `audit/`：审计相关接口
- `auth/`：认证接口
- `common/`：通用请求、错误处理
- `dashboard/`：首页数据接口
- `dict/`：字典接口
- `file/`：文件接口
- `iam/`：权限接口
- `localization/`：本地化接口
- `message/`：消息接口
- `plugin/`：插件接口
- `profile/`：个人中心接口
- `secondFactor/`：二次验证接口
- `system/`：系统管理接口
- `user/`：用户接口

#### `theme/`

- 主题配置、Ant Design 主题映射、偏好设置。

#### `types/`

- TypeScript 类型定义。

#### `utils/`

- 工具函数，例如剪贴板、确认框、脱敏、上传 URL、校验器等。

#### `watermark/`

- 水印配置和显示控制。

## 4. services/* 业务模块

`services/` 下每个目录都是一个 Maven 模块。当前默认以“单体微服务”方式运行，即由 `lumira-server` 聚合启动；但这些目录本身仍保持清晰模块边界，为未来再次拆成独立服务预留条件。

```text
services/
├─ lumira-server/         当前默认后端启动入口
├─ auth-service/             认证服务
├─ file-service/             文件服务
├─ message-service/          消息服务
├─ plugin-service/           插件服务
├─ localization-service/     本地化服务
├─ payment-service/          支付服务
└─ job-executor/             XXL-JOB 执行器
```

### 4.1 lumira-server

- `src/main/java/com/lumira/server/`：聚合启动类和少量运行配置。
- 当前部署、健康检查、日志、监控和打包都以它为准。

### 4.2 auth-service

- `src/main/java/com/lumira/auth/`：认证启动类、认证应用服务、控制器等。
- `src/main/resources/`：认证服务配置。
- 负责登录、会话、刷新、认证内部能力。

### 4.3 file-service

- `src/main/java/com/lumira/file/`：文件服务启动类、文件控制器、安全过滤器。
- 负责文件上传、下载、公开资源和文件鉴权。

### 4.5 message-service

- `src/main/java/com/lumira/message/`：消息服务、WebSocket、内部任务、鉴权逻辑。
- 负责站内消息、实时消息、消息 outbox、会话校验。

### 4.6 plugin-service

- `src/main/java/com/lumira/plugin/`：插件服务启动类、插件管理、运行时、网关适配。
- 负责插件生命周期、插件运行时和插件扩展能力。

### 4.7 localization-service

- `src/main/java/com/lumira/localization/`：本地化服务启动类。
- 负责独立语言包、本地化资源、翻译中心相关能力。

### 4.8 payment-service

- `src/main/java/com/lumira/payment/`：支付服务启动类、支付配置、回调和 Outbox。
- 负责支付服务商配置、订单、Webhook 和异步事件。

### 4.9 job-executor

- `src/main/java/com/lumira/job/`：XXL-JOB 执行器、任务处理器、后端调用客户端。
- 负责 outbox relay、消息心跳、在线会话心跳等后台任务。

## 5. libs/

`libs/` 是共享库和内部契约层，供后端主服务和各独立服务复用。

```text
libs/
├─ common-core/
├─ common-web/
├─ common-security/
└─ lumira-api/
```

### 5.1 common-core

- `src/main/java/com/lumira/common/api/`：统一响应对象。
- `src/main/java/com/lumira/common/constant/`：通用常量。
- `src/main/java/com/lumira/common/enums/`：错误码和基础枚举。
- `src/main/java/com/lumira/common/exception/`：业务异常。
- `src/main/java/com/lumira/common/vo/`：通用 VO。

### 5.2 common-web

- `src/main/java/com/lumira/common/web/`：请求上下文、Trace、Feign 请求头转发等。

### 5.3 common-security

- `src/main/java/com/lumira/common/security/`：当前用户对象、内部令牌过滤器、权限守卫、安全上下文。

### 5.4 lumira-api

- `src/main/java/com/lumira/api/auth/`：认证 DTO。
- `src/main/java/com/lumira/api/client/`：内部 Feign 接口。
- `src/main/java/com/lumira/api/file/`：文件 DTO。
- `src/main/java/com/lumira/api/message/`：消息 DTO。
- `src/main/java/com/lumira/api/system/`：系统侧 DTO、菜单树、权限快照、验证能力。
- 作用：服务之间共享的契约和数据对象。

## 6. doc/

`doc/` 存放项目设计和架构说明。

```text
doc/
├─ 01-technical-scheme.md
├─ 02-directory-module-spec.md
├─ 03-database-design.md
├─ 04-interface-spec.md
├─ 05-permission-rbac.md
├─ 06-lumira-ui-architecture.md
├─ 07-backend-architecture.md
├─ 08-development-roadmap.md
├─ 09-realtime-message-notification-api.md
├─ 10-project-directory-guide.md
├─ 11-1panel-container-deploy.md
├─ 12-plugin-duplication-maintenance.md
├─ 13-service-data-ownership.md
├─ 14-system-service-module-boundaries.md
├─ 15-gateway-auth-permission-boundaries.md
├─ 16-event-outbox-architecture.md
├─ 17-architecture-runbook.md
├─ 20-product-requirements-document.md
├─ 21-test-strategy-and-cases.md
├─ 22-test-execution-checklist-template.md
├─ 23-test-report-template.md
├─ 24-page-manual-test-workbook.md
├─ 24-page-manual-test-workbook.docx
└─ 25-monolith-service-split-readiness.md
```

- `01-...` 到 `08-...`：总体技术方案、架构规范、开发规范和实施路线。
- `09-...` 到 `17-...`：专项接口、目录说明、部署说明和架构治理专题。
- `10-project-directory-guide.md`：就是这份目录结构说明。
- `20-product-requirements-document.md`：产品需求书（PRD）。
- `21-...` 到 `25-...`：测试策略、测试模板、手工测试工作簿和单体微服务拆分边界说明。

## 7. sql/

- `saas.sql`：数据库脚本或初始化脚本，通常包含基础表结构和数据。

## 8. deploy/

- `README.md`：部署说明。
- `docker-compose.yml`：容器化部署示例。

## 9. 如何判断“排版是否工整”

从目录结构上看，这个仓库整体是比较工整的，原因是：

- 顶层按职责分成了 `services/`、`lumira-ui/`、`libs/`、`doc/`、`sql/`、`deploy/`
- 后端按 `common / infrastructure / modules` 分层
- 前端按 `pages / services / components / layouts / auth / i18n / theme` 分层
- 独立服务都放在 `services/` 下，边界清楚
- 共享能力都集中在 `libs/`

比较需要注意的地方是：

- `lumira-ui/src/.umi/` 和 `lumira-ui/src/.umi-production/` 是生成目录，会让树看起来更杂
- `services/lumira-system/modules/plugin/runtime/runtime/` 这种重复命名的目录，视觉上会显得有点绕
- 某些历史说明仍然会提到重新拆分后的独立服务形态，阅读时要以 `lumira-server` 聚合运行模式为当前事实

如果你要继续，我可以下一步把这份文档再整理成“更适合提交到仓库的正式版”，比如：

1. 去掉重复和过细的说明，变成更像规范文档的版本。
2. 再补一版“目录树 + 作用说明”的图表版，阅读起来更直观。
