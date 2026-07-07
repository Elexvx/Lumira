package com.lumira.message.controller;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.dto.MessageDTO;
import com.lumira.message.infrastructure.security.MessageSessionAuthenticationService;
import com.lumira.message.vo.MessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageV2ControllerTest {

    private MessageAppService messageAppService;
    private SecurityContextFacade securityContextFacade;
    private MessageSessionAuthenticationService sessionAuthenticationService;
    private MessageV2Controller controller;

    @BeforeEach
    void setUp() {
        messageAppService = mock(MessageAppService.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        sessionAuthenticationService = mock(MessageSessionAuthenticationService.class);
        controller = new MessageV2Controller(messageAppService, securityContextFacade, sessionAuthenticationService);
    }

    @Test
    void listMessages_shouldDelegateToApplicationService() {
        CurrentUser currentUser = currentUser("message:message:view");
        CurrentUser refreshedUser = currentUser("message:message:view");
        MessageVO.NoticePageResponse page = new MessageVO.NoticePageResponse();
        page.setPageNo(1L);
        page.setPageSize(10L);
        page.setTotal(-1L);
        page.setRecords(List.of());
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenReturn(new MessageSessionAuthenticationService.AuthenticatedAccess(refreshedUser, null));
        when(messageAppService.listMessages(refreshedUser, 1L, 10L)).thenReturn(page);

        var response = controller.listMessages(1L, 10L);

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(page);
        verify(messageAppService).listMessages(refreshedUser, 1L, 10L);
    }

    @Test
    void listArchive_shouldDelegateToApplicationServiceWithRequestContract() {
        CurrentUser currentUser = currentUser("system:notification:view");
        CurrentUser refreshedUser = currentUser("system:notification:view");
        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        request.setPageNo(2L);
        request.setPageSize(20L);
        MessageVO.NoticeArchivePageResponse page = new MessageVO.NoticeArchivePageResponse();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenReturn(new MessageSessionAuthenticationService.AuthenticatedAccess(refreshedUser, null));
        when(messageAppService.listArchive(refreshedUser, request)).thenReturn(page);

        var response = controller.listArchive(request);

        assertThat(response.getData()).isSameAs(page);
        verify(messageAppService).listArchive(refreshedUser, request);
    }

    @Test
    void readAll_shouldDelegateToApplicationService() {
        CurrentUser currentUser = currentUser("message:message:read");
        CurrentUser refreshedUser = currentUser("message:message:read");
        MessageVO.UnreadCountVO unreadCount = new MessageVO.UnreadCountVO();
        unreadCount.setUnreadCount(0L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenReturn(new MessageSessionAuthenticationService.AuthenticatedAccess(refreshedUser, null));
        when(messageAppService.markAllRead(refreshedUser)).thenReturn(unreadCount);

        var response = controller.readAll();

        assertThat(response.getData().getUnreadCount()).isZero();
        verify(messageAppService).markAllRead(refreshedUser);
    }

    @Test
    void readMessage_shouldDelegateToApplicationService() {
        CurrentUser currentUser = currentUser("message:message:read");
        CurrentUser refreshedUser = currentUser("message:message:read");
        MessageVO.NoticeVO notice = new MessageVO.NoticeVO();
        notice.setId(99L);
        notice.setReadFlag(Boolean.TRUE);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenReturn(new MessageSessionAuthenticationService.AuthenticatedAccess(refreshedUser, null));
        when(messageAppService.markMessageRead(refreshedUser, 99L)).thenReturn(notice);

        var response = controller.readMessage(99L);

        assertThat(response.getData().getReadFlag()).isTrue();
        verify(messageAppService).markMessageRead(refreshedUser, 99L);
    }

    @Test
    void unreadCount_shouldRejectMissingPermissionBeforeCallingApplicationService() {
        CurrentUser currentUser = currentUser("message:other:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenReturn(new MessageSessionAuthenticationService.AuthenticatedAccess(currentUser("message:other:view"), null));

        assertThatThrownBy(() -> controller.unreadCount())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Missing permission");
    }

    @Test
    void unreadCount_shouldRejectUnauthenticatedUserEvenWhenPermissionIsPresent() {
        CurrentUser currentUser = currentUser("message:message:view");
        currentUser.setAuthenticated(false);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.unreadCount())
                .isInstanceOf(BizException.class);
        verify(messageAppService, never()).countUnread(currentUser);
    }

    @Test
    void unreadCount_shouldRejectBlankUsernameBeforeCallingApplicationService() {
        CurrentUser currentUser = currentUser("message:message:view");
        currentUser.setUsername(" ");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.unreadCount())
                .isInstanceOf(BizException.class);
        verify(messageAppService, never()).countUnread(currentUser);
    }

    @Test
    void unreadCount_shouldRejectMissingSessionVersionBeforeCallingApplicationService() {
        CurrentUser currentUser = currentUser("message:message:view");
        currentUser.setSessionVersion(null);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.unreadCount())
                .isInstanceOf(BizException.class);
        verify(messageAppService, never()).countUnread(currentUser);
    }

    @Test
    void unreadCount_shouldRejectStaleSessionSnapshotBeforeCallingApplicationService() {
        CurrentUser currentUser = currentUser("message:message:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(sessionAuthenticationService.authenticateSessionTicket("session-1", 1001L, "user-uuid-1001", null, 1, "permissions-1"))
                .thenThrow(new BizException(ErrorCode.SESSION_EXPIRED, "stale"));

        assertThatThrownBy(() -> controller.unreadCount())
                .isInstanceOf(BizException.class);
        verify(messageAppService, never()).countUnread(currentUser);
    }

    @Test
    void unreadCount_shouldRejectTrustedUserWhenResolverIsUnavailable() {
        MessageV2Controller strictController = new MessageV2Controller(messageAppService, securityContextFacade, null);
        CurrentUser currentUser = currentUser("message:message:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> strictController.unreadCount())
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(messageAppService, never()).countUnread(currentUser);
    }

    private CurrentUser currentUser(String permission) {
        CurrentUser currentUser = new CurrentUser(1001L, "alice", 1001L, "session-1", 1, true, Set.of(permission));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("permissions-1");
        return currentUser;
    }
}
