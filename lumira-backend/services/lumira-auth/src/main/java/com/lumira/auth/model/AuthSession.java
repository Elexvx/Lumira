package com.lumira.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lumira.common.security.data.DataPermissionRule;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthSession {
    private String sessionId;
    private Long userId;
    private String username;
    private Long currentTenantId;
    private Instant loginTime;
    private Instant lastActivityAt;
    private Instant expireTime;
    private Integer sessionVersion;
    private String clientType;
    private String loginIp;
    private String userAgent;
    private String refreshTokenId;
    private String nickname;
    private String realName;
    private String avatarUrl;
    private String mobile;
    private String email;
    private String birthMonth;
    private String gender;
    private String region;
    private String availableTime;
    private String idCardNumber;
    private String locale;
    private String permissionsVersion;
    private List<String> permissions;
    private List<Long> roleIds;
    private Long primaryDeptId;
    private List<Long> deptIds;
    private List<Long> descendantDeptIds;
    private List<DataPermissionRule> dataScopes;
    private Boolean requiresPasswordChange;
    private String defaultHomePath;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getCurrentTenantId() {
        return currentTenantId;
    }

    public void setCurrentTenantId(Long currentTenantId) {
        this.currentTenantId = currentTenantId;
    }

    public Instant getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Instant loginTime) {
        this.loginTime = loginTime;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public Instant getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Instant expireTime) {
        this.expireTime = expireTime;
    }

    public Integer getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(Integer sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getRefreshTokenId() {
        return refreshTokenId;
    }

    public void setRefreshTokenId(String refreshTokenId) {
        this.refreshTokenId = refreshTokenId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBirthMonth() {
        return birthMonth;
    }

    public void setBirthMonth(String birthMonth) {
        this.birthMonth = birthMonth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAvailableTime() {
        return availableTime;
    }

    public void setAvailableTime(String availableTime) {
        this.availableTime = availableTime;
    }

    public String getIdCardNumber() {
        return idCardNumber;
    }

    public void setIdCardNumber(String idCardNumber) {
        this.idCardNumber = idCardNumber;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getPermissionsVersion() {
        return permissionsVersion;
    }

    public void setPermissionsVersion(String permissionsVersion) {
        this.permissionsVersion = permissionsVersion;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }

    public Long getPrimaryDeptId() {
        return primaryDeptId;
    }

    public void setPrimaryDeptId(Long primaryDeptId) {
        this.primaryDeptId = primaryDeptId;
    }

    public List<Long> getDeptIds() {
        return deptIds;
    }

    public void setDeptIds(List<Long> deptIds) {
        this.deptIds = deptIds;
    }

    public List<Long> getDescendantDeptIds() {
        return descendantDeptIds;
    }

    public void setDescendantDeptIds(List<Long> descendantDeptIds) {
        this.descendantDeptIds = descendantDeptIds;
    }

    public List<DataPermissionRule> getDataScopes() {
        return dataScopes;
    }

    public void setDataScopes(List<DataPermissionRule> dataScopes) {
        this.dataScopes = dataScopes;
    }

    public Boolean getRequiresPasswordChange() {
        return requiresPasswordChange;
    }

    public void setRequiresPasswordChange(Boolean requiresPasswordChange) {
        this.requiresPasswordChange = requiresPasswordChange;
    }

    public String getDefaultHomePath() {
        return defaultHomePath;
    }

    public void setDefaultHomePath(String defaultHomePath) {
        this.defaultHomePath = defaultHomePath;
    }
}
