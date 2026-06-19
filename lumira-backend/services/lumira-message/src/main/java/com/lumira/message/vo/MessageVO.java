package com.lumira.message.vo;

import java.time.LocalDateTime;

public final class MessageVO {

    private MessageVO() {
    }

    public static class NoticeVO {
        private Long id;
        private Long tenantId;
        private String messageType;
        private String targetScope;
        private Long targetUserId;
        private String targetUserName;
        private Long targetRoleId;
        private String targetRoleName;
        private String title;
        private String content;
        private String sourceType;
        private String publishStatus;
        private LocalDateTime publishedAt;
        private Long createdBy;
        private Long updatedBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Boolean readFlag;
        private LocalDateTime readAt;

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

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public String getTargetScope() {
            return targetScope;
        }

        public void setTargetScope(String targetScope) {
            this.targetScope = targetScope;
        }

        public Long getTargetUserId() {
            return targetUserId;
        }

        public void setTargetUserId(Long targetUserId) {
            this.targetUserId = targetUserId;
        }

        public String getTargetUserName() {
            return targetUserName;
        }

        public void setTargetUserName(String targetUserName) {
            this.targetUserName = targetUserName;
        }

        public Long getTargetRoleId() {
            return targetRoleId;
        }

        public void setTargetRoleId(Long targetRoleId) {
            this.targetRoleId = targetRoleId;
        }

        public String getTargetRoleName() {
            return targetRoleName;
        }

        public void setTargetRoleName(String targetRoleName) {
            this.targetRoleName = targetRoleName;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getPublishStatus() {
            return publishStatus;
        }

        public void setPublishStatus(String publishStatus) {
            this.publishStatus = publishStatus;
        }

        public LocalDateTime getPublishedAt() {
            return publishedAt;
        }

        public void setPublishedAt(LocalDateTime publishedAt) {
            this.publishedAt = publishedAt;
        }

        public Long getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(Long createdBy) {
            this.createdBy = createdBy;
        }

        public Long getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(Long updatedBy) {
            this.updatedBy = updatedBy;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public Boolean getReadFlag() {
            return readFlag;
        }

        public void setReadFlag(Boolean readFlag) {
            this.readFlag = readFlag;
        }

        public LocalDateTime getReadAt() {
            return readAt;
        }

        public void setReadAt(LocalDateTime readAt) {
            this.readAt = readAt;
        }
    }

    public static class NoticePageResponse extends com.lumira.common.vo.PageResponse<MessageVO.NoticeVO> {
        private Boolean hasMore;
        private Boolean totalCapped;

        public Boolean getHasMore() {
            return hasMore;
        }

        public void setHasMore(Boolean hasMore) {
            this.hasMore = hasMore;
        }

        public Boolean getTotalCapped() {
            return totalCapped;
        }

        public void setTotalCapped(Boolean totalCapped) {
            this.totalCapped = totalCapped;
        }
    }

    public static class NoticeArchivePageResponse extends com.lumira.common.vo.PageResponse<MessageVO.NoticeVO> {
        private Boolean hasMore;
        private Boolean totalCapped;

        public Boolean getHasMore() {
            return hasMore;
        }

        public void setHasMore(Boolean hasMore) {
            this.hasMore = hasMore;
        }

        public Boolean getTotalCapped() {
            return totalCapped;
        }

        public void setTotalCapped(Boolean totalCapped) {
            this.totalCapped = totalCapped;
        }
    }

    public static class DeliveryLogPageResponse extends com.lumira.common.vo.PageResponse<MessageVO.DeliveryLogVO> {
        private Boolean hasMore;
        private Boolean totalCapped;

        public Boolean getHasMore() {
            return hasMore;
        }

        public void setHasMore(Boolean hasMore) {
            this.hasMore = hasMore;
        }

        public Boolean getTotalCapped() {
            return totalCapped;
        }

        public void setTotalCapped(Boolean totalCapped) {
            this.totalCapped = totalCapped;
        }
    }

    public static class MessageEventVO {
        private String eventType;
        private Long tenantId;
        private Long userId;
        private Integer unreadCount;
        private String message;
        private NoticeVO notice;
        private LocalDateTime timestamp;

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Integer getUnreadCount() {
            return unreadCount;
        }

        public void setUnreadCount(Integer unreadCount) {
            this.unreadCount = unreadCount;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public NoticeVO getNotice() {
            return notice;
        }

        public void setNotice(NoticeVO notice) {
            this.notice = notice;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class UnreadCountVO {
        private Long unreadCount;

        public Long getUnreadCount() {
            return unreadCount;
        }

        public void setUnreadCount(Long unreadCount) {
            this.unreadCount = unreadCount;
        }
    }

    public static class DeliveryLogVO {
        private Long id;
        private Long tenantId;
        private Long noticeId;
        private String channel;
        private String targetScope;
        private Long targetUserId;
        private String targetUserName;
        private String targetEmail;
        private String title;
        private String content;
        private String sendStatus;
        private String errorMessage;
        private LocalDateTime sentAt;
        private Long createdBy;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getNoticeId() { return noticeId; }
        public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getTargetScope() { return targetScope; }
        public void setTargetScope(String targetScope) { this.targetScope = targetScope; }
        public Long getTargetUserId() { return targetUserId; }
        public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
        public String getTargetUserName() { return targetUserName; }
        public void setTargetUserName(String targetUserName) { this.targetUserName = targetUserName; }
        public String getTargetEmail() { return targetEmail; }
        public void setTargetEmail(String targetEmail) { this.targetEmail = targetEmail; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSendStatus() { return sendStatus; }
        public void setSendStatus(String sendStatus) { this.sendStatus = sendStatus; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public LocalDateTime getSentAt() { return sentAt; }
        public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class WebSocketTicketVO {
        private String ticket;
        private Long expiresInSeconds;

        public String getTicket() {
            return ticket;
        }

        public void setTicket(String ticket) {
            this.ticket = ticket;
        }

        public Long getExpiresInSeconds() {
            return expiresInSeconds;
        }

        public void setExpiresInSeconds(Long expiresInSeconds) {
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    public static class WebSocketRuntimeVO {
        private Integer activeConnections;
        private Integer tenantCount;
        private Integer userCount;
        private LocalDateTime earliestConnectedAt;
        private LocalDateTime sampledAt;
        private java.util.List<TenantConnectionVO> tenants;
        private java.util.List<UserConnectionVO> topUsers;

        public Integer getActiveConnections() { return activeConnections; }
        public void setActiveConnections(Integer activeConnections) { this.activeConnections = activeConnections; }
        public Integer getTenantCount() { return tenantCount; }
        public void setTenantCount(Integer tenantCount) { this.tenantCount = tenantCount; }
        public Integer getUserCount() { return userCount; }
        public void setUserCount(Integer userCount) { this.userCount = userCount; }
        public LocalDateTime getEarliestConnectedAt() { return earliestConnectedAt; }
        public void setEarliestConnectedAt(LocalDateTime earliestConnectedAt) { this.earliestConnectedAt = earliestConnectedAt; }
        public LocalDateTime getSampledAt() { return sampledAt; }
        public void setSampledAt(LocalDateTime sampledAt) { this.sampledAt = sampledAt; }
        public java.util.List<TenantConnectionVO> getTenants() { return tenants; }
        public void setTenants(java.util.List<TenantConnectionVO> tenants) { this.tenants = tenants; }
        public java.util.List<UserConnectionVO> getTopUsers() { return topUsers; }
        public void setTopUsers(java.util.List<UserConnectionVO> topUsers) { this.topUsers = topUsers; }
    }

    public static class TenantConnectionVO {
        private Long tenantId;
        private Integer connectionCount;

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Integer getConnectionCount() { return connectionCount; }
        public void setConnectionCount(Integer connectionCount) { this.connectionCount = connectionCount; }
    }

    public static class UserConnectionVO {
        private Long userId;
        private Integer connectionCount;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Integer getConnectionCount() { return connectionCount; }
        public void setConnectionCount(Integer connectionCount) { this.connectionCount = connectionCount; }
    }
}
