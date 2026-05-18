package com.legendary.invention.message.app;

import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.vo.PageResponse;
import com.legendary.invention.message.dto.MessageDTO;
import com.legendary.invention.message.service.MessagePushService;
import com.legendary.invention.message.service.SmtpNotificationMailService;
import com.legendary.invention.message.vo.MessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessageAppServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private OperationAuditService operationAuditService;

    @Mock
    private MessagePushService messagePushService;

    @Mock
    private SmtpNotificationMailService smtpNotificationMailService;

    private MessageAppService messageAppService;

    @BeforeEach
    void setUp() {
        messageAppService = new MessageAppService(
                jdbcTemplate,
                operationAuditService,
                messagePushService,
                smtpNotificationMailService
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMessages_shouldReturnPagedNotices() {
        doReturn(1L).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));

        MessageVO.NoticeVO notice = notice(1001L, "欢迎公告");
        doReturn(List.of(notice))
                .when(jdbcTemplate)
                .query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));

        CurrentUser currentUser = currentUser();
        PageResponse<MessageVO.NoticeVO> response = messageAppService.listMessages(currentUser, 1, 20);

        assertThat(response.getPageNo()).isEqualTo(1L);
        assertThat(response.getPageSize()).isEqualTo(20L);
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getRecords().get(0).getTitle()).isEqualTo("欢迎公告");
    }

    @Test
    void countUnread_shouldNormalizeNullCountToZero() {
        doReturn(null).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));

        Long unreadCount = messageAppService.countUnread(currentUser());

        assertThat(unreadCount).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listArchive_shouldScopeRegularUsersToOwnedOrVisibleMessages() {
        doReturn(0L).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        doReturn(List.of())
                .when(jdbcTemplate)
                .query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));

        messageAppService.listArchive(currentUser(), new MessageDTO.MessageArchiveQueryRequest());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Long.class), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("n.created_by = ?")
                .contains("n.target_user_id = ?")
                .contains("sys_user_role ur");
    }

    private CurrentUser currentUser() {
        return new CurrentUser(1001L, "alice", 1001L, "session-1", 3, true, Set.of("message:message:view"));
    }

    private MessageVO.NoticeVO notice(Long id, String title) {
        MessageVO.NoticeVO notice = new MessageVO.NoticeVO();
        notice.setId(id);
        notice.setTenantId(1001L);
        notice.setMessageType("MESSAGE");
        notice.setTargetScope("TENANT");
        notice.setTitle(title);
        notice.setContent("内容");
        notice.setSourceType("MANUAL");
        notice.setPublishStatus("PUBLISHED");
        notice.setPublishedAt(LocalDateTime.now());
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        notice.setReadFlag(Boolean.FALSE);
        return notice;
    }
}
