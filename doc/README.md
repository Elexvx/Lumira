# Lumira 文档导航

这里是 Lumira 文档的唯一入口。文档默认使用中文；类名、路径、配置项、API 字段和通用技术术语保留英文，避免翻译后无法对应代码。

## 按角色阅读

### 新成员

1. [技术方案总览](01-technical-scheme.md)：了解系统定位和运行形态。
2. [项目目录与模块规范](02-directory-module-spec.md)：知道代码放在哪里、模块如何协作。
3. [架构运行手册](17-architecture-runbook.md)：启动项目并完成一次健康检查。
4. 根据工作方向继续阅读前端或后端规范。

### 产品与项目人员

- [产品需求文档](20-product-requirements-document.md)：产品范围、角色、功能和验收口径。

### 前端开发

- [前端开发规范](06-frontend-architecture.md)：目录、组件、路由、状态、表单和质量要求。
- [接口规范](04-interface-spec.md)：请求、响应、分页、错误码和幂等约定。
- [权限与多租户](05-permission-rbac.md)：页面、按钮、接口和数据权限如何配合。

### 后端开发

- [后端开发规范](07-backend-architecture.md)：分层、模块、事务、缓存、日志和测试要求。
- [数据库规范](03-database-design.md)：表、字段、索引、租户和迁移规则。
- [服务与数据归属](13-service-data-ownership.md)：哪个模块拥有哪类数据，跨模块如何访问。
- [模块边界与新模块模板](14-system-service-module-boundaries.md)：平台域、业务域和新模块落地方式。
- [API、认证与权限边界](15-gateway-auth-permission-boundaries.md)：请求链路与各层职责。
- [事件与 Outbox](16-event-outbox-architecture.md)：可靠异步事件的生产和消费规则。
- [持久化边界](architecture/persistence-boundary.md)：应用层与数据库访问层的边界。
- [持久化历史债务](architecture/persistence-boundary-debt.md)：架构测试读取的兼容清单，不是新代码范例。

### 专项功能

- [实时站内信接口](09-realtime-message-notification-api.md)

### 测试

测试文档按“定义策略 → 执行 → 汇总 → 报告”的顺序使用：

1. [测试策略与用例](21-test-strategy-and-cases.md)：范围、风险和用例库。
2. [逐页手工测试工作簿](24-page-manual-test-workbook.md)：记录每个页面的实际结果。
3. [测试执行台账模板](22-test-execution-checklist-template.md)：汇总一轮执行结果。
4. [测试报告模板](23-test-report-template.md)：形成可发布的质量结论。

### 部署与运维

- [生产部署指南](../deploy/README.md)：Vercel、Docker Compose、1Panel、域名、HTTPS、备份与观测。
- [架构运行手册](17-architecture-runbook.md)：本地/测试/生产运行和常见故障定位。
- [回滚方案](release/rollback-plan.md)：代码、配置、数据库、文件、AI 与支付回滚。
- [数据库初始化](../lumira-backend/sql/README.md)：新库初始化入口和限制。

### 架构决策

- [ADR-0001：采用 DDD 模块化单体](adr/0001-adopt-ddd-modular-monolith.md)
- [ADR-0002：AI Provider Runtime 端口](adr/0002-ai-provider-runtime-port.md)

## 机器读取的配套文件

以下文件不是普通指南，修改时必须同步检查读取它们的测试或脚本：

- `27-ddd-owner-table-manifest.csv`：表归属清单，由后端架构测试读取。
- `35-ddd-rollback-drill-template.json`：回滚演练记录模板。
- `architecture/persistence-boundary-debt.md`：历史持久化例外清单，由架构测试读取。

## 写作与维护规则

- 一份文档只解决一个明确问题；相同事实只保留一个权威位置，其他文档用相对链接引用。
- 先写“用途、适用范围和结论”，再写操作步骤与背景；命令必须注明从仓库根目录还是子目录执行。
- 当前事实使用现在时；规划内容必须明确标注“计划”或“未来”，不能与已落地能力混写。
- 文档链接一律使用仓库相对路径，禁止提交开发者本机绝对路径。
- 临时测试记录、截图和生成报告不作为长期指南；需要保留时放入对应发布记录或外部制品库。
- 修改代码路径、命令、环境变量、接口或架构边界时，同一变更中同步更新对应文档。
