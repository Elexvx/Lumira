package com.legendary.invention.api.message;

import java.time.LocalDateTime;

public class MessageEventDTO {

    private String eventType;
    private Long tenantId;
    private Long userId;
    private Integer unreadCount;
    private String message;
    private MessageNoticeDTO notice;
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

    public MessageNoticeDTO getNotice() {
        return notice;
    }

    public void setNotice(MessageNoticeDTO notice) {
        this.notice = notice;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
