package com.lumira.saas.modules.ai.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

        public String getDefaultSystemPromptTemplate() {
            return defaultSystemPromptTemplate;
        }

        public void setDefaultSystemPromptTemplate(String defaultSystemPromptTemplate) {
            this.defaultSystemPromptTemplate = defaultSystemPromptTemplate;
        }
    }

    public static class EmployeeCapabilityVO {
        private String capabilityCode;
        private String capabilityName;
        private String category;
        private String description;
        private String riskLevel;
        private Boolean readOnly;
        private Boolean needConfirm;
        private String permissionMode;

        public String getCapabilityCode() {
            return capabilityCode;
        }

        public void setCapabilityCode(String capabilityCode) {
            this.capabilityCode = capabilityCode;
        }

        public String getCapabilityName() {
            return capabilityName;
        }

        public void setCapabilityName(String capabilityName) {
            this.capabilityName = capabilityName;
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

        public String getPermissionMode() {
            return permissionMode;
        }

        public void setPermissionMode(String permissionMode) {
            this.permissionMode = permissionMode;
        }
    }

    public static class GovernanceOverviewVO {
        private Long employeeCount;
        private Long enabledEmployeeCount;
        private Long llmServiceCount;
        private Long enabledLlmServiceCount;
        private Long missingApiKeyServiceCount;
        private Long skillCount;
        private Long highRiskSkillCount;
        private Long highRiskAllowedBindingCount;
        private Long confirmationRequiredSkillCount;
        private LocalDateTime sampledAt;

        public Long getEmployeeCount() { return employeeCount; }
        public void setEmployeeCount(Long employeeCount) { this.employeeCount = employeeCount; }
        public Long getEnabledEmployeeCount() { return enabledEmployeeCount; }
        public void setEnabledEmployeeCount(Long enabledEmployeeCount) { this.enabledEmployeeCount = enabledEmployeeCount; }
        public Long getLlmServiceCount() { return llmServiceCount; }
        public void setLlmServiceCount(Long llmServiceCount) { this.llmServiceCount = llmServiceCount; }
        public Long getEnabledLlmServiceCount() { return enabledLlmServiceCount; }
        public void setEnabledLlmServiceCount(Long enabledLlmServiceCount) { this.enabledLlmServiceCount = enabledLlmServiceCount; }
        public Long getMissingApiKeyServiceCount() { return missingApiKeyServiceCount; }
        public void setMissingApiKeyServiceCount(Long missingApiKeyServiceCount) { this.missingApiKeyServiceCount = missingApiKeyServiceCount; }
        public Long getSkillCount() { return skillCount; }
        public void setSkillCount(Long skillCount) { this.skillCount = skillCount; }
        public Long getHighRiskSkillCount() { return highRiskSkillCount; }
        public void setHighRiskSkillCount(Long highRiskSkillCount) { this.highRiskSkillCount = highRiskSkillCount; }
        public Long getHighRiskAllowedBindingCount() { return highRiskAllowedBindingCount; }
        public void setHighRiskAllowedBindingCount(Long highRiskAllowedBindingCount) { this.highRiskAllowedBindingCount = highRiskAllowedBindingCount; }
        public Long getConfirmationRequiredSkillCount() { return confirmationRequiredSkillCount; }
        public void setConfirmationRequiredSkillCount(Long confirmationRequiredSkillCount) { this.confirmationRequiredSkillCount = confirmationRequiredSkillCount; }
        public LocalDateTime getSampledAt() { return sampledAt; }
        public void setSampledAt(LocalDateTime sampledAt) { this.sampledAt = sampledAt; }
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

    public static class LlmServiceTestResultVO {
        private Boolean success;
        private String message;
        private String provider;
        private String model;
        private Long latencyMs;
        private String replyText;

        public Boolean getSuccess() {
            return success;
        }

        public void setSuccess(Boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
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

        public Long getLatencyMs() {
            return latencyMs;
        }

        public void setLatencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
        }

        public String getReplyText() {
            return replyText;
        }

        public void setReplyText(String replyText) {
            this.replyText = replyText;
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
        private String permissionMode;
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

        public String getPermissionMode() {
            return permissionMode;
        }

        public void setPermissionMode(String permissionMode) {
            this.permissionMode = permissionMode;
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

    public static class ToolVO {
        private String toolCode;
        private String toolName;
        private String category;
        private String description;
        private String riskLevel;
        private Boolean readOnly;
        private Boolean needConfirm;
        private String requiredPermission;
        private Map<String, Object> inputSchema;

        public String getToolCode() {
            return toolCode;
        }

        public void setToolCode(String toolCode) {
            this.toolCode = toolCode;
        }

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
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

        public String getRequiredPermission() {
            return requiredPermission;
        }

        public void setRequiredPermission(String requiredPermission) {
            this.requiredPermission = requiredPermission;
        }

        public Map<String, Object> getInputSchema() {
            return inputSchema;
        }

        public void setInputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema;
        }
    }

    public static class ToolExecuteResultVO {
        private String toolCode;
        private String resultStatus;
        private String message;
        private Map<String, Object> data;
        private LocalDateTime executedAt;

        public String getToolCode() {
            return toolCode;
        }

        public void setToolCode(String toolCode) {
            this.toolCode = toolCode;
        }

        public String getResultStatus() {
            return resultStatus;
        }

        public void setResultStatus(String resultStatus) {
            this.resultStatus = resultStatus;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public void setData(Map<String, Object> data) {
            this.data = data;
        }

        public LocalDateTime getExecutedAt() {
            return executedAt;
        }

        public void setExecutedAt(LocalDateTime executedAt) {
            this.executedAt = executedAt;
        }
    }

    public static class ToolPolicyVO {
        private Long id;
        private Long tenantId;
        private String policyName;
        private String toolCode;
        private String actionType;
        private String riskLevel;
        private String matchType;
        private String matchValue;
        private String verdict;
        private String message;
        private Boolean enabled;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getPolicyName() { return policyName; }
        public void setPolicyName(String policyName) { this.policyName = policyName; }
        public String getToolCode() { return toolCode; }
        public void setToolCode(String toolCode) { this.toolCode = toolCode; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public String getMatchType() { return matchType; }
        public void setMatchType(String matchType) { this.matchType = matchType; }
        public String getMatchValue() { return matchValue; }
        public void setMatchValue(String matchValue) { this.matchValue = matchValue; }
        public String getVerdict() { return verdict; }
        public void setVerdict(String verdict) { this.verdict = verdict; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public LocalDateTime getUpdateTime() { return updateTime; }
        public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    }

    public static class ToolPlanVO {
        private Long id;
        private Long tenantId;
        private Long conversationId;
        private Long employeeId;
        private String toolCode;
        private String toolName;
        private String actionType;
        private String riskLevel;
        private String summary;
        private String permissionKey;
        private Boolean requiresConfirm;
        private String supervisorVerdict;
        private String supervisorMessage;
        private String policyVerdict;
        private String policyMessage;
        private String status;
        private Map<String, Object> arguments;
        private String argumentsHash;
        private String authorizationSnapshotJson;
        private Boolean approvalRequired;
        private LocalDateTime approvedAt;
        private LocalDateTime expiresAt;
        private LocalDateTime createTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getConversationId() { return conversationId; }
        public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public String getToolCode() { return toolCode; }
        public void setToolCode(String toolCode) { this.toolCode = toolCode; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getPermissionKey() { return permissionKey; }
        public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }
        public Boolean getRequiresConfirm() { return requiresConfirm; }
        public void setRequiresConfirm(Boolean requiresConfirm) { this.requiresConfirm = requiresConfirm; }
        public String getSupervisorVerdict() { return supervisorVerdict; }
        public void setSupervisorVerdict(String supervisorVerdict) { this.supervisorVerdict = supervisorVerdict; }
        public String getSupervisorMessage() { return supervisorMessage; }
        public void setSupervisorMessage(String supervisorMessage) { this.supervisorMessage = supervisorMessage; }
        public String getPolicyVerdict() { return policyVerdict; }
        public void setPolicyVerdict(String policyVerdict) { this.policyVerdict = policyVerdict; }
        public String getPolicyMessage() { return policyMessage; }
        public void setPolicyMessage(String policyMessage) { this.policyMessage = policyMessage; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Map<String, Object> getArguments() { return arguments; }
        public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }
        public String getArgumentsHash() { return argumentsHash; }
        public void setArgumentsHash(String argumentsHash) { this.argumentsHash = argumentsHash; }
        public String getAuthorizationSnapshotJson() { return authorizationSnapshotJson; }
        public void setAuthorizationSnapshotJson(String authorizationSnapshotJson) { this.authorizationSnapshotJson = authorizationSnapshotJson; }
        public Boolean getApprovalRequired() { return approvalRequired; }
        public void setApprovalRequired(Boolean approvalRequired) { this.approvalRequired = approvalRequired; }
        public LocalDateTime getApprovedAt() { return approvedAt; }
        public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    }

    public static class ConversationVO {
        private Long id;
        private Long tenantId;
        private Long employeeId;
        private Long ownerUserId;
        private String employeeName;
        private String conversationCode;
        private String title;
        private String preview;
        private String status;
        private Boolean pinned;
        private LocalDateTime latestMessageAt;
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

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getEmployeeName() {
            return employeeName;
        }

        public void setEmployeeName(String employeeName) {
            this.employeeName = employeeName;
        }

        public String getConversationCode() {
            return conversationCode;
        }

        public void setConversationCode(String conversationCode) {
            this.conversationCode = conversationCode;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getPreview() {
            return preview;
        }

        public void setPreview(String preview) {
            this.preview = preview;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Boolean getPinned() {
            return pinned;
        }

        public Boolean getIsPinned() {
            return pinned;
        }

        public void setPinned(Boolean pinned) {
            this.pinned = pinned;
        }

        public LocalDateTime getLatestMessageAt() {
            return latestMessageAt;
        }

        public void setLatestMessageAt(LocalDateTime latestMessageAt) {
            this.latestMessageAt = latestMessageAt;
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

    public static class MessageVO {
        private Long id;
        private Long conversationId;
        private String role;
        private String content;
        private List<MessageAttachmentVO> attachments;
        private LocalDateTime createTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<MessageAttachmentVO> getAttachments() {
            return attachments;
        }

        public void setAttachments(List<MessageAttachmentVO> attachments) {
            this.attachments = attachments;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }
    }

    public static class KnowledgeBaseVO {
        private Long id;
        private Long tenantId;
        private String kbCode;
        private String name;
        private String description;
        private String status;
        private String visibilityScope;
        private Long ownerUserId;
        private Long documentCount;
        private Long chunkCount;
        private Long createdBy;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getKbCode() { return kbCode; }
        public void setKbCode(String kbCode) { this.kbCode = kbCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getVisibilityScope() { return visibilityScope; }
        public void setVisibilityScope(String visibilityScope) { this.visibilityScope = visibilityScope; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getDocumentCount() { return documentCount; }
        public void setDocumentCount(Long documentCount) { this.documentCount = documentCount; }
        public Long getChunkCount() { return chunkCount; }
        public void setChunkCount(Long chunkCount) { this.chunkCount = chunkCount; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public LocalDateTime getUpdateTime() { return updateTime; }
        public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    }

    public static class KnowledgeDocumentVO {
        private Long id;
        private Long tenantId;
        private Long knowledgeBaseId;
        private Long fileId;
        private String title;
        private String originalFileName;
        private String fileExtension;
        private String mimeType;
        private Long fileSizeBytes;
        private String status;
        private String parseError;
        private Integer extractedCharCount;
        private Integer chunkCount;
        private Long createdBy;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getKnowledgeBaseId() { return knowledgeBaseId; }
        public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getOriginalFileName() { return originalFileName; }
        public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
        public String getFileExtension() { return fileExtension; }
        public void setFileExtension(String fileExtension) { this.fileExtension = fileExtension; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public Long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getParseError() { return parseError; }
        public void setParseError(String parseError) { this.parseError = parseError; }
        public Integer getExtractedCharCount() { return extractedCharCount; }
        public void setExtractedCharCount(Integer extractedCharCount) { this.extractedCharCount = extractedCharCount; }
        public Integer getChunkCount() { return chunkCount; }
        public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public LocalDateTime getUpdateTime() { return updateTime; }
        public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    }

    public static class KnowledgeReferenceVO {
        private Long chunkId;
        private Long knowledgeBaseId;
        private String knowledgeBaseName;
        private Long documentId;
        private String documentTitle;
        private Long fileId;
        private String originalFileName;
        private Integer chunkIndex;
        private String content;

        public Long getChunkId() { return chunkId; }
        public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
        public Long getKnowledgeBaseId() { return knowledgeBaseId; }
        public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
        public String getKnowledgeBaseName() { return knowledgeBaseName; }
        public void setKnowledgeBaseName(String knowledgeBaseName) { this.knowledgeBaseName = knowledgeBaseName; }
        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }
        public String getDocumentTitle() { return documentTitle; }
        public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        public String getOriginalFileName() { return originalFileName; }
        public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
        public Integer getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class ChatResponseVO {
        private Long conversationId;
        private String conversationCode;
        private Long employeeId;
        private String replyText;
        private String thinkingContent;
        private String replyRole;
        private String provider;
        private String model;
        private List<KnowledgeReferenceVO> references;
        private ToolPlanVO toolPlan;
        private ToolExecuteResultVO toolResult;
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

        public String getThinkingContent() {
            return thinkingContent;
        }

        public void setThinkingContent(String thinkingContent) {
            this.thinkingContent = thinkingContent;
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

        public List<KnowledgeReferenceVO> getReferences() {
            return references;
        }

        public void setReferences(List<KnowledgeReferenceVO> references) {
            this.references = references;
        }

        public ToolPlanVO getToolPlan() {
            return toolPlan;
        }

        public void setToolPlan(ToolPlanVO toolPlan) {
            this.toolPlan = toolPlan;
        }

        public ToolExecuteResultVO getToolResult() {
            return toolResult;
        }

        public void setToolResult(ToolExecuteResultVO toolResult) {
            this.toolResult = toolResult;
        }

        public LocalDateTime getReplyAt() {
            return replyAt;
        }

        public void setReplyAt(LocalDateTime replyAt) {
            this.replyAt = replyAt;
        }
    }

    public static class ChatStreamEventVO {
        private String type;
        private String message;
        private String delta;
        private ChatResponseVO response;
        private ToolPlanVO toolPlan;
        private ToolExecuteResultVO toolResult;

        public static ChatStreamEventVO status(String message) {
            ChatStreamEventVO event = new ChatStreamEventVO();
            event.setType("status");
            event.setMessage(message);
            return event;
        }

        public static ChatStreamEventVO delta(String delta) {
            ChatStreamEventVO event = new ChatStreamEventVO();
            event.setType("delta");
            event.setDelta(delta);
            return event;
        }

        public static ChatStreamEventVO thinking(String delta) {
            ChatStreamEventVO event = new ChatStreamEventVO();
            event.setType("thinking");
            event.setDelta(delta);
            return event;
        }

        public static ChatStreamEventVO done(ChatResponseVO response) {
            ChatStreamEventVO event = new ChatStreamEventVO();
            event.setType("done");
            event.setResponse(response);
            return event;
        }

        public static ChatStreamEventVO error(String message) {
            ChatStreamEventVO event = new ChatStreamEventVO();
            event.setType("error");
            event.setMessage(message);
            return event;
        }

        public static ChatStreamEventVO toolProposal(ToolPlanVO toolPlan) {
            ChatStreamEventVO event = new ChatStreamEventVO();
            event.setType("tool_proposal");
            event.setToolPlan(toolPlan);
            return event;
        }

        public static ChatStreamEventVO toolResult(ToolExecuteResultVO toolResult) {
            ChatStreamEventVO event = new ChatStreamEventVO();
            event.setType("tool_result");
            event.setToolResult(toolResult);
            return event;
        }

        public static ChatStreamEventVO toolBlocked(ToolPlanVO toolPlan, String message) {
            ChatStreamEventVO event = new ChatStreamEventVO();
            event.setType("tool_blocked");
            event.setToolPlan(toolPlan);
            event.setMessage(message);
            return event;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getDelta() {
            return delta;
        }

        public void setDelta(String delta) {
            this.delta = delta;
        }

        public ChatResponseVO getResponse() {
            return response;
        }

        public void setResponse(ChatResponseVO response) {
            this.response = response;
        }

        public ToolPlanVO getToolPlan() {
            return toolPlan;
        }

        public void setToolPlan(ToolPlanVO toolPlan) {
            this.toolPlan = toolPlan;
        }

        public ToolExecuteResultVO getToolResult() {
            return toolResult;
        }

        public void setToolResult(ToolExecuteResultVO toolResult) {
            this.toolResult = toolResult;
        }
    }

    public static class MessageAttachmentVO {
        private Long id;
        private Long fileId;
        private String originalFileName;
        private String fileExtension;
        private String mimeType;
        private Long fileSizeBytes;
        private String fileSizeLabel;
        private String publicUrl;
        private String previewUrl;
        private String downloadUrl;
        private String previewMode;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getFileId() {
            return fileId;
        }

        public void setFileId(Long fileId) {
            this.fileId = fileId;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }

        public void setOriginalFileName(String originalFileName) {
            this.originalFileName = originalFileName;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public void setFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public Long getFileSizeBytes() {
            return fileSizeBytes;
        }

        public void setFileSizeBytes(Long fileSizeBytes) {
            this.fileSizeBytes = fileSizeBytes;
        }

        public String getFileSizeLabel() {
            return fileSizeLabel;
        }

        public void setFileSizeLabel(String fileSizeLabel) {
            this.fileSizeLabel = fileSizeLabel;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public void setPublicUrl(String publicUrl) {
            this.publicUrl = publicUrl;
        }

        public String getPreviewUrl() {
            return previewUrl;
        }

        public void setPreviewUrl(String previewUrl) {
            this.previewUrl = previewUrl;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public void setDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }

        public String getPreviewMode() {
            return previewMode;
        }

        public void setPreviewMode(String previewMode) {
            this.previewMode = previewMode;
        }
    }

    public static class ConversationShareVO {
        private String shareToken;
        private Long conversationId;
        private String shareTitle;
        private LocalDateTime expiresAt;
        private LocalDateTime createTime;

        public String getShareToken() {
            return shareToken;
        }

        public void setShareToken(String shareToken) {
            this.shareToken = shareToken;
        }

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }

        public String getShareTitle() {
            return shareTitle;
        }

        public void setShareTitle(String shareTitle) {
            this.shareTitle = shareTitle;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }
    }

    public static class ConversationShareDetailVO {
        private ConversationShareVO share;
        private ConversationVO conversation;
        private List<MessageVO> messages;

        public ConversationShareVO getShare() {
            return share;
        }

        public void setShare(ConversationShareVO share) {
            this.share = share;
        }

        public ConversationVO getConversation() {
            return conversation;
        }

        public void setConversation(ConversationVO conversation) {
            this.conversation = conversation;
        }

        public List<MessageVO> getMessages() {
            return messages;
        }

        public void setMessages(List<MessageVO> messages) {
            this.messages = messages;
        }
    }

    public static class ConversationExportVO {
        private Long conversationId;
        private String title;
        private String format;
        private String fileName;
        private String mimeType;
        private String content;

        public Long getConversationId() {
            return conversationId;
        }

        public void setConversationId(Long conversationId) {
            this.conversationId = conversationId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
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
