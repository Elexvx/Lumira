package com.lumira.message.service;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.message.infrastructure.security.MessageSessionAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class MessageSessionHandshakeInterceptor implements HandshakeInterceptor {

    public static final String CURRENT_USER_ATTR = "message.currentUser";

    private final MessageSessionAuthenticationService sessionAuthenticationService;
    private final MessageWebSocketTicketService ticketService;

    public MessageSessionHandshakeInterceptor(
            MessageSessionAuthenticationService sessionAuthenticationService,
            MessageWebSocketTicketService ticketService
    ) {
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.ticketService = ticketService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            MessageSessionAuthenticationService.AuthenticatedAccess authenticatedAccess = authenticate(servletRequest.getServletRequest());
            attributes.put(CURRENT_USER_ATTR, authenticatedAccess.currentUser());
            attributes.put("message.session", authenticatedAccess.snapshot());
            return true;
        } catch (BizException exception) {
            response.setStatusCode(org.springframework.http.HttpStatus.valueOf(exception.getErrorCode().getHttpStatus()));
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

    private MessageSessionAuthenticationService.AuthenticatedAccess authenticate(HttpServletRequest request) {
        String ticket = request.getParameter("ticket");
        if (ticket != null && !ticket.isBlank()) {
            MessageWebSocketTicketService.TicketPayload payload = ticketService.consume(ticket);
            if (payload != null) {
                return sessionAuthenticationService.authenticateSessionTicket(payload.sessionId(), payload.userId(), payload.sessionVersion());
            }
        }
        throw new BizException(ErrorCode.UNAUTHORIZED, "缺少WebSocket认证凭证");
    }
}
