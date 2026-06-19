# ADR 0002: AI Provider Runtime Port

## Status

Accepted

## Context

Lumira is moving AI into an independent bounded context. The AI owner must support local development, production provider calls, knowledge embedding, and future physical service split without coupling the application layer to a specific LLM SDK or another owner module.

Directly calling provider SDKs or HTTP endpoints from `AiCommandService` would mix domain/application flow with infrastructure concerns, make fallback behavior hard to test, and increase risk during physical split.

## Decision

Introduce `AiProviderRuntime` as the AI provider port in `services/lumira-ai`.

- `DefaultAiProviderRuntime` provides a deterministic local fallback: `lumira-local` chat and `local-hashing-v1` embedding.
- When `lumira.ai.provider.openai-compatible.*` is configured, the runtime calls OpenAI-compatible `/chat/completions` and `/embeddings` endpoints.
- Provider failures degrade to the local runtime instead of failing the whole chat or indexing flow.
- Knowledge chunks persist `embedding_model`, `embedding_dim`, `embedding_vector_json`, and `vector_indexed_at` using the existing AI owner schema.
- `/api/v2/ai/health` exposes provider runtime status so release drills can prove whether AI is using local fallback or a remote provider.

## Consequences

Benefits:

- `AiCommandService` depends on a stable application port, not provider-specific HTTP details.
- Local and CI tests remain deterministic without external credentials.
- Provider-native rollout can be done with configuration, runtime observability, and rollback to local fallback.
- Future provider implementations can be added without changing chat or knowledge indexing use cases.

Trade-offs:

- Local fallback is not equivalent to production provider quality.
- The OpenAI-compatible adapter still needs production-equivalent smoke tests with real provider credentials.
- Vector retrieval remains bounded SQL plus stored vector projection until a dedicated vector database is introduced.

## Verification

- `DefaultAiProviderRuntimeTest` verifies deterministic local chat/embedding fallback.
- `AiCommandServiceTest` verifies command service can run through provider and owner ports.
- `AiReadinessV2ControllerTest` verifies provider runtime status is part of AI readiness/health/metrics.
