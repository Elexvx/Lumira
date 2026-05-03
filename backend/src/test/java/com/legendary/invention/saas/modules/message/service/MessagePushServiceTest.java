package com.legendary.invention.saas.modules.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.saas.infrastructure.event.PlatformEventOutboxService;
import com.legendary.invention.saas.modules.message.vo.MessageVO;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagePushServiceTest {

    @Test
    void publishCreatedShouldDispatchImmediatelyWithoutTransaction() {
        RecordingMessageWebSocketRegistry registry = new RecordingMessageWebSocketRegistry();
        RecordingPlatformEventOutboxService outboxService = new RecordingPlatformEventOutboxService();
        MessagePushService service = new MessagePushService(registry, outboxService);

        service.publishCreated(buildNotice("TENANT", null, null));

        assertEquals(List.of("tenant:1001:NOTICE_CREATED"), registry.dispatches);
        assertEquals(List.of("MESSAGE:NOTICE_CREATED"), outboxService.recordedEventTypes);
    }

    @Test
    void publishCreatedShouldDispatchAfterCommitWhenTransactionCompletes() {
        RecordingMessageWebSocketRegistry registry = new RecordingMessageWebSocketRegistry();
        RecordingPlatformEventOutboxService outboxService = new RecordingPlatformEventOutboxService();
        MessagePushService service = new MessagePushService(registry, outboxService);
        TransactionTemplate transactionTemplate = new TransactionTemplate(new TestTransactionManager());

        transactionTemplate.executeWithoutResult(status -> {
            service.publishCreated(buildNotice("TENANT", null, null));
            assertTrue(registry.dispatches.isEmpty());
            assertTrue(outboxService.recordedEventTypes.isEmpty());
        });

        assertEquals(List.of("tenant:1001:NOTICE_CREATED"), registry.dispatches);
        assertEquals(List.of("MESSAGE:NOTICE_CREATED"), outboxService.recordedEventTypes);
    }

    @Test
    void publishUnreadCountShouldNotDispatchWhenTransactionRollsBack() {
        RecordingMessageWebSocketRegistry registry = new RecordingMessageWebSocketRegistry();
        RecordingPlatformEventOutboxService outboxService = new RecordingPlatformEventOutboxService();
        MessagePushService service = new MessagePushService(registry, outboxService);
        TransactionTemplate transactionTemplate = new TransactionTemplate(new TestTransactionManager());

        try {
            transactionTemplate.executeWithoutResult(status -> {
                service.publishUnreadCount(1001L, 2001L, 7);
                assertTrue(registry.dispatches.isEmpty());
                assertTrue(outboxService.recordedEventTypes.isEmpty());
                throw new IllegalStateException("rollback");
            });
        } catch (IllegalStateException expected) {
            assertEquals("rollback", expected.getMessage());
        }

        assertTrue(registry.dispatches.isEmpty());
        assertTrue(outboxService.recordedEventTypes.isEmpty());
    }

    @Test
    void publishReadShouldDispatchAfterCommitToTargetUser() {
        RecordingMessageWebSocketRegistry registry = new RecordingMessageWebSocketRegistry();
        RecordingPlatformEventOutboxService outboxService = new RecordingPlatformEventOutboxService();
        MessagePushService service = new MessagePushService(registry, outboxService);
        TransactionTemplate transactionTemplate = new TransactionTemplate(new TestTransactionManager());

        transactionTemplate.executeWithoutResult(status -> {
            service.publishRead(1001L, 2001L, buildNotice("USER", 2001L, null), 4);
            assertTrue(registry.dispatches.isEmpty());
            assertTrue(outboxService.recordedEventTypes.isEmpty());
        });

        assertEquals(List.of("user:1001:2001:NOTICE_READ:4"), registry.dispatches);
        assertEquals(List.of("MESSAGE:NOTICE_READ"), outboxService.recordedEventTypes);
    }

    private MessageVO.NoticeVO buildNotice(String targetScope, Long targetUserId, Long targetRoleId) {
        MessageVO.NoticeVO notice = new MessageVO.NoticeVO();
        notice.setTenantId(1001L);
        notice.setTargetScope(targetScope);
        notice.setTargetUserId(targetUserId);
        notice.setTargetRoleId(targetRoleId);
        notice.setTitle("测试站内信");
        notice.setContent("测试内容");
        notice.setMessageType("MESSAGE");
        notice.setSourceType("MANUAL");
        notice.setPublishStatus("PUBLISHED");
        return notice;
    }

    private static final class RecordingMessageWebSocketRegistry extends MessageWebSocketRegistry {
        private final List<String> dispatches = new CopyOnWriteArrayList<>();

        private RecordingMessageWebSocketRegistry() {
            super(new ObjectMapper());
        }

        @Override
        public void sendToTenant(Long tenantId, MessageVO.MessageEventVO event) {
            dispatches.add("tenant:" + tenantId + ":" + event.getEventType());
        }

        @Override
        public void sendToUser(Long tenantId, Long userId, MessageVO.MessageEventVO event) {
            dispatches.add("user:" + tenantId + ":" + userId + ":" + event.getEventType() + ":" + event.getUnreadCount());
        }
    }

    private static final class RecordingPlatformEventOutboxService extends PlatformEventOutboxService {
        private final List<String> recordedEventTypes = new CopyOnWriteArrayList<>();

        private RecordingPlatformEventOutboxService() {
            super(new ObjectMapper(), null);
        }

        @Override
        public void recordAfterCommit(
                String sourceType,
                String eventType,
                Long tenantId,
                Long userId,
                String eventKey,
                Object payload
        ) {
            recordedEventTypes.add(sourceType + ":" + eventType);
        }
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        private final TestTransaction transaction = new TestTransaction();

        private TestTransactionManager() {
            setTransactionSynchronization(SYNCHRONIZATION_ALWAYS);
        }

        @Override
        protected Object doGetTransaction() {
            return transaction;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TestTransaction) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            ((TestTransaction) transaction).active = true;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            ((TestTransaction) status.getTransaction()).active = false;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            ((TestTransaction) status.getTransaction()).active = false;
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            ((TestTransaction) transaction).active = false;
        }
    }

    private static final class TestTransaction {
        private boolean active;
    }
}
