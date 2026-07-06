package com.lumira.saas.modules.system.verification.vo;

import java.util.List;

public class VerificationVerificationVO {

    private Boolean verified;
    private Long userId;
    private String userUuid;
    private String username;
    private String message;
    private List<String> recoveryCodes;

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<String> getRecoveryCodes() { return recoveryCodes; }
    public void setRecoveryCodes(List<String> recoveryCodes) { this.recoveryCodes = recoveryCodes; }
}
