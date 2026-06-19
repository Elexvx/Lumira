package com.lumira.saas.modules.system.audit.vo;

import java.time.LocalDateTime;

public class AuditLogVO {

    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String logType;
    private String logResult;
    private String moduleName;
    private String actionName;
    private String operationType;
    private String detailMessage;
    private String failReason;
    private String requestId;
    private String traceId;
    private String loginIp;
    private String userAgent;
    private Long conversationId;
    private Long employeeId;
    private String skillCode;
    private String toolName;
    private String permissionMode;
    private Integer confirmRequired;
    private Integer confirmResult;
    private String requestPayloadJson;
    private String responsePayloadJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getLogType() { return logType; }
    public void setLogType(String logType) { this.logType = logType; }
    public String getLogResult() { return logResult; }
    public void setLogResult(String logResult) { this.logResult = logResult; }
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getDetailMessage() { return detailMessage; }
    public void setDetailMessage(String detailMessage) { this.detailMessage = detailMessage; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getLoginIp() { return loginIp; }
    public void setLoginIp(String loginIp) { this.loginIp = loginIp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getPermissionMode() { return permissionMode; }
    public void setPermissionMode(String permissionMode) { this.permissionMode = permissionMode; }
    public Integer getConfirmRequired() { return confirmRequired; }
    public void setConfirmRequired(Integer confirmRequired) { this.confirmRequired = confirmRequired; }
    public Integer getConfirmResult() { return confirmResult; }
    public void setConfirmResult(Integer confirmResult) { this.confirmResult = confirmResult; }
    public String getRequestPayloadJson() { return requestPayloadJson; }
    public void setRequestPayloadJson(String requestPayloadJson) { this.requestPayloadJson = requestPayloadJson; }
    public String getResponsePayloadJson() { return responsePayloadJson; }
    public void setResponsePayloadJson(String responsePayloadJson) { this.responsePayloadJson = responsePayloadJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
