package com.legendary.invention.saas.modules.message.service;

import com.legendary.invention.saas.modules.message.vo.MessageVO;
import org.springframework.stereotype.Service;

@Service
public class MessagePushService {

    private final MessageWebSocketRegistry webSocketRegistry;

    public MessagePushService(MessageWebSocketRegistry webSocketRegistry) {
        this.webSocketRegistry = webSocketRegistry;
    }

    public void publishCreated(MessageVO.NoticeVO notice) {
        MessageVO.MessageEventVO event = buildEvent("NOTICE_CREATED", notice, null);
        dispatch(notice, event);
    }

    public void publishRetracted(MessageVO.NoticeVO notice) {
        MessageVO.MessageEventVO event = buildEvent("NOTICE_RETRACTED", notice, null);
        dispatch(notice, event);
    }

    public void publishRead(Long tenantId, Long userId, MessageVO.NoticeVO notice, Integer unreadCount) {
        MessageVO.MessageEventVO event = buildEvent("NOTICE_READ", notice, unreadCount);
        event.setUserId(userId);
        webSocketRegistry.sendToUser(tenantId, userId, event);
    }

    public void publishUnreadCount(Long tenantId, Long userId, Integer unreadCount) {
        MessageVO.MessageEventVO event = new MessageVO.MessageEventVO();
        event.setEventType("UNREAD_COUNT");
        event.setTenantId(tenantId);
        event.setUserId(userId);
        event.setUnreadCount(unreadCount);
        event.setMessage("未读消息数量已更新");
        webSocketRegistry.sendToUser(tenantId, userId, event);
    }

    private void dispatch(MessageVO.NoticeVO notice, MessageVO.MessageEventVO event) {
        if ("USER".equalsIgnoreCase(notice.getTargetScope()) && notice.getTargetUserId() != null) {
            webSocketRegistry.sendToUser(notice.getTenantId(), notice.getTargetUserId(), event);
            return;
        }
        webSocketRegistry.sendToTenant(notice.getTenantId(), event);
    }

    private MessageVO.MessageEventVO buildEvent(String eventType, MessageVO.NoticeVO notice, Integer unreadCount) {
        MessageVO.MessageEventVO event = new MessageVO.MessageEventVO();
        event.setEventType(eventType);
        event.setTenantId(notice.getTenantId());
        event.setUserId(notice.getTargetUserId());
        event.setUnreadCount(unreadCount);
        event.setNotice(notice);
        event.setMessage("NOTICE_CREATED".equals(eventType) ? "消息已发布" : "消息状态已更新");
        return event;
    }
}
