package com.lumira.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.message.MessageEventDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.message.infrastructure.security.MessageSessionAuthenticationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageWebSocketRegistryTest {

    @Test
    void registryShouldNotExposeNumericOnlyUserOperations() {
        assertThat(Arrays.stream(MessageWebSocketRegistry.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(MessageWebSocketRegistry.class))
                .map(Method::toString)
                .filter(signature -> signature.contains("register(org.springframework.web.socket.WebSocketSession,java.lang.Long)")
                        || signature.contains("sendToUser(java.lang.Long,com.lumira.api.message.MessageEventDTO)"))
                .toList())
                .isEmpty();
    }

    @Test
    void registerShouldIgnoreNullSession() {
        MessageWebSocketRegistry registry = registry();

        registry.register(null, currentUser("user-uuid-1001"));

        assertThat(registry.snapshot().activeConnections()).isZero();
        assertThat(registry.snapshot().userCount()).isZero();
    }

    @Test
    void sendToUserShouldSkipWhenEventUserUuidDoesNotMatchSubscriber() throws Exception {
        MessageWebSocketRegistry registry = registry();
        WebSocketSession session = openSession("session-1");
        registry.register(session, currentUser("user-uuid-1001"));
        MessageEventDTO event = new MessageEventDTO();
        event.setUserId(1001L);
        event.setUserUuid("user-uuid-other");
        event.setEventType("UNREAD_COUNT");

        registry.sendToUser(1001L, "user-uuid-1001", event);

        verify(session, org.mockito.Mockito.times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToUserShouldDeliverWhenExplicitUserUuidMatchesSubscriber() throws Exception {
        MessageWebSocketRegistry registry = registry();
        WebSocketSession session = openSession("session-1");
        registry.register(session, currentUser("user-uuid-1001"));
        MessageEventDTO event = new MessageEventDTO();
        event.setUserId(1001L);
        event.setUserUuid("user-uuid-1001");
        event.setEventType("UNREAD_COUNT");

        registry.sendToUser(1001L, "user-uuid-1001", event);

        verify(session, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToUserShouldRequireExplicitUserUuidMatchWhenProvided() throws Exception {
        MessageWebSocketRegistry registry = registry();
        WebSocketSession session = openSession("session-1");
        registry.register(session, currentUser("user-uuid-1001"));
        MessageEventDTO event = new MessageEventDTO();
        event.setUserId(1001L);
        event.setEventType("NOTICE_CREATED");

        registry.sendToUser(1001L, "user-uuid-other", event);

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToUserShouldCloseStaleSubscriberWhenSessionTrustExpires() throws Exception {
        MessageSessionAuthenticationService authenticationService = mock(MessageSessionAuthenticationService.class);
        when(authenticationService.isTrustedSession("session-1", 1001L, "user-uuid-1001", 9L, 3, "permissions-1")).thenReturn(false);
        MessageWebSocketRegistry registry = registry(authenticationService, Duration.ZERO);
        WebSocketSession session = openSession("session-1");
        registry.register(session, currentUser("user-uuid-1001"));
        MessageEventDTO event = new MessageEventDTO();
        event.setUserId(1001L);
        event.setUserUuid("user-uuid-1001");
        event.setEventType("UNREAD_COUNT");

        registry.sendToUser(1001L, "user-uuid-1001", event);

        verify(authenticationService).isTrustedSession("session-1", 1001L, "user-uuid-1001", 9L, 3, "permissions-1");
        verify(session, times(1)).sendMessage(any(TextMessage.class));
        verify(session).close();
        assertThat(registry.snapshot().activeConnections()).isZero();
        assertThat(registry.snapshot().userCount()).isZero();
    }

    @Test
    void sendToUserShouldSkipImmediateRevalidationWithinTrustWindow() throws Exception {
        MessageSessionAuthenticationService authenticationService = mock(MessageSessionAuthenticationService.class);
        MessageWebSocketRegistry registry = registry(authenticationService, Duration.ofMinutes(5));
        WebSocketSession session = openSession("session-1");
        registry.register(session, currentUser("user-uuid-1001"));
        MessageEventDTO event = new MessageEventDTO();
        event.setUserId(1001L);
        event.setUserUuid("user-uuid-1001");
        event.setEventType("UNREAD_COUNT");

        registry.sendToUser(1001L, "user-uuid-1001", event);

        verify(authenticationService, never()).isTrustedSession(any(), any(), any(), any(), any(), any());
        verify(session, times(2)).sendMessage(any(TextMessage.class));
    }

    private WebSocketSession openSession(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        doReturn(sessionId).when(session).getId();
        doReturn(true).when(session).isOpen();
        return session;
    }

    private CurrentUser currentUser(String userUuid) {
        CurrentUser currentUser = new CurrentUser(1001L, "alice", 1001L, "session-1", 3, true, Set.of("message:message:view"));
        currentUser.setUserUuid(userUuid);
        currentUser.setSimulatedRoleId(9L);
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private MessageWebSocketRegistry registry() {
        return registry(mock(MessageSessionAuthenticationService.class), Duration.ofMinutes(5));
    }

    private MessageWebSocketRegistry registry(MessageSessionAuthenticationService authenticationService, Duration trustInterval) {
        return new MessageWebSocketRegistry(
                new ObjectMapper().findAndRegisterModules(),
                new MessageEventFactory(),
                new SimpleMeterRegistry(),
                authenticationService,
                Clock.systemUTC(),
                trustInterval
        );
    }
}
