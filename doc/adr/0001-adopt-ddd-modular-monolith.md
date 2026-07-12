# ADR-0001：采用 DDD 模块化单体

## 状态

已采纳。

## 背景

Lumira 通过 `services/lumira-admin` 聚合运行，同时保留 system、auth、message、file、plugin、localization、payment、AI 和 Team 等 Maven 模块。

这种运行方式部署简单，但早期代码在部分模块中混合了 CRUD、用例编排、持久化实体、缓存和领域规则。若继续扩大 `system-service`，后续业务边界、权限快照失效和物理拆分都会变得更困难。

DDD 对本项目最有价值的两点是：

- 通过限界上下文明确业务与数据 owner。
- 通过应用层、领域层、Repository 和领域事件保护核心规则。

## 决策

后端目标架构采用 DDD 导向的模块化单体。

- `services/lumira-admin` 继续作为同步请求的唯一聚合入口。
- 每个业务模块必须有明确的代码、数据、契约、权限和事件边界。
- 物理微服务拆分是后续运行决策，不作为清理领域边界的前提。
- 新代码按以下依赖方向组织：

```text
interfaces/controller -> application -> domain
infrastructure        -> domain/application ports
```

各层职责：

- `interfaces` / `controller`：协议适配、请求校验和响应组装。
- `application`：用例编排、事务、授权、审计和事件发布。
- `domain`：聚合、值对象、领域服务、Repository 接口和领域事件。
- `infrastructure`：MyBatis、Redis、外部客户端、消息、对象存储和 Repository 实现。

简单 CRUD 不要求建立复杂领域模型，但仍必须遵守模块、数据和持久化边界。

## 影响

收益：

- 业务规则和数据归属更清晰，架构测试可以保护边界。
- 聚合部署保持简单，同时降低未来物理拆分风险。
- 缓存失效和跨模块协作可以通过明确的领域事实表达。

代价：

- 历史模块需要渐进迁移，新旧结构会在一段时间内并存。
- 团队需要统一理解限界上下文、聚合、Repository 和事件。
- 过度设计简单 CRUD 会降低交付效率，评审时需要控制建模粒度。

本决策不要求全局采用 Event Sourcing 或 CQRS，也不要求修改现有前端 API 路径。

## 被否决的方案

### 保持宽泛的分层 CRUD

无法阻止 `system-service` 继续吸收无关业务，也难以保护表 owner 和领域规则，因此不采用。

### 立即拆成物理微服务

在边界尚未稳定前会提前引入部署、可观测、事务和调试复杂度，因此不采用。

### 引入重量级 DDD 框架

现有 Spring Boot、MyBatis、Flyway、Redis 和 Outbox 足以支撑轻量约定与架构测试，因此不采用。

## 相关文档

- [后端开发规范](../07-backend-architecture.md)
- [服务与数据归属](../13-service-data-ownership.md)
- [模块边界与新模块模板](../14-system-service-module-boundaries.md)
- [事件与 Outbox](../16-event-outbox-architecture.md)
