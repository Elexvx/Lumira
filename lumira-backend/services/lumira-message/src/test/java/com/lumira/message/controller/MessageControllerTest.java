package com.lumira.message.controller;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.infrastructure.security.MessageSessionAuthenticationService;
import com.lumira.message.vo.MessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageControllerTest {

    private MessageAppService messageAppService;
    private SecurityContextFacade securityContextFacade;
    private com.lumira.message.service.MessageWebSocketTicketService webSocketTicketService;
    private com.lumira.message.service.MessageWebSocketRegistry webSocketRegistry;
    private MessageSessionAuthenticationService sessionAuthenticationService;
    private MessageController controller;

    @BeforeEach
    void setUp() {
        messageAppService = mock(MessageAppService.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        webSocketTicketService = mock(com.lumira.message.service.MessageWebSocketTicketService.class);
        webSocketRegistry = mock(com.lumira.message.service.MessageWebSocketRegistry.class);
        sessionAuthenticationService = mock(MessageSessionAuthenticationService.class);
        controller = new MessageController(
                messageAppService,
                securityContextFacade,
                webSocketTicketService,
                webSocketRegistry,
                sessionAuthenticationService
        );
    }

    @Test
    void listMessages_shouldDelegateToApplicationService() {
        CurrentUser currentUser = currentUser("message:message:view");
        CurrentUser refreshedUser = currentUser("message:message:view");
        MessageVO.NoticePageResponse page = new MessageVO.NoticePageResponse();
        page.setPageNo(1L);
        page.setPageSize(10L);
        page.setTotal(-1L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 3, "permissions-1"))
                .thenReturn(new MessageSessionAuthenticationService.AuthenticatedAccess(refreshedUser, null));
        when(messageAppService.listMessages(refreshedUser, 1L, 10L)).thenReturn(page);

        var response = controller.listMessages(1L, 10L);

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(page);
        verify(messageAppService).listMessages(refreshedUser, 1L, 10L);
    }

    @Test
    void listMessages_shouldRejectMissingPermissionBeforeCallingApplicationService() {
        CurrentUser currentUser = currentUser("message:other:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 3, "permissions-1"))
                .thenReturn(new MessageSessionAuthenticationService.AuthenticatedAccess(currentUser("message:other:view"), null));

        assertThatThrownBy(() -> controller.listMessages(1L, 10L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");
    }

    @Test
    void listMessages_shouldRejectUnauthenticatedUserEvenWhenPermissionIsPresent() {
        CurrentUser currentUser = currentUser("message:message:view");
        currentUser.setAuthenticated(false);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.listMessages(1L, 10L))
                .isInstanceOf(BizException.class);
        verify(messageAppService, never()).listMessages(currentUser, 1L, 10L);
    }

    @Test
    void listMessages_shouldRejectBlankUsernameBeforeCallingApplicationService() {
        CurrentUser currentUser = currentUser("message:message:view");
        currentUser.setUsername(" ");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.listMessages(1L, 10L))
                .isInstanceOf(BizException.class);
        verify(messageAppService, never()).listMessages(currentUser, 1L, 10L);
    }

    @Test
    void listMessages_shouldRejectMissingSessionIdBeforeCallingApplicationService() {
        CurrentUser currentUser = currentUser("message:message:view");
        currentUser.setSessionId(null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.listMessages(1L, 10L))
                .isInstanceOf(BizException.class);
        verify(messageAppService, never()).listMessages(currentUser, 1L, 10L);
    }

    @Test
    void listMessages_shouldRejectStaleSessionSnapshotBeforeCallingApplicationService() {
        CurrentUser currentUser = currentUser("message:message:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 3, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.SESSION_EXPIRED, "stale"));

        assertThatThrownBy(() -> controller.listMessages(1L, 10L))
                .isInstanceOf(BizException.class);
        verify(messageAppService, never()).listMessages(currentUser, 1L, 10L);
    }

    @Test
    void listMessages_shouldRejectTrustedUserWhenResolverIsUnavailable() {
        MessageController strictController = new MessageController(
                messageAppService,
                securityContextFacade,
                webSocketTicketService,
                webSocketRegistry,
                null
        );
        CurrentUser currentUser = currentUser("message:message:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> strictController.listMessages(1L, 10L))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(messageAppService, never()).listMessages(currentUser, 1L, 10L);
    }

    private CurrentUser currentUser(String permission) {
        CurrentUser currentUser = new CurrentUser(1001L, "alice", "session-1", 3, true, Set.of(permission));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }
}
