package com.lumira.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.message.config.MessageProperties;
import com.lumira.message.infrastructure.security.MessageSessionAuthenticationService;
import com.lumira.message.infrastructure.redis.CacheTemplate;
import com.lumira.message.vo.MessageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageWebSocketTicketServiceTest {

    @Mock
    private CacheTemplate cacheTemplate;

    @Mock
    private MessageSessionAuthenticationService sessionAuthenticationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void issue_shouldPersistOneTimeTicketWithExpectedTtl() {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setWsTicketExpiresInSeconds(45L);
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, messageProperties, sessionAuthenticationService);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", 9L, 3, "permissions-1"))
                .thenReturn(authenticatedAccess(Set.of("message:message:view")));

        MessageVO.WebSocketTicketVO ticket = ticketService.issue(currentUser());

        assertThat(ticket.getTicket()).isNotBlank();
        assertThat(ticket.getExpiresInSeconds()).isEqualTo(45L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(cacheTemplate).put(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith("message:ws-ticket:");
        assertThat(valueCaptor.getValue()).contains("\"sessionId\":\"session-1\"");
        assertThat(valueCaptor.getValue()).contains("\"userUuid\":\"user-uuid-1001\"");
        assertThat(valueCaptor.getValue()).contains("\"simulatedRoleId\":9");
        assertThat(valueCaptor.getValue()).contains("\"permissionsVersion\":\"permissions-1\"");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(45L));
    }

    @Test
    void issue_shouldRejectUnauthenticatedUserBeforeWritingTicket() {
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, new MessageProperties(), sessionAuthenticationService);

        assertThatThrownBy(() -> ticketService.issue(unauthenticatedUser()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(sessionAuthenticationService);
    }

    @Test
    void issue_shouldRejectBlankUsernameBeforeWritingTicket() {
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, new MessageProperties(), sessionAuthenticationService);
        CurrentUser user = currentUser();
        user.setUsername(" ");

        assertThatThrownBy(() -> ticketService.issue(user))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(cacheTemplate, never()).put(any(), any(), any());
        verifyNoInteractions(sessionAuthenticationService);
    }

    @Test
    void issue_shouldRejectUnsafeSessionIdBeforeWritingTicket() {
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, new MessageProperties(), sessionAuthenticationService);
        CurrentUser user = currentUser();
        user.setSessionId("../session");

        assertThatThrownBy(() -> ticketService.issue(user))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(cacheTemplate, never()).put(any(), any(), any());
        verifyNoInteractions(sessionAuthenticationService);
    }

    @Test
    void issue_shouldRejectMissingLiveViewPermissionBeforeWritingTicket() {
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, new MessageProperties(), sessionAuthenticationService);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", 9L, 3, "permissions-1"))
                .thenReturn(authenticatedAccess(Set.of("system:config:view")));

        assertThatThrownBy(() -> ticketService.issue(currentUser()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(cacheTemplate, never()).put(any(), any(), any());
    }

    @Test
    void issue_shouldRejectExpiredTrustedSessionBeforeWritingTicket() {
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, new MessageProperties(), sessionAuthenticationService);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", 9L, 3, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.SESSION_EXPIRED, "expired"));

        assertThatThrownBy(() -> ticketService.issue(currentUser()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_EXPIRED));

        verify(cacheTemplate, never()).put(any(), any(), any());
    }

    @Test
    void issue_shouldRejectInvalidTtlBeforeWritingTicket() {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setWsTicketExpiresInSeconds(0L);
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, messageProperties, sessionAuthenticationService);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", 9L, 3, "permissions-1"))
                .thenReturn(authenticatedAccess(Set.of("message:message:view")));

        assertThatThrownBy(() -> ticketService.issue(currentUser()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SYSTEM_ERROR));

        verify(cacheTemplate, never()).put(any(), any(), any());
    }

    @Test
    void consume_shouldLoadPayloadAndRemoveTheTicketKey() throws Exception {
        MessageProperties messageProperties = new MessageProperties();
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, messageProperties, sessionAuthenticationService);
        String ticket = "0123456789abcdef0123456789abcdef";
        when(cacheTemplate.getAndRemove("message:ws-ticket:" + ticket)).thenReturn(
                objectMapper.writeValueAsString(new MessageWebSocketTicketService.TicketPayload("session-1", 1001L, "user-uuid-1001", 9L, 3, "permissions-1"))
        );

        MessageWebSocketTicketService.TicketPayload payload = ticketService.consume(" " + ticket + " ");

        assertThat(payload).isEqualTo(new MessageWebSocketTicketService.TicketPayload("session-1", 1001L, "user-uuid-1001", 9L, 3, "permissions-1"));
    }

    @Test
    void consume_shouldRejectInvalidTicketFormatBeforeCacheAccess() {
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, new MessageProperties(), sessionAuthenticationService);

        assertThat(ticketService.consume("../ticket")).isNull();

        verifyNoInteractions(cacheTemplate);
    }

    @Test
    void consume_shouldRejectInvalidPayloadAfterOneTimeRemoval() throws Exception {
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, new MessageProperties(), sessionAuthenticationService);
        String ticket = "0123456789abcdef0123456789abcdef";
        when(cacheTemplate.getAndRemove("message:ws-ticket:" + ticket)).thenReturn(
                objectMapper.writeValueAsString(new MessageWebSocketTicketService.TicketPayload("", 0L, null, null, null, null))
        );

        assertThat(ticketService.consume(ticket)).isNull();
    }

    @Test
    void consume_shouldRejectPayloadMissingIdentityVersionAfterOneTimeRemoval() throws Exception {
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, new MessageProperties(), sessionAuthenticationService);
        String ticket = "0123456789abcdef0123456789abcdef";
        when(cacheTemplate.getAndRemove("message:ws-ticket:" + ticket)).thenReturn(
                objectMapper.writeValueAsString(new MessageWebSocketTicketService.TicketPayload("session-1", 1001L, null, null, 3, null))
        );

        assertThat(ticketService.consume(ticket)).isNull();
    }

    @Test
    void consume_shouldRejectUnsafePayloadSessionIdAfterOneTimeRemoval() throws Exception {
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, new MessageProperties(), sessionAuthenticationService);
        String ticket = "0123456789abcdef0123456789abcdef";
        when(cacheTemplate.getAndRemove("message:ws-ticket:" + ticket)).thenReturn(
                objectMapper.writeValueAsString(new MessageWebSocketTicketService.TicketPayload("../session", 1001L, "user-uuid-1001", 9L, 3, "permissions-1"))
        );

        assertThat(ticketService.consume(ticket)).isNull();
    }

    @Test
    void consume_shouldRejectOversizedPayloadAfterOneTimeRemoval() {
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, new MessageProperties(), sessionAuthenticationService);
        String ticket = "0123456789abcdef0123456789abcdef";
        when(cacheTemplate.getAndRemove("message:ws-ticket:" + ticket)).thenReturn("x".repeat(4097));

        assertThat(ticketService.consume(ticket)).isNull();
    }

    private MessageSessionAuthenticationService.AuthenticatedAccess authenticatedAccess(Set<String> permissions) {
        CurrentUser trustedCurrentUser = currentUser();
        trustedCurrentUser.setPermissions(permissions);
        CurrentUserDTO snapshot = new CurrentUserDTO(
                trustedCurrentUser.getUserId(),
                trustedCurrentUser.getUserUuid(),
                trustedCurrentUser.getUsername(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                trustedCurrentUser.getSimulatedRoleId(),
                List.of(),
                trustedCurrentUser.getSessionId(),
                trustedCurrentUser.getPermissionsVersion(),
                trustedCurrentUser.getSessionVersion(),
                permissions.stream().sorted().toList(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                false,
                "/"
        );
        return new MessageSessionAuthenticationService.AuthenticatedAccess(trustedCurrentUser, snapshot);
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser(1001L, "alice", 1001L, "session-1", 3, true, Set.of("message:message:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setSimulatedRoleId(9L);
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(1001L, "alice", 1001L, "session-1", 3, false, Set.of("message:message:view"));
    }
}
