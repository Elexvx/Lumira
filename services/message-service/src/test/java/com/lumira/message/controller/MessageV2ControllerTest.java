package com.lumira.message.controller;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.dto.MessageDTO;
import com.lumira.message.vo.MessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageV2ControllerTest {

    private MessageAppService messageAppService;
    private SecurityContextFacade securityContextFacade;
    private MessageV2Controller controller;

    @BeforeEach
    void setUp() {
        messageAppService = mock(MessageAppService.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        controller = new MessageV2Controller(messageAppService, securityContextFacade);
    }

    @Test
    void listMessages_shouldDelegateToApplicationService() {
        CurrentUser currentUser = currentUser("message:message:view");
        MessageVO.NoticePageResponse page = new MessageVO.NoticePageResponse();
        page.setPageNo(1L);
        page.setPageSize(10L);
        page.setTotal(-1L);
        page.setRecords(List.of());
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(messageAppService.listMessages(currentUser, 1L, 10L)).thenReturn(page);

        var response = controller.listMessages(1L, 10L);

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(page);
        verify(messageAppService).listMessages(currentUser, 1L, 10L);
    }

    @Test
    void listArchive_shouldDelegateToApplicationServiceWithRequestContract() {
        CurrentUser currentUser = currentUser("system:notification:view");
        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        request.setPageNo(2L);
        request.setPageSize(20L);
        MessageVO.NoticeArchivePageResponse page = new MessageVO.NoticeArchivePageResponse();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(messageAppService.listArchive(currentUser, request)).thenReturn(page);

        var response = controller.listArchive(request);

        assertThat(response.getData()).isSameAs(page);
        verify(messageAppService).listArchive(currentUser, request);
    }

    @Test
    void readAll_shouldDelegateToApplicationService() {
        CurrentUser currentUser = currentUser("message:message:read");
        MessageVO.UnreadCountVO unreadCount = new MessageVO.UnreadCountVO();
        unreadCount.setUnreadCount(0L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(messageAppService.markAllRead(currentUser)).thenReturn(unreadCount);

        var response = controller.readAll();

        assertThat(response.getData().getUnreadCount()).isZero();
        verify(messageAppService).markAllRead(currentUser);
    }

    @Test
    void unreadCount_shouldRejectMissingPermissionBeforeCallingApplicationService() {
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser("message:other:view"));

        assertThatThrownBy(() -> controller.unreadCount())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少权限");
    }

    private CurrentUser currentUser(String permission) {
        return new CurrentUser(1001L, "alice", 1001L, "session-1", 1, true, Set.of(permission));
    }
}
