package com.lumira.message.service;

import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageWebSocketHandlerTest {

    @Test
    void afterConnectionEstablished_shouldCloseBlankUsernameSessionBeforeRegistration() throws Exception {
        MessageWebSocketRegistry webSocketRegistry = mock(MessageWebSocketRegistry.class);
        MessageConnectionSnapshotService connectionSnapshotService = mock(MessageConnectionSnapshotService.class);
        MessageWebSocketHandler handler = new MessageWebSocketHandler(webSocketRegistry, connectionSnapshotService);
        WebSocketSession session = mock(WebSocketSession.class);
        CurrentUser currentUser = new CurrentUser(1001L, " ", "session-1", 3, true, Set.of("message:message:view"));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(MessageSessionHandshakeInterceptor.CURRENT_USER_ATTR, currentUser);
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        verify(session).close();
        verify(webSocketRegistry, never()).register(session, currentUser);
        verify(connectionSnapshotService, never()).emitSnapshot(currentUser);
    }

    @Test
    void afterConnectionEstablished_shouldCloseMissingSessionVersionBeforeRegistration() throws Exception {
        MessageWebSocketRegistry webSocketRegistry = mock(MessageWebSocketRegistry.class);
        MessageConnectionSnapshotService connectionSnapshotService = mock(MessageConnectionSnapshotService.class);
        MessageWebSocketHandler handler = new MessageWebSocketHandler(webSocketRegistry, connectionSnapshotService);
        WebSocketSession session = mock(WebSocketSession.class);
        CurrentUser currentUser = new CurrentUser(1001L, "alice", "session-1", null, true, Set.of("message:message:view"));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(MessageSessionHandshakeInterceptor.CURRENT_USER_ATTR, currentUser);
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        verify(session).close();
        verify(webSocketRegistry, never()).register(session, currentUser);
        verify(connectionSnapshotService, never()).emitSnapshot(currentUser);
    }

    @Test
    void afterConnectionEstablished_shouldCloseSessionWhenRegistryRejectsLiveSession() throws Exception {
        MessageWebSocketRegistry webSocketRegistry = mock(MessageWebSocketRegistry.class);
        MessageConnectionSnapshotService connectionSnapshotService = mock(MessageConnectionSnapshotService.class);
        MessageWebSocketHandler handler = new MessageWebSocketHandler(webSocketRegistry, connectionSnapshotService);
        WebSocketSession session = mock(WebSocketSession.class);
        CurrentUser currentUser = new CurrentUser(1001L, "alice", "session-1", 3, true, Set.of("message:message:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(MessageSessionHandshakeInterceptor.CURRENT_USER_ATTR, currentUser);
        when(session.getAttributes()).thenReturn(attributes);
        when(webSocketRegistry.register(session, currentUser)).thenReturn(null);

        handler.afterConnectionEstablished(session);

        verify(session).close();
        verify(connectionSnapshotService, never()).emitSnapshot(currentUser);
    }
}
