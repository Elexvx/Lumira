package com.lumira.message.controller;

import com.lumira.common.exception.BizException;
import com.lumira.message.app.PlatformEventOutboxService;
import com.lumira.message.config.MessageProperties;
import com.lumira.message.service.MessageEventDeliveryService;
import com.lumira.message.service.MessageWebSocketRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalJobControllerTest {

    @Test
    void replayOutboxShouldRejectInvalidIdBeforeOutboxCall() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        InternalJobController controller = new InternalJobController(
                mock(MessageWebSocketRegistry.class),
                outboxService,
                mock(MessageEventDeliveryService.class),
                new MessageProperties(),
                "message-secret"
        );

        assertThatThrownBy(() -> controller.replayOutbox(0L, "message-secret"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(outboxService);
    }

    @Test
    void relayOutboxShouldRejectOversizedTokenBeforeOutboxCall() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        InternalJobController controller = new InternalJobController(
                mock(MessageWebSocketRegistry.class),
                outboxService,
                mock(MessageEventDeliveryService.class),
                new MessageProperties(),
                "message-secret"
        );

        assertThatThrownBy(() -> controller.relayOutbox("a".repeat(513)))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(outboxService);
    }

    @Test
    void relayOutboxShouldRejectWhenScopedMessageTokenMissing() {
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        InternalJobController controller = new InternalJobController(
                mock(MessageWebSocketRegistry.class),
                outboxService,
                mock(MessageEventDeliveryService.class),
                new MessageProperties(),
                ""
        );

        assertThatThrownBy(() -> controller.relayOutbox("global-secret"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(outboxService);
    }
}
