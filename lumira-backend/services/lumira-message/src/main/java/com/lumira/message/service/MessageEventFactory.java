package com.lumira.message.service;

import com.lumira.api.message.MessageEventDTO;
import com.lumira.api.message.MessageNoticeDTO;
import com.lumira.common.web.TraceContext;
import com.lumira.message.vo.MessageVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Component
public class MessageEventFactory {

    public static final String CATEGORY_BUSINESS = "BUSINESS";
    public static final String CATEGORY_COMPENSATION = "COMPENSATION";
    public static final String SOURCE_MESSAGE = "MESSAGE";
    public static final String EVENT_CREATED = "NOTICE_CREATED";
    public static final String EVENT_RETRACTED = "NOTICE_RETRACTED";
    public static final String EVENT_READ = "NOTICE_READ";
    public static final String EVENT_UNREAD_COUNT = "UNREAD_COUNT";
    public static final String EVENT_SYNC_STATE = "SYNC_STATE";
    public static final String EVENT_CONNECTED = "CONNECTED";
    public static final String EVENT_HEARTBEAT = "HEARTBEAT";

    public MessageEventDTO createCreatedEvent(MessageVO.NoticeVO notice) {
        return buildNoticeEvent(CATEGORY_BUSINESS, EVENT_CREATED, notice, null, "消息已发布", notice == null ? null : notice.getId(), null, null);
    }

    public MessageEventDTO createRetractedEvent(MessageVO.NoticeVO notice) {
        return buildNoticeEvent(CATEGORY_BUSINESS, EVENT_RETRACTED, notice, null, "消息状态已更新", notice == null ? null : notice.getId(), null, null);
    }

    public MessageEventDTO createReadEvent(Long tenantId, Long userId, MessageVO.NoticeVO notice, Integer unreadCount) {
        return buildNoticeEvent(CATEGORY_BUSINESS, EVENT_READ, notice, unreadCount, "消息状态已更新", notice == null ? null : notice.getId(), tenantId, userId);
    }

    public MessageEventDTO createUnreadCountEvent(Long tenantId, Long userId, Integer unreadCount) {
        MessageEventDTO event = buildBaseEvent(CATEGORY_BUSINESS, EVENT_UNREAD_COUNT, SOURCE_MESSAGE, tenantId, userId, null, unreadCount == null ? 0L : unreadCount.longValue());
        event.setUnreadCount(unreadCount);
        event.setMessage("未读消息数量已更新");
        event.getPayload().put("unreadCount", unreadCount);
        return event;
    }

    public MessageEventDTO createSyncStateEvent(Long tenantId, Long userId, Integer unreadCount, Long latestVersion, Integer sessionVersion) {
        MessageEventDTO event = buildBaseEvent(CATEGORY_COMPENSATION, EVENT_SYNC_STATE, SOURCE_MESSAGE, tenantId, userId, null, latestVersion);
        event.setUnreadCount(unreadCount);
        event.setMessage("消息状态已同步");
        event.getPayload().put("unreadCount", unreadCount);
        event.getPayload().put("latestVersion", latestVersion);
        event.getPayload().put("sessionVersion", sessionVersion);
        event.getPayload().put("source", "initial-sync");
        return event;
    }

    public MessageEventDTO createHeartbeatEvent(Long tenantId, Long userId) {
        MessageEventDTO event = buildBaseEvent(CATEGORY_COMPENSATION, EVENT_HEARTBEAT, SOURCE_MESSAGE, tenantId, userId, null, null);
        event.setMessage("heartbeat");
        return event;
    }

    public MessageNoticeDTO copyNotice(MessageVO.NoticeVO notice) {
        if (notice == null) {
            return null;
        }
        MessageNoticeDTO dto = new MessageNoticeDTO();
        dto.setId(notice.getId());
        dto.setTenantId(notice.getTenantId());
        dto.setMessageType(notice.getMessageType());
        dto.setTargetScope(notice.getTargetScope());
        dto.setTargetUserId(notice.getTargetUserId());
        dto.setTargetUserName(notice.getTargetUserName());
        dto.setTargetRoleId(notice.getTargetRoleId());
        dto.setTargetRoleName(notice.getTargetRoleName());
        dto.setTitle(notice.getTitle());
        dto.setContent(notice.getContent());
        dto.setSourceType(notice.getSourceType());
        dto.setPublishStatus(notice.getPublishStatus());
        dto.setPublishedAt(notice.getPublishedAt());
        dto.setCreatedBy(notice.getCreatedBy());
        dto.setUpdatedBy(notice.getUpdatedBy());
        dto.setCreatedAt(notice.getCreatedAt());
        dto.setUpdatedAt(notice.getUpdatedAt());
        dto.setReadFlag(notice.getReadFlag());
        dto.setReadAt(notice.getReadAt());
        return dto;
    }

    public MessageEventDTO toConnectedEvent(Long tenantId, Long userId) {
        MessageEventDTO event = buildBaseEvent(CATEGORY_COMPENSATION, EVENT_CONNECTED, SOURCE_MESSAGE, tenantId, userId, null, null);
        event.setMessage("消息通道已连接");
        return event;
    }

    public MessageEventDTO toHeartbeatEvent(Long tenantId, Long userId) {
        return createHeartbeatEvent(tenantId, userId);
    }

    private MessageEventDTO buildNoticeEvent(
            String eventCategory,
            String eventType,
            MessageVO.NoticeVO notice,
            Integer unreadCount,
            String message,
            Long version,
            Long tenantId,
            Long userId
    ) {
        MessageEventDTO event = buildBaseEvent(
                eventCategory,
                eventType,
                SOURCE_MESSAGE,
                tenantId == null ? (notice == null ? null : notice.getTenantId()) : tenantId,
                userId == null ? (notice == null ? null : notice.getTargetUserId()) : userId,
                copyNotice(notice),
                version
        );
        event.setUnreadCount(unreadCount);
        event.setMessage(message);
        if (notice != null) {
            event.getPayload().put("targetScope", notice.getTargetScope());
            event.getPayload().put("targetUserId", notice.getTargetUserId());
            event.getPayload().put("targetRoleId", notice.getTargetRoleId());
            event.getPayload().put("notice", event.getNotice());
        }
        if (unreadCount != null) {
            event.getPayload().put("unreadCount", unreadCount);
        }
        event.getPayload().put("message", message);
        return event;
    }

    private MessageEventDTO buildBaseEvent(
            String eventCategory,
            String eventType,
            String sourceType,
            Long tenantId,
            Long userId,
            MessageNoticeDTO notice,
            Long version
    ) {
        MessageEventDTO event = new MessageEventDTO();
        event.setEventCategory(eventCategory);
        event.setSourceType(sourceType);
        event.setEventType(eventType);
        event.setTenantId(tenantId);
        event.setUserId(userId);
        event.setNotice(notice);
        event.setVersion(version);
        event.setTraceId(TraceContext.getTraceId());
        event.setRequestId(TraceContext.getRequestId());
        event.setTimestamp(LocalDateTime.now());
        event.setPayload(new LinkedHashMap<>());
        event.setEventKey(buildEventKey(event));
        return event;
    }

    public String buildEventKey(MessageEventDTO event) {
        Long tenantId = event == null ? null : event.getTenantId();
        Long userId = event == null ? null : event.getUserId();
        Long version = event == null ? null : event.getVersion();
        String eventType = event == null ? "UNKNOWN" : event.getEventType();
        MessageNoticeDTO notice = event == null ? null : event.getNotice();
        String noticePart = notice == null || notice.getId() == null ? "none" : String.valueOf(notice.getId());
        String userPart = userId == null ? "tenant" : String.valueOf(userId);
        String versionPart = version == null ? "none" : String.valueOf(version);
        return eventType + ":" + (tenantId == null ? "unknown" : tenantId) + ":" + noticePart + ":" + userPart + ":" + versionPart;
    }
}
