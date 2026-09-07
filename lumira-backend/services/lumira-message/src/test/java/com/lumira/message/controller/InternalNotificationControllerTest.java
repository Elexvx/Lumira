package com.lumira.message.controller;

import com.lumira.api.event.EventConsumptionPort;
import com.lumira.api.notification.NotificationCommand;
import com.lumira.common.exception.BizException;
import com.lumira.message.app.MessageAppService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalNotificationControllerTest {

    @Test
    void commitsTheOwnerNotificationThroughTheDurableConsumptionPort() {
        MessageAppService messageAppService = mock(MessageAppService.class);
        EventConsumptionPort consumptionPort = mock(EventConsumptionPort.class);
        doAnswer(invocation -> {
            Runnable sideEffect = invocation.getArgument(1);
            sideEffect.run();
            return true;
        }).when(consumptionPort).executeOnce(any(), any());
        InternalNotificationController controller = new InternalNotificationController(
                messageAppService,
                consumptionPort,
                "message-token"
        );

        var response = controller.publish(command(), "message-token");

        assertThat(response.getData()).isTrue();
        verify(consumptionPort).executeOnce(any(), any());
        verify(messageAppService).createSystemEventMessage(any());
    }

    @Test
    void returnsDuplicateWithoutRepeatingTheOwnerWrite() {
        MessageAppService messageAppService = mock(MessageAppService.class);
        EventConsumptionPort consumptionPort = mock(EventConsumptionPort.class);
        org.mockito.Mockito.when(consumptionPort.executeOnce(any(), any())).thenReturn(false);
        InternalNotificationController controller = new InternalNotificationController(
                messageAppService,
                consumptionPort,
                "message-token"
        );

        assertThat(controller.publish(command(), "message-token").getData()).isFalse();
        verify(messageAppService, never()).createSystemEventMessage(any());
    }

    @Test
    void rejectsAnInvalidInternalTokenBeforeTouchingTheOwner() {
        MessageAppService messageAppService = mock(MessageAppService.class);
        InternalNotificationController controller = new InternalNotificationController(
                messageAppService,
                mock(EventConsumptionPort.class),
                "message-token"
        );

        assertThatThrownBy(() -> controller.publish(command(), "wrong-token"))
                .isInstanceOf(BizException.class);
        verify(messageAppService, never()).createSystemEventMessage(any());
    }

    private NotificationCommand command() {
        return new NotificationCommand(
                "event-1",
                "PAYMENT_ORDER_PAID",
                "payment",
                "ORDER-1",
                1001L,
                "user-uuid-1001",
                "支付成功",
                "订单 ORDER-1 已支付成功。"
        );
    }
}
