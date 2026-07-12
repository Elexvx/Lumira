# ADR-0002：AI Provider Runtime 端口

## 状态

已采纳。

## 背景

AI 模块需要同时支持本地开发、生产模型服务、知识库 Embedding 和未来物理拆分。若 `AiCommandService` 直接调用某个供应商 SDK 或 HTTP 接口，应用流程会与基础设施耦合，降级逻辑也难以测试。

## 决策

在 `services/lumira-ai` 中以 `AiProviderRuntime` 作为模型供应商端口。

- `DefaultAiProviderRuntime` 提供确定性的本地后备：`lumira-local` Chat 和 `local-hashing-v1` Embedding。
- 配置 `lumira.ai.provider.openai-compatible.*` 后，通过 OpenAI-compatible 的 `/chat/completions` 和 `/embeddings` 调用远程服务。
- 供应商调用失败时降级到本地实现，避免整个对话或索引流程不可用。
- 知识块在 AI owner Schema 中保存 `embedding_model`、`embedding_dim`、`embedding_vector_json` 和 `vector_indexed_at`。
- `/api/v2/ai/health` 暴露运行时状态，用于发布验证和故障定位。

## 影响

收益：

- 应用层依赖稳定端口，不依赖供应商的 HTTP 细节。
- 本地和 CI 无需外部凭据即可确定性测试。
- 可以通过配置切换供应商，并快速回退到本地实现。
- 新增供应商实现时不需要修改对话或知识索引用例。

限制：

- 本地后备不代表生产模型质量，只保证功能可用和测试稳定。
- OpenAI-compatible 适配器仍需使用真实生产配置做冒烟测试。
- 当前向量检索仍是受限 SQL 与持久化向量投影；引入专用向量数据库前需要评估数据迁移和回滚。

## 验证

- `DefaultAiProviderRuntimeTest`：验证本地 Chat 和 Embedding 的确定性结果。
- `AiCommandServiceTest`：验证命令服务通过 Provider 与 owner 端口运行。
- `AiReadinessV2ControllerTest`：验证 AI 健康状态包含 Provider Runtime 信息。
