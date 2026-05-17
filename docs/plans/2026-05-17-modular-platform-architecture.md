# 模块化平台底座架构梳理与实施方案

**目标：** 将当前系统从“已有多个业务模块的 SaaS 后台”梳理成“底座能力稳定、业务模块可组合、插件能力可演进”的长期平台架构。

**架构判断：** 当前仓库已经具备 `system-service`、网关、若干独立服务骨架、`common-*` 共享库、插件运行时、官网 CMS、审批、评审等模块。后续不应先把所有业务堆完再拆，也不应一开始把所有场景拆成独立服务，而应先建立模块注册协议和统一能力边界，在统一控制面内完成软插件化，再按成熟度迁移为独立服务或动态插件。

**技术栈：** Java 21、Spring Boot 4、Spring Cloud Alibaba、Spring Cloud Gateway、MyBatis Plus、MySQL、Redis、Flyway、React 19、Umi Max、Ant Design、Next.js App Router、插件 SPI、Maven 多模块。

---

## 1. 结论

本项目的目标应定义为：

> 一个平台底座，内置统一认证、权限、菜单、文件、消息、配置、审计、任务、审批、评审、表单、内容发布和插件运行能力；比赛、期刊、官网、活动、会议等业务系统不是独立重写的一套系统，而是由这些能力组合出来的场景模块。

当前最优路线是：

```text
统一底座
  -> 通用能力模块
    -> 业务场景模块
      -> 模块注册 / 启停 / 权限 / 菜单 / API / 前端入口
        -> 成熟后再独立服务化或插件包化
```

也就是：

- 先定义模块边界和注册协议。
- 再把现有能力按“底座 / 通用能力 / 场景模块 / 插件运行时”重新归类。
- 新业务先按模块边界在当前控制面里纵向隔离开发。
- 当某个模块稳定、边界清楚、部署周期独立、流量或安全要求独立时，再拆成独立服务或插件包。

不推荐：

- 先把比赛、期刊、活动、会议、官网等全部写进一个大系统，后面再拆。这样会把权限、表、菜单、流程、文件、消息全部缠在一起，拆分成本最高。
- 一开始就把每个业务都拆成独立微服务或动态插件。当前很多能力还需要复用统一权限、统一菜单、统一审计、统一提交、统一评审，过早物理拆分会放大联调成本。

## 2. 当前系统现状

### 2.1 已经具备的平台底座

从当前仓库看，平台已经不是普通后台模板，而是已经进入微服务平台骨架阶段：

- 根 `pom.xml` 已聚合 `backend`、`services/*` 和 `libs/*`。
- `backend/` 当前承担 `system-service`，保留核心管理控制面和一批业务域。
- `services/gateway-service/` 已收口统一入口。
- `services/auth-service/`、`file-service/`、`message-service/`、`plugin-service/`、`localization-service/`、`job-executor/` 已有独立服务骨架。
- `libs/common-core/`、`common-web/`、`common-security/`、`legendary-api/` 已承担共享契约和基础能力。
- 管理前端 `frontend/` 已经以后端菜单和权限为主驱动菜单。
- 官网前端 `site-frontend/` 已独立出来，通过公开 API 读取已发布内容。

这说明当前项目已经适合走“平台控制面 + 能力模块 + 逐步服务化”的路线。

### 2.2 已经存在的通用能力模块

当前系统已经有一些模块天然适合作为通用能力，而不是归属于某一个具体业务：

| 能力 | 当前落点 | 定位 |
| --- | --- | --- |
| 审批 | `backend/src/main/java/.../modules/approval`、`approval_*` 表 | 通用流程能力 |
| 评审 | `backend/src/main/java/.../modules/evaluation`、`evaluation_*` 表 | 通用评分/评审能力 |
| 官网/CMS | `backend/src/main/java/.../modules/site`、`site_*` 表、`site-frontend/` | 通用内容、页面、表单、提交能力雏形 |
| 文件 | `services/file-service`、`file_object` | 平台底座能力 |
| 消息 | `services/message-service`、站内信、WebSocket/outbox | 平台底座能力 |
| 任务 | `backend/modules/task`、`services/job-executor` | 通用任务中心和调度能力 |
| 插件 | `services/plugin-service`、`sys_plugin_*` 表、前端运行容器 | 扩展运行时能力 |
| 本地化 | `services/localization-service`、`frontend/src/services/localization` | 平台底座能力 |
| AI | `backend/modules/ai`、`frontend/src/pages/ai` | 业务增强能力，可作为可选模块 |

其中 `approval`、`evaluation`、`site_form`、`site_form_submission` 最值得抽象成“业务模块可复用能力”，因为比赛、期刊、活动、会议都会重复使用。

### 2.3 当前插件能力边界

`plugin-service` 已经有比较完整的插件管理模型：

- `sys_plugin_definition`：插件定义。
- `sys_plugin_version`：插件版本、包路径、校验报告、激活状态。
- `sys_plugin_dependency`：插件依赖。
- `sys_plugin_menu_rel`：插件声明菜单。
- `sys_plugin_permission_rel`：插件声明权限。
- `sys_plugin_tenant`：插件在租户/平台数据域下启停和配置。
- `sys_plugin_runtime_log`：插件运行日志。

后端运行时已经支持：

- 上传插件包。
- 安装、升级、回滚、启用、停用、卸载。
- 加载 `backend/plugin.jar`。
- 通过 `ServiceLoader` 加载 SPI。
- 插件声明菜单、权限、健康检查、HTTP handler、定时任务、二次验证 provider。
- `/api/p/{pluginCode}/**` 转发到插件 HTTP handler。

前端运行时已经支持：

- `/plugins/:pluginCode` 作为插件容器。
- 启动时拉取 `current/menus` 和 `current/available`。
- 根据插件 manifest 加载前端资源。
- `mountPlugin` / `unmountPlugin` 挂载插件 UI。

因此当前不是“没有插件化基础”，而是需要明确：

- 哪些能力应该内置为核心模块。
- 哪些能力应该作为可选业务模块。
- 哪些能力未来才值得做成动态插件包。

## 3. 目标分层

### 3.1 平台底座层

底座层只放所有模块都需要复用、且不应该被业务模块重复实现的能力。

建议底座层包括：

- 登录、会话、Token、二次验证。
- 用户、角色、权限、菜单。
- 统一请求链路、traceId、requestId。
- 统一响应、错误码、异常处理。
- 文件上传、下载、对象元数据。
- 消息通知、站内信、WebSocket、outbox。
- 审计日志、操作日志、登录日志。
- 字典、配置、品牌、协议、安全策略。
- 任务调度、异步任务、重试。
- 本地化。
- 网关、服务发现、配置中心、限流、分布式事务。
- 插件安装、启停、运行时加载、插件 API 网关。

开发红线：

- 业务模块不得绕开统一权限。
- 业务模块不得自己实现文件存储。
- 业务模块不得自己实现消息通知。
- 业务模块不得自己维护菜单权限快照。
- 业务模块不得直接操作其他模块的表作为长期方案。
- 业务模块不得把 controller 写成业务核心。

### 3.2 通用能力模块层

通用能力模块是多个业务场景都会复用的业务能力。它们可以先内置在 `system-service` 或独立服务骨架中，但必须按领域隔离。

建议沉淀为通用能力的模块：

| 模块编码 | 模块名称 | 当前基础 | 后续目标 |
| --- | --- | --- | --- |
| `approval` | 审批流程 | 已有 `approval_*` | 为投稿、报名、发布、配置变更提供流程能力 |
| `evaluation` | 评审评分 | 已有 `evaluation_*` | 为比赛评审、论文审稿、项目评分复用 |
| `submission` | 提交/投稿/报名 | 目前散落在 `site_form_submission` | 抽成通用提交能力 |
| `form` | 动态表单 | 目前在 `site_form.schema_json` | 抽成通用表单模型 |
| `content` | 内容发布 | 目前在 `site_content`、`site_page` | 支持资讯、公告、期刊文章发布 |
| `notification` | 通知模板 | 已有消息能力 | 抽模板、触发条件、发送策略 |
| `result` | 结果发布 | 目前可由 `evaluation_result` 支撑 | 比赛结果、录用结果、审核结果复用 |
| `member` | 申请人/会员中心 | 暂未独立 | 对外用户提交、查看状态、补件、通知 |

注意：`submission` 和 `form` 当前不要急着从 `site` 表里强拆，可以先通过新文档和新代码规范要求后续新增业务使用通用命名和通用服务接口，后续迁移时再把 `site_form` 逐步上升为 `form_definition` / `submission_record`。

### 3.3 业务场景模块层

业务场景模块负责把通用能力组合成具体产品形态。

建议业务场景模块包括：

| 场景模块 | 组合能力 | 说明 |
| --- | --- | --- |
| `competition` | 表单、提交、文件、评审、结果、通知、官网发布 | 比赛、评选、奖项申报 |
| `journal` | 投稿、文件、编辑初审、专家评审、录用通知、内容归档 | 期刊、论文、文章征集 |
| `conference` | 报名、投稿、议程、评审、通知、证书 | 会议系统 |
| `activity` | 报名表单、审核、签到、通知 | 活动管理 |
| `official-site` | 站点、导航、页面、内容、表单、公开 API | 当前 `site` 模块已覆盖一部分 |
| `ai-workbench` | AI 员工、技能、对话、工具调用 | 可作为高级可选能力 |

业务场景模块不应该重复实现底层能力，而应该通过用例编排复用它们。

示例：

```text
competition
  -> form: 报名表
  -> submission: 报名记录
  -> file: 附件和作品
  -> evaluation: 专家评分
  -> approval: 结果复核
  -> notification: 通知参赛人
  -> content/site: 公开展示获奖结果
```

```text
journal
  -> form: 投稿信息表
  -> submission: 稿件提交记录
  -> file: 原稿、修改稿、附件
  -> approval: 编辑部初审
  -> evaluation: 专家审稿
  -> notification: 录用/退修/退稿通知
  -> content/site: 已发布文章或期刊目录
```

### 3.4 插件运行时层

插件层用于扩展平台，而不是替代所有内置模块。

适合插件化的能力：

- 第三方登录或二次验证 provider。
- 短信、邮件、支付、对象存储等供应商适配。
- 独立交付的小业务模块。
- 特定客户定制页面或流程。
- 不影响主链路稳定性的增强能力。

不建议第一阶段插件化的能力：

- 登录主链路。
- 权限主链路。
- 菜单权限快照主链路。
- 核心文件中心。
- 核心审批/评审/提交模型。
- 数据库基础结构。

这些能力应该先在底座或通用能力模块中稳定，插件只通过明确 SPI 或 API 调用它们。

## 4. 模块注册协议

所有模块都应该有一个统一的注册描述。内置模块可以用代码/数据库种子注册，动态插件可以用 manifest 注册。

建议统一字段：

```json
{
  "moduleCode": "journal",
  "moduleName": "期刊投稿",
  "moduleType": "SCENE",
  "version": "1.0.0",
  "description": "面向期刊文章投稿、审稿和发布的业务场景模块",
  "dependencies": [
    { "moduleCode": "form", "minVersion": "1.0.0" },
    { "moduleCode": "submission", "minVersion": "1.0.0" },
    { "moduleCode": "evaluation", "minVersion": "1.0.0" },
    { "moduleCode": "file", "minVersion": "1.0.0" },
    { "moduleCode": "message", "minVersion": "1.0.0" }
  ],
  "menus": [
    {
      "menuCode": "journal.submissions",
      "parentMenuCode": "journal.root",
      "menuName": "投稿管理",
      "routePath": "/journal/submissions",
      "component": "@/pages/journal/submissions",
      "icon": "FileTextOutlined",
      "permissionKey": "journal:submission:view",
      "sortNo": 1
    }
  ],
  "permissions": [
    {
      "permissionKey": "journal:submission:view",
      "permissionName": "查看投稿",
      "permissionGroup": "journal"
    }
  ],
  "apiPrefixes": ["/api/v1/journal/**"],
  "publicApiPrefixes": ["/api/v1/public/journal/**"],
  "configSchema": {},
  "events": {
    "publishes": ["journal.submission.created", "journal.review.completed"],
    "subscribes": ["file.uploaded"]
  }
}
```

### 4.1 模块类型

建议模块类型分为：

| 类型 | 含义 | 示例 |
| --- | --- | --- |
| `FOUNDATION` | 平台底座，不可随意停用 | auth、system、file、message、audit |
| `CAPABILITY` | 通用能力，可被多个场景复用 | approval、evaluation、form、submission、content |
| `SCENE` | 业务场景模块 | competition、journal、conference、activity |
| `ADAPTER` | 外部系统或供应商适配 | sms、wechat、payment、storage |
| `PLUGIN` | 动态插件包 | 客户定制模块、第三方扩展 |

### 4.2 生命周期

建议统一生命周期：

```text
DRAFT -> INSTALLED -> ENABLED -> DISABLED -> DEPRECATED -> UNINSTALLED
```

对于内置模块：

- 不一定存在上传安装过程。
- 仍然应该支持 `ENABLED` / `DISABLED` 控制菜单和入口。
- `FOUNDATION` 模块通常不可停用，只能配置。

对于动态插件：

- 继续沿用当前 `upload`、`install`、`enable`、`disable`、`upgrade`、`rollback`、`uninstall`。
- 启停后必须刷新权限快照和菜单。

## 5. 后端落地规范

### 5.1 包结构

新内置模块建议遵循：

```text
backend/src/main/java/com/legendary/invention/saas/modules/<module>/
  controller/
  app/
  domain/
  dto/
  vo/
  mapper/
  entity/
  event/
  support/
```

当前 `site`、`evaluation`、`approval` 已经基本符合这个方向，但部分模块缺少 `entity` / `mapper` 或领域层，后续增强时应补齐。

### 5.2 API 前缀

建议统一：

| 类型 | 前缀 |
| --- | --- |
| 管理 API | `/api/v1/<module>/**` |
| 公开 API | `/api/v1/public/<module>/**` |
| 内部 API | `/internal/<module>/**` |
| 插件 API | `/api/p/{pluginCode}/**` |

当前存在 `/api/approvals`、`/api/evaluations`、`/api/tasks` 这类未带 `/v1` 的路径。短期可以保留兼容，新增业务模块应统一使用 `/api/v1/<module>`。

### 5.3 数据表命名

建议：

```text
<module>_<aggregate>
<module>_<aggregate>_record
<module>_<aggregate>_version
<module>_<aggregate>_rel
```

示例：

- `competition_event`
- `competition_entry`
- `journal_submission`
- `journal_review_round`
- `form_definition`
- `submission_record`
- `evaluation_template`
- `approval_template`

当前 `approval_*`、`evaluation_*`、`site_*` 命名是可接受的，应继续保持。

### 5.4 模块间调用

优先级：

1. 同一服务内通过 app/service 调用，不跨 controller。
2. 已独立服务通过 `legendary-api` 定义内部契约。
3. 跨服务写操作通过事件/outbox 解耦。
4. 插件只能通过公开 SPI、HTTP handler 上下文或受控内部 API 调用平台能力。

禁止：

- 一个模块直接操作另一个模块的 mapper。
- 一个模块拼 SQL 改另一个模块的数据表。
- 一个模块绕开 `PermissionGuard`。
- 插件直接参与平台主事务。

### 5.5 事件建议

建议建立统一事件命名：

```text
<module>.<aggregate>.<action>
```

示例：

- `submission.record.created`
- `approval.instance.completed`
- `evaluation.task.submitted`
- `journal.submission.accepted`
- `competition.entry.reviewed`
- `site.content.published`

事件至少包含：

- `eventKey`
- `tenantId`
- `moduleCode`
- `aggregateType`
- `aggregateId`
- `operatorId`
- `payloadJson`
- `traceId`
- `requestId`

## 6. 前端落地规范

### 6.1 目录结构

新内置模块建议：

```text
frontend/src/pages/<module>/
  index.tsx
  components/
  columns.tsx
  forms.tsx
  detail.tsx

frontend/src/services/<module>/
  index.ts
```

当前已有：

- `frontend/src/pages/evaluations`
- `frontend/src/pages/approvals`
- `frontend/src/pages/site`
- `frontend/src/pages/tasks`
- `frontend/src/services/evaluation`
- `frontend/src/services/approval`
- `frontend/src/services/site`
- `frontend/src/services/task`

后续 `competition`、`journal` 应按同样方式落地。

### 6.2 菜单和权限

当前菜单来源是：

- 后端返回 `menuTree`。
- 前端 `frontend/src/routes/meta.ts` 提供本地路由元信息。
- `frontend/src/access.ts` 根据权限判断访问能力。
- `frontend/src/app.layout.tsx` 将后端菜单与本地路由组合。
- 插件菜单由 `pluginService.currentMenus()` 加入启动状态。

因此新增模块必须同时完成：

- 后端菜单注册。
- 后端权限注册。
- 角色权限分配。
- 前端路由元信息。
- `access.ts` 访问判断。
- `services/<module>` 请求封装。
- 页面级按钮权限控制。

模块缺任何一环，都会出现“页面有了但菜单不可见”或“按钮禁用/接口无权限”的问题。

### 6.3 插件前端

动态插件前端继续使用：

- `/plugins/:pluginCode`
- `frontend/src/pages/plugins/RuntimeContainer.tsx`
- `frontend/src/plugins/loader.ts`
- `frontend/src/plugins/runtime.ts`
- 插件 manifest

插件前端不应要求修改主前端路由文件。插件只声明菜单到 `/plugins/<pluginCode>` 或自身受控子路径。

## 7. 比赛模块设计示例

### 7.1 模块定位

`competition` 是场景模块，不是底座能力。

它负责组织比赛业务概念：

- 比赛项目。
- 赛道/组别。
- 报名入口。
- 作品提交。
- 评审安排。
- 结果发布。

它不应该自己实现：

- 文件上传。
- 表单引擎。
- 专家评分底层能力。
- 审批流程底层能力。
- 消息通知。
- 官网发布。

### 7.2 推荐组合

```text
competition_event
  -> form_definition
  -> submission_record
  -> file_object
  -> evaluation_instance
  -> approval_instance
  -> message_notice
  -> site_content
```

### 7.3 第一阶段最小闭环

交付：

- 比赛列表。
- 比赛详情。
- 报名表配置。
- 报名记录。
- 创建评审实例。
- 专家评分。
- 归档结果。
- 结果在官网展示。

不交付：

- 复杂赛制。
- 多轮晋级。
- 自动分组。
- 证书生成。
- 线下签到。

这些应作为后续增强模块。

## 8. 期刊模块设计示例

### 8.1 模块定位

`journal` 是场景模块，核心是“稿件生命周期”。

它应复用：

- 表单能力收集投稿信息。
- 文件能力保存稿件。
- 审批能力做编辑初审。
- 评审能力做专家审稿。
- 消息能力通知作者。
- 内容能力发布录用文章。

### 8.2 推荐状态流

```text
DRAFT
  -> SUBMITTED
  -> EDITOR_REVIEW
  -> EXPERT_REVIEW
  -> REVISION_REQUIRED
  -> ACCEPTED
  -> REJECTED
  -> PUBLISHED
```

### 8.3 第一阶段最小闭环

交付：

- 期刊栏目/专题。
- 投稿入口。
- 投稿记录。
- 编辑初审。
- 专家评审。
- 录用/退修/拒稿。
- 已录用文章进入内容库。

不交付：

- 查重系统。
- 排版系统。
- DOI。
- 多期刊复杂出版流程。
- 作者中心复杂档案。

这些等提交、审稿、发布主链路稳定后再扩展。

## 9. 实施路线

### Phase 1：先补“模块注册规范”

目标：让内置模块和动态插件共享一套模块元数据语言。

任务：

1. 新增文档约束模块类型、编码、生命周期、菜单、权限、API、事件。
2. 梳理现有 `site`、`evaluation`、`approval`、`plugin` 的模块元数据。
3. 建议新增内置模块注册表或代码注册器，例如 `platform_module_definition`。
4. 先不要迁移所有老数据，先让新模块按规范注册。

验收：

- 新模块能通过统一清单看到模块编码、类型、依赖、菜单、权限。
- 插件和内置模块在概念上能被同一套模块中心展示。

### Phase 2：抽象提交和表单能力

目标：避免期刊、比赛、活动各写一套提交表单。

任务：

1. 以当前 `site_form` 和 `site_form_submission` 为参照，设计通用 `form_definition` 与 `submission_record`。
2. 保留 `site` 现有接口不破坏官网。
3. 新场景模块优先调用通用表单/提交 app service。
4. 给 `submission_record` 预留 `source_module`、`source_business_type`、`source_business_id`。

验收：

- 官网表单、比赛报名、期刊投稿可以共用同一种提交结构。
- 提交记录可按来源模块过滤。

### Phase 3：把评审能力明确为通用能力

目标：让比赛评审、期刊审稿都使用 `evaluation`。

任务：

1. 保留 `evaluation_template.object_type` 作为业务对象类型。
2. 统一 `object_type` 编码，例如 `competition_entry`、`journal_submission`。
3. 为场景模块提供创建评审实例的 app service。
4. 把评审结果通过事件回写场景模块。

验收：

- `competition` 不需要新建评分表。
- `journal` 不需要新建审稿评分表。
- 两者只维护自己的业务状态和对评审结果的引用。

### Phase 4：建设第一个场景模块

建议优先选择 `journal` 或 `competition` 其中一个。

如果希望验证“提交 + 文件 + 评审 + 内容发布”，优先 `journal`。

如果希望验证“报名 + 专家评分 + 结果发布”，优先 `competition`。

不要两个一起开。第一套场景模块的价值是验证底座组合方式，不是堆功能数量。

### Phase 5：服务化或插件化

当一个模块满足以下条件后，再做物理拆分：

- 有独立部署节奏。
- 有独立扩缩容需求。
- 与主系统只通过 API/事件/契约通信。
- 数据表边界清晰。
- 权限、菜单、配置、审计都已走模块注册协议。
- 有完整测试覆盖和迁移脚本。

拆分优先级建议：

1. 供应商适配类：短信、支付、对象存储。
2. 前后端相对独立的小业务：活动、会议。
3. 流量或安全边界强的业务：期刊、比赛。
4. 最后才考虑核心通用能力拆分：审批、评审、提交。

## 10. 近期代码改造清单

### 已落地进度

第一步已经先以低风险只读方式落地：

- 新增 `PlatformModuleCatalog`，集中登记现有 `system`、`auth`、`file`、`message`、`localization`、`approval`、`evaluation`、`task`、`site`、`plugin`、`ai`，并预登记 `form`、`submission`、`journal`、`competition`、`sms`。
- 新增 `GET /api/v1/system/modules`，用于后续模块中心页面、模块启停、菜单权限分组和插件/内置模块统一展示。
- 前端 `systemService.modules()` 和 `PlatformModuleRecord` 类型已接入，后续可以直接建设模块中心 UI。
- 新增 `/settings/modules` 模块中心只读页面，已挂入系统设置导航、路由元信息和内置菜单目录，当前只做展示不做启停。
- 模块中心补充了模块详情抽屉，可以查看模块编码、类型、生命周期、来源、所属服务、管理入口、依赖模块、API 边界和权限声明。
- 新增 `docs/15-module-registration-spec.md`，把模块字段、生命周期、依赖、菜单权限、API 和事件规则沉淀成独立规范。
- 模块清单新增依赖健康计算，后端可返回缺失依赖、未启用依赖、依赖是否满足、是否可作为启用候选和阻塞原因。
- 新增 `GET /api/v1/system/modules/{moduleCode}` 单模块详情接口，前端详情抽屉已改为按模块编码加载最新详情。
- 新增 `PlatformModuleRegistry` 契约和 `StaticPlatformModuleRegistry` 实现，系统管理服务已改为依赖注册表契约，为后续数据库注册表和插件 manifest 多来源合并预留入口。
- 新增 `platform_module_definition`、`platform_module_dependency` 持久化表，当前只读接入，不提供启停写操作。
- 新增 `DatabasePlatformModuleRepository` 和 `CompositePlatformModuleRegistry`，模块中心现在可以合并静态内置清单与数据库模块定义，数据库定义可覆盖同编码静态模块。
- 模块中心管理面补充来源治理能力，列表和详情可区分 `BUILTIN`、`DATABASE`、`PLUGIN`、`MANIFEST`，并支持按来源筛选和统计数据库注册模块、阻塞模块。
- 新增 `journal` 期刊场景数据库注册种子，模块中心可真实展示 `DATABASE` 来源并覆盖静态 `PLANNED` 定义，当前仍保留依赖阻塞以反映 `form`、`submission` 尚未启用。
- 模块详情新增注册诊断字段，后端返回 `overriddenByDatabase`、`registrationSourceOrder`、`registeredAt`，前端详情抽屉可展示数据库定义是否覆盖内置定义、来源合并顺序和注册时间。
- 新增 `POST /api/v1/system/modules/validate` 写入前校验接口，只校验不落库，可检测重复编码、非法类型/状态/来源、缺失依赖、未启用依赖和循环依赖。
- 模块中心新增“校验草案”抽屉，可输入模块草案并调用校验接口展示阻塞项、警告、缺失依赖、未启用依赖和循环路径；当前仍不保存。
- 新增 `POST /api/v1/system/modules` 受控创建接口，当前只允许创建 `DATABASE` 来源、`PLANNED` 生命周期模块，必须校验通过，不支持覆盖、编辑、删除或启停。

### Task 1: 新增模块注册文档和清单

**Files:**

- Create: `docs/plans/2026-05-17-modular-platform-architecture.md`
- Created: `backend/src/main/java/com/legendary/invention/saas/modules/system/module/PlatformModuleCatalog.java`
- Created: `backend/src/main/java/com/legendary/invention/saas/modules/system/module/PlatformModuleRegistry.java`
- Created: `backend/src/main/java/com/legendary/invention/saas/modules/system/module/StaticPlatformModuleRegistry.java`
- Created: `backend/src/main/java/com/legendary/invention/saas/modules/system/module/DatabasePlatformModuleRepository.java`
- Created: `backend/src/main/java/com/legendary/invention/saas/modules/system/module/CompositePlatformModuleRegistry.java`
- Created: `backend/src/main/java/com/legendary/invention/saas/modules/system/module/PlatformModuleDefinitionValidator.java`
- Created: `backend/src/main/java/com/legendary/invention/saas/modules/system/module/vo/PlatformModuleVO.java`
- Created: `backend/src/main/java/com/legendary/invention/saas/modules/system/module/vo/PlatformModuleValidationVO.java`
- Created: `backend/src/test/java/com/legendary/invention/saas/modules/system/module/PlatformModuleCatalogTest.java`
- Created: `backend/src/test/java/com/legendary/invention/saas/modules/system/module/StaticPlatformModuleRegistryTest.java`
- Created: `backend/src/test/java/com/legendary/invention/saas/modules/system/module/CompositePlatformModuleRegistryTest.java`
- Created: `backend/src/test/java/com/legendary/invention/saas/modules/system/module/PlatformModuleDefinitionValidatorTest.java`
- Created: `backend/src/main/resources/db/migration/V11__platform_module_registry.sql`
- Created: `backend/src/main/resources/db/migration/V12__seed_platform_module_journal.sql`
- Modified: `backend/src/main/java/com/legendary/invention/saas/modules/system/controller/SystemController.java`
- Modified: `backend/src/main/java/com/legendary/invention/saas/modules/system/app/SystemManagementAppService.java`
- Modified: `frontend/src/types/api.ts`
- Modified: `frontend/src/services/system/index.ts`
- Created: `frontend/src/pages/settings/modules/index.tsx`
- Modified: `frontend/src/routes/meta.ts`
- Modified: `frontend/src/navigation/settingsNavigation.tsx`
- Modified: `frontend/src/access.ts`
- Modified: `frontend/src/locales/zh-CN.ts`
- Modified: `frontend/src/locales/en-US.ts`
- Modified: `backend/src/main/java/com/legendary/invention/saas/modules/system/app/SystemRouteCatalog.java`
- Created: `docs/15-module-registration-spec.md`

**Steps:**

1. 固化本文档。
2. 从本文档提取稳定规范到 `docs/15-module-registration-spec.md`。
3. 在 README 推荐阅读中加入模块注册规范。

**Verify:**

```bash
rg -n "moduleCode|模块注册|competition|journal" docs
```

### Task 2: 建立内置模块清单

**Files:**

- Later Create: `backend/src/main/java/com/legendary/invention/saas/modules/system/module/PlatformModuleCatalog.java`
- Later Modify: `backend/src/main/java/com/legendary/invention/saas/modules/system/app/SystemRouteCatalog.java`

**Steps:**

1. 定义内置模块元数据 record。
2. 登记 `system`、`file`、`message`、`approval`、`evaluation`、`site`、`plugin`、`localization`、`ai`。
3. 让菜单、权限、模块中心逐步从统一清单派生。

**Verify:**

```bash
mvn -q -pl backend -am -DskipTests compile
```

### Task 3: 统一新增模块 API 规范

**Files:**

- Later Modify: `docs/04-interface-spec.md`
- Later Modify: `docs/07-backend-architecture.md`

**Steps:**

1. 明确新增管理 API 必须使用 `/api/v1/<module>/**`。
2. 明确公开 API 使用 `/api/v1/public/<module>/**`。
3. 标注 `/api/approvals`、`/api/evaluations`、`/api/tasks` 为历史兼容入口。

**Verify:**

```bash
rg -n "@RequestMapping\\(\"/api/(approvals|evaluations|tasks)" backend/src/main/java
```

### Task 4: 提交/表单能力抽象方案

**Files:**

- Later Create: `docs/plans/2026-05-17-form-submission-capability-plan.md`
- Later Review: `backend/src/main/java/com/legendary/invention/saas/modules/site/app/SiteManagementAppService.java`
- Later Review: `backend/src/main/resources/db/migration/V1__baseline.sql`

**Steps:**

1. 从 `site_form`、`site_form_submission` 提取通用字段。
2. 设计兼容迁移方案，不破坏现有官网。
3. 定义 `form` 和 `submission` app service 接口。

**Verify:**

```bash
rg -n "site_form|site_form_submission|schema_json|submit_policy" backend/src/main/resources/db/migration backend/src/main/java
```

### Task 5: 选择第一个场景模块

**Files:**

- Later Create: `docs/plans/YYYY-MM-DD-journal-module-mvp.md` or `docs/plans/YYYY-MM-DD-competition-module-mvp.md`

**Decision:**

- 若优先验证投稿、审稿、内容发布，选择 `journal`。
- 若优先验证报名、评分、结果发布，选择 `competition`。

**Verify:**

选择后再进入代码设计，不在本文档里直接展开所有业务表，避免第一次场景模块过大。

## 11. 总体判断

当前系统已经具备走模块化平台的基础，但还缺一个明确的“模块注册中枢”和“通用能力复用边界”。

下一步不应该继续随业务名称直接堆页面和接口，而应该按下面顺序推进：

```text
模块规范
  -> 内置模块清单
    -> 表单/提交抽象
      -> 评审/审批复用规范
        -> 第一个业务场景模块
          -> 插件化/服务化增强
```

这样做的好处是：

- 比赛不是孤立比赛系统，而是 `competition + form + submission + evaluation + content`。
- 期刊不是孤立期刊系统，而是 `journal + submission + approval + evaluation + content`。
- 官网不是静态站，而是 `site + content + form + submission + public API`。
- 插件不是另起炉灶，而是通过模块注册、菜单、权限、API 网关、运行时协议接入底座。

最终系统会变成一个真正可长期演进的平台，而不是多个业务系统被强行塞进同一个后台。
