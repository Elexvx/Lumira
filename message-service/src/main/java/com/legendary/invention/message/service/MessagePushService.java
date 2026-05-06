package com.legendary.invention.message.service;

import com.legendary.invention.message.vo.MessageVO;
import com.legendary.invention.message.app.PlatformEventOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MessagePushService {

    private static final Logger logger = LoggerFactory.getLogger(MessagePushService.class);
    private static final String SOURCE_MESSAGE = "MESSAGE";

    private final MessageWebSocketRegistry webSocketRegistry;
    private final PlatformEventOutboxService platformEventOutboxService;

    public MessagePushService(MessageWebSocketRegistry webSocketRegistry, PlatformEventOutboxService platformEventOutboxService) {
        this.webSocketRegistry = webSocketRegistry;
        this.platformEventOutboxService = platformEventOutboxService;
    }

    public void publishCreated(MessageVO.NoticeVO notice) {
        MessageVO.MessageEventVO event = buildEvent("NOTICE_CREATED", notice, null);
        dispatchAfterCommit(() -> {
            platformEventOutboxService.recordAfterCommit(
                    SOURCE_MESSAGE,
                    event.getEventType(),
                    event.getTenantId(),
                    event.getUserId(),
                    buildEventKey(event),
                    event
            );
            dispatch(notice, event);
        });
    }

    public void publishRetracted(MessageVO.NoticeVO notice) {
        MessageVO.MessageEventVO event = buildEvent("NOTICE_RETRACTED", notice, null);
        dispatchAfterCommit(() -> {
            platformEventOutboxService.recordAfterCommit(
                    SOURCE_MESSAGE,
                    event.getEventType(),
                    event.getTenantId(),
                    event.getUserId(),
                    buildEventKey(event),
                    event
            );
            dispatch(notice, event);
        });
    }

    public void publishRead(Long tenantId, Long userId, MessageVO.NoticeVO notice, Integer unreadCount) {
        MessageVO.MessageEventVO event = buildEvent("NOTICE_READ", notice, unreadCount);
        event.setUserId(userId);
        dispatchAfterCommit(() -> {
            platformEventOutboxService.recordAfterCommit(
                    SOURCE_MESSAGE,
                    event.getEventType(),
                    tenantId,
                    userId,
                    buildEventKey(event),
                    event
            );
            webSocketRegistry.sendToUser(tenantId, userId, event);
        });
    }

    public void publishUnreadCount(Long tenantId, Long userId, Integer unreadCount) {
        MessageVO.MessageEventVO event = new MessageVO.MessageEventVO();
        event.setEventType("UNREAD_COUNT");
        event.setTenantId(tenantId);
        event.setUserId(userId);
        event.setUnreadCount(unreadCount);
        event.setMessage("未读消息数量已更新");
        dispatchAfterCommit(() -> {
            platformEventOutboxService.recordAfterCommit(
                    SOURCE_MESSAGE,
                    event.getEventType(),
                    tenantId,
                    userId,
                    buildEventKey(event),
                    event
            );
            webSocketRegistry.sendToUser(tenantId, userId, event);
        });
    }

    private void dispatch(MessageVO.NoticeVO notice, MessageVO.MessageEventVO event) {
        if ("USER".equalsIgnoreCase(notice.getTargetScope()) && notice.getTargetUserId() != null) {
            webSocketRegistry.sendToUser(notice.getTenantId(), notice.getTargetUserId(), event);
            return;
        }
        webSocketRegistry.sendToTenant(notice.getTenantId(), event);
    }

    private void dispatchAfterCommit(Runnable dispatchAction) {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || !TransactionSynchronizationManager.isActualTransactionActive()) {
            dispatchAction.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dispatchAction.run();
                } catch (RuntimeException exception) {
                    logger.warn("消息WebSocket提交后发送失败: {}", exception.getMessage(), exception);
                }
            }
        });
    }

    private MessageVO.MessageEventVO buildEvent(String eventType, MessageVO.NoticeVO notice, Integer unreadCount) {
        MessageVO.MessageEventVO event = new MessageVO.MessageEventVO();
        event.setEventType(eventType);
        event.setTenantId(notice.getTenantId());
        event.setUserId(notice.getTargetUserId());
        event.setUnreadCount(unreadCount);
        event.setNotice(notice);
        event.setMessage("NOTICE_CREATED".equals(eventType) ? "消息已发布" : "消息状态已更新");
        event.setTimestamp(java.time.LocalDateTime.now());
        return event;
    }

    private String buildEventKey(MessageVO.MessageEventVO event) {
        Long tenantId = event.getTenantId();
        Long userId = event.getUserId();
        MessageVO.NoticeVO notice = event.getNotice();
        Long noticeId = notice == null ? null : notice.getId();
        String noticePart = noticeId == null ? "none" : String.valueOf(noticeId);
        String userPart = userId == null ? "tenant" : String.valueOf(userId);
        String unreadPart = event.getUnreadCount() == null ? "none" : String.valueOf(event.getUnreadCount());
        return event.getEventType() + ":" + (tenantId == null ? "unknown" : tenantId) + ":" + noticePart + ":" + userPart + ":" + unreadPart;
    }
}
