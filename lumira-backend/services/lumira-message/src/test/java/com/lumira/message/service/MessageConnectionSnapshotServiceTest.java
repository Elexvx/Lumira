package com.lumira.message.service;

import com.lumira.common.security.CurrentUser;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.app.PlatformEventOutboxService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MessageConnectionSnapshotServiceTest {

    @Test
    void emitSnapshot_shouldRejectBlankUsernameBeforeUnreadCountAndDelivery() {
        MessageEventFactory messageEventFactory = mock(MessageEventFactory.class);
        MessageEventDeliveryService messageEventDeliveryService = mock(MessageEventDeliveryService.class);
        MessageAppService messageAppService = mock(MessageAppService.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        MessageConnectionSnapshotService service = new MessageConnectionSnapshotService(
                messageEventFactory,
                messageEventDeliveryService,
                messageAppService,
                outboxService
        );
        CurrentUser currentUser = new CurrentUser(1001L, " ", 1001L, "session-1", 3, true, Set.of("message:message:view"));

        service.emitSnapshot(currentUser);

        verifyNoInteractions(messageAppService, outboxService, messageEventFactory, messageEventDeliveryService);
    }

    @Test
    void emitSnapshot_shouldRejectMissingSessionVersionBeforeUnreadCountAndDelivery() {
        MessageEventFactory messageEventFactory = mock(MessageEventFactory.class);
        MessageEventDeliveryService messageEventDeliveryService = mock(MessageEventDeliveryService.class);
        MessageAppService messageAppService = mock(MessageAppService.class);
        PlatformEventOutboxService outboxService = mock(PlatformEventOutboxService.class);
        MessageConnectionSnapshotService service = new MessageConnectionSnapshotService(
                messageEventFactory,
                messageEventDeliveryService,
                messageAppService,
                outboxService
        );
        CurrentUser currentUser = new CurrentUser(1001L, "alice", 1001L, "session-1", null, true, Set.of("message:message:view"));

        service.emitSnapshot(currentUser);

        verifyNoInteractions(messageAppService, outboxService, messageEventFactory, messageEventDeliveryService);
    }
}
