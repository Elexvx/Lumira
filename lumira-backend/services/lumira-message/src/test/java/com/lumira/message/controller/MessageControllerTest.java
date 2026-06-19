package com.lumira.message.controller;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.vo.MessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageControllerTest {

    private MessageAppService messageAppService;
    private SecurityContextFacade securityContextFacade;
    private com.lumira.message.service.MessageWebSocketTicketService webSocketTicketService;
    private com.lumira.message.service.MessageWebSocketRegistry webSocketRegistry;
    private MessageController controller;

    @BeforeEach
    void setUp() {
        messageAppService = mock(MessageAppService.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        webSocketTicketService = mock(com.lumira.message.service.MessageWebSocketTicketService.class);
        webSocketRegistry = mock(com.lumira.message.service.MessageWebSocketRegistry.class);
        controller = new MessageController(messageAppService, securityContextFacade, webSocketTicketService, webSocketRegistry);
    }

    @Test
    void listMessages_shouldDelegateToApplicationService() {
        CurrentUser currentUser = currentUser("message:message:view");
        MessageVO.NoticePageResponse page = new MessageVO.NoticePageResponse();
        page.setPageNo(1L);
        page.setPageSize(10L);
        page.setTotal(-1L);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(messageAppService.listMessages(currentUser, 1L, 10L)).thenReturn(page);

        var response = controller.listMessages(1L, 10L);

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(page);
        verify(messageAppService).listMessages(currentUser, 1L, 10L);
    }

    @Test
    void listMessages_shouldRejectMissingPermissionBeforeCallingApplicationService() {
        CurrentUser currentUser = currentUser("message:other:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> controller.listMessages(1L, 10L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少权限");
    }

    private CurrentUser currentUser(String permission) {
        return new CurrentUser(1001L, "alice", 1001L, "session-1", 3, true, Set.of(permission));
    }
}
