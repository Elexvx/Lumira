# Lumira 目录结构与模块拆分规范

## 1. 文档定位

本规范用于统一目录组织方式、模块边界、职责划分、命名标准与扩展方式。

它的目标不是“目录好看”，而是保证系统在长期演进中仍然具备高可维护性、高可读性、高复用性和高可靠性。

当前仓库以实际目录为准：前端主工程位于 `lumira-ui/src/`，后端正式运行入口位于 `services/lumira-admin/`，其余 `services/*-service` 为聚合运行下的模块边界与未来拆分预留。

## 2. 总体拆分原则

- 壳层稳定。
- 能力下沉。
- 业务解耦。
- 边界清晰。
- 横切能力集中。

## 3. 前端目录建议

前端以 `lumira-ui/src/` 为根，当前主线目录建议包含：

- `app.ts`
- `access.ts`
- `global.less`
- `layouts/`
- `pages/`
- `components/`
- `services/`
- `hooks/`
- `bootstrap/`
- `features/`
- `query/`
- `theme/`
- `utils/`
- `constants/`
- `enums/`
- `types/`
- `locales/`
- `assets/`
- `auth/`
- `cache/`
- `branding/`
- `agreement/`
- `floatingWindow/`
- `watermark/`
- `plugins/`

### 3.1 壳层

- `app.ts` 负责全局初始化、请求配置、布局配置、租户恢复、登录检查和异常拦截。
- `access.ts` 只负责前端展示层权限表达。
- `layouts/` 只负责系统壳层能力。

### 3.2 页面层

`pages/` 只存放真正的路由页面和页面级组合逻辑，建议按业务域拆分，例如：

- `dashboard/`
- `system/`
- `settings/`
- `files/`
- `plugins/`
- `ai/`
- `message/`
- `profile/`
- `user/`
- `exception/`

### 3.3 复用层

- `components/` 存放系统级复用组件。
- `services/` 存放所有接口调用封装。
- `hooks/` 存放复用型逻辑。
- `utils/`、`constants/`、`enums/`、`types/` 各司其职，不混用。

### 3.4 当前一级能力目录

- `auth/`：登录态、Token 生命周期、权限快照、登出清理。
- `cache/`：前端缓存统一治理。
- `bootstrap/`：应用启动、公开配置加载、运行时初始化。
- `features/`：CRUD、表格、详情、表单、权限等页面能力封装。
- `query/`：React Query 基础封装。
- `theme/`：主题、视觉 token 与全局反馈桥接。

## 4. 前端页面模板

- 列表页：页面容器、查询区、操作区、表格区、详情区。
- 表单页：新增、编辑、查看三种模式。
- 详情页：基础信息、状态信息、关联信息、操作日志。
- 移动端：列表退化为卡片流，复杂查询折叠，详情纵向排布。

## 5. 后端目录建议

后端当前采用模块化单体，正式运行入口为 `services/lumira-admin/`，并聚合以下目录族：

- `services/lumira-admin/`
- `services/lumira-system/`
- `services/lumira-auth/`
- `services/lumira-file/`
- `services/lumira-message/`
- `services/lumira-plugin/`
- `services/lumira-localization/`
- `services/lumira-payment/`
- `services/lumira-quartz/`
- `libs/`

### 5.1 `lumira-server`

正式 Spring Boot 启动入口，对外统一承载系统、认证、文件、消息、插件、本地化、支付和任务模块。

### 5.2 `services/*-service`

按业务域拆分的 Maven 模块。当前默认不独立对外启动，但继续承担边界隔离、代码归属和未来拆分预留。

### 5.3 `libs/*`

跨服务共享的稳定公共库，例如通用 API、Web、安全、核心常量等。

### 5.4 `modules`

`system-service` 内的核心业务模块当前主要包括：

- `auth/`
- `user/`
- `iam/`
- `dict/`
- `config/`
- `file/`
- `message/`
- `audit/`
- `ai/`
- `dashboard/`
- `system/`

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

- 前端租户上下文由 `bootstrap/`、`auth/`、`cache/` 和统一请求层协同处理。
- 后端租户能力由统一上下文、拦截器和业务模块共同承载，避免让某个前端目录结构承担全部语义。
- 后端 `iam/` 集中承载角色、菜单、按钮权限、接口权限和数据范围权限。

## 8. 响应式与缓存约束

- 前端统一通过布局、hooks 和主题断点处理响应式适配策略。
- 前端 `cache/` 统一缓存治理，缓存 Key 必须包含租户维度。
- 不允许在页面中散落硬编码响应式逻辑或 localStorage 规则。

## 9. 第一阶段目录落地优先级

前端优先落地：

- `layouts`
- `pages`
- `components`
- `services`
- `auth`
- `bootstrap`
- `features`
- `cache`

后端优先落地：

- `services/lumira-admin`
- `services/lumira-system`
- `services/lumira-auth`
- `services/lumira-file`
- `services/lumira-message`
- `libs/common-*`
