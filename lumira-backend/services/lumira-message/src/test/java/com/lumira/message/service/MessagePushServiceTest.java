package com.lumira.message.service;

import com.lumira.api.message.MessageEventDTO;
import com.lumira.message.app.PlatformEventOutboxEntity;
import com.lumira.message.app.PlatformEventOutboxService;
import com.lumira.message.vo.MessageVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagePushServiceTest {

    @Mock
    private MessageEventFactory messageEventFactory;

    @Mock
    private MessageEventDeliveryService messageEventDeliveryService;

    @Mock
    private PlatformEventOutboxService platformEventOutboxService;

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void publishCreatedShouldPersistOutboxInActiveTransactionAndDeliverOnlyAfterCommit() {
        MessageEventDTO event = event();
        PlatformEventOutboxEntity outboxEvent = outboxEvent();
        MessagePushService pushService = service();
        when(messageEventFactory.createCreatedEvent(any())).thenReturn(event);
        when(platformEventOutboxService.record(event)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return outboxEvent;
        });
        beginTransaction();

        pushService.publishCreated(notice());

        verify(platformEventOutboxService).record(event);
        verify(platformEventOutboxService, never()).dispatchImmediately(any(), any());
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        completeCommit(synchronizations);

        verify(platformEventOutboxService).dispatchImmediately(same(outboxEvent), same(messageEventDeliveryService));
    }

    @Test
    void publishCreatedShouldNotDeliverWhenOwningTransactionRollsBack() {
        MessageEventDTO event = event();
        PlatformEventOutboxEntity outboxEvent = outboxEvent();
        MessagePushService pushService = service();
        when(messageEventFactory.createCreatedEvent(any())).thenReturn(event);
        when(platformEventOutboxService.record(event)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return outboxEvent;
        });
        beginTransaction();

        pushService.publishCreated(notice());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(platformEventOutboxService).record(event);
        verify(platformEventOutboxService, never()).dispatchImmediately(any(), any());
    }

    @Test
    void publishCreatedWithoutTransactionShouldPersistBeforeImmediateDispatch() {
        MessageEventDTO event = event();
        PlatformEventOutboxEntity outboxEvent = outboxEvent();
        MessagePushService pushService = service();
        when(messageEventFactory.createCreatedEvent(any())).thenReturn(event);
        when(platformEventOutboxService.record(event)).thenReturn(outboxEvent);

        pushService.publishCreated(notice());

        InOrder inOrder = inOrder(platformEventOutboxService);
        inOrder.verify(platformEventOutboxService).record(event);
        inOrder.verify(platformEventOutboxService).dispatchImmediately(same(outboxEvent), same(messageEventDeliveryService));
    }

    @Test
    void publishCreatedShouldKeepDurableOutboxWhenImmediateDispatchFails() {
        MessageEventDTO event = event();
        PlatformEventOutboxEntity outboxEvent = outboxEvent();
        MessagePushService pushService = service();
        when(messageEventFactory.createCreatedEvent(any())).thenReturn(event);
        when(platformEventOutboxService.record(event)).thenReturn(outboxEvent);
        doThrow(new RuntimeException("websocket unavailable"))
                .when(platformEventOutboxService)
                .dispatchImmediately(same(outboxEvent), same(messageEventDeliveryService));

        assertThatCode(() -> pushService.publishCreated(notice())).doesNotThrowAnyException();

        verify(platformEventOutboxService).record(event);
        verify(platformEventOutboxService).dispatchImmediately(same(outboxEvent), same(messageEventDeliveryService));
    }

    private MessagePushService service() {
        return new MessagePushService(messageEventFactory, messageEventDeliveryService, platformEventOutboxService);
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private void completeCommit(List<TransactionSynchronization> synchronizations) {
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private MessageEventDTO event() {
        MessageEventDTO event = new MessageEventDTO();
        event.setEventType("NOTICE_CREATED");
        return event;
    }

    private PlatformEventOutboxEntity outboxEvent() {
        PlatformEventOutboxEntity event = new PlatformEventOutboxEntity();
        event.setId(9001L);
        event.setDispatchStatus(PlatformEventOutboxService.STATUS_RECORDED);
        return event;
    }

    private MessageVO.NoticeVO notice() {
        return new MessageVO.NoticeVO();
    }
}
