package com.legendary.invention.message.service;

import com.legendary.invention.common.security.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;

@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final MessageWebSocketRegistry webSocketRegistry;
    private final MessageConnectionSnapshotService connectionSnapshotService;

    public MessageWebSocketHandler(
            MessageWebSocketRegistry webSocketRegistry,
            MessageConnectionSnapshotService connectionSnapshotService
    ) {
        this.webSocketRegistry = webSocketRegistry;
        this.connectionSnapshotService = connectionSnapshotService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object currentUserAttr = session.getAttributes().get(MessageSessionHandshakeInterceptor.CURRENT_USER_ATTR);
        if (!(currentUserAttr instanceof CurrentUser currentUser)) {
            session.close();
            return;
        }
        if (currentUser.getCurrentTenantId() == null || currentUser.getUserId() == null) {
            session.close();
            return;
        }
        webSocketRegistry.register(session, currentUser.getCurrentTenantId(), currentUser.getUserId());
        connectionSnapshotService.emitSnapshot(currentUser);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload() == null ? "" : message.getPayload().trim();
        if ("ping".equalsIgnoreCase(payload)) {
            session.sendMessage(new TextMessage("{\"eventType\":\"PONG\",\"timestamp\":\"" + Instant.now() + "\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        webSocketRegistry.unregister(session);
    }
}
