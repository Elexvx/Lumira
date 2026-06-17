package com.lumira.ai.vo;

import java.time.LocalDateTime;

public class AiEmployeeVO {
    private Long id;
    private Long tenantId;
    private String username;
    private String nickname;
    private String position;
    private String avatarKey;
    private String description;
    private String greeting;
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
