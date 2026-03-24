# Ant Design Pro 大型 SaaS 系统目录结构与模块拆分规范

## 1. 文档定位

本规范用于统一目录组织方式、模块边界、职责划分、命名标准与扩展方式。

它的目标不是“目录好看”，而是保证系统在长期演进中仍然具备高可维护性、高可读性、高复用性和高可靠性。

## 2. 总体拆分原则

- 壳层稳定。
- 能力下沉。
- 业务解耦。
- 边界清晰。
- 横切能力集中。

## 3. 前端目录建议

前端以 `src/` 为根，建议包含：

- `app.ts`
- `access.ts`
- `global.less`
- `layouts/`
- `pages/`
- `components/`
- `services/`
- `models/`
- `hooks/`
- `utils/`
- `constants/`
- `enums/`
- `types/`
- `locales/`
- `assets/`
- `tenant/`
- `auth/`
- `responsive/`
- `cache/`
- `plugins/`

### 3.1 壳层

- `app.ts` 负责全局初始化、请求配置、布局配置、租户恢复、登录检查和异常拦截。
- `access.ts` 只负责前端展示层权限表达。
- `layouts/` 只负责系统壳层能力。

### 3.2 页面层

`pages/` 只存放真正的路由页面和页面级组合逻辑，建议按业务域拆分，例如：

- `dashboard/`
- `system/`
- `tenant/`
- `iam/`
- `message/`
- `file/`
- `task/`
- `audit/`
- `config/`
- `profile/`
- `exception/`

### 3.3 复用层

- `components/` 存放系统级复用组件。
- `services/` 存放所有接口调用封装。
- `models/` 存放跨页面共享状态。
- `hooks/` 存放复用型逻辑。
- `utils/`、`constants/`、`enums/`、`types/` 各司其职，不混用。

### 3.4 一级能力目录

- `tenant/`：租户上下文、切换、缓存清理。
- `auth/`：登录态、Token 生命周期、权限快照、登出清理。
- `responsive/`：断点、设备识别、布局适配。
- `cache/`：前端缓存统一治理。

## 4. 前端页面模板

- 列表页：页面容器、查询区、操作区、表格区、详情区。
- 表单页：新增、编辑、查看三种模式。
- 详情页：基础信息、状态信息、关联信息、操作日志。
- 移动端：列表退化为卡片流，复杂查询折叠，详情纵向排布。

## 5. 后端目录建议

后端建议采用模块化单体，基础目录如下：

- `common/`
- `infrastructure/`
- `modules/`
- `interfaces/`

### 5.1 `common`

只存放真正跨模块共享且稳定的公共能力，例如返回结构、异常、错误码、上下文、分页对象。

### 5.2 `infrastructure`

只放技术基础设施实现，例如数据库、Redis、对象存储、MQ、调度、日志、安全、Web 配置、租户拦截器和 MyBatis 拦截器。

### 5.3 `modules`

核心业务模块建议包括：

- `auth/`
- `tenant/`
- `user/`
- `org/`
- `iam/`
- `dict/`
- `config/`
- `file/`
- `message/`
- `task/`
- `audit/`
- `dashboard/`
- `openapi/`

## 6. 模块内部结构

每个领域模块建议采用：

- `controller/`
- `app/`
- `domain/`
- `repository/`
- `mapper/`
- `entity/`
- `dto/`
- `vo/`
- `convert/`
- `service/`
- `event/`

### 6.1 职责约束

- `controller` 只接收请求和返回结果。
- `app` 负责用例编排。
- `domain` 负责核心业务规则。
- `repository` 和 `mapper` 负责数据访问。
- `entity`、`dto`、`vo` 不混用。
- `convert` 统一对象转换。
- `event` 处理模块内外事件。

## 7. 多租户与权限约束

- 前端 `tenant/` 统一处理租户上下文和切换。
- 后端 `tenant` 模块负责租户生命周期与配置，基础设施层负责租户拦截与隔离。
- 后端 `iam/` 集中承载角色、菜单、按钮权限、接口权限和数据范围权限。

## 8. 响应式与缓存约束

- 前端 `responsive/` 统一断点和设备适配策略。
- 前端 `cache/` 统一缓存治理，缓存 Key 必须包含租户维度。
- 不允许在页面中散落硬编码响应式逻辑或 localStorage 规则。

## 9. 第一阶段目录落地优先级

前端优先落地：

- `layouts`
- `pages`
- `components`
- `services`
- `tenant`
- `auth`
- `responsive`
- `cache`

后端优先落地：

- `common`
- `infrastructure`
- `modules/auth`
- `modules/tenant`
- `modules/user`
- `modules/org`
- `modules/iam`
- `modules/dict`
- `modules/config`
- `modules/audit`
