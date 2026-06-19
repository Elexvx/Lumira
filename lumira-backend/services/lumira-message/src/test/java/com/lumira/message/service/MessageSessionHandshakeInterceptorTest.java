package com.lumira.message.service;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.message.infrastructure.security.MessageSessionAuthenticationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageSessionHandshakeInterceptorTest {

    @Mock
    private MessageSessionAuthenticationService sessionAuthenticationService;

    @Mock
    private MessageWebSocketTicketService ticketService;

    @Test
    void beforeHandshake_shouldRejectBearerAccessTokenWithoutTicket() {
        MessageSessionHandshakeInterceptor interceptor = new MessageSessionHandshakeInterceptor(sessionAuthenticationService, ticketService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(attributes).isEmpty();
    }

    @Test
    void beforeHandshake_shouldAcceptOneTimeTicket() {
        MessageSessionHandshakeInterceptor interceptor = new MessageSessionHandshakeInterceptor(sessionAuthenticationService, ticketService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("ticket", "ticket-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();
        CurrentUser currentUser = new CurrentUser(1001L, "alice", 1001L, "session-1", 3, true, Set.of("message:message:view"));
        CurrentUserDTO snapshot = snapshot();
        when(ticketService.consume("ticket-1")).thenReturn(new MessageWebSocketTicketService.TicketPayload("session-1", 1001L, 3));
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, 3))
                .thenReturn(new MessageSessionAuthenticationService.AuthenticatedAccess(currentUser, snapshot));

        boolean accepted = interceptor.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), mock(WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(MessageSessionHandshakeInterceptor.CURRENT_USER_ATTR)).isEqualTo(currentUser);
        assertThat(attributes.get("message.session")).isEqualTo(snapshot);
    }

    private CurrentUserDTO snapshot() {
        return new CurrentUserDTO(
                1001L,
                "alice",
                "Alice",
                "Alice",
                null,
                "13800000000",
                "alice@example.com",
                null,
                null,
                null,
                null,
                null,
                "zh-CN",
                "session-1",
                "v1",
                3,
                List.of("message:message:view"),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                false,
                "/dashboard/home"
        );
    }
}
