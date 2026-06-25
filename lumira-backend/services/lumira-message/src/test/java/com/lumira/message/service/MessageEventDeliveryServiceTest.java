package com.lumira.message.service;

import com.lumira.api.message.MessageEventDTO;
import com.lumira.api.message.MessageNoticeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageEventDeliveryServiceTest {

    @Mock
    private MessageWebSocketRegistry registry;

    @Mock
    private MessageRecipientResolver recipientResolver;

    private MessageEventDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new MessageEventDeliveryService(registry, recipientResolver);
    }

    @Test
    void deliverShouldRoutePlatformBroadcastToAllSubscribers() {
        MessageEventDTO event = buildEvent("PLATFORM", null, null);

        deliveryService.deliver(event);

        verify(registry).sendToAll(event);
    }

    @Test
    void deliverShouldRouteUserMessageToTheTargetUser() {
        MessageEventDTO event = buildEvent("USER", 2001L, null);

        deliveryService.deliver(event);

        verify(registry).sendToUser(2001L, event);
    }

    @Test
    void deliverShouldRouteRoleMessageToResolvedUsers() {
        MessageEventDTO event = buildEvent("ROLE", null, 3001L);
        when(recipientResolver.resolveRecipientUserIds(event.getNotice())).thenReturn(List.of(2001L, 2002L));

        deliveryService.deliver(event);

        verify(registry).sendToUser(eq(2001L), eq(event));
        verify(registry).sendToUser(eq(2002L), eq(event));
    }

    private MessageEventDTO buildEvent(String targetScope, Long targetUserId, Long targetRoleId) {
        MessageNoticeDTO notice = new MessageNoticeDTO();
        notice.setTargetScope(targetScope);
        notice.setTargetUserId(targetUserId);
        notice.setTargetRoleId(targetRoleId);

        MessageEventDTO event = new MessageEventDTO();
        event.setUserId(targetUserId);
        event.setNotice(notice);
        event.setEventType("NOTICE_CREATED");
        return event;
    }
}
