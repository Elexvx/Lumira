package com.legendary.invention.saas.modules.ai.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AiVO {

    private AiVO() {
    }

    public static class EmployeeVO {
        private Long id;
        private Long tenantId;
        private String username;
        private String nickname;
        private String position;
        private String avatarKey;
        private String description;
        private String greeting;
        private String systemPrompt;
        private Long defaultLlmServiceId;
        private String defaultLlmServiceTitle;
        private Boolean enabled;
        private Integer sortOrder;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

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

        public String getDefaultLlmServiceTitle() {
            return defaultLlmServiceTitle;
        }

        public void setDefaultLlmServiceTitle(String defaultLlmServiceTitle) {
            this.defaultLlmServiceTitle = defaultLlmServiceTitle;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }
    }

    public static class EmployeeDetailVO extends EmployeeVO {
        private String defaultSystemPromptTemplate;
        private List<EmployeeSkillVO> skills;

        public String getDefaultSystemPromptTemplate() {
            return defaultSystemPromptTemplate;
        }

        public void setDefaultSystemPromptTemplate(String defaultSystemPromptTemplate) {
            this.defaultSystemPromptTemplate = defaultSystemPromptTemplate;
        }

        public List<EmployeeSkillVO> getSkills() {
            return skills;
        }

        public void setSkills(List<EmployeeSkillVO> skills) {
            this.skills = skills;
        }
    }

    public static class EmployeeSkillVO {
        private Long id;
        private String skillCode;
        private String skillName;
        private String category;
        private String description;
        private String riskLevel;
        private Boolean readOnly;
        private Boolean needConfirm;
        private Boolean enabled;
        private String permissionMode;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getSkillCode() {
            return skillCode;
        }

        public void setSkillCode(String skillCode) {
            this.skillCode = skillCode;
        }

        public String getSkillName() {
            return skillName;
        }

        public void setSkillName(String skillName) {
            this.skillName = skillName;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public Boolean getReadOnly() {
            return readOnly;
        }

        public void setReadOnly(Boolean readOnly) {
            this.readOnly = readOnly;
        }

        public Boolean getNeedConfirm() {
            return needConfirm;
        }

        public void setNeedConfirm(Boolean needConfirm) {
            this.needConfirm = needConfirm;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getPermissionMode() {
            return permissionMode;
        }

        public void setPermissionMode(String permissionMode) {
            this.permissionMode = permissionMode;
        }
    }

    public static class LlmServiceVO {
        private Long id;
        private Long tenantId;
        private String provider;
        private String code;
        private String title;
        private String baseUrl;
        private String defaultModel;
        private Boolean enabled;
        private Integer timeoutMs;
        private BigDecimal temperature;
        private Integer maxTokens;
        private Boolean apiKeyConfigured;
        private String apiKeyMasked;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
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

        public Boolean getApiKeyConfigured() {
            return apiKeyConfigured;
        }

        public void setApiKeyConfigured(Boolean apiKeyConfigured) {
            this.apiKeyConfigured = apiKeyConfigured;
        }

        public String getApiKeyMasked() {
            return apiKeyMasked;
        }

        public void setApiKeyMasked(String apiKeyMasked) {
            this.apiKeyMasked = apiKeyMasked;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }
    }

    public static class SkillVO {
        private Long id;
        private String skillCode;
        private String skillName;
        private String category;
        private String description;
        private String riskLevel;
        private Boolean readOnly;
        private Boolean needConfirm;
        private Boolean enabled;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getSkillCode() {
            return skillCode;
        }

        public void setSkillCode(String skillCode) {
            this.skillCode = skillCode;
        }

        public String getSkillName() {
            return skillName;
        }

        public void setSkillName(String skillName) {
            this.skillName = skillName;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public Boolean getReadOnly() {
            return readOnly;
        }

        public void setReadOnly(Boolean readOnly) {
            this.readOnly = readOnly;
        }

        public Boolean getNeedConfirm() {
            return needConfirm;
        }

        public void setNeedConfirm(Boolean needConfirm) {
            this.needConfirm = needConfirm;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }
    }

    public static class ChatResponseVO {
        private Long conversationId;
        private String conversationCode;
        private Long employeeId;
        private String replyText;
        private String replyRole;
        private String provider;
        private String model;
        private Boolean mock;
        private LocalDateTime replyAt;

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }

        public String getConversationCode() {
            return conversationCode;
        }

        public void setConversationCode(String conversationCode) {
            this.conversationCode = conversationCode;
        }

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public String getReplyText() {
            return replyText;
        }

        public void setReplyText(String replyText) {
            this.replyText = replyText;
        }

        public String getReplyRole() {
            return replyRole;
        }

        public void setReplyRole(String replyRole) {
            this.replyRole = replyRole;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Boolean getMock() {
            return mock;
        }

        public void setMock(Boolean mock) {
            this.mock = mock;
        }

        public LocalDateTime getReplyAt() {
            return replyAt;
        }

        public void setReplyAt(LocalDateTime replyAt) {
            this.replyAt = replyAt;
        }
    }

    public static class PromptTemplateVO {
        private String defaultSystemPromptTemplate;

        public String getDefaultSystemPromptTemplate() {
            return defaultSystemPromptTemplate;
        }

        public void setDefaultSystemPromptTemplate(String defaultSystemPromptTemplate) {
            this.defaultSystemPromptTemplate = defaultSystemPromptTemplate;
        }
    }
}
