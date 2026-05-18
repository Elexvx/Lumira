package com.legendary.invention.saas.modules.iam.service;

import com.legendary.invention.saas.modules.user.entity.SysUserEntity;

import java.time.LocalDateTime;
import java.util.List;

public class IamUserAccount {
    private Long userId;
    private String userNo;
    private String displayName;
    private String avatarUrl;
    private String status;
    private String userType;
    private String source;
    private LocalDateTime registeredAt;
    private LocalDateTime lastLoginAt;
    private List<IdentityView> identities = List.of();
    private CredentialView passwordCredential;
    private SysUserEntity legacyUser;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserNo() { return userNo; }
    public void setUserNo(String userNo) { this.userNo = userNo; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public List<IdentityView> getIdentities() { return identities; }
    public void setIdentities(List<IdentityView> identities) { this.identities = identities == null ? List.of() : identities; }
    public CredentialView getPasswordCredential() { return passwordCredential; }
    public void setPasswordCredential(CredentialView passwordCredential) { this.passwordCredential = passwordCredential; }
    public SysUserEntity getLegacyUser() { return legacyUser; }
    public void setLegacyUser(SysUserEntity legacyUser) { this.legacyUser = legacyUser; }

    public static class IdentityView {
        private Long id;
        private Long userId;
        private String identityType;
        private String identifier;
        private String identifierNormalized;
        private Boolean verified;
        private Boolean primaryIdentity;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getIdentityType() { return identityType; }
        public void setIdentityType(String identityType) { this.identityType = identityType; }
        public String getIdentifier() { return identifier; }
        public void setIdentifier(String identifier) { this.identifier = identifier; }
        public String getIdentifierNormalized() { return identifierNormalized; }
        public void setIdentifierNormalized(String identifierNormalized) { this.identifierNormalized = identifierNormalized; }
        public Boolean getVerified() { return verified; }
        public void setVerified(Boolean verified) { this.verified = verified; }
        public Boolean getPrimaryIdentity() { return primaryIdentity; }
        public void setPrimaryIdentity(Boolean primaryIdentity) { this.primaryIdentity = primaryIdentity; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class CredentialView {
        private Long id;
        private Long userId;
        private String credentialType;
        private String credentialSecret;
        private String algorithm;
        private Integer version;
        private LocalDateTime expireAt;
        private LocalDateTime lastChangedAt;
        private String status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getCredentialType() { return credentialType; }
        public void setCredentialType(String credentialType) { this.credentialType = credentialType; }
        public String getCredentialSecret() { return credentialSecret; }
        public void setCredentialSecret(String credentialSecret) { this.credentialSecret = credentialSecret; }
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public LocalDateTime getExpireAt() { return expireAt; }
        public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
        public LocalDateTime getLastChangedAt() { return lastChangedAt; }
        public void setLastChangedAt(LocalDateTime lastChangedAt) { this.lastChangedAt = lastChangedAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
