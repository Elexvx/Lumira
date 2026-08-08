package com.lumira.message.service;

import com.lumira.api.message.MessageEventDTO;
import com.lumira.message.app.PlatformEventOutboxEntity;
import com.lumira.message.app.PlatformEventOutboxService;
import com.lumira.message.vo.MessageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MessagePushService {

    private static final Logger logger = LoggerFactory.getLogger(MessagePushService.class);

    private final MessageEventFactory messageEventFactory;
    private final MessageEventDeliveryService messageEventDeliveryService;
    private final PlatformEventOutboxService platformEventOutboxService;

    public MessagePushService(
            MessageEventFactory messageEventFactory,
            MessageEventDeliveryService messageEventDeliveryService,
            PlatformEventOutboxService platformEventOutboxService
    ) {
        this.messageEventFactory = messageEventFactory;
        this.messageEventDeliveryService = messageEventDeliveryService;
        this.platformEventOutboxService = platformEventOutboxService;
    }

    public void publishCreated(MessageVO.NoticeVO notice) {
        MessageEventDTO event = messageEventFactory.createCreatedEvent(notice);
        recordThenDispatchAfterCommit(event);
    }

    public void publishRetracted(MessageVO.NoticeVO notice) {
        MessageEventDTO event = messageEventFactory.createRetractedEvent(notice);
        recordThenDispatchAfterCommit(event);
    }

    public void publishRead(Long userId, String userUuid, MessageVO.NoticeVO notice, Integer unreadCount) {
        MessageEventDTO event = messageEventFactory.createReadEvent(userId, userUuid, notice, unreadCount);
        recordThenDispatchAfterCommit(event);
    }

    public void publishUnreadCount(Long userId, String userUuid, Integer unreadCount) {
        MessageEventDTO event = messageEventFactory.createUnreadCountEvent(userId, userUuid, unreadCount);
        recordThenDispatchAfterCommit(event);
    }

    private void recordThenDispatchAfterCommit(MessageEventDTO event) {
        var outboxEvent = platformEventOutboxService.record(event);
        if (!TransactionSynchronizationManager.isSynchronizationActive() || !TransactionSynchronizationManager.isActualTransactionActive()) {
            dispatchImmediately(outboxEvent);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchImmediately(outboxEvent);
            }
        });
    }

    private void dispatchImmediately(PlatformEventOutboxEntity outboxEvent) {
        try {
            platformEventOutboxService.dispatchImmediately(outboxEvent, messageEventDeliveryService);
        } catch (RuntimeException exception) {
            logger.warn("消息即时投递失败，保留 outbox 供 relay 重试: {}", exception.getMessage(), exception);
        }
    }
}
