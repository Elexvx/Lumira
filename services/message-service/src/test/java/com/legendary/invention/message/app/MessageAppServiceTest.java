package com.legendary.invention.message.app;

import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.vo.PageResponse;
import com.legendary.invention.message.dto.MessageDTO;
import com.legendary.invention.message.dto.MessageQueryModels.NoticeArchiveQuery;
import com.legendary.invention.message.mapper.MessageDeliveryLogMapper;
import com.legendary.invention.message.mapper.MessageNoticeMapper;
import com.legendary.invention.message.service.MessagePushService;
import com.legendary.invention.message.service.SmtpNotificationMailService;
import com.legendary.invention.message.service.WechatOfficialAccountNotificationService;
import com.legendary.invention.message.vo.MessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessageAppServiceTest {

    @Mock
    private MessageNoticeMapper messageNoticeMapper;

    @Mock
    private MessageDeliveryLogMapper messageDeliveryLogMapper;

    @Mock
    private OperationAuditService operationAuditService;

    @Mock
    private MessagePushService messagePushService;

    @Mock
    private SmtpNotificationMailService smtpNotificationMailService;

    @Mock
    private WechatOfficialAccountNotificationService wechatOfficialAccountNotificationService;

    private MessageAppService messageAppService;

    @BeforeEach
    void setUp() {
        messageAppService = new MessageAppService(
                messageNoticeMapper,
                messageDeliveryLogMapper,
                operationAuditService,
                messagePushService,
                smtpNotificationMailService,
                wechatOfficialAccountNotificationService
        );
    }

    @Test
    void listMessages_shouldReturnPagedNotices() {
        MessageVO.NoticeVO notice = notice(1001L, "欢迎公告");
        when(messageNoticeMapper.countVisiblePublished(1001L, 1001L)).thenReturn(1L);
        when(messageNoticeMapper.listVisiblePublished(1001L, 1001L, 20L, 0L)).thenReturn(List.of(notice));

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
        when(messageNoticeMapper.countUnread(1001L, 1001L)).thenReturn(null);

        Long unreadCount = messageAppService.countUnread(currentUser());

        assertThat(unreadCount).isZero();
    }

    @Test
    void listArchive_shouldScopeRegularUsersToOwnedOrVisibleMessages() {
        when(messageNoticeMapper.countArchive(any(NoticeArchiveQuery.class))).thenReturn(0L);
        when(messageNoticeMapper.listArchive(any(NoticeArchiveQuery.class))).thenReturn(List.of());

        messageAppService.listArchive(currentUser(), new MessageDTO.MessageArchiveQueryRequest());

        ArgumentCaptor<NoticeArchiveQuery> queryCaptor = ArgumentCaptor.forClass(NoticeArchiveQuery.class);
        verify(messageNoticeMapper).countArchive(queryCaptor.capture());
        assertThat(queryCaptor.getValue().isManageArchive()).isFalse();
        assertThat(queryCaptor.getValue().getUserId()).isEqualTo(1001L);
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
