# Spring Cloud Alibaba 微服务重构说明

## 1. 当前目标

本仓库已经从“模块化单体底座”进入“微服务平台骨架”阶段。当前主线不是继续堆单体模块，而是把入口、注册、配置、治理、任务和事务能力按官方规范迁到 Spring Cloud Alibaba 体系内。

## 2. 官方版本锁定

以下版本来自官方 release / compatibility / download 页面，作为当前工程的锁定基线：

- Spring Boot `4.0.6`
- Spring Cloud `2025.1.1`
- Spring Cloud Alibaba `2025.1.0.0`
- Nacos Server `3.2.1`
- Sentinel `1.8.9`
- XXL-JOB `3.4.0`
- Seata `2.6.0`

## 3. 模块边界

- `gateway-service`：统一入口网关。
- `backend/`：当前作为 `system-service`，承接原有核心业务。
- `auth-service`、`tenant-service`、`file-service`、`message-service`、`plugin-service`、`audit-service`、`localization-service`、`job-executor`：后续拆分目标服务。
- `common-core`、`common-web`、`common-security`、`common-tenant`、`legendary-api`：共享契约和基础能力。

## 4. 配置规范

- Nacos 配置必须使用 `spring.config.import`。
- 不再使用 `bootstrap.yml` 作为主配置入口。
- 服务名、Nacos namespace、server-addr、路由、灰度和限流规则都应以配置中心为准。
- 敏感配置只能通过环境变量或密钥系统注入，不应明文硬编码进仓库。

## 5. 启动顺序

1. 启动 MySQL、Redis、Nacos。
2. 启动 `backend` 作为 `system-service`。
3. 启动 `gateway-service`。
4. 后续再启用各独立服务模块和 XXL-Job / Seata。

## 6. 进一步拆分顺序

- 第一批：`file-service`、`message-service`、`job-executor`
- 第二批：`plugin-service`
- 第三批：`auth-service`、`system-service`
- 第四批：`tenant-service`

## 7. 当前已落地的第一批

- `gateway-service` 已收口 `/api/v1/files/**`、`/api/uploads/**`、`/api/v1/message/**`、`/ws/message` 路由。
- `job-executor` 已接入 XXL-Job 执行器，并通过后端内部任务接口触发 outbox relay、message heartbeat、online-session heartbeat。
- 后端业务进程已去掉上述三处 `@Scheduled`，调度职责开始外移到执行器。

## 7. 官方参考

- [Spring Cloud](https://spring.io/projects/spring-cloud)
- [Spring Cloud Alibaba Releases](https://github.com/alibaba/spring-cloud-alibaba/releases)
- [Nacos Server Download](https://nacos.io/en/download/nacos-server/)
- [Sentinel Releases](https://github.com/alibaba/Sentinel/releases)
- [XXL-JOB 官方文档](https://github.com/xuxueli/xxl-job/blob/master/doc/XXL-JOB%E5%AE%98%E6%96%B9%E6%96%87%E6%A1%A3.md)
- [Seata Release History](https://seata.apache.org/release-history/seata-server/)
