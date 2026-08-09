package com.lumira.saas.modules.ai.dto;

import com.lumira.saas.modules.ai.vo.AiVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class AiDTO {

    private AiDTO() {
    }

    public static class EmployeeUpsertRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String nickname;
        private String position;
        private String avatarKey;
        private String description;
        private String greeting;
        private String systemPrompt;
        private Long defaultLlmServiceId;
        private Integer sortOrder;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public String getAvatarKey() {
            return avatarKey;
        }

        public void setAvatarKey(String avatarKey) {
            this.avatarKey = avatarKey;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getGreeting() {
            return greeting;
        }

        public void setGreeting(String greeting) {
            this.greeting = greeting;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public Long getDefaultLlmServiceId() {
            return defaultLlmServiceId;
        }

        public void setDefaultLlmServiceId(Long defaultLlmServiceId) {
            this.defaultLlmServiceId = defaultLlmServiceId;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }

    }

    public static class EmployeeKnowledgeBasesUpdateRequest {
        private List<Long> knowledgeBaseIds;

        public List<Long> getKnowledgeBaseIds() {
            return knowledgeBaseIds;
        }

        public void setKnowledgeBaseIds(List<Long> knowledgeBaseIds) {
            this.knowledgeBaseIds = knowledgeBaseIds;
        }
    }

    public static class EmployeeCapabilityItem {
        @NotBlank
        private String capabilityCode;
        @NotBlank
        private String permissionMode;

        public String getCapabilityCode() {
            return capabilityCode;
        }

        public void setCapabilityCode(String capabilityCode) {
            this.capabilityCode = capabilityCode;
        }

        public String getPermissionMode() {
            return permissionMode;
        }

        public void setPermissionMode(String permissionMode) {
            this.permissionMode = permissionMode;
        }
    }

    public static class EmployeeCapabilitiesUpdateRequest {
        @Valid
        private List<EmployeeCapabilityItem> capabilities;

        public List<EmployeeCapabilityItem> getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(List<EmployeeCapabilityItem> capabilities) {
            this.capabilities = capabilities;
        }
    }

    public static class KnowledgeBaseUpsertRequest {
        @NotBlank
        private String name;
        private String description;
        private String status;
        private String visibilityScope;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getVisibilityScope() {
            return visibilityScope;
        }

        public void setVisibilityScope(String visibilityScope) {
            this.visibilityScope = visibilityScope;
        }
    }

    public static class KnowledgeSearchRequest {
        @NotBlank
        private String query;
        private List<Long> knowledgeBaseIds;
        private Integer limit;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public List<Long> getKnowledgeBaseIds() {
            return knowledgeBaseIds;
        }

        public void setKnowledgeBaseIds(List<Long> knowledgeBaseIds) {
            this.knowledgeBaseIds = knowledgeBaseIds;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }
    }

    public static class LlmServiceUpsertRequest {
        @NotBlank
        private String provider;
        @NotBlank
        private String code;
        @NotBlank
        private String title;
        private String baseUrl;
        private String apiKey;
        private String defaultModel;
        private Boolean enabled;
        private Integer timeoutMs;
        private BigDecimal temperature;
        private Integer maxTokens;

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

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
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

    public static class LlmServiceTestRequest {
        private Long serviceId;
        private String provider;
        private String code;
        private String title;
        private String baseUrl;
        private String apiKey;
        private String defaultModel;
        private Integer timeoutMs;
        private BigDecimal temperature;
        private Integer maxTokens;

        public Long getServiceId() {
            return serviceId;
        }

        public void setServiceId(Long serviceId) {
            this.serviceId = serviceId;
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

    public static class ChatRequest {
        private Long employeeId;
        private List<Long> employeeIds;
        private Long conversationId;
        private Long pendingToolCallId;
        @NotBlank
        private String message;
        private Boolean enableThinking;
        @Valid
        private List<ChatAttachmentItem> attachments;
        private List<String> skillCodes;
        private List<Long> knowledgeBaseIds;
        private List<AiVO.KnowledgeReferenceVO> knowledgeReferences;
        private Boolean confirmed;

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public List<Long> getEmployeeIds() {
            return employeeIds;
        }

        public void setEmployeeIds(List<Long> employeeIds) {
            this.employeeIds = employeeIds;
        }

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }

        public Long getPendingToolCallId() {
            return pendingToolCallId;
        }

        public void setPendingToolCallId(Long pendingToolCallId) {
            this.pendingToolCallId = pendingToolCallId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Boolean getEnableThinking() {
            return enableThinking;
        }

        public void setEnableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
        }

        public List<ChatAttachmentItem> getAttachments() {
            return attachments;
        }

        public void setAttachments(List<ChatAttachmentItem> attachments) {
            this.attachments = attachments;
        }

        public List<String> getSkillCodes() {
            return skillCodes;
        }

        public void setSkillCodes(List<String> skillCodes) {
            this.skillCodes = skillCodes;
        }

        public List<Long> getKnowledgeBaseIds() {
            return knowledgeBaseIds;
        }

        public void setKnowledgeBaseIds(List<Long> knowledgeBaseIds) {
            this.knowledgeBaseIds = knowledgeBaseIds;
        }

        public List<AiVO.KnowledgeReferenceVO> getKnowledgeReferences() {
            return knowledgeReferences;
        }

        public void setKnowledgeReferences(List<AiVO.KnowledgeReferenceVO> knowledgeReferences) {
            this.knowledgeReferences = knowledgeReferences;
        }

        public Boolean getConfirmed() {
            return confirmed;
        }

        public void setConfirmed(Boolean confirmed) {
            this.confirmed = confirmed;
        }
    }

    public static class ChatAttachmentItem {
        @NotNull
        private Long fileId;

        public Long getFileId() {
            return fileId;
        }

        public void setFileId(Long fileId) {
            this.fileId = fileId;
        }
    }

    public static class ToolExecuteRequest {
        @NotNull
        private Long employeeId;
        private Long conversationId;
        @NotBlank
        private String toolCode;
        private Map<String, Object> arguments;
        private Boolean confirmed;

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }

        public String getToolCode() {
            return toolCode;
        }

        public void setToolCode(String toolCode) {
            this.toolCode = toolCode;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }

        public void setArguments(Map<String, Object> arguments) {
            this.arguments = arguments;
        }

        public Boolean getConfirmed() {
            return confirmed;
        }

        public void setConfirmed(Boolean confirmed) {
            this.confirmed = confirmed;
        }
    }

    public static class ToolProposeRequest {
        private Long employeeId;
        private Long conversationId;
        private String message;
        private String toolCode;
        private Map<String, Object> arguments;
        @Valid
        private List<ChatAttachmentItem> attachments;

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getToolCode() {
            return toolCode;
        }

        public void setToolCode(String toolCode) {
            this.toolCode = toolCode;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }

        public void setArguments(Map<String, Object> arguments) {
            this.arguments = arguments;
        }

        public List<ChatAttachmentItem> getAttachments() {
            return attachments;
        }

        public void setAttachments(List<ChatAttachmentItem> attachments) {
            this.attachments = attachments;
        }
    }

    public static class ToolConfirmRequest {
        @NotNull
        private Long pendingToolCallId;

        public Long getPendingToolCallId() {
            return pendingToolCallId;
        }

        public void setPendingToolCallId(Long pendingToolCallId) {
            this.pendingToolCallId = pendingToolCallId;
        }
    }

    public static class ToolPolicyUpsertRequest {
        @NotBlank
        private String policyName;
        private String toolCode;
        private String actionType;
        private String riskLevel;
        private String matchType;
        private String matchValue;
        private String verdict;
        private String message;
        private Boolean enabled;

        public String getPolicyName() {
            return policyName;
        }

        public void setPolicyName(String policyName) {
            this.policyName = policyName;
        }

        public String getToolCode() {
            return toolCode;
        }

        public void setToolCode(String toolCode) {
            this.toolCode = toolCode;
        }

        public String getActionType() {
            return actionType;
        }

        public void setActionType(String actionType) {
            this.actionType = actionType;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public String getMatchType() {
            return matchType;
        }

        public void setMatchType(String matchType) {
            this.matchType = matchType;
        }

        public String getMatchValue() {
            return matchValue;
        }

        public void setMatchValue(String matchValue) {
            this.matchValue = matchValue;
        }

        public String getVerdict() {
            return verdict;
        }

        public void setVerdict(String verdict) {
            this.verdict = verdict;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ConversationUpdateRequest {
        private String title;
        private Boolean pinned;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Boolean getPinned() {
            return pinned;
        }

        public void setPinned(Boolean pinned) {
            this.pinned = pinned;
        }
    }

    public static class ConversationExportRequest {
        @NotBlank
        private String format;

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }
}
