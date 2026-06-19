package com.lumira.saas.modules.iam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("iam_user_identity")
public class IamUserIdentityEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String identityType;
    private String identifier;
    private String identifierNormalized;
    private Integer verified;
    private Integer primaryIdentity;
    private LocalDateTime boundAt;
    private LocalDateTime lastUsedAt;
    private String status;
    private Integer deleted;

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
    public Integer getVerified() { return verified; }
    public void setVerified(Integer verified) { this.verified = verified; }
    public Integer getPrimaryIdentity() { return primaryIdentity; }
    public void setPrimaryIdentity(Integer primaryIdentity) { this.primaryIdentity = primaryIdentity; }
    public LocalDateTime getBoundAt() { return boundAt; }
    public void setBoundAt(LocalDateTime boundAt) { this.boundAt = boundAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
