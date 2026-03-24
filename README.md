# legendary-invention

Ant Design Pro 大型 SaaS 系统脚手架仓库。

## 当前定位

这个仓库不是普通后台模板，而是面向多租户、多端适配、高可靠、可观测、可灰度演进的 SaaS 系统底座。

我们当前采用文档先行的方式推进，把架构边界、目录结构、模块拆分和后续落地顺序先固定下来，再逐步进入前后端实现。

## 现阶段文档

- [总体技术方案](docs/01-technical-scheme.md)
- [目录结构与模块拆分规范](docs/02-directory-module-spec.md)
- [数据库设计与基础表结构规范](docs/03-database-design.md)
- [接口规范与统一响应标准](docs/04-interface-spec.md)
- [权限模型与多租户 RBAC 设计规范](docs/05-permission-rbac.md)
- [前端架构与响应式布局规范](docs/06-frontend-architecture.md)
- [后端架构与基础设施规范](docs/07-backend-architecture.md)
- [开发实施路线与第一阶段落地清单](docs/08-development-roadmap.md)
- [第一阶段详细任务拆解与 Codex 执行提示词总文档](docs/09-first-phase-codex-execution.md)
- [第一轮 Codex 执行提示词：工程骨架与基础设施底座初始化](docs/10-first-round-codex-prompt.md)

## 第一阶段目标

1. 搭建统一的前后端技术底座。
2. 完成租户、认证、权限、响应式、缓存、日志、审计的基础骨架。
3. 形成可直接扩展业务模块的工程结构。
4. 为后续数据库规范、接口规范和部署规范预留入口。

## 接下来要做的事

1. 按这 10 份规范开始初始化前端和后端工程骨架。
2. 先落登录、租户切换、权限与审计链路。
3. 再补齐通用页面模板、数据库基础表和接口约束。
4. 逐步进入业务模块开发。
