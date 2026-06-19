package com.lumira.saas.modules.ai.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public interface AiChatModelFactory {

    AiChatClient create(AiLlmServiceConfig config);

    interface AiChatClient {
        AiVO.ChatResponseVO chat(AiDTO.ChatRequest request, AiVO.EmployeeDetailVO employee, List<AiVO.SkillVO> skills);

        AiVO.ChatResponseVO streamChat(
                AiDTO.ChatRequest request,
                AiVO.EmployeeDetailVO employee,
                List<AiVO.SkillVO> skills,
                Consumer<String> onDelta,
                Consumer<String> onThinking
        );
    }
}

final class AiLlmServiceConfig {
    private Long id;
    private String provider;
    private String code;
    private String title;
    private String baseUrl;
    private String apiKey;
    private String defaultModel;
    private Integer timeoutMs;
    private BigDecimal temperature;
    private Integer maxTokens;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}

@Service
@Primary
class HttpAiChatModelFactory implements AiChatModelFactory {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一名企业级 SaaS 平台中的数字员工。
            你的目标是：基于当前平台的授权范围，稳妥、专业、清晰地完成用户交办的任务。
            你必须遵循以下要求：
            1. 先确认上下文，再执行任务。
            2. 遵守平台权限边界，不越权访问数据。
            3. 当任务涉及高风险操作时，先请求二次确认。
            4. 输出尽量简洁、结构清晰，优先给出可执行结论。
            """;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    HttpAiChatModelFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AiChatClient create(AiLlmServiceConfig config) {
        validateConfig(config);
        return new AiChatClient() {
            @Override
            public AiVO.ChatResponseVO chat(AiDTO.ChatRequest request, AiVO.EmployeeDetailVO employee, List<AiVO.SkillVO> skills) {
                return invokeChatCompletion(config, request, employee, skills);
            }

            @Override
            public AiVO.ChatResponseVO streamChat(
                    AiDTO.ChatRequest request,
                    AiVO.EmployeeDetailVO employee,
                    List<AiVO.SkillVO> skills,
                    Consumer<String> onDelta,
                    Consumer<String> onThinking
            ) {
                return invokeStreamingChatCompletion(config, request, employee, skills, onDelta, onThinking);
            }
        };
    }

    private AiVO.ChatResponseVO invokeChatCompletion(
            AiLlmServiceConfig config,
            AiDTO.ChatRequest request,
            AiVO.EmployeeDetailVO employee,
            List<AiVO.SkillVO> skills
    ) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(resolveEndpoint(config)))
                    .timeout(Duration.ofMillis(resolveTimeout(config)))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json");
            if (StringUtils.hasText(config.getApiKey())) {
                requestBuilder.header("Authorization", "Bearer " + config.getApiKey().trim());
            }

            HttpRequest httpRequest = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(config, request, employee, skills), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(config, request, employee, httpResponse);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 调用被中断");
        } catch (IOException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 调用失败: " + safeMessage(exception));
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 请求参数无效: " + safeMessage(exception));
        }
    }

    private AiVO.ChatResponseVO invokeStreamingChatCompletion(
            AiLlmServiceConfig config,
            AiDTO.ChatRequest request,
            AiVO.EmployeeDetailVO employee,
            List<AiVO.SkillVO> skills,
            Consumer<String> onDelta,
            Consumer<String> onThinking
    ) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(resolveEndpoint(config)))
                    .timeout(Duration.ofMillis(resolveTimeout(config)))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "text/event-stream");
            if (StringUtils.hasText(config.getApiKey())) {
                requestBuilder.header("Authorization", "Bearer " + config.getApiKey().trim());
            }

            HttpRequest httpRequest = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(config, request, employee, skills, true), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            return parseStreamingResponse(config, request, employee, httpResponse, onDelta, onThinking);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 调用被中断");
        } catch (IOException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 调用失败: " + safeMessage(exception));
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 请求参数无效: " + safeMessage(exception));
        }
    }

    private AiVO.ChatResponseVO parseResponse(
            AiLlmServiceConfig config,
            AiDTO.ChatRequest request,
            AiVO.EmployeeDetailVO employee,
            HttpResponse<String> httpResponse
    ) throws IOException {
        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            String errorMessage = extractErrorMessage(httpResponse.body());
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 调用失败(" + httpResponse.statusCode() + "): " + errorMessage);
        }

        JsonNode root = objectMapper.readTree(httpResponse.body());
        String replyText = extractReplyText(root);
        if (!StringUtils.hasText(replyText)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 返回内容为空");
        }

        AiVO.ChatResponseVO response = new AiVO.ChatResponseVO();
        response.setConversationId(request.getConversationId());
        response.setEmployeeId(employee.getId());
        response.setReplyRole("ASSISTANT");
        response.setProvider(normalizeProvider(config.getProvider()));
        response.setModel(resolveModel(config, root));
        response.setReplyAt(LocalDateTime.now());
        response.setReplyText(replyText.trim());
        String thinkingContent = extractReasoningContent(root);
        response.setThinkingContent(StringUtils.hasText(thinkingContent) ? thinkingContent.trim() : null);
        return response;
    }

    private AiVO.ChatResponseVO parseStreamingResponse(
            AiLlmServiceConfig config,
            AiDTO.ChatRequest request,
            AiVO.EmployeeDetailVO employee,
            HttpResponse<java.io.InputStream> httpResponse,
            Consumer<String> onDelta,
            Consumer<String> onThinking
    ) throws IOException {
        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            String body = new String(httpResponse.body().readAllBytes(), StandardCharsets.UTF_8);
            String errorMessage = extractErrorMessage(body);
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 调用失败(" + httpResponse.statusCode() + "): " + errorMessage);
        }

        StringBuilder replyBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        StringBuilder eventBuilder = new StringBuilder();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(httpResponse.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    appendStreamingEvent(eventBuilder.toString(), replyBuilder, thinkingBuilder, onDelta, onThinking);
                    eventBuilder.setLength(0);
                    continue;
                }
                if (line.startsWith("data:")) {
                    eventBuilder.append(line.substring(5).trim());
                }
            }
        }
        appendStreamingEvent(eventBuilder.toString(), replyBuilder, thinkingBuilder, onDelta, onThinking);

        String replyText = replyBuilder.toString();
        if (!StringUtils.hasText(replyText)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 返回内容为空");
        }

        AiVO.ChatResponseVO response = new AiVO.ChatResponseVO();
        response.setConversationId(request.getConversationId());
        response.setEmployeeId(employee.getId());
        response.setReplyRole("ASSISTANT");
        response.setProvider(normalizeProvider(config.getProvider()));
        response.setModel(resolveModel(config, null));
        response.setReplyAt(LocalDateTime.now());
        response.setReplyText(replyText.trim());
        response.setThinkingContent(StringUtils.hasText(thinkingBuilder.toString()) ? thinkingBuilder.toString().trim() : null);
        return response;
    }

    private void appendStreamingEvent(
            String payload,
            StringBuilder replyBuilder,
            StringBuilder thinkingBuilder,
            Consumer<String> onDelta,
            Consumer<String> onThinking
    ) throws IOException {
        if (!StringUtils.hasText(payload) || "[DONE]".equals(payload.trim())) {
            return;
        }

        JsonNode root = objectMapper.readTree(payload);
        String thinking = extractStreamingThinking(root);
        if (StringUtils.hasText(thinking)) {
            thinkingBuilder.append(thinking);
            if (onThinking != null) {
                onThinking.accept(thinking);
            }
        }

        String delta = extractStreamingDelta(root);
        if (!StringUtils.hasText(delta)) {
            return;
        }

        replyBuilder.append(delta);
        if (onDelta != null) {
            onDelta.accept(delta);
        }
    }

    private String buildRequestBody(
            AiLlmServiceConfig config,
            AiDTO.ChatRequest request,
            AiVO.EmployeeDetailVO employee,
            List<AiVO.SkillVO> skills
    ) throws IOException {
        var body = objectMapper.createObjectNode();
        String model = resolveModel(config, null);
        body.put("model", model);
        body.put("stream", false);

        BigDecimal temperature = config.getTemperature();
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (config.getMaxTokens() != null) {
            body.put("max_tokens", config.getMaxTokens());
        }
        applyProviderRequestOptions(config, request, body);

        var messages = body.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", buildSystemPrompt(employee, skills));
        messages.addObject()
                .put("role", "user")
                .put("content", buildUserPrompt(request));
        return objectMapper.writeValueAsString(body);
    }

    private String buildRequestBody(
            AiLlmServiceConfig config,
            AiDTO.ChatRequest request,
            AiVO.EmployeeDetailVO employee,
            List<AiVO.SkillVO> skills,
            boolean stream
    ) throws IOException {
        var body = objectMapper.readTree(buildRequestBody(config, request, employee, skills));
        if (body instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            objectNode.put("stream", stream);
            return objectMapper.writeValueAsString(objectNode);
        }
        return objectMapper.writeValueAsString(body);
    }

    private String buildSystemPrompt(AiVO.EmployeeDetailVO employee, List<AiVO.SkillVO> skills) {
        StringBuilder builder = new StringBuilder();
        String employeePrompt = StringUtils.hasText(employee.getSystemPrompt())
                ? employee.getSystemPrompt().trim()
                : DEFAULT_SYSTEM_PROMPT;
        builder.append(employeePrompt);

        builder.append("\n\n当前数字员工信息：");
        if (StringUtils.hasText(employee.getNickname())) {
            builder.append("\n- 名称：").append(employee.getNickname().trim());
        }
        if (StringUtils.hasText(employee.getPosition())) {
            builder.append("\n- 职位：").append(employee.getPosition().trim());
        }
        if (StringUtils.hasText(employee.getDescription())) {
            builder.append("\n- 简介：").append(employee.getDescription().trim());
        }

        if (skills != null && !skills.isEmpty()) {
            builder.append("\n\n可用技能：");
            for (AiVO.SkillVO skill : skills) {
                if (skill == null || !StringUtils.hasText(skill.getSkillCode())) {
                    continue;
                }
                builder.append("\n- ")
                        .append(safeText(skill.getSkillName(), skill.getSkillCode()))
                        .append(" [").append(skill.getSkillCode().trim()).append("]");
                if (StringUtils.hasText(skill.getCategory())) {
                    builder.append(" / ").append(skill.getCategory().trim());
                }
            }
            builder.append("\n\n工具调用约束：");
            builder.append("\n- 你只能通过平台 AI 工具执行入口使用上述技能，不要声称已经访问、修改或执行了未经过工具返回确认的底层数据。");
            builder.append("\n- 涉及新增、修改、删除、导出、发送、启停等高风险动作时，必须先要求用户二次确认。");
            builder.append("\n- 当工具返回权限不足或数据为空时，要如实说明限制，不要编造系统状态。");
        }

        return builder.toString().trim();
    }

    private String buildUserPrompt(AiDTO.ChatRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(request.getMessage().trim());
        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            builder.append("\n\n附件信息：");
            for (AiDTO.ChatAttachmentItem attachment : request.getAttachments()) {
                if (attachment == null || attachment.getFileId() == null) {
                    continue;
                }
                builder.append("\n- fileId=").append(attachment.getFileId());
            }
        }
        if (request.getKnowledgeReferences() != null && !request.getKnowledgeReferences().isEmpty()) {
            builder.append("\n\n已授权知识库参考资料：");
            int index = 1;
            for (AiVO.KnowledgeReferenceVO reference : request.getKnowledgeReferences()) {
                if (reference == null || !StringUtils.hasText(reference.getContent())) {
                    continue;
                }
                builder.append("\n\n[资料").append(index).append("] ")
                        .append(safeText(reference.getKnowledgeBaseName(), "知识库"))
                        .append(" / ")
                        .append(safeText(reference.getDocumentTitle(), reference.getOriginalFileName()));
                builder.append("\n").append(reference.getContent().trim());
                index++;
            }
            builder.append("\n\n请优先基于上述资料回答；如果资料不足，请明确说明未在知识库中找到充分依据。");
        }
        return builder.toString().trim();
    }

    private String extractReplyText(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode firstChoice = choices.get(0);
            String content = firstChoice.path("message").path("content").asText(null);
            if (StringUtils.hasText(content)) {
                return content;
            }
            content = firstChoice.path("text").asText(null);
            if (StringUtils.hasText(content)) {
                return content;
            }
        }

        String outputText = root.path("output_text").asText(null);
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }

        JsonNode output = root.path("output");
        if (output.isArray() && !output.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode contentItem : content) {
                        String text = contentItem.path("text").asText(null);
                        if (StringUtils.hasText(text)) {
                            builder.append(text);
                        }
                    }
                }
            }
            if (StringUtils.hasText(builder.toString())) {
                return builder.toString();
            }
        }

        return null;
    }

    private String extractStreamingDelta(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode firstChoice = choices.get(0);
            String content = firstChoice.path("delta").path("content").asText(null);
            if (StringUtils.hasText(content)) {
                return content;
            }
            content = firstChoice.path("text").asText(null);
            if (StringUtils.hasText(content)) {
                return content;
            }
        }

        String delta = root.path("delta").asText(null);
        if (StringUtils.hasText(delta)) {
            return delta;
        }
        String outputText = root.path("output_text").asText(null);
        return StringUtils.hasText(outputText) ? outputText : null;
    }

    private String extractStreamingThinking(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode firstChoice = choices.get(0);
            String reasoningContent = firstChoice.path("delta").path("reasoning_content").asText(null);
            if (StringUtils.hasText(reasoningContent)) {
                return reasoningContent;
            }
        }

        String reasoningContent = root.path("reasoning_content").asText(null);
        return StringUtils.hasText(reasoningContent) ? reasoningContent : null;
    }

    private String extractReasoningContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode firstChoice = choices.get(0);
            String reasoningContent = firstChoice.path("message").path("reasoning_content").asText(null);
            if (StringUtils.hasText(reasoningContent)) {
                return reasoningContent;
            }
            reasoningContent = firstChoice.path("reasoning_content").asText(null);
            if (StringUtils.hasText(reasoningContent)) {
                return reasoningContent;
            }
        }

        String reasoningContent = root.path("reasoning_content").asText(null);
        return StringUtils.hasText(reasoningContent) ? reasoningContent : null;
    }

    private String extractErrorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "响应内容为空";
        }
        try {
            return extractErrorMessage(objectMapper.readTree(body));
        } catch (IOException ignored) {
            return body.trim();
        }
    }

    private String extractErrorMessage(JsonNode root) {
        String message = root.path("error").path("message").asText(null);
        if (StringUtils.hasText(message)) {
            return message;
        }
        message = root.path("message").asText(null);
        if (StringUtils.hasText(message)) {
            return message;
        }
        return root.toString();
    }

    private String resolveEndpoint(AiLlmServiceConfig config) {
        String baseUrl = StringUtils.hasText(config.getBaseUrl()) ? config.getBaseUrl().trim() : defaultBaseUrlForProvider(config.getProvider());
        if (!StringUtils.hasText(baseUrl)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务未配置 Base URL");
        }
        validatePublicHttpBaseUrl(baseUrl);

        String normalizedProvider = normalizeProvider(config.getProvider());
        String normalizedBaseUrl = stripTrailingSlash(baseUrl);
        if ("deepseek".equals(normalizedProvider) && "https://api.deepseek.com/v1".equals(normalizedBaseUrl)) {
            normalizedBaseUrl = "https://api.deepseek.com";
        }
        if (normalizedBaseUrl.endsWith("/chat/completions")) {
            return normalizedBaseUrl;
        }
        if ("deepseek".equals(normalizedProvider)) {
            if (normalizedBaseUrl.endsWith("/v1")) {
                return normalizedBaseUrl + "/chat/completions";
            }
            return normalizedBaseUrl + "/chat/completions";
        }
        if (normalizedBaseUrl.endsWith("/v1")) {
            return normalizedBaseUrl + "/chat/completions";
        }
        return normalizedBaseUrl + "/v1/chat/completions";
    }

    private void validatePublicHttpBaseUrl(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务 Base URL 无效");
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务 Base URL 仅支持 HTTP 或 HTTPS");
        }
        if (uri.getRawUserInfo() != null || !StringUtils.hasText(uri.getHost())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务 Base URL 无效");
        }
        for (InetAddress address : resolveHostAddresses(uri.getHost())) {
            if (isBlockedAddress(address)) {
                throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务 Base URL 不允许访问内网或本机地址");
            }
        }
    }

    private InetAddress[] resolveHostAddresses(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务 Base URL 主机无法解析");
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isIpv6UniqueLocal(bytes);
    }

    private boolean isIpv6UniqueLocal(byte[] bytes) {
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private String defaultBaseUrlForProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        String normalizedProvider = normalizeProvider(provider);
        return switch (normalizedProvider) {
            case "openai", "openai-compatible" -> "https://api.openai.com/v1";
            case "deepseek" -> "https://api.deepseek.com";
            case "aliyun-bailian", "dashscope" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "ollama" -> "http://localhost:11434/v1";
            default -> null;
        };
    }

    private boolean shouldEnableThinking(AiLlmServiceConfig config) {
        String normalizedProvider = normalizeProvider(config.getProvider());
        return "aliyun-bailian".equals(normalizedProvider) || "dashscope".equals(normalizedProvider);
    }

    private void applyProviderRequestOptions(
            AiLlmServiceConfig config,
            AiDTO.ChatRequest request,
            com.fasterxml.jackson.databind.node.ObjectNode body
    ) {
        String normalizedProvider = normalizeProvider(config.getProvider());
        if ("deepseek".equals(normalizedProvider)) {
            applyDeepSeekRequestOptions(config, request, body);
            return;
        }
        if (shouldEnableThinking(config)) {
            boolean enableThinking = request == null || request.getEnableThinking() == null || Boolean.TRUE.equals(request.getEnableThinking());
            body.put("enable_thinking", enableThinking);
        }
    }

    private void applyDeepSeekRequestOptions(AiLlmServiceConfig config, AiDTO.ChatRequest request, com.fasterxml.jackson.databind.node.ObjectNode body) {
        String originalModel = StringUtils.hasText(config.getDefaultModel())
                ? config.getDefaultModel().trim().toLowerCase(Locale.ROOT)
                : "";
        if ("deepseek-chat".equals(originalModel)) {
            body.set("thinking", objectMapper.createObjectNode().put("type", "disabled"));
            return;
        }
        if ("deepseek-reasoner".equals(originalModel)) {
            boolean enableThinking = request == null || request.getEnableThinking() == null || Boolean.TRUE.equals(request.getEnableThinking());
            body.set("thinking", objectMapper.createObjectNode().put("type", enableThinking ? "enabled" : "disabled"));
        }
    }

    private String resolveModel(AiLlmServiceConfig config, JsonNode root) {
        if (StringUtils.hasText(config.getDefaultModel())) {
            return normalizeModelForProvider(config, config.getDefaultModel().trim());
        }
        if (root != null) {
            String model = root.path("model").asText(null);
            if (StringUtils.hasText(model)) {
                return normalizeModelForProvider(config, model);
            }
        }
        throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务未配置默认模型");
    }

    private String normalizeModelForProvider(AiLlmServiceConfig config, String model) {
        if (!StringUtils.hasText(model)) {
            return model;
        }
        if (!"deepseek".equals(normalizeProvider(config.getProvider()))) {
            return model;
        }
        String normalizedModel = model.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedModel) {
            case "deepseek-chat" -> "deepseek-v4-flash";
            case "deepseek-reasoner" -> "deepseek-v4-flash";
            default -> model.trim();
        };
    }

    private int resolveTimeout(AiLlmServiceConfig config) {
        return config.getTimeoutMs() == null || config.getTimeoutMs() <= 0 ? 60000 : config.getTimeoutMs();
    }

    private void validateConfig(AiLlmServiceConfig config) {
        if (config == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "数字员工未配置可用 LLM 服务");
        }
        if (!StringUtils.hasText(config.getProvider())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务未配置 provider");
        }
        if (!StringUtils.hasText(config.getDefaultModel())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务未配置默认模型");
        }
    }

    private String normalizeProvider(String provider) {
        return StringUtils.hasText(provider) ? provider.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message) ? message : exception.getClass().getSimpleName();
    }

    private String safeText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.trim() : fallback;
    }
}
