package com.lumira.saas.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("iam_user_credential")
public class IamUserCredentialEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String credentialType;
    private String credentialSecret;
    private String algorithm;
    private Integer version;
    private LocalDateTime expireAt;
    private LocalDateTime lastChangedAt;
    private String status;
    private Integer deleted;

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
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
