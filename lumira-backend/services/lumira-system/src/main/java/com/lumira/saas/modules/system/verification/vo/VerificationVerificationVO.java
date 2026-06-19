package com.lumira.saas.modules.system.verification.vo;

public class VerificationVerificationVO {

    private Boolean verified;
    private Long tenantId;
    private Long userId;
    private String username;
    private String message;

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
