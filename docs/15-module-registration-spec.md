# 模块注册规范

本文档定义平台底座的模块注册协议。后续比赛、期刊、活动、会议、供应商适配和客户定制插件都应先声明模块，再接入菜单、权限、API、事件和配置。

## 1. 目标

模块注册用于解决三个问题：

- 让内置模块、场景模块和动态插件能进入同一个模块中心。
- 让菜单、权限、API 前缀、依赖关系和生命周期有统一来源。
- 让业务功能先按模块边界开发，成熟后再独立服务化或插件包化。

模块注册不是简单的菜单分组。一个模块必须能说明自己是谁、依赖谁、暴露什么入口、需要什么权限、由哪个服务承载、当前处于什么生命周期。

## 2. 模块类型

| 类型 | 含义 | 示例 |
| --- | --- | --- |
| `FOUNDATION` | 平台底座能力，通常不可停用 | system、auth、file、message、localization |
| `CAPABILITY` | 可被多个业务场景复用的通用能力 | approval、evaluation、form、submission、site |
| `SCENE` | 由通用能力组合出来的业务场景 | journal、competition、conference、activity |
| `ADAPTER` | 第三方系统、供应商或外部协议适配 | sms、wechat、payment、storage |
| `PLUGIN` | 动态安装的插件包 | 客户定制模块、第三方扩展 |

## 3. 生命周期

当前只读清单已使用以下状态：

| 状态 | 含义 |
| --- | --- |
| `ENABLED` | 已启用，菜单/API/权限可以被使用 |
| `DISABLED` | 已安装但停用，入口应隐藏或阻断 |
| `PLANNED` | 已进入规划，但当前未落地运行 |
| `DEPRECATED` | 仍保留兼容，但不建议新业务依赖 |

动态插件的完整生命周期后续应扩展为：

```text
DRAFT -> INSTALLED -> ENABLED -> DISABLED -> DEPRECATED -> UNINSTALLED
```

内置底座模块不一定经历上传和安装过程，但仍应保留生命周期字段，便于模块中心统一展示和后续治理。

## 4. 标准字段

当前后端 `PlatformModuleVO` 已落地的字段如下：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `moduleCode` | 是 | 稳定模块编码，使用小写短横线或小写单词，不随名称变化 |
| `moduleName` | 是 | 展示名称 |
| `moduleType` | 是 | 模块类型 |
| `lifecycleStatus` | 是 | 生命周期状态 |
| `sourceType` | 是 | 来源类型，支持 `BUILTIN`、`DATABASE`、`PLUGIN`、`MANIFEST` |
| `description` | 否 | 模块说明 |
| `ownerService` | 是 | 承载服务或组合服务 |
| `adminRoutePath` | 否 | 管理端入口路由 |
| `apiPrefixes` | 否 | 模块拥有或暴露的 API 前缀 |
| `permissionKeys` | 否 | 模块声明的权限键 |
| `dependencies` | 否 | 依赖模块编码 |
| `dependencySatisfied` | 是 | 依赖是否全部存在且已启用 |
| `missingDependencies` | 是 | 当前注册表中不存在的依赖模块编码 |
| `inactiveDependencies` | 是 | 已存在但不是 `ENABLED` 状态的依赖模块编码 |
| `readyToEnable` | 是 | 当前模块是否可作为启用候选 |
| `readinessIssues` | 是 | 阻止模块启用或治理推进的原因 |
| `overriddenByDatabase` | 是 | 当前模块定义是否由数据库来源覆盖了内置来源 |
| `registrationSourceOrder` | 是 | 注册来源合并顺序，例如 `BUILTIN -> DATABASE` |
| `registeredAt` | 否 | 持久化注册时间；内置代码模块可以为空 |
| `builtin` | 是 | 是否为内置模块 |

后续持久化注册表可增加：

- `version`
- `minPlatformVersion`
- `configSchema`
- `publicApiPrefixes`
- `eventsPublishes`
- `eventsSubscribes`
- `healthCheckPath`
- `sortNo`
- `tenantVisible`
- `foundationLock`

模块中心应始终展示来源类型，并支持按来源筛选。当前来源含义：

| 来源 | 含义 |
| --- | --- |
| `BUILTIN` | 代码内置模块，通常是平台底座和核心通用能力 |
| `DATABASE` | 数据库注册模块，适合业务场景模块、客户定制模块和后续运营维护 |
| `PLUGIN` | 插件运行态同步的模块 |
| `MANIFEST` | 插件或模块包 manifest 解析出的模块声明 |

## 5. 注册示例

```json
{
  "moduleCode": "journal",
  "moduleName": "期刊场景",
  "moduleType": "SCENE",
  "lifecycleStatus": "PLANNED",
  "sourceType": "BUILTIN",
  "description": "面向期刊文章投稿、审稿、录用和发布的业务场景模块。",
  "ownerService": "system-service",
  "adminRoutePath": "/journal",
  "apiPrefixes": ["/api/v1/journal/**"],
  "permissionKeys": [
    "journal:view",
    "journal:submission:view",
    "journal:submission:review"
  ],
  "dependencies": [
    "form",
    "submission",
    "approval",
    "evaluation",
    "file",
    "message",
    "site"
  ],
  "dependencySatisfied": false,
  "missingDependencies": [],
  "inactiveDependencies": ["form", "submission"],
  "readyToEnable": false,
  "readinessIssues": [
    "模块仍处于规划状态，尚未完成运行时实现",
    "依赖模块未启用: form, submission"
  ],
  "builtin": true
}
```

## 6. 依赖健康

模块中心当前需要展示两类依赖问题：

- 缺失依赖：注册表中找不到对应模块。
- 未启用依赖：模块存在，但生命周期不是 `ENABLED`。

启用候选判断规则：

1. 当前模块生命周期必须允许启用。
2. 所有依赖模块必须存在。
3. 所有依赖模块必须已启用。
4. 模块不能处于 `PLANNED`、`DISABLED`、`DEPRECATED` 等阻塞状态。

依赖健康只是治理判断，不等于真实启停动作。真实启停后续还需要菜单权限刷新、租户可见性、插件运行态和配置校验。

## 7. 写入前校验

模块定义写入数据库或插件 manifest 同步前，必须先通过校验接口：

```text
POST /api/v1/system/modules/validate
```

校验接口只返回结果，不创建、不更新、不启停模块。

模块中心已经提供“校验草案”抽屉。该抽屉允许输入模块编码、类型、生命周期、来源、依赖、API 前缀和权限声明，并展示校验结果。

校验内容：

- 模块编码是否已存在。
- 模块类型是否合法。
- 生命周期是否合法。
- 来源类型是否合法。
- 是否依赖自身。
- 依赖模块是否缺失。
- 依赖模块是否未启用。
- 是否形成循环依赖。

返回结果至少包含：

- `valid`
- `duplicateModuleCode`
- `issues`
- `warnings`
- `missingDependencies`
- `inactiveDependencies`
- `cyclePath`

只有 `valid = true` 的模块定义，后续才允许进入真实写入流程。`warnings` 不阻断写入，但应在管理界面明确提示。

当前已开放受控创建接口：

```text
POST /api/v1/system/modules
```

创建限制：

- 只允许 `sourceType = DATABASE`。
- 只允许 `lifecycleStatus = PLANNED`。
- 必须通过同一套写入前校验。
- 不允许覆盖已有同编码模块。
- 不支持编辑、删除或启停。

## 8. 后端接入规则

当前后端通过 `PlatformModuleRegistry` 读取模块注册信息。现阶段由 `CompositePlatformModuleRegistry` 合并输出：

- `StaticPlatformModuleRegistry`：读取代码内置清单，承载底座和现有内置能力的默认定义。
- `DatabasePlatformModuleRepository`：读取 `platform_module_definition` 和 `platform_module_dependency`，为后续业务场景模块、客户定制模块和插件 manifest 同步结果预留持久化落点。

合并规则是静态清单先进入注册表，数据库模块后进入注册表；如果模块编码相同，数据库定义会覆盖静态定义。控制器和系统管理服务不应直接依赖具体来源。

当前已通过 Flyway 种子注册 `journal` 期刊场景，来源为 `DATABASE`，生命周期仍为 `PLANNED`。它用于验证数据库注册模块覆盖静态定义和依赖健康展示，不代表期刊业务已经完成启用。

新增内置模块时必须完成：

1. 在模块目录下建立清晰边界：`controller`、`app`、`domain`、`dto`、`vo`、`mapper`、`entity`、`event`。
2. 在模块注册表中声明模块编码、类型、状态、依赖、权限、API 前缀和管理入口。
3. 管理 API 优先使用 `/api/v1/<module>/**`。
4. 公开 API 使用 `/api/v1/public/<module>/**`。
5. 内部调用使用 app/service 或 `legendary-api` 契约，不跨模块调用 controller。
6. 跨模块写操作优先通过事件/outbox 解耦。
7. 不允许绕过统一权限、审计、文件和消息能力。

## 9. 前端接入规则

新增内置模块时必须完成：

1. 新增 `frontend/src/services/<module>/index.ts`。
2. 新增 `frontend/src/pages/<module>/` 页面目录。
3. 在 `frontend/src/routes/meta.ts` 增加本地路由元信息。
4. 在后端菜单种子或模块菜单声明中增加菜单。
5. 在 `frontend/src/access.ts` 增加页面访问判断。
6. 页面按钮必须按权限控制展示或禁用。
7. 模块中心应能展示该模块的依赖、API 前缀和权限声明。

动态插件前端不应修改主前端路由文件，应通过插件 manifest、插件菜单和 `/plugins/:pluginCode` 容器接入。

## 10. 依赖规则

依赖只能从上层指向下层或同层通用能力：

```text
SCENE -> CAPABILITY -> FOUNDATION
ADAPTER -> FOUNDATION 或 PLUGIN
PLUGIN -> FOUNDATION / CAPABILITY 的公开 SPI 或 API
```

禁止：

- `FOUNDATION` 依赖 `SCENE`。
- `CAPABILITY` 强依赖某个具体 `SCENE`。
- 两个模块互相依赖。
- 一个模块直接写另一个模块的数据表。
- 插件进入平台主事务。

## 11. 菜单与权限规则

模块可以声明多个菜单和多个权限，但权限键必须归属明确：

```text
<module>:<resource>:<action>
```

示例：

- `journal:submission:view`
- `journal:submission:review`
- `competition:event:manage`
- `submission:record:create`
- `form:definition:publish`

底座历史权限可以保留兼容，例如 `system:menu:view`，但新增业务模块不应继续使用过于宽泛的权限键。

## 12. API 规则

新增模块默认使用：

| 类型 | 前缀 |
| --- | --- |
| 管理 API | `/api/v1/<module>/**` |
| 公开 API | `/api/v1/public/<module>/**` |
| 内部 API | `/internal/<module>/**` |
| 插件 API | `/api/p/{pluginCode}/**` |

历史路径如 `/api/approvals/**`、`/api/evaluations/**` 可以继续兼容，但不应作为新模块模板。

## 13. 事件规则

事件命名：

```text
<module>.<aggregate>.<action>
```

示例：

- `submission.record.created`
- `approval.instance.completed`
- `evaluation.task.submitted`
- `journal.submission.accepted`
- `competition.entry.reviewed`

事件载荷至少应包含：

- `eventKey`
- `tenantId`
- `moduleCode`
- `aggregateType`
- `aggregateId`
- `operatorId`
- `payloadJson`
- `traceId`
- `requestId`

## 14. 模块成熟度

模块成熟后才考虑物理拆分。拆分前必须满足：

- 模块注册信息完整。
- 表边界清晰。
- 菜单、权限、API、事件都可由模块声明解释。
- 与其他模块没有直接 mapper 或 SQL 耦合。
- 有迁移脚本和测试覆盖。
- 有独立部署、扩缩容、安全或交付节奏需求。

建议顺序：

1. 先做统一注册和只读模块中心。
2. 再做模块配置、依赖校验和启停策略。
3. 再抽通用 `form`、`submission` 能力。
4. 再做 `journal` 或 `competition` 场景模块验证。
5. 最后把边界成熟的模块独立服务化或插件包化。
