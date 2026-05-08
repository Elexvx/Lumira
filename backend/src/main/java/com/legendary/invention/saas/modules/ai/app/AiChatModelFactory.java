package com.legendary.invention.saas.modules.ai.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public interface AiChatModelFactory {

    AiChatClient create(AiLlmServiceConfig config);

    interface AiChatClient {
        AiVO.ChatResponseVO chat(AiDTO.ChatRequest request, AiVO.EmployeeDetailVO employee, List<AiVO.SkillVO> skills);
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
            你的目标是：基于当前租户的授权范围，稳妥、专业、清晰地完成用户交办的任务。
            你必须遵循以下要求：
            1. 先确认上下文，再执行任务。
            2. 遵守租户隔离和权限边界，不越权访问数据。
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
        return (request, employee, skills) -> invokeChatCompletion(config, request, employee, skills);
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

    private AiVO.ChatResponseVO parseResponse(
            AiLlmServiceConfig config,
            AiDTO.ChatRequest request,
            AiVO.EmployeeDetailVO employee,
            HttpResponse<String> httpResponse
    ) throws IOException {
        JsonNode root = objectMapper.readTree(httpResponse.body());
        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            String errorMessage = extractErrorMessage(root);
            throw new BizException(ErrorCode.BIZ_ERROR, "LLM 调用失败(" + httpResponse.statusCode() + "): " + errorMessage);
        }

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
        return response;
    }

    private String buildRequestBody(
            AiLlmServiceConfig config,
            AiDTO.ChatRequest request,
            AiVO.EmployeeDetailVO employee,
            List<AiVO.SkillVO> skills
    ) throws IOException {
        var body = objectMapper.createObjectNode();
        body.put("model", resolveModel(config, null));
        body.put("stream", false);

        BigDecimal temperature = config.getTemperature();
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (config.getMaxTokens() != null) {
            body.put("max_tokens", config.getMaxTokens());
        }

        var messages = body.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", buildSystemPrompt(employee, skills));
        messages.addObject()
                .put("role", "user")
                .put("content", buildUserPrompt(request));
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

        String normalizedBaseUrl = stripTrailingSlash(baseUrl);
        if (normalizedBaseUrl.endsWith("/chat/completions")) {
            return normalizedBaseUrl;
        }
        if (normalizedBaseUrl.endsWith("/v1")) {
            return normalizedBaseUrl + "/chat/completions";
        }
        return normalizedBaseUrl + "/v1/chat/completions";
    }

    private String defaultBaseUrlForProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return null;
        }
        String normalizedProvider = normalizeProvider(provider);
        return switch (normalizedProvider) {
            case "openai", "openai-compatible" -> "https://api.openai.com/v1";
            case "deepseek" -> "https://api.deepseek.com/v1";
            case "dashscope" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "ollama" -> "http://localhost:11434/v1";
            default -> null;
        };
    }

    private String resolveModel(AiLlmServiceConfig config, JsonNode root) {
        if (StringUtils.hasText(config.getDefaultModel())) {
            return config.getDefaultModel().trim();
        }
        if (root != null) {
            String model = root.path("model").asText(null);
            if (StringUtils.hasText(model)) {
                return model;
            }
        }
        throw new BizException(ErrorCode.BIZ_ERROR, "LLM 服务未配置默认模型");
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
