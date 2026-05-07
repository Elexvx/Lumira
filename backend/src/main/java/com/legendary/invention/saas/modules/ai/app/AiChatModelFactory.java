package com.legendary.invention.saas.modules.ai.app;

import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
    private java.math.BigDecimal temperature;
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

    public java.math.BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(java.math.BigDecimal temperature) {
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
class MockAiChatModelFactory implements AiChatModelFactory {

    @Override
    public AiChatClient create(AiLlmServiceConfig config) {
        return (request, employee, skills) -> {
            AiVO.ChatResponseVO response = new AiVO.ChatResponseVO();
            response.setConversationId(request.getConversationId());
            response.setEmployeeId(employee.getId());
            response.setReplyRole("ASSISTANT");
            response.setProvider(config == null ? null : config.getProvider());
            response.setModel(config == null ? null : config.getDefaultModel());
            response.setMock(Boolean.TRUE);
            response.setReplyAt(LocalDateTime.now());
            String nickname = employee.getNickname() == null ? employee.getUsername() : employee.getNickname();
            response.setReplyText("【MVP 预留回复】" + nickname + " 已接收消息：" + request.getMessage());
            return response;
        };
    }
}
