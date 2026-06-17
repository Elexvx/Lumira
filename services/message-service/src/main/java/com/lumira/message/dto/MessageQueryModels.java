package com.lumira.message.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class MessageQueryModels {

    private MessageQueryModels() {
    }

    public static class NoticeArchiveQuery {
        private Long tenantId;
        private Long userId;
        private boolean manageArchive;
        private String keyword;
        private String messageType;
        private String targetScope;
        private String sourceType;
        private String publishStatus;
        private LocalDateTime publishedAtStart;
        private LocalDateTime publishedAtEnd;
        private String sortField;
        private String sortOrder;
        private String permissionSnapshotVersion;
        private long limit;
        private long offset;
        private long countLimit;
        private List<Long> roleIds = List.of();

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public boolean isManageArchive() { return manageArchive; }
        public void setManageArchive(boolean manageArchive) { this.manageArchive = manageArchive; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public String getMessageType() { return messageType; }
        public void setMessageType(String messageType) { this.messageType = messageType; }
        public String getTargetScope() { return targetScope; }
        public void setTargetScope(String targetScope) { this.targetScope = targetScope; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getPublishStatus() { return publishStatus; }
        public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
        public LocalDateTime getPublishedAtStart() { return publishedAtStart; }
        public void setPublishedAtStart(LocalDateTime publishedAtStart) { this.publishedAtStart = publishedAtStart; }
        public LocalDateTime getPublishedAtEnd() { return publishedAtEnd; }
        public void setPublishedAtEnd(LocalDateTime publishedAtEnd) { this.publishedAtEnd = publishedAtEnd; }
        public String getSortField() { return sortField; }
        public void setSortField(String sortField) { this.sortField = sortField; }
        public String getSortOrder() { return sortOrder; }
        public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
        public String getPermissionSnapshotVersion() { return permissionSnapshotVersion; }
        public void setPermissionSnapshotVersion(String permissionSnapshotVersion) { this.permissionSnapshotVersion = permissionSnapshotVersion; }
        public long getLimit() { return limit; }
        public void setLimit(long limit) { this.limit = limit; }
        public long getOffset() { return offset; }
        public void setOffset(long offset) { this.offset = offset; }
        public long getCountLimit() { return countLimit; }
        public void setCountLimit(long countLimit) { this.countLimit = countLimit; }
        public List<Long> getRoleIds() { return roleIds; }
        public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds == null ? List.of() : roleIds; }
    }

    public static class DeliveryLogQuery {
        private Long tenantId;
        private String keyword;
        private String channel;
        private String targetScope;
        private String sendStatus;
        private LocalDateTime createdAtStart;
        private LocalDateTime createdAtEnd;
        private long limit;
        private long offset;
        private long countLimit;

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getTargetScope() { return targetScope; }
        public void setTargetScope(String targetScope) { this.targetScope = targetScope; }
        public String getSendStatus() { return sendStatus; }
        public void setSendStatus(String sendStatus) { this.sendStatus = sendStatus; }
        public LocalDateTime getCreatedAtStart() { return createdAtStart; }
        public void setCreatedAtStart(LocalDateTime createdAtStart) { this.createdAtStart = createdAtStart; }
        public LocalDateTime getCreatedAtEnd() { return createdAtEnd; }
        public void setCreatedAtEnd(LocalDateTime createdAtEnd) { this.createdAtEnd = createdAtEnd; }
        public long getLimit() { return limit; }
        public void setLimit(long limit) { this.limit = limit; }
        public long getOffset() { return offset; }
        public void setOffset(long offset) { this.offset = offset; }
        public long getCountLimit() { return countLimit; }
        public void setCountLimit(long countLimit) { this.countLimit = countLimit; }
    }

}
