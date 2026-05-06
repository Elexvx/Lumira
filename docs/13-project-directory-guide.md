# 项目目录结构说明

> 说明：这是一份当前仓库的目录快照说明，重点解释“每个文件夹里面有什么、负责什么”。
> `node_modules/`、`target/`、`frontend/src/.umi/`、`frontend/src/.umi-production/` 属于生成产物，不作为主源码结构重点。

## 1. 根目录

```text
legendary-invention/
├─ README.md                 仓库总说明、启动方式、架构概览
├─ pom.xml                   根 Maven 父 POM，聚合 backend / services / libs
├─ backend/                  主后端 system-service
├─ frontend/                 前端管理端
├─ services/                 独立微服务集合
├─ libs/                     共享库与内部契约
├─ docs/                     架构、设计、迁移与规范文档
├─ database/                 数据库脚本与初始化文件
├─ deploy/                   部署相关文件
├─ .codex/                   Codex 本地配置
├─ .vscode/                  编辑器配置
└─ node_modules/             前端依赖缓存，不属于源码
```

### 根目录各项说明

- `README.md`：项目的整体入口说明，包含仓库定位、技术栈和启动方式。
- `pom.xml`：根 Maven 聚合文件，定义了多模块结构和统一依赖版本。
- `backend/`：当前系统主后端，承载 system-service 的核心业务。
- `frontend/`：前端管理台，负责页面、布局、权限、登录态和 API 调用。
- `services/`：微服务拆分后的独立服务，每个服务都有自己的 `pom.xml` 和 `main` 启动类。
- `libs/`：共享能力和内部接口契约，供后端各模块与服务复用。
- `docs/`：架构设计、目录规范、数据库设计、权限设计、微服务重构说明等。
- `database/`：独立数据库脚本，例如 `saas.sql`。
- `deploy/`：部署说明和 Docker Compose 相关文件。

## 2. backend/

`backend/` 现在是主后端工程，也就是 `system-service`。它不是“所有服务的容器”，而是一个独立 Spring Boot 应用。

### 2.1 backend 顶层

```text
backend/
├─ pom.xml
├─ src/
│  ├─ main/java/com/legendary/invention/saas/
│  └─ main/resources/
├─ storage/                  本地运行时存储目录
└─ target/                   Maven 构建产物
```

- `pom.xml`：定义 system-service 的依赖、插件和构建方式。
- `src/main/java/com/legendary/invention/saas/`：后端源码根包。
- `src/main/resources/`：配置文件、Flyway 迁移、日志配置、启动 banner 等。
- `storage/`：本地运行时存储目录，通常用于文件、任务、日志等。
- `target/`：构建产物目录，属于生成内容。

### 2.2 backend 源码分层

```text
backend/src/main/java/com/legendary/invention/saas/
├─ SaasApplication.java      后端启动类
├─ common/                   通用响应、异常、分页、常量
├─ infrastructure/           安全、租户、Redis、Trace、任务、上传等基础设施
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

这一层放的是技术基础设施，不放业务规则。

- `config/`：Web MVC、上传安全、Jackson 兼容等配置。
- `event/`：平台事件、Outbox、事件发布与转发。
- `http/`：Feign 请求头转发配置。
- `job/`：内部任务控制器。
- `observability/`：TraceId、请求上下文。
- `redis/`：Redis 配置与缓存模板。
- `repeatsubmit/`：防重复提交切面。
- `security/`：认证过滤器、权限上下文、安全配置、JWT 逻辑。
- `sentinel/`：限流与熔断相关配置。
- `tenant/`：租户上下文、租户过滤器。
- `upload/`：上传相关能力。

#### `modules/`

业务模块按领域拆分，每个模块内部再按职责分层。

##### `audit/`

- `app/`：审计应用服务。
- `controller/`：审计接口控制器。
- `entity/`：审计实体。
- `mapper/`：审计表映射器。
- 作用：登录审计、操作审计等日志记录。

##### `auth/`

- `app/`：登录、刷新、第二因素、加密等应用服务。
- `controller/`：认证相关接口。
- `dto/`：登录请求、刷新请求、挑战请求等入参。
- `vo/`：认证返回结果、登录加密 key、会话信息等。
- 作用：登录、登出、刷新 token、验证码、二次验证、会话管理。

##### `config/`

- `controller/`：健康检查等系统基础接口。
- 作用：承载一些最基础的系统入口。

##### `file/`

- `app/`：文件管理应用服务。
- `controller/`：文件上传、下载、文件中心接口。
- `vo/`：文件展示对象。
- 作用：文件上传、文件元数据、访问控制。

##### `iam/`

- `service/`：权限守卫、权限快照、菜单树等能力。
- 作用：角色、菜单、按钮权限、权限判断。

##### `localization/`

- `app/`：本地化管理、语言包构建、发布等。
- `controller/`：本地化中心接口。
- `dto/`：语言、命名空间、翻译、发布请求。
- `vo/`：语言、命名空间、条目、发布结果、运行时 bundle。
- 作用：语言包管理、翻译管理、运行时语言包发布。

##### `message/`

- `app/`：消息中心应用服务。
- `config/`：消息相关配置。
- `controller/`：消息列表、未读数、WebSocket、归档等接口。
- `dto/`：消息创建、查询、通知等请求。
- `service/`：消息推送、会话校验、消息处理。
- `vo/`：消息列表、通知、WebSocket 凭证等返回对象。
- 作用：站内信、消息推送、消息中心实时通信。

##### `plugin/`

- `app/`：插件管理应用服务。
- `controller/`：插件管理接口。
- `dto/`：插件安装、启停、配置请求。
- `entity/`：插件实体。
- `gateway/`：插件网关控制器。
- `loader/`：插件包加载与运行时加载。
- `mapper/`：插件数据库映射。
- `registry/`：插件注册与运行时描述。
- `runtime/`：插件运行时配置、上下文、SPI、调度、健康检查等。
- `service/`：插件迁移、持久化、版本处理。
- `vo/`：插件展示对象。
- 作用：插件安装、运行、卸载、权限、菜单和生命周期管理。

##### `system/`

- `app/`：系统管理、在线会话管理、系统路由目录等。
- `controller/`：用户、角色、菜单、配置、个人中心、监控、公开接口等。
- `dto/`：系统管理入参。
- `monitor/`：系统监控应用、控制器、返回对象。
- `online/`：在线会话事件、发布、订阅、Redis Stream 支持。
- `support/`：SMTP 等辅助能力。
- `verification/`：系统验证、TOTP、Base32 编解码等。
- `vo/`：系统页面返回对象。
- 作用：后台系统管理主模块。

##### `tenant/`

- `app/`：租户相关应用逻辑。
- `controller/`：租户相关接口。
- `domain/`：租户领域规则。
- `dto/`：租户请求对象。
- `entity/`：租户实体。
- `mapper/`：租户数据库映射。
- `vo/`：租户展示对象。
- 作用：租户生命周期、租户查询、租户切换等。

##### `user/`

- `domain/`：用户领域逻辑。
- `entity/`：用户实体。
- `mapper/`：用户数据访问。
- 作用：用户基础数据与领域模型。

### 2.3 backend 资源目录

```text
backend/src/main/resources/
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

## 3. frontend/

`frontend/` 是前端管理端，负责登录、路由、布局、权限、设置中心、页面、服务请求和多语言。

### 3.1 frontend 顶层

```text
frontend/
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

### 3.2 frontend/src 顶层文件

```text
frontend/src/
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

### 3.4 frontend/src 主要功能目录

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
- `TenantSelector/`：租户切换器
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

- `BlankLayout/`：空白布局
- `SettingsLayout/`：设置中心布局
- `UserLayout/`：登录和用户相关布局
- `components/`：顶部动作区、消息中心抽屉等布局组件

#### `locales/`

- `zh-CN.ts`、`en-US.ts` 等本地化文案。

#### `navigation/`

- 放导航相关配置，例如设置中心导航。

#### `pages/`

这是页面目录，按业务域分层。

- `audit/`：审计页面
- `dashboard/`：首页仪表盘
- `exception/`：异常页，包括无权限、未找到等
- `files/`：文件中心
- `iam/`：权限相关页面
- `localization/`：本地化页面
- `plugins/`：插件运行容器
- `profile/`：个人中心
- `settings/`：系统设置中心
- `system/`：系统管理页面
- `tenant/`：租户相关页面
- `user/`：登录页
- `user-center/`：用户中心

##### `pages/profile/`

- `center/`：个人中心主体
- `center/components/`：个人资料卡、绑定弹窗、二次验证弹窗等

##### `pages/settings/`

系统设置中心的主页面集合，里面包括：

- `dicts/`：字典管理
- `files/`：文件中心设置
- `localization/`：本地化管理
- `menus/`：菜单管理
- `monitoring/`：监控中心
- `notifications/`：通知中心
- `personalization/`：品牌、协议、水印等个性化设置
- `plugins/`：插件设置
- `profile-fields/`：个人资料字段设置
- `security/`：安全设置
- `verification/`：验证设置

##### `pages/system/`

- `roles/`：角色管理
- `users/`：用户管理
- `online-users.tsx`：在线用户管理
- `smtp.tsx`：SMTP 配置

##### `pages/tenant/`

- `overview/`：租户概览
- `overview/components/`：租户概览卡片、表格、操作组件等

##### `pages/user/`

- `Login.tsx`、`Login.css`：登录页
- `login/components/`：登录表单相关组件
- `login/captchaInput.ts`：验证码输入逻辑

#### `plugins/`

- 前端运行时插件、错误边界、manifest、registry 等。

#### `query/`

- 请求查询客户端和统一数据请求层。

#### `responsive/`

- 断点、响应式工具和设备适配。

#### `services/`

接口封装目录，按业务域拆分：

- `audit/`：审计相关接口
- `auth/`：认证接口
- `common/`：通用请求、错误处理
- `config/`：配置相关接口
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
- `tenant/`：租户接口
- `user/`：用户接口

#### `tenant/`

- 租户上下文、租户选择、租户缓存清理等前端逻辑。

#### `theme/`

- 主题配置、Ant Design 主题映射、偏好设置。

#### `types/`

- TypeScript 类型定义。

#### `utils/`

- 工具函数，例如剪贴板、确认框、脱敏、上传 URL、校验器等。

#### `watermark/`

- 水印配置和显示控制。

## 4. services/

`services/` 下每个目录都是一个独立 Spring Boot 服务。它们都各自有自己的 `pom.xml`、`src/main/java`、`src/main/resources`，部分还有 `src/test/java`。

```text
services/
├─ gateway-service/          统一网关
├─ auth-service/             认证服务
├─ tenant-service/           租户服务
├─ file-service/             文件服务
├─ message-service/          消息服务
├─ plugin-service/           插件服务
├─ localization-service/     本地化服务
├─ job-executor/             XXL-JOB 执行器
└─ audit-service/            已剥离的旧审计服务目录，若残留通常仅是历史或生成物
```

### 4.1 gateway-service

- `src/main/java/com/legendary/invention/gateway/`：网关启动类和 Sentinel 配置。
- `src/main/resources/application.yml`：路由、网关端口、Nacos、Sentinel 相关配置。
- 负责统一入口、路由转发、限流和入口治理。

### 4.2 auth-service

- `src/main/java/com/legendary/invention/auth/`：认证启动类、认证应用服务、控制器等。
- `src/main/resources/`：认证服务配置。
- 负责登录、会话、刷新、认证内部能力。

### 4.3 tenant-service

- `src/main/java/com/legendary/invention/tenant/`：租户服务启动类、控制器、领域服务。
- 负责租户查询、租户切换、租户可见性等。

### 4.4 file-service

- `src/main/java/com/legendary/invention/file/`：文件服务启动类、文件控制器、安全过滤器。
- 负责文件上传、下载、公开资源和文件鉴权。

### 4.5 message-service

- `src/main/java/com/legendary/invention/message/`：消息服务、WebSocket、内部任务、鉴权逻辑。
- 负责站内消息、实时消息、消息 outbox、会话校验。

### 4.6 plugin-service

- `src/main/java/com/legendary/invention/plugin/`：插件服务启动类、插件管理、运行时、网关适配。
- 负责插件生命周期、插件运行时和插件扩展能力。

### 4.7 localization-service

- `src/main/java/com/legendary/invention/localization/`：本地化服务启动类。
- 负责独立语言包、本地化资源、翻译中心相关能力。

### 4.8 job-executor

- `src/main/java/com/legendary/invention/job/`：XXL-JOB 执行器、任务处理器、后端调用客户端。
- 负责 outbox relay、消息心跳、在线会话心跳等后台任务。

### 4.9 audit-service

- 该目录在当前工程里已经不再作为活跃服务主线使用。
- 如果目录还残留，通常是历史遗留或构建产物，不代表它仍在参与当前启动链路。

## 5. libs/

`libs/` 是共享库和内部契约层，供后端主服务和各独立服务复用。

```text
libs/
├─ common-core/
├─ common-web/
├─ common-security/
├─ common-tenant/
└─ legendary-api/
```

### 5.1 common-core

- `src/main/java/com/legendary/invention/common/api/`：统一响应对象。
- `src/main/java/com/legendary/invention/common/constant/`：通用常量。
- `src/main/java/com/legendary/invention/common/enums/`：错误码和基础枚举。
- `src/main/java/com/legendary/invention/common/exception/`：业务异常。
- `src/main/java/com/legendary/invention/common/vo/`：通用 VO。

### 5.2 common-web

- `src/main/java/com/legendary/invention/common/web/`：请求上下文、Trace、Feign 请求头转发等。

### 5.3 common-security

- `src/main/java/com/legendary/invention/common/security/`：当前用户对象、内部令牌过滤器、权限守卫、安全上下文。

### 5.4 common-tenant

- `src/main/java/com/legendary/invention/common/tenant/`：租户上下文对象。

### 5.5 legendary-api

- `src/main/java/com/legendary/invention/api/auth/`：认证 DTO。
- `src/main/java/com/legendary/invention/api/client/`：内部 Feign 接口。
- `src/main/java/com/legendary/invention/api/file/`：文件 DTO。
- `src/main/java/com/legendary/invention/api/message/`：消息 DTO。
- `src/main/java/com/legendary/invention/api/system/`：系统侧 DTO、菜单树、权限快照、验证能力。
- `src/main/java/com/legendary/invention/api/tenant/`：租户 DTO。
- 作用：服务之间共享的契约和数据对象。

## 6. docs/

`docs/` 存放项目设计和架构说明。

```text
docs/
├─ 01-technical-scheme.md
├─ 02-directory-module-spec.md
├─ 03-database-design.md
├─ 04-interface-spec.md
├─ 05-permission-rbac.md
├─ 06-frontend-architecture.md
├─ 07-backend-architecture.md
├─ 08-development-roadmap.md
├─ 09-first-phase-codex-execution.md
├─ 10-first-round-codex-prompt.md
├─ 11-bootstrap-setup.md
├─ 12-microservice-restructure.md
├─ 12-realtime-message-notification-api.md
├─ 13-project-directory-guide.md
└─ antdesign-doc/
```

- `01-...` 到 `12-...`：前期技术方案、架构设计、权限设计、重构说明等。
- `13-project-directory-guide.md`：就是这份目录结构说明。
- `antdesign-doc/`：本地 Ant Design 参考文档。

## 7. database/

- `saas.sql`：数据库脚本或初始化脚本，通常包含基础表结构和数据。

## 8. deploy/

- `README.md`：部署说明。
- `docker-compose.yml`：容器化部署示例。

## 9. 如何判断“排版是否工整”

从目录结构上看，这个仓库整体是比较工整的，原因是：

- 顶层按职责分成了 `backend/`、`frontend/`、`services/`、`libs/`、`docs/`、`database/`、`deploy/`
- 后端按 `common / infrastructure / modules` 分层
- 前端按 `pages / services / components / layouts / auth / i18n / theme` 分层
- 独立服务都放在 `services/` 下，边界清楚
- 共享能力都集中在 `libs/`

比较需要注意的地方是：

- `frontend/src/.umi/` 和 `frontend/src/.umi-production/` 是生成目录，会让树看起来更杂
- `backend/modules/plugin/runtime/runtime/` 这种重复命名的目录，视觉上会显得有点绕
- `audit-service` 目录目前已不在主线中，建议后续如果确认不再使用，可以考虑把历史残留再彻底清掉

如果你要继续，我可以下一步把这份文档再整理成“更适合提交到仓库的正式版”，比如：

1. 去掉重复和过细的说明，变成更像规范文档的版本。
2. 再补一版“目录树 + 作用说明”的图表版，阅读起来更直观。
