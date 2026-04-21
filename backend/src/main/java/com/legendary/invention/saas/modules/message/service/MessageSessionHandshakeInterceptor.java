package com.legendary.invention.saas.modules.message.service;

import com.legendary.invention.saas.common.constant.HeaderConstants;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.security.service.SessionAuthenticationService;
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

    private final SessionAuthenticationService sessionAuthenticationService;

    public MessageSessionHandshakeInterceptor(SessionAuthenticationService sessionAuthenticationService) {
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String accessToken = resolveAccessToken(httpRequest);
        if (accessToken == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess = sessionAuthenticationService.authenticateAccessToken(accessToken);
            attributes.put(CURRENT_USER_ATTR, authenticatedAccess.currentUser());
            attributes.put("message.session", authenticatedAccess.session());
            return true;
        } catch (BizException exception) {
            response.setStatusCode(org.springframework.http.HttpStatus.valueOf(exception.getErrorCode().getHttpStatus()));
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HeaderConstants.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        String queryToken = request.getParameter("accessToken");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.trim();
        }
        return null;
    }
}
