package com.lumira.saas.modules.system.online;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlineSessionEventSubscriberTest {

    @Test
    void onMessageShouldIgnoreOversizedPayloadBeforeDispatch() {
        OnlineSessionStreamService streamService = mock(OnlineSessionStreamService.class);
        Message message = message("x".repeat(5000));

        new OnlineSessionEventSubscriber(new ObjectMapper(), streamService, objectProvider(null)).onMessage(message, null);

        verify(streamService, never()).dispatch(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onMessageShouldIgnoreUntrustedEventBeforeDispatch() throws Exception {
        OnlineSessionStreamService streamService = mock(OnlineSessionStreamService.class);
        OnlineSessionEvent event = trustedEvent();
        event.setSessionId("../session");

        new OnlineSessionEventSubscriber(new ObjectMapper(), streamService, objectProvider(null))
                .onMessage(message(new ObjectMapper().writeValueAsString(event)), null);

        verify(streamService, never()).dispatch(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onMessageShouldIgnoreIdentityEventWithoutUserUuidBeforeDispatch() throws Exception {
        OnlineSessionStreamService streamService = mock(OnlineSessionStreamService.class);
        OnlineSessionEvent event = trustedEvent();
        event.setUserUuid(null);

        new OnlineSessionEventSubscriber(new ObjectMapper(), streamService, objectProvider(null))
                .onMessage(message(new ObjectMapper().writeValueAsString(event)), null);

        verify(streamService, never()).dispatch(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onMessageShouldDispatchTrustedEvent() throws Exception {
        OnlineSessionStreamService streamService = mock(OnlineSessionStreamService.class);
        OnlineSessionEventIdentityVerifier identityVerifier = mock(OnlineSessionEventIdentityVerifier.class);
        when(identityVerifier.hasTrustedIdentity(org.mockito.ArgumentMatchers.any(OnlineSessionEvent.class))).thenReturn(true);

        new OnlineSessionEventSubscriber(new ObjectMapper(), streamService, objectProvider(identityVerifier))
                .onMessage(message(new ObjectMapper().writeValueAsString(trustedEvent())), null);

        verify(streamService).dispatch(org.mockito.ArgumentMatchers.any(OnlineSessionEvent.class));
    }

    @Test
    void onMessageShouldRejectEventWhenUserUuidDoesNotMatchCurrentUserRow() throws Exception {
        OnlineSessionStreamService streamService = mock(OnlineSessionStreamService.class);
        OnlineSessionEventIdentityVerifier identityVerifier = mock(OnlineSessionEventIdentityVerifier.class);
        when(identityVerifier.hasTrustedIdentity(org.mockito.ArgumentMatchers.any(OnlineSessionEvent.class))).thenReturn(false);

        new OnlineSessionEventSubscriber(new ObjectMapper(), streamService, objectProvider(identityVerifier))
                .onMessage(message(new ObjectMapper().writeValueAsString(trustedEvent())), null);

        verify(streamService, never()).dispatch(org.mockito.ArgumentMatchers.any(OnlineSessionEvent.class));
    }

    private Message message(String payload) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private OnlineSessionEvent trustedEvent() {
        OnlineSessionEvent event = new OnlineSessionEvent();
        event.setAction(OnlineSessionEvent.ACTION_UPSERT);
        event.setUserId(1001L);
        event.setUserUuid("user-uuid-1");
        event.setSessionId("session-1");
        event.setOperatorUsername("alice");
        return event;
    }

    private static <T> ObjectProvider<T> objectProvider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public Iterator<T> iterator() {
                return value == null ? List.<T>of().iterator() : List.of(value).iterator();
            }

            @Override
            public Stream<T> stream() {
                return value == null ? Stream.empty() : Stream.of(value);
            }

            @Override
            public Stream<T> orderedStream() {
                return stream();
            }
        };
    }
}
