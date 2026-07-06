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
import static org.mockito.Mockito.never;
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
        when(recipientResolver.resolveRecipients(event.getNotice()))
                .thenReturn(List.of(new MessageRecipientResolver.Recipient(2001L, "user-uuid-2001")));

        deliveryService.deliver(event);

        verify(registry).sendToUser(2001L, "user-uuid-2001", event);
    }

    @Test
    void deliverShouldDropUserMessageWhenResolverRejectsTargetIdentity() {
        MessageEventDTO event = buildEvent("USER", 2001L, null);
        when(recipientResolver.resolveRecipients(event.getNotice())).thenReturn(List.of());

        deliveryService.deliver(event);

        verify(registry, never()).sendToUser(eq(2001L), org.mockito.ArgumentMatchers.any(), eq(event));
        verify(registry, never()).sendToAll(event);
    }

    @Test
    void deliverShouldRouteRoleMessageToResolvedUsers() {
        MessageEventDTO event = buildEvent("ROLE", null, 3001L);
        when(recipientResolver.resolveRecipients(event.getNotice())).thenReturn(List.of(
                new MessageRecipientResolver.Recipient(2001L, "user-uuid-2001"),
                new MessageRecipientResolver.Recipient(2002L, "user-uuid-2002")
        ));

        deliveryService.deliver(event);

        verify(registry).sendToUser(eq(2001L), eq("user-uuid-2001"), eq(event));
        verify(registry).sendToUser(eq(2002L), eq("user-uuid-2002"), eq(event));
    }

    @Test
    void deliverShouldRouteDirectUserEventOnlyWhenUserUuidIsPresent() {
        MessageEventDTO event = new MessageEventDTO();
        event.setUserId(2001L);
        event.setUserUuid("user-uuid-2001");

        deliveryService.deliver(event);

        verify(registry).sendToUser(2001L, "user-uuid-2001", event);
    }

    @Test
    void deliverShouldDropDirectUserEventWhenUserUuidIsMissing() {
        MessageEventDTO event = new MessageEventDTO();
        event.setUserId(2001L);

        deliveryService.deliver(event);

        verify(registry, never()).sendToUser(eq(2001L), org.mockito.ArgumentMatchers.any(), eq(event));
        verify(registry, never()).sendToAll(event);
    }

    @Test
    void deliverShouldDropNoticeWhenTargetScopeIsUnknown() {
        MessageEventDTO event = buildEvent("UNKNOWN", null, null);

        deliveryService.deliver(event);

        verify(registry, never()).sendToAll(event);
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
