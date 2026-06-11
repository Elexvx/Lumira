package com.lumira.message.service;

import com.lumira.api.message.MessageEventDTO;
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
        dispatchAfterCommit(() -> {
            platformEventOutboxService.recordAfterCommit(event);
            messageEventDeliveryService.deliver(event);
        });
    }

    public void publishRetracted(MessageVO.NoticeVO notice) {
        MessageEventDTO event = messageEventFactory.createRetractedEvent(notice);
        dispatchAfterCommit(() -> {
            platformEventOutboxService.recordAfterCommit(event);
            messageEventDeliveryService.deliver(event);
        });
    }

    public void publishRead(Long tenantId, Long userId, MessageVO.NoticeVO notice, Integer unreadCount) {
        MessageEventDTO event = messageEventFactory.createReadEvent(tenantId, userId, notice, unreadCount);
        dispatchAfterCommit(() -> {
            platformEventOutboxService.recordAfterCommit(event);
            messageEventDeliveryService.deliver(event);
        });
    }

    public void publishUnreadCount(Long tenantId, Long userId, Integer unreadCount) {
        MessageEventDTO event = messageEventFactory.createUnreadCountEvent(tenantId, userId, unreadCount);
        dispatchAfterCommit(() -> {
            platformEventOutboxService.recordAfterCommit(event);
            messageEventDeliveryService.deliver(event);
        });
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
                    logger.warn("消息提交后发送失败: {}", exception.getMessage(), exception);
                }
            }
        });
    }
}
