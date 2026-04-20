package com.yourcompany.saas.modules.message.vo;

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
        private String title;
        private String content;
        private String sourceType;
        private String publishStatus;
        private LocalDateTime publishedAt;
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
}
