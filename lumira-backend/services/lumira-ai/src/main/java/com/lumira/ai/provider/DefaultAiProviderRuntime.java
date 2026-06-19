package com.lumira.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.ai.config.AiProviderProperties;
import com.lumira.ai.vo.AiKnowledgeReferenceVO;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultAiProviderRuntime implements AiProviderRuntime {

    private static final String LOCAL_PROVIDER = "lumira-local";
    private static final String LOCAL_CHAT_MODEL = "knowledge-grounded-v1";
    private static final String LOCAL_EMBEDDING_MODEL = "local-hashing-v1";
    private static final int LOCAL_EMBEDDING_DIMENSION = 16;

    private final AiProviderProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public DefaultAiProviderRuntime(
            AiProviderProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatCompletion complete(ChatPrompt prompt) {
        if (properties.getOpenaiCompatible().configured()) {
            try {
                return remoteChat(prompt);
            } catch (RuntimeException exception) {
                ChatCompletion local = localChat(prompt);
                return new ChatCompletion(local.text(), local.provider(), local.model(), false, true);
            }
        }
        return localChat(prompt);
    }

    @Override
    public EmbeddingVector embed(String text) {
        if (properties.getOpenaiCompatible().configured()) {
            try {
                return remoteEmbedding(text);
            } catch (RuntimeException exception) {
                EmbeddingVector local = localEmbedding(text);
                return new EmbeddingVector(local.model(), local.values(), false, true);
            }
        }
        return localEmbedding(text);
    }

    @Override
    public ProviderStatus status() {
        boolean configured = properties.getOpenaiCompatible().configured();
        return new ProviderStatus(
                configured ? "openai-compatible" : LOCAL_PROVIDER,
                configured ? properties.getOpenaiCompatible().getChatModel() : LOCAL_CHAT_MODEL,
                configured ? properties.getOpenaiCompatible().getEmbeddingModel() : LOCAL_EMBEDDING_MODEL,
                configured,
                !configured
        );
    }

    private ChatCompletion remoteChat(ChatPrompt prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getOpenaiCompatible().getChatModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是 Lumira 企业系统助手。优先基于给定知识库参考回答，避免编造。"),
                Map.of("role", "user", "content", prompt.userMessage() + "\n\n知识库参考：\n" + referenceText(prompt.references()))
        ));
        body.put("temperature", 0.2);
        String responseBody = client()
                .post()
                .uri("chat/completions")
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode response = readTree(responseBody);
        String text = response == null ? null : response.at("/choices/0/message/content").asText(null);
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("Remote chat response is empty");
        }
        return new ChatCompletion(text, "openai-compatible", properties.getOpenaiCompatible().getChatModel(), true, false);
    }

    private EmbeddingVector remoteEmbedding(String text) {
        Map<String, Object> body = Map.of(
                "model", properties.getOpenaiCompatible().getEmbeddingModel(),
                "input", text == null ? "" : text
        );
        String responseBody = client()
                .post()
                .uri("embeddings")
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode response = readTree(responseBody);
        JsonNode embedding = response == null ? null : response.at("/data/0/embedding");
        if (embedding == null || !embedding.isArray()) {
            throw new IllegalStateException("Remote embedding response is empty");
        }
        List<Double> values = new ArrayList<>();
        embedding.forEach(value -> values.add(value.asDouble()));
        return new EmbeddingVector(properties.getOpenaiCompatible().getEmbeddingModel(), values, true, false);
    }

    private ChatCompletion localChat(ChatPrompt prompt) {
        if (prompt.references() == null || prompt.references().isEmpty()) {
            return new ChatCompletion(
                    "已收到：" + prompt.userMessage() + "\n\n当前独立 AI 服务未命中知识库内容，已保留会话上下文。",
                    LOCAL_PROVIDER,
                    LOCAL_CHAT_MODEL,
                    false,
                    false
            );
        }
        StringBuilder reply = new StringBuilder("基于知识库检索到的内容，给出以下参考：\n");
        for (int i = 0; i < Math.min(3, prompt.references().size()); i++) {
            AiKnowledgeReferenceVO reference = prompt.references().get(i);
            reply.append(i + 1)
                    .append(". ")
                    .append(reference.documentTitle())
                    .append(": ")
                    .append(truncate(reference.content(), 180))
                    .append('\n');
        }
        return new ChatCompletion(reply.toString().trim(), LOCAL_PROVIDER, LOCAL_CHAT_MODEL, false, false);
    }

    private EmbeddingVector localEmbedding(String text) {
        double[] buckets = new double[LOCAL_EMBEDDING_DIMENSION];
        byte[] digest = digest(text == null ? "" : text);
        for (int i = 0; i < digest.length; i++) {
            buckets[i % LOCAL_EMBEDDING_DIMENSION] += (digest[i] & 0xff) / 255.0d;
        }
        double norm = 0.0d;
        for (double bucket : buckets) {
            norm += bucket * bucket;
        }
        norm = Math.sqrt(norm);
        List<Double> values = new ArrayList<>(LOCAL_EMBEDDING_DIMENSION);
        for (double bucket : buckets) {
            values.add(norm == 0.0d ? 0.0d : bucket / norm);
        }
        return new EmbeddingVector(LOCAL_EMBEDDING_MODEL, values, false, false);
    }

    private RestClient client() {
        return restClientBuilder
                .baseUrl(normalizedBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getOpenaiCompatible().getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    private JsonNode readTree(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception exception) {
            throw new IllegalStateException("Remote AI provider returned invalid JSON", exception);
        }
    }

    private String normalizedBaseUrl() {
        String baseUrl = properties.getOpenaiCompatible().getBaseUrl();
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private String referenceText(List<AiKnowledgeReferenceVO> references) {
        if (references == null || references.isEmpty()) {
            return "(无)";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(5, references.size()); i++) {
            AiKnowledgeReferenceVO reference = references.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(reference.documentTitle())
                    .append(" - ")
                    .append(truncate(reference.content(), 600))
                    .append('\n');
        }
        return builder.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }
}
