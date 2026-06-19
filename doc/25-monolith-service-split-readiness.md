# 单体微服务与未来拆分边界

当前后端正式运行形态是单体微服务：只保留 `services/lumira-admin` 作为 Spring Boot 启动入口，`services/*-service` 目录作为业务模块 jar 被聚合进同一个进程。

## 当前约束

- 只有 `services/lumira-admin` 可以包含 `*Application.java` 启动类。
- 只有 `services/lumira-admin/src/main/resources/application.yml` 可以承载应用级配置、端口、Nacos、Sentinel、数据库、Redis、Flyway、Actuator、SpringDoc 等运行配置。
- 业务模块可以保留 Java 包、mapper、migration、测试和模块内资源，但不再携带独立 `application*.yml`。
- 前端、Nginx、Docker、脚本只面向 `lumira-server` 这一后端进程。

## 保留的拆分边界

以下 Maven 模块继续作为未来拆分边界保留：

- `services/lumira-auth`
- `services/lumira-system`
- `services/lumira-file`
- `services/lumira-message`
- `services/lumira-plugin`
- `services/lumira-localization`
- `services/lumira-payment`
- `services/lumira-quartz`

这些模块仍应保持清晰的包边界、数据 owner 边界和 API DTO 边界。新增跨模块调用优先通过 `libs/lumira-common-api` 或明确的应用服务接口表达，避免直接穿透其他模块的内部实体和 mapper。

## 未来拆分最小步骤

当某个模块需要重新物理拆分为独立微服务时，按以下最小步骤恢复：

1. 在目标模块新增独立启动类，例如 `PaymentServiceApplication`。
2. 在目标模块新增 `src/main/resources/application.yml`，从 `services/lumira-admin/src/main/resources/application.yml` 中复制该模块需要的配置前缀。
3. 为目标模块设置独立 `spring.application.name`、`server.port`、数据库/Redis/Flyway 位置、Actuator 和服务发现配置。
4. 将 `lumira-server` 对该模块的本地依赖调用替换为网关或内部 API 调用。
5. 在 Docker Compose、Nginx 或网关中恢复目标模块路由。
6. 保持前端路径不变，仍通过 `/api` 进入后端。

这样当前可以保持单进程部署的简单性，未来也可以按模块逐个拆分，而不需要重写业务代码。
