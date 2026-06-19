package com.lumira.saas.modules.system.user.vo;

import java.time.LocalDateTime;
import java.util.List;

public class UserDetailVO extends UserVO {

    private List<Long> roleIds;
    private List<Long> deptIds;
    private Long primaryDeptId;
    private List<Long> tenantIds;
    private List<UserIdentityVO> identities;
    private List<UserDeviceVO> recentDevices;
    private UserSecuritySettingVO securitySetting;

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
    public List<Long> getDeptIds() { return deptIds; }
    public void setDeptIds(List<Long> deptIds) { this.deptIds = deptIds; }
    public Long getPrimaryDeptId() { return primaryDeptId; }
    public void setPrimaryDeptId(Long primaryDeptId) { this.primaryDeptId = primaryDeptId; }
    public List<Long> getTenantIds() { return tenantIds; }
    public void setTenantIds(List<Long> tenantIds) { this.tenantIds = tenantIds; }
    public List<UserIdentityVO> getIdentities() { return identities; }
    public void setIdentities(List<UserIdentityVO> identities) { this.identities = identities; }
    public List<UserDeviceVO> getRecentDevices() { return recentDevices; }
    public void setRecentDevices(List<UserDeviceVO> recentDevices) { this.recentDevices = recentDevices; }
    public UserSecuritySettingVO getSecuritySetting() { return securitySetting; }
    public void setSecuritySetting(UserSecuritySettingVO securitySetting) { this.securitySetting = securitySetting; }

    public static class UserIdentityVO {
        private Long id;
        private String identityType;
        private String identifier;
        private Boolean verified;
        private Boolean primaryIdentity;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getIdentityType() { return identityType; }
        public void setIdentityType(String identityType) { this.identityType = identityType; }
        public String getIdentifier() { return identifier; }
        public void setIdentifier(String identifier) { this.identifier = identifier; }
        public Boolean getVerified() { return verified; }
        public void setVerified(Boolean verified) { this.verified = verified; }
        public Boolean getPrimaryIdentity() { return primaryIdentity; }
        public void setPrimaryIdentity(Boolean primaryIdentity) { this.primaryIdentity = primaryIdentity; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class UserDeviceVO {
        private Long id;
        private String deviceId;
        private String deviceName;
        private String deviceType;
        private String os;
        private String browser;
        private String lastIp;
        private LocalDateTime lastActiveAt;
        private Boolean trusted;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
        public String getOs() { return os; }
        public void setOs(String os) { this.os = os; }
        public String getBrowser() { return browser; }
        public void setBrowser(String browser) { this.browser = browser; }
        public String getLastIp() { return lastIp; }
        public void setLastIp(String lastIp) { this.lastIp = lastIp; }
        public LocalDateTime getLastActiveAt() { return lastActiveAt; }
        public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
        public Boolean getTrusted() { return trusted; }
        public void setTrusted(Boolean trusted) { this.trusted = trusted; }
    }

    public static class UserSecuritySettingVO {
        private Boolean mfaEnabled;
        private Boolean passwordLoginEnabled;
        private Boolean smsLoginEnabled;
        private Boolean emailLoginEnabled;
        private Boolean passkeyEnabled;
        private Boolean loginNotifyEnabled;

        public Boolean getMfaEnabled() { return mfaEnabled; }
        public void setMfaEnabled(Boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
        public Boolean getPasswordLoginEnabled() { return passwordLoginEnabled; }
        public void setPasswordLoginEnabled(Boolean passwordLoginEnabled) { this.passwordLoginEnabled = passwordLoginEnabled; }
        public Boolean getSmsLoginEnabled() { return smsLoginEnabled; }
        public void setSmsLoginEnabled(Boolean smsLoginEnabled) { this.smsLoginEnabled = smsLoginEnabled; }
        public Boolean getEmailLoginEnabled() { return emailLoginEnabled; }
        public void setEmailLoginEnabled(Boolean emailLoginEnabled) { this.emailLoginEnabled = emailLoginEnabled; }
        public Boolean getPasskeyEnabled() { return passkeyEnabled; }
        public void setPasskeyEnabled(Boolean passkeyEnabled) { this.passkeyEnabled = passkeyEnabled; }
        public Boolean getLoginNotifyEnabled() { return loginNotifyEnabled; }
        public void setLoginNotifyEnabled(Boolean loginNotifyEnabled) { this.loginNotifyEnabled = loginNotifyEnabled; }
    }
}
