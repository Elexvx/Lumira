package com.legendary.invention.saas.modules.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

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
        @Valid
        private List<EmployeeSkillItem> skills;

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

        public List<EmployeeSkillItem> getSkills() {
            return skills;
        }

        public void setSkills(List<EmployeeSkillItem> skills) {
            this.skills = skills;
        }
    }

    public static class EmployeeSkillItem {
        @NotBlank
        private String skillCode;
        @NotBlank
        private String permissionMode;

        public String getSkillCode() {
            return skillCode;
        }

        public void setSkillCode(String skillCode) {
            this.skillCode = skillCode;
        }

        public String getPermissionMode() {
            return permissionMode;
        }

        public void setPermissionMode(String permissionMode) {
            this.permissionMode = permissionMode;
        }
    }

    public static class EmployeeSkillsUpdateRequest {
        @NotEmpty
        @Valid
        private List<EmployeeSkillItem> skills;

        public List<EmployeeSkillItem> getSkills() {
            return skills;
        }

        public void setSkills(List<EmployeeSkillItem> skills) {
            this.skills = skills;
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

    public static class ChatRequest {
        @NotNull
        private Long employeeId;
        private Long conversationId;
        @NotBlank
        private String message;
        private List<String> skillCodes;
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

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<String> getSkillCodes() {
            return skillCodes;
        }

        public void setSkillCodes(List<String> skillCodes) {
            this.skillCodes = skillCodes;
        }

        public Boolean getConfirmed() {
            return confirmed;
        }

        public void setConfirmed(Boolean confirmed) {
            this.confirmed = confirmed;
        }
    }
}
