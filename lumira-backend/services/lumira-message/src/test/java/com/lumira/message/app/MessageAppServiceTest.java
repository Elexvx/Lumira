package com.lumira.message.app;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.message.config.MessageProperties;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.message.dto.MessageDTO;
import com.lumira.message.infrastructure.redis.CacheTemplate;
import com.lumira.message.dto.MessageQueryModels.NoticeArchiveQuery;
import com.lumira.message.dto.MessageQueryModels.DeliveryLogQuery;
import com.lumira.message.entity.MessageDeliveryLogEntity;
import com.lumira.message.entity.MessageNoticeEntity;
import com.lumira.message.mapper.MessageDeliveryLogMapper;
import com.lumira.message.mapper.MessageNoticeMapper;
import com.lumira.message.service.MessagePushService;
import com.lumira.message.service.SmtpNotificationMailService;
import com.lumira.message.service.WechatOfficialAccountNotificationService;
import com.lumira.message.vo.MessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class MessageAppServiceTest {

    private static final long READ_MODEL_VERSION = 10L;

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

    @Mock
    private SystemInternalApi systemInternalApi;

    @Mock
    private CacheTemplate cacheTemplate;

    private final MessageProperties messageProperties = new MessageProperties();

    private MessageAppService messageAppService;

    @BeforeEach
    void setUp() {
        messageAppService = new MessageAppService(
                messageNoticeMapper,
                messageDeliveryLogMapper,
                operationAuditService,
                messagePushService,
                smtpNotificationMailService,
                wechatOfficialAccountNotificationService,
                systemInternalApi,
                cacheTemplate,
                messageProperties
        );
    }

    @Test
    void listMessages_shouldReturnPagedNotices() {
        MessageVO.NoticeVO notice = notice(1001L, "notice-1");
        notice.setTargetScope("USER");
        notice.setTargetUserId(2001L);
        notice.setTargetUserUuid("user-uuid-2001");
        MessageVO.NoticeVO extra = notice(1002L, "notice-2");
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.listVisiblePublished(1001L, "user-uuid-1001", List.of(3001L), 2L, 0L)).thenReturn(List.of(notice, extra));
        when(systemInternalApi.userIdentitiesByIds(List.of(2001L))).thenReturn(List.of(
                new SystemUserSnapshotDTO(2001L, "user-uuid-2001", "bob", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        ));

        CurrentUser currentUser = currentUser();
        MessageVO.NoticePageResponse response = messageAppService.listMessages(currentUser, 1, 1);

        assertThat(response.getPageNo()).isEqualTo(1L);
        assertThat(response.getPageSize()).isEqualTo(1L);
        assertThat(response.getTotal()).isEqualTo(-1L);
        assertThat(response.getHasMore()).isTrue();
        assertThat(response.getTotalCapped()).isTrue();
        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getRecords().get(0).getTitle()).isEqualTo("notice-1");
        assertThat(response.getRecords().get(0).getTargetUserName()).isEqualTo("bob");
    }

    @Test
    void listMessages_shouldReturnEmptyPageForAnonymousUser() {
        MessageVO.NoticePageResponse response = messageAppService.listMessages(null, 1, 20);

        assertThat(response.getPageNo()).isEqualTo(1L);
        assertThat(response.getPageSize()).isEqualTo(20L);
        assertThat(response.getTotal()).isZero();
        assertThat(response.getHasMore()).isFalse();
        assertThat(response.getTotalCapped()).isFalse();
        assertThat(response.getRecords()).isEmpty();
        verify(systemInternalApi, never()).permissionRoleSnapshot(any(), any());
        verify(messageNoticeMapper, never()).listVisiblePublished(any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void listMessages_shouldTreatUnauthenticatedUserAsAnonymousBeforeDatabaseAccess() {
        MessageVO.NoticePageResponse response = messageAppService.listMessages(unauthenticatedUser(), 1, 20);

        assertThat(response.getTotal()).isZero();
        assertThat(response.getRecords()).isEmpty();
        verify(systemInternalApi, never()).permissionRoleSnapshot(any(), any());
        verify(messageNoticeMapper, never()).listVisiblePublished(any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void listMessages_shouldTreatBlankUsernameAsAnonymousBeforeDatabaseAccess() {
        MessageVO.NoticePageResponse response = messageAppService.listMessages(blankUsernameUser(), 1, 20);

        assertThat(response.getTotal()).isZero();
        assertThat(response.getRecords()).isEmpty();
        verify(systemInternalApi, never()).permissionRoleSnapshot(any(), any());
        verify(messageNoticeMapper, never()).listVisiblePublished(any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void listMessages_shouldTreatMissingSessionVersionAsAnonymousBeforeDatabaseAccess() {
        MessageVO.NoticePageResponse response = messageAppService.listMessages(missingSessionVersionUser(), 1, 20);

        assertThat(response.getTotal()).isZero();
        assertThat(response.getRecords()).isEmpty();
        verify(systemInternalApi, never()).permissionRoleSnapshot(any(), any());
        verify(messageNoticeMapper, never()).listVisiblePublished(any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void listMessages_shouldUseCurrentUserPermissionSnapshotWhenAligned() {
        MessageVO.NoticeVO notice = notice(1001L, "session-snapshot-notice");
        notice.setTargetScope("PLATFORM");
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(9L);
        when(messageNoticeMapper.listVisiblePublished(eq(1001L), eq("user-uuid-1001"), argThat(list ->
                        list != null && list.containsAll(List.of(3001L, 3002L)) && list.size() == 2
                ), eq(2L), eq(0L)))
                .thenReturn(List.of(notice));

        MessageVO.NoticePageResponse response = messageAppService.listMessages(currentUserWithSnapshot(), 1, 1);

        assertThat(response.getRecords()).hasSize(1);
        verify(systemInternalApi, never()).permissionRoleSnapshot(any(), any());
    }

    @Test
    void listMessages_shouldFallbackToSystemPermissionSnapshotWhenVersionMisaligned() {
        MessageVO.NoticeVO notice = notice(1001L, "remote-snapshot-notice");
        notice.setTargetScope("PLATFORM");
        CurrentUser currentUser = currentUserWithSnapshot();
        currentUser.setPermissionsVersion("v10");
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(11L);
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of(7001L, 8001L), "v11"));
        when(messageNoticeMapper.listVisiblePublished(eq(1001L), eq("user-uuid-1001"), eq(List.of(7001L, 8001L)), eq(2L), eq(0L)))
                .thenReturn(List.of(notice));

        messageAppService.listMessages(currentUser, 1, 1);

        verify(systemInternalApi).permissionRoleSnapshot(1001L, "user-uuid-1001");
    }

    @Test
    void listMessages_shouldUseSessionPermissionSnapshotWhenIamReadModelUnavailable() {
        MessageVO.NoticeVO notice = notice(1001L, "session-snapshot-notice");
        notice.setTargetScope("PLATFORM");

        CurrentUser currentUser = currentUserWithSnapshot();
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(null);
        when(messageNoticeMapper.listVisiblePublished(
                eq(1001L),
                eq("user-uuid-1001"),
                argThat(list ->
                        list != null && list.containsAll(List.of(3001L, 3002L)) && list.size() == 2
                ),
                eq(2L),
                eq(0L)
        )).thenReturn(List.of(notice));

        MessageVO.NoticePageResponse response = messageAppService.listMessages(currentUser, 1, 1);

        assertThat(response.getRecords()).hasSize(1);
        verify(systemInternalApi, never()).permissionRoleSnapshot(1001L, "user-uuid-1001");
    }

    @Test
    void countUnread_shouldUseCurrentUserPermissionSnapshotWhenAligned() {
        CurrentUser currentUser = currentUserWithSnapshot();
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(9L);
        when(systemInternalApi.readModelVersion("message", "unread")).thenReturn(READ_MODEL_VERSION);
        when(messageNoticeMapper.countUnread(eq(1001L), eq("user-uuid-1001"), argThat(list ->
                list != null && list.containsAll(List.of(3001L, 3002L)) && list.size() == 2
        ), eq(100L))).thenReturn(7L);
        when(cacheTemplate.get("message:unread-count:1001:user-uuid-1001:v9:v10")).thenReturn(null);

        Long unreadCount = messageAppService.countUnread(currentUser);

        assertThat(unreadCount).isEqualTo(7L);
        verify(systemInternalApi, never()).permissionRoleSnapshot(any(), any());
        verify(messageNoticeMapper).countUnread(eq(1001L), eq("user-uuid-1001"), argThat(list ->
                list != null && list.containsAll(List.of(3001L, 3002L)) && list.size() == 2
        ), eq(100L));
    }

    @Test
    void listMessages_shouldReuseRoleVisibilityCacheOnRepeatedCalls() {
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.listVisiblePublished(eq(1001L), eq("user-uuid-1001"), eq(List.of(3001L)), anyLong(), anyLong()))
                .thenReturn(List.of());

        CurrentUser currentUser = currentUser();
        messageAppService.listMessages(currentUser, 1, 1);
        messageAppService.listMessages(currentUser, 1, 1);

        verify(systemInternalApi, times(1)).permissionRoleSnapshot(1001L, "user-uuid-1001");
        assertThat(messageAppService.roleVisibilityCacheHits()).isEqualTo(1L);
        assertThat(messageAppService.roleVisibilityCacheMisses()).isEqualTo(1L);
    }

    @Test
    void listMessages_shouldInvalidateCachedRoleVisibilityWhenPermissionVersionAdvances() {
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(permissionSnapshot(List.of(3001L), "v1"))
                .thenReturn(permissionSnapshot(List.of(4001L), "v2"));
        when(messageNoticeMapper.listVisiblePublished(eq(1001L), eq("user-uuid-1001"), eq(List.of(3001L)), anyLong(), anyLong()))
                .thenReturn(List.of());
        when(messageNoticeMapper.listVisiblePublished(eq(1001L), eq("user-uuid-1001"), eq(List.of(4001L)), anyLong(), anyLong()))
                .thenReturn(List.of());

        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion("v0");
        messageAppService.listMessages(currentUser, 1, 1);
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(2L);
        clearMapField(messageAppService, "permissionSnapshotVersionCache");
        clearMapField(messageAppService, "localMessageListCache");
        messageAppService.listMessages(currentUser, 1, 1);

        verify(systemInternalApi, times(2)).permissionRoleSnapshot(1001L, "user-uuid-1001");
        verify(messageNoticeMapper).listVisiblePublished(eq(1001L), eq("user-uuid-1001"), eq(List.of(3001L)), anyLong(), anyLong());
        verify(messageNoticeMapper).listVisiblePublished(eq(1001L), eq("user-uuid-1001"), eq(List.of(4001L)), anyLong(), anyLong());
    }

    @Test
    void listMessages_shouldCacheTargetUserNameOnSecondListCall() {
        var cacheStore = new java.util.HashMap<String, String>();
        when(cacheTemplate.get(anyString())).thenAnswer(invocation -> cacheStore.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            cacheStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(cacheTemplate).put(anyString(), anyString(), any());

        MessageVO.NoticeVO notice = notice(1001L, "user-notice");
        notice.setTargetScope("USER");
        notice.setTargetUserId(2001L);
        notice.setTargetUserUuid("user-uuid-2001");
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of()));
        when(messageNoticeMapper.listVisiblePublished(1001L, "user-uuid-1001", List.of(), 2L, 0L))
                .thenReturn(List.of(notice))
                .thenReturn(List.of(notice));
        when(systemInternalApi.userIdentitiesByIds(List.of(2001L)))
                .thenReturn(List.of(new SystemUserSnapshotDTO(2001L, "user-uuid-2001", "bob", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)));

        CurrentUser currentUser = currentUser();
        messageAppService.listMessages(currentUser, 1, 1);
        messageAppService.listMessages(currentUser, 1, 1);

        verify(systemInternalApi).userIdentitiesByIds(eq(List.of(2001L)));
        verify(systemInternalApi, times(1)).permissionRoleSnapshot(1001L, "user-uuid-1001");
    }

    @Test
    void listMessages_shouldNotReuseTargetUserNameCacheAcrossUserUuidMismatch() {
        var cacheStore = new java.util.HashMap<String, String>();
        when(cacheTemplate.get(anyString())).thenAnswer(invocation -> cacheStore.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            cacheStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(cacheTemplate).put(anyString(), anyString(), any());

        MessageVO.NoticeVO firstNotice = notice(1001L, "first-user-notice");
        firstNotice.setTargetScope("USER");
        firstNotice.setTargetUserId(2001L);
        firstNotice.setTargetUserUuid("user-uuid-2001");
        MessageVO.NoticeVO reusedIdNotice = notice(1002L, "reused-id-notice");
        reusedIdNotice.setTargetScope("USER");
        reusedIdNotice.setTargetUserId(2001L);
        reusedIdNotice.setTargetUserUuid("user-uuid-reused");

        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of()));
        when(messageNoticeMapper.listVisiblePublished(1001L, "user-uuid-1001", List.of(), 2L, 0L))
                .thenReturn(List.of(firstNotice));
        when(messageNoticeMapper.listVisiblePublished(1001L, "user-uuid-1001", List.of(), 2L, 1L))
                .thenReturn(List.of(reusedIdNotice));
        when(systemInternalApi.userIdentitiesByIds(List.of(2001L)))
                .thenReturn(List.of(new SystemUserSnapshotDTO(2001L, "user-uuid-2001", "bob", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)))
                .thenReturn(List.of(new SystemUserSnapshotDTO(2001L, "user-uuid-reused", "carol", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)));

        CurrentUser currentUser = currentUser();
        MessageVO.NoticePageResponse firstResponse = messageAppService.listMessages(currentUser, 1, 1);
        MessageVO.NoticePageResponse secondResponse = messageAppService.listMessages(currentUser, 2, 1);

        assertThat(firstResponse.getRecords().get(0).getTargetUserName()).isEqualTo("bob");
        assertThat(secondResponse.getRecords().get(0).getTargetUserName()).isEqualTo("carol");
        verify(systemInternalApi, times(2)).userIdentitiesByIds(eq(List.of(2001L)));
    }

    @Test
    void listMessages_shouldNotResolveDisabledTargetUserName() {
        MessageVO.NoticeVO notice = notice(1001L, "disabled-user-notice");
        notice.setTargetScope("USER");
        notice.setTargetUserId(2001L);
        notice.setTargetUserUuid("user-uuid-2001");

        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of()));
        when(messageNoticeMapper.listVisiblePublished(1001L, "user-uuid-1001", List.of(), 2L, 0L))
                .thenReturn(List.of(notice));
        when(systemInternalApi.userIdentitiesByIds(List.of(2001L)))
                .thenReturn(List.of(new SystemUserSnapshotDTO(2001L, "user-uuid-2001", "bob", null, "DISABLED", null, null, null, null, null, null, null, null, null, null, null)));

        MessageVO.NoticePageResponse response = messageAppService.listMessages(currentUser(), 1, 1);

        assertThat(response.getRecords().get(0).getTargetUserName()).isNull();
        verify(systemInternalApi).userIdentitiesByIds(eq(List.of(2001L)));
    }

    @Test
    void listMessages_shouldCacheTargetRoleNameOnSecondListCall() {
        var cacheStore = new java.util.HashMap<String, String>();
        when(cacheTemplate.get(anyString())).thenAnswer(invocation -> cacheStore.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            cacheStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(cacheTemplate).put(anyString(), anyString(), any());

        MessageVO.NoticeVO notice = notice(1001L, "role-notice");
        notice.setTargetScope("ROLE");
        notice.setTargetRoleId(3001L);
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.listVisiblePublished(1001L, "user-uuid-1001", List.of(3001L), 2L, 0L))
                .thenReturn(List.of(notice));
        when(systemInternalApi.roleNamesByIds(List.of(3001L)))
                .thenReturn(List.of(new SystemRoleSnapshotDTO(3001L, "ADMIN", "Admin")));

        CurrentUser currentUser = currentUser();
        messageAppService.listMessages(currentUser, 1, 1);
        messageAppService.listMessages(currentUser, 1, 1);

        verify(systemInternalApi).roleNamesByIds(eq(List.of(3001L)));
        verify(messageNoticeMapper, times(1))
                .listVisiblePublished(1001L, "user-uuid-1001", List.of(3001L), 2L, 0L);
    }

    @Test
    void countUnread_shouldNormalizeNullCountToZero() {
        when(systemInternalApi.readModelVersion("message", "unread")).thenReturn(READ_MODEL_VERSION);
        when(cacheTemplate.get("message:unread-count:1001:user-uuid-1001:v1:v10")).thenReturn(null);
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Arrays.asList(3001L, 3001L, null)));
        when(messageNoticeMapper.countUnread(1001L, "user-uuid-1001", List.of(3001L), 100L)).thenReturn(null);

        Long unreadCount = messageAppService.countUnread(currentUser());

        assertThat(unreadCount).isZero();
        verify(cacheTemplate).put(eq("message:unread-count:1001:user-uuid-1001:v1:v10"), eq("0"), any(Duration.class));
    }

    @Test
    void countUnread_shouldUseCachedValueWhenAvailable() {
        when(systemInternalApi.readModelVersion("message", "unread")).thenReturn(READ_MODEL_VERSION);
        when(cacheTemplate.get("message:unread-count:1001:user-uuid-1001:v1:v10")).thenReturn("42");

        Long unreadCount = messageAppService.countUnread(currentUser());

        assertThat(unreadCount).isEqualTo(42L);
        verify(messageNoticeMapper, times(0)).countUnread(anyLong(), any(), any(), anyLong());
    }

    @Test
    void countUnread_shouldIgnoreInvalidCacheAndFallbackToDatabase() {
        when(systemInternalApi.readModelVersion("message", "unread")).thenReturn(READ_MODEL_VERSION);
        when(cacheTemplate.get("message:unread-count:1001:user-uuid-1001:v1:v10")).thenReturn("invalid");
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(Arrays.asList(3001L)));
        when(messageNoticeMapper.countUnread(1001L, "user-uuid-1001", List.of(3001L), 100L)).thenReturn(1L);

        Long unreadCount = messageAppService.countUnread(currentUser());

        assertThat(unreadCount).isEqualTo(1L);
        verify(messageNoticeMapper).countUnread(anyLong(), eq("user-uuid-1001"), eq(List.of(3001L)), anyLong());
        verify(cacheTemplate).put(eq("message:unread-count:1001:user-uuid-1001:v1:v10"), eq("1"), any(Duration.class));
    }

    @Test
    void listArchive_shouldScopeRegularUsersToOwnedOrVisibleMessages() {
        MessageVO.NoticeVO roleNotice = notice(1003L, "role-notice");
        roleNotice.setTargetScope("ROLE");
        roleNotice.setTargetRoleId(3001L);
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.countArchive(any(NoticeArchiveQuery.class))).thenReturn(0L);
        when(messageNoticeMapper.listArchive(any(NoticeArchiveQuery.class))).thenReturn(List.of(roleNotice));
        when(systemInternalApi.roleNamesByIds(List.of(3001L))).thenReturn(List.of(
                new SystemRoleSnapshotDTO(3001L, "ADMIN", "Admin")
        ));

        MessageVO.NoticeArchivePageResponse response = messageAppService.listArchive(currentUser(), new MessageDTO.MessageArchiveQueryRequest());

        ArgumentCaptor<NoticeArchiveQuery> queryCaptor = ArgumentCaptor.forClass(NoticeArchiveQuery.class);
        verify(messageNoticeMapper).countArchive(queryCaptor.capture());
        assertThat(queryCaptor.getValue().isManageArchive()).isFalse();
        assertThat(queryCaptor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(queryCaptor.getValue().getRoleIds()).containsExactly(3001L);
        assertThat(queryCaptor.getValue().getPermissionSnapshotVersion()).isEqualTo("v1");
        assertThat(queryCaptor.getValue().getCountLimit()).isEqualTo(21L);
        assertThat(response.getRecords().get(0).getTargetRoleName()).isEqualTo("Admin");
    }

    @Test
    void listArchive_shouldTreatUnauthenticatedUserAsAnonymousBeforeDatabaseAccess() {
        MessageVO.NoticeArchivePageResponse response = messageAppService.listArchive(unauthenticatedUser(), new MessageDTO.MessageArchiveQueryRequest());

        assertThat(response.getTotal()).isZero();
        assertThat(response.getRecords()).isEmpty();
        verify(systemInternalApi, never()).permissionRoleSnapshot(any(), any());
        verify(messageNoticeMapper, never()).countArchive(any(NoticeArchiveQuery.class));
        verify(messageNoticeMapper, never()).listArchive(any(NoticeArchiveQuery.class));
    }

    @Test
    void listArchive_shouldExposeHasMoreWhenArchiveTotalIsCapped() {
        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        request.setPageNo(2L);
        request.setPageSize(10L);
        MessageVO.NoticeVO notice = notice(1001L, "archive-result");
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.countArchive(any(NoticeArchiveQuery.class))).thenReturn(21L);
        when(messageNoticeMapper.listArchive(any(NoticeArchiveQuery.class))).thenReturn(List.of(notice));

        MessageVO.NoticeArchivePageResponse response = messageAppService.listArchive(currentUser(), request);

        ArgumentCaptor<NoticeArchiveQuery> queryCaptor = ArgumentCaptor.forClass(NoticeArchiveQuery.class);
        verify(messageNoticeMapper).countArchive(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCountLimit()).isEqualTo(21L);
        assertThat(response).isInstanceOf(MessageVO.NoticeArchivePageResponse.class);
        MessageVO.NoticeArchivePageResponse archiveResponse = (MessageVO.NoticeArchivePageResponse) response;
        assertThat(archiveResponse.getHasMore()).isTrue();
        assertThat(archiveResponse.getTotalCapped()).isTrue();
        assertThat(archiveResponse.getTotal()).isEqualTo(21L);
    }

    @Test
    void listArchive_shouldNotMarkHasMoreWhenTotalUnderCountLimit() {
        MessageVO.NoticeVO notice = notice(1001L, "archive-result");
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.countArchive(any(NoticeArchiveQuery.class))).thenReturn(8L);
        when(messageNoticeMapper.listArchive(any(NoticeArchiveQuery.class))).thenReturn(List.of(notice));

        MessageVO.NoticeArchivePageResponse response = messageAppService.listArchive(currentUser(), new MessageDTO.MessageArchiveQueryRequest());

        assertThat(response).isInstanceOf(MessageVO.NoticeArchivePageResponse.class);
        MessageVO.NoticeArchivePageResponse archiveResponse = (MessageVO.NoticeArchivePageResponse) response;
        assertThat(archiveResponse.getHasMore()).isFalse();
        assertThat(archiveResponse.getTotalCapped()).isFalse();
        assertThat(archiveResponse.getTotal()).isEqualTo(8L);
    }

    @Test
    void listArchive_shouldCacheCountForRepeatedQueries() {
        var cacheStore = new java.util.HashMap<String, String>();
        when(cacheTemplate.get(anyString())).thenAnswer(invocation -> cacheStore.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            cacheStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(cacheTemplate).put(anyString(), anyString(), any());

        MessageVO.NoticeVO notice = notice(1001L, "archive-result");
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.countArchive(any(NoticeArchiveQuery.class))).thenReturn(8L);
        when(messageNoticeMapper.listArchive(any(NoticeArchiveQuery.class))).thenReturn(List.of(notice));

        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        messageAppService.listArchive(currentUser(), request);
        messageAppService.listArchive(currentUser(), request);

        verify(messageNoticeMapper, times(1)).countArchive(any(NoticeArchiveQuery.class));
    }

    @Test
    void listDeliveryLogs_shouldExposeHasMoreWhenDeliveryLogTotalIsCapped() {
        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        request.setPageNo(2L);
        request.setPageSize(10L);
        when(messageDeliveryLogMapper.countDeliveryLogs(any(DeliveryLogQuery.class))).thenReturn(21L);
        when(messageDeliveryLogMapper.listDeliveryLogs(any(DeliveryLogQuery.class))).thenReturn(List.of());

        MessageVO.DeliveryLogPageResponse response = messageAppService.listDeliveryLogs(currentUser(), request);

        ArgumentCaptor<DeliveryLogQuery> queryCaptor = ArgumentCaptor.forClass(DeliveryLogQuery.class);
        verify(messageDeliveryLogMapper).countDeliveryLogs(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCountLimit()).isEqualTo(21L);
        assertThat(response.getHasMore()).isTrue();
        assertThat(response.getTotalCapped()).isTrue();
        assertThat(response.getTotal()).isEqualTo(21L);
    }

    @Test
    void countUnread_shouldTreatUnauthenticatedUserAsAnonymousBeforeDatabaseAccess() {
        Long unreadCount = messageAppService.countUnread(unauthenticatedUser());

        assertThat(unreadCount).isZero();
        verify(systemInternalApi, never()).permissionRoleSnapshot(any(), any());
        verify(messageNoticeMapper, never()).countUnread(anyLong(), any(), any(), anyLong());
    }

    @Test
    void listDeliveryLogs_shouldScopeNormalUserToOwnCreatedLogs() {
        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        when(messageDeliveryLogMapper.countDeliveryLogs(any(DeliveryLogQuery.class))).thenReturn(0L);
        when(messageDeliveryLogMapper.listDeliveryLogs(any(DeliveryLogQuery.class))).thenReturn(List.of());

        messageAppService.listDeliveryLogs(currentUser(), request);

        ArgumentCaptor<DeliveryLogQuery> queryCaptor = ArgumentCaptor.forClass(DeliveryLogQuery.class);
        verify(messageDeliveryLogMapper).countDeliveryLogs(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(queryCaptor.getValue().isManageDeliveryLogs()).isFalse();
    }

    @Test
    void listDeliveryLogs_shouldAllowPrivilegedUserToManageAllLogs() {
        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        CurrentUser currentUser = trusted(new CurrentUser(
                1001L,
                "alice",
                2002L,
                "session-1",
                3,
                true,
                Set.of("system:notification:view", "system:notification:write")
        ));
        when(messageDeliveryLogMapper.countDeliveryLogs(any(DeliveryLogQuery.class))).thenReturn(0L);
        when(messageDeliveryLogMapper.listDeliveryLogs(any(DeliveryLogQuery.class))).thenReturn(List.of());

        messageAppService.listDeliveryLogs(currentUser, request);

        ArgumentCaptor<DeliveryLogQuery> queryCaptor = ArgumentCaptor.forClass(DeliveryLogQuery.class);
        verify(messageDeliveryLogMapper).countDeliveryLogs(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(queryCaptor.getValue().isManageDeliveryLogs()).isTrue();
    }

    @Test
    void listDeliveryLogs_shouldReturnEmptyPageForAnonymousUser() {
        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();

        MessageVO.DeliveryLogPageResponse response = messageAppService.listDeliveryLogs(null, request);

        assertThat(response.getPageNo()).isEqualTo(1L);
        assertThat(response.getPageSize()).isEqualTo(20L);
        assertThat(response.getTotal()).isZero();
        assertThat(response.getHasMore()).isFalse();
        assertThat(response.getTotalCapped()).isFalse();
        assertThat(response.getRecords()).isEmpty();
        verify(messageDeliveryLogMapper, never()).countDeliveryLogs(any(DeliveryLogQuery.class));
        verify(messageDeliveryLogMapper, never()).listDeliveryLogs(any(DeliveryLogQuery.class));
    }

    @Test
    void listDeliveryLogs_shouldNotMarkHasMoreWhenTotalUnderCountLimit() {
        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        when(messageDeliveryLogMapper.countDeliveryLogs(any(DeliveryLogQuery.class))).thenReturn(8L);
        when(messageDeliveryLogMapper.listDeliveryLogs(any(DeliveryLogQuery.class))).thenReturn(List.of());

        MessageVO.DeliveryLogPageResponse response = messageAppService.listDeliveryLogs(currentUser(), request);

        assertThat(response.getHasMore()).isFalse();
        assertThat(response.getTotalCapped()).isFalse();
        assertThat(response.getTotal()).isEqualTo(8L);
    }

    @Test
    void listDeliveryLogs_shouldCacheCountForRepeatedQueries() {
        var cacheStore = new java.util.HashMap<String, String>();
        when(cacheTemplate.get(anyString())).thenAnswer(invocation -> cacheStore.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            cacheStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(cacheTemplate).put(anyString(), anyString(), any());

        when(messageDeliveryLogMapper.countDeliveryLogs(any(DeliveryLogQuery.class))).thenReturn(8L);
        when(messageDeliveryLogMapper.listDeliveryLogs(any(DeliveryLogQuery.class))).thenReturn(List.of());

        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        messageAppService.listDeliveryLogs(currentUser(), request);
        messageAppService.listDeliveryLogs(currentUser(), request);

        verify(messageDeliveryLogMapper, times(1)).countDeliveryLogs(any(DeliveryLogQuery.class));
    }

    @Test
    void markAllRead_shouldUseRoleSnapshotForVisibility() {
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.countUnread(eq(1001L), eq("user-uuid-1001"), eq(List.of(3001L)), eq(100L))).thenReturn(0L);
        when(systemInternalApi.readModelVersion("message", "unread")).thenReturn(READ_MODEL_VERSION);

        messageAppService.markAllRead(readUser());

        verify(messageNoticeMapper).markAllRead(eq(1001L), eq("user-uuid-1001"), eq(List.of(3001L)), any(LocalDateTime.class));
        verify(systemInternalApi, times(1)).permissionRoleSnapshot(1001L, "user-uuid-1001");
        verify(systemInternalApi).bumpReadModelVersion("message", "unread", "message.unread");
    }

    @Test
    void markAllRead_shouldRejectUnauthenticatedUserBeforeDatabaseAccess() {
        assertThatThrownBy(() -> messageAppService.markAllRead(unauthenticatedUser()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(messageNoticeMapper, never()).markAllRead(anyLong(), any(), any(), any(LocalDateTime.class));
        verify(systemInternalApi, never()).permissionRoleSnapshot(any(), any());
    }

    @Test
    void markAllRead_shouldRejectMissingReadPermissionBeforeDatabaseAccess() {
        assertThatThrownBy(() -> messageAppService.markAllRead(currentUser()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(messageNoticeMapper, never()).markAllRead(anyLong(), any(), any(), any(LocalDateTime.class));
    }

    @Test
    void retractMessage_shouldBindOriginalPublishStatusInFinalUpdate() {
        MessageVO.NoticeVO original = notice(9001L, "notice");
        MessageVO.NoticeVO retracted = notice(9001L, "notice");
        retracted.setPublishStatus("RETRACTED");
        when(messageNoticeMapper.findNoticeById(9001L, 1001L, "user-uuid-1001"))
                .thenReturn(original, retracted);
        when(messageNoticeMapper.update(any(), any())).thenReturn(1);

        messageAppService.retractMessage(retractUser(), 9001L);

        ArgumentCaptor<UpdateWrapper<MessageNoticeEntity>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(messageNoticeMapper).update(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("id", "publish_status", "deleted");
    }

    @Test
    void retractMessage_shouldRejectMissingRetractPermissionBeforeUpdate() {
        assertThatThrownBy(() -> messageAppService.retractMessage(currentUser(), 9001L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(messageNoticeMapper, never()).update(any(), any());
    }

    @Test
    void hotPathMetrics_shouldRecordP95Timing() {
        MessageAppService service = new MessageAppService(
                messageNoticeMapper,
                messageDeliveryLogMapper,
                operationAuditService,
                messagePushService,
                smtpNotificationMailService,
                wechatOfficialAccountNotificationService,
                systemInternalApi,
                cacheTemplate,
                messageProperties,
                new SimpleMeterRegistry()
        );
        when(systemInternalApi.permissionRoleSnapshot(1001L, "user-uuid-1001")).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.listVisiblePublished(eq(1001L), eq("user-uuid-1001"), eq(List.of(3001L)), anyLong(), anyLong()))
                .thenReturn(List.of());

        service.listMessages(currentUser(), 1, 1);
        service.countUnread(currentUser());
        when(messageNoticeMapper.countUnread(eq(1001L), eq("user-uuid-1001"), eq(List.of(3001L)), eq(100L))).thenReturn(0L);
        when(messageNoticeMapper.markAllRead(eq(1001L), eq("user-uuid-1001"), eq(List.of(3001L)), any(LocalDateTime.class))).thenReturn(1);

        service.markAllRead(readUser());

        assertThat(service.listMessagesP95Millis()).isNotNegative();
        assertThat(service.unreadCountP95Millis()).isNotNegative();
        assertThat(service.readAllP95Millis()).isNotNegative();
    }

    @Test
    void snapshotMetrics_shouldExposeMessageHotPathCounters() {
        MessageAppService service = new MessageAppService(
                messageNoticeMapper,
                messageDeliveryLogMapper,
                operationAuditService,
                messagePushService,
                smtpNotificationMailService,
                wechatOfficialAccountNotificationService,
                systemInternalApi,
                cacheTemplate,
                messageProperties,
                new SimpleMeterRegistry()
        );

        MessageAppService.MetricsSnapshot snapshot = service.snapshotMetrics();

        assertThat(snapshot.listMessagesP95Millis()).isZero();
        assertThat(snapshot.unreadCountP95Millis()).isZero();
        assertThat(snapshot.readAllP95Millis()).isZero();
        assertThat(snapshot.archiveCappedCountQueryTotal()).isZero();
        assertThat(snapshot.deliveryLogCappedCountQueryTotal()).isZero();
        assertThat(snapshot.unreadCountCacheHits()).isZero();
        assertThat(snapshot.unreadCountCacheMisses()).isZero();
        assertThat(snapshot.unreadCountCacheHitRatio()).isZero();
        assertThat(snapshot.archiveCountCacheHits()).isZero();
        assertThat(snapshot.archiveCountCacheMisses()).isZero();
        assertThat(snapshot.archiveCountCacheHitRatio()).isZero();
        assertThat(snapshot.deliveryLogCountCacheHits()).isZero();
        assertThat(snapshot.deliveryLogCountCacheMisses()).isZero();
        assertThat(snapshot.deliveryLogCountCacheHitRatio()).isZero();
    }

    @Test
    void createMessage_shouldRequireSystemNotificationWriteForPlatformBroadcast() {
        MessageDTO.MessageCreateRequest request = new MessageDTO.MessageCreateRequest();
        request.setTitle("platform notice");
        request.setContent("content");
        request.setTargetScope("PLATFORM");
        request.setChannels(List.of("INBOX", "EMAIL"));

        CurrentUser currentUser = trusted(new CurrentUser(
                1001L,
                "alice",
                2002L,
                "session-1",
                3,
                true,
                Set.of("message:message:write")
        ));

        assertThatThrownBy(() -> messageAppService.createMessage(currentUser, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(messageNoticeMapper, never()).insert(any(MessageNoticeEntity.class));
        verify(systemInternalApi, never()).platformUserEmailRecipients();
    }

    @Test
    void createMessage_shouldRejectUnauthenticatedUserBeforeInsert() {
        MessageDTO.MessageCreateRequest request = new MessageDTO.MessageCreateRequest();
        request.setChannels(List.of("INBOX"));
        request.setTargetScope("USER");
        request.setTargetUserId(2001L);
        request.setTitle("direct");
        request.setContent("content");

        assertThatThrownBy(() -> messageAppService.createMessage(unauthenticatedUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(messageNoticeMapper, never()).insert(any(MessageNoticeEntity.class));
        verify(messageDeliveryLogMapper, never()).insert(any(MessageDeliveryLogEntity.class));
    }

    @Test
    void createMessage_shouldRejectMissingWritePermissionBeforeInsert() {
        MessageDTO.MessageCreateRequest request = new MessageDTO.MessageCreateRequest();
        request.setChannels(List.of("INBOX"));
        request.setTargetScope("USER");
        request.setTargetUserId(2001L);
        request.setTitle("direct");
        request.setContent("content");

        assertThatThrownBy(() -> messageAppService.createMessage(currentUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(messageNoticeMapper, never()).insert(any(MessageNoticeEntity.class));
        verify(messageDeliveryLogMapper, never()).insert(any(MessageDeliveryLogEntity.class));
    }

    @Test
    void createMessage_shouldRejectBlankUsernameBeforeInsert() {
        MessageDTO.MessageCreateRequest request = new MessageDTO.MessageCreateRequest();
        request.setChannels(List.of("INBOX"));
        request.setTargetScope("USER");
        request.setTargetUserId(2001L);
        request.setTitle("direct");
        request.setContent("content");

        assertThatThrownBy(() -> messageAppService.createMessage(blankUsernameUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(messageNoticeMapper, never()).insert(any(MessageNoticeEntity.class));
        verify(messageDeliveryLogMapper, never()).insert(any(MessageDeliveryLogEntity.class));
    }

    @Test
    void createMessage_shouldRejectNullRequestBeforeInsert() {
        assertThatThrownBy(() -> messageAppService.createMessage(writeUser(), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(messageNoticeMapper, never()).insert(any(MessageNoticeEntity.class));
        verify(messageDeliveryLogMapper, never()).insert(any(MessageDeliveryLogEntity.class));
        verify(messagePushService, never()).publishCreated(any());
    }

    @Test
    void createMessage_shouldRejectUnknownTargetScopeBeforeInsert() {
        MessageDTO.MessageCreateRequest request = new MessageDTO.MessageCreateRequest();
        request.setChannels(List.of("INBOX"));
        request.setTargetScope("TEAM");
        request.setTitle("direct");
        request.setContent("content");

        assertThatThrownBy(() -> messageAppService.createMessage(writeUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(messageNoticeMapper, never()).insert(any(MessageNoticeEntity.class));
        verify(messageDeliveryLogMapper, never()).insert(any(MessageDeliveryLogEntity.class));
        verify(messagePushService, never()).publishCreated(any());
    }

    @Test
    void createMessage_shouldRejectNonPositiveTargetUserIdBeforeInsert() {
        MessageDTO.MessageCreateRequest request = new MessageDTO.MessageCreateRequest();
        request.setChannels(List.of("INBOX", "EMAIL"));
        request.setTargetScope("USER");
        request.setTargetUserId(0L);
        request.setTitle("direct");
        request.setContent("content");

        assertThatThrownBy(() -> messageAppService.createMessage(writeUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(messageNoticeMapper, never()).insert(any(MessageNoticeEntity.class));
        verify(messageDeliveryLogMapper, never()).insert(any(MessageDeliveryLogEntity.class));
        verify(systemInternalApi, never()).userEmailRecipientsByIds(any());
    }

    @Test
    void createMessage_shouldRejectNonPositiveTargetRoleIdBeforeInsert() {
        MessageDTO.MessageCreateRequest request = new MessageDTO.MessageCreateRequest();
        request.setChannels(List.of("INBOX", "WECHAT_OFFICIAL"));
        request.setTargetScope("ROLE");
        request.setTargetRoleId(-1L);
        request.setTitle("role");
        request.setContent("content");

        assertThatThrownBy(() -> messageAppService.createMessage(writeUser(), request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(messageNoticeMapper, never()).insert(any(MessageNoticeEntity.class));
        verify(messageDeliveryLogMapper, never()).insert(any(MessageDeliveryLogEntity.class));
        verify(systemInternalApi, never()).userWechatRecipientsByRole(any());
    }

    @Test
    void createMessage_shouldMaskRecipientContactInDeliveryLog() {
        MessageDTO.MessageCreateRequest request = new MessageDTO.MessageCreateRequest();
        request.setTitle("direct email");
        request.setContent("content");
        request.setTargetScope("USER");
        request.setTargetUserId(2001L);
        request.setChannels(List.of("INBOX", "EMAIL"));
        when(smtpNotificationMailService.isConfigured()).thenReturn(true);
        doAnswer(invocation -> {
            MessageNoticeEntity entity = invocation.getArgument(0);
            entity.setId(9001L);
            return 1;
        }).when(messageNoticeMapper).insert(any(MessageNoticeEntity.class));
        when(messageNoticeMapper.findNoticeById(9001L, 1001L, "user-uuid-1001")).thenReturn(notice(9001L, "direct email"));
        when(systemInternalApi.findTargetUserUuidById(2001L)).thenReturn("user-uuid-2001");
        when(systemInternalApi.userEmailRecipientsByIds(List.of(2001L)))
                .thenReturn(List.of(new com.lumira.api.system.SystemUserEmailRecipientDTO(
                        2001L,
                        "user-uuid-2001",
                        "bob",
                        "bob.sensitive@example.com"
                )));

        messageAppService.createMessage(
                trusted(new CurrentUser(1001L, "alice", 2002L, "session-1", 3, true, Set.of("message:message:write"))),
                request
        );

        verify(smtpNotificationMailService).send("bob.sensitive@example.com", "direct email", "content");
        ArgumentCaptor<MessageNoticeEntity> noticeCaptor = ArgumentCaptor.forClass(MessageNoticeEntity.class);
        verify(messageNoticeMapper).insert(noticeCaptor.capture());
        assertThat(noticeCaptor.getValue().getTargetUserUuid()).isEqualTo("user-uuid-2001");
        ArgumentCaptor<MessageDeliveryLogEntity> logCaptor = ArgumentCaptor.forClass(MessageDeliveryLogEntity.class);
        verify(messageDeliveryLogMapper, times(2)).insert(logCaptor.capture());
        assertThat(logCaptor.getAllValues())
                .extracting(MessageDeliveryLogEntity::getTargetEmail)
                .contains("b***e@example.com");
    }

    @Test
    void createMessage_shouldRejectContactSnapshotWithoutUserUuidBeforeInsert() {
        MessageDTO.MessageCreateRequest request = new MessageDTO.MessageCreateRequest();
        request.setTitle("direct email");
        request.setContent("content");
        request.setTargetScope("USER");
        request.setTargetUserId(2001L);
        request.setChannels(List.of("INBOX", "EMAIL"));
        when(systemInternalApi.findTargetUserUuidById(2001L)).thenReturn(null);

        assertThatThrownBy(() -> messageAppService.createMessage(
                trusted(new CurrentUser(1001L, "alice", 2002L, "session-1", 3, true, Set.of("message:message:write"))),
                request
        )).isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(smtpNotificationMailService, never()).send(anyString(), anyString(), anyString());
        verify(messageNoticeMapper, never()).insert(any(MessageNoticeEntity.class));
        verify(messageDeliveryLogMapper, never()).insert(any(MessageDeliveryLogEntity.class));
        verify(systemInternalApi, never()).userEmailRecipientsByIds(List.of(2001L));
    }

    @Test
    void countUnread_shouldRejectDisabledTrustedUserBeforeDatabaseAccess() {
        CurrentUser currentUser = new CurrentUser(1001L, "alice", 2002L, "session-1", 3, true, Set.of("message:message:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("stale");
        when(systemInternalApi.findUserIdentityById(1001L)).thenReturn(
                new SystemUserSnapshotDTO(1001L, "user-uuid-1001", "alice", null, "DISABLED", null, null, null, null, null, null, null, null, null, null, null)
        );

        assertThatThrownBy(() -> messageAppService.countUnread(currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(messageNoticeMapper, never()).countUnread(anyLong(), anyString(), any(), anyLong());
    }

    private CurrentUser currentUser() {
        return trusted(new CurrentUser(1001L, "alice", 2002L, "session-1", 3, true, Set.of("message:message:view")));
    }

    private CurrentUser readUser() {
        return trusted(new CurrentUser(1001L, "alice", 2002L, "session-1", 3, true, Set.of("message:message:read")));
    }

    private CurrentUser writeUser() {
        return trusted(new CurrentUser(1001L, "alice", 2002L, "session-1", 3, true, Set.of("message:message:write")));
    }

    private CurrentUser retractUser() {
        return trusted(new CurrentUser(1001L, "alice", 2002L, "session-1", 3, true, Set.of("message:message:retract")));
    }

    private CurrentUser trusted(CurrentUser currentUser) {
        currentUser.setUserUuid("user-uuid-" + currentUser.getUserId());
        currentUser.setPermissionsVersion("stale");
        when(systemInternalApi.findUserIdentityById(currentUser.getUserId())).thenReturn(userSnapshot(currentUser.getUserId(), currentUser.getUsername(), "ENABLED"));
        when(systemInternalApi.permissionSnapshot(currentUser.getUserId(), currentUser.getUserUuid()))
                .thenReturn(permissionSnapshot(currentUser));
        return currentUser;
    }

    private CurrentUser unauthenticatedUser() {
        return new CurrentUser(1001L, "alice", 2002L, "session-1", 3, false, Set.of("*", "message:message:write"));
    }

    private CurrentUser blankUsernameUser() {
        return new CurrentUser(1001L, " ", 2002L, "session-1", 3, true, Set.of("*", "message:message:write"));
    }

    private CurrentUser missingSessionVersionUser() {
        return new CurrentUser(1001L, "alice", 2002L, "session-1", null, true, Set.of("*", "message:message:write"));
    }

    private PermissionSnapshotDTO permissionSnapshot(List<Long> roleIds) {
        return permissionSnapshot(roleIds, "v1");
    }

    private PermissionSnapshotDTO permissionSnapshot(List<Long> roleIds, String version) {
        return new PermissionSnapshotDTO(version, List.of("message:message:view"), roleIds, null, List.of(), List.of(), List.of(), "/");
    }

    private PermissionSnapshotDTO permissionSnapshot(CurrentUser currentUser) {
        List<String> permissions = currentUser.getPermissions() == null ? List.of() : List.copyOf(currentUser.getPermissions());
        List<Long> roleIds = currentUser.getRoleIds() == null ? List.of() : List.copyOf(currentUser.getRoleIds());
        List<Long> deptIds = currentUser.getDeptIds() == null ? List.of() : List.copyOf(currentUser.getDeptIds());
        List<Long> descendantDeptIds = currentUser.getDescendantDeptIds() == null ? List.of() : List.copyOf(currentUser.getDescendantDeptIds());
        return new PermissionSnapshotDTO("perm-v" + currentUser.getUserId(), permissions, roleIds, currentUser.getPrimaryDeptId(), deptIds, descendantDeptIds, List.of(), "/");
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private CurrentUser currentUserWithSnapshot() {
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion("v9");
        LinkedHashSet<Long> roleIds = new LinkedHashSet<>();
        roleIds.add(3001L);
        roleIds.add(3002L);
        currentUser.setRoleIds(roleIds);
        when(systemInternalApi.permissionSnapshot(1001L, "user-uuid-1001"))
                .thenReturn(new PermissionSnapshotDTO(
                        "v9",
                        List.of("message:message:view"),
                        List.of(3001L, 3002L),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        "/"
                ));
        return currentUser;
    }

    @SuppressWarnings("unchecked")
    private void clearMapField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            ((Map<Object, Object>) field.get(target)).clear();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to clear field: " + fieldName, exception);
        }
    }

    private MessageVO.NoticeVO notice(Long id, String title) {
        MessageVO.NoticeVO notice = new MessageVO.NoticeVO();
        notice.setId(id);
        notice.setMessageType("MESSAGE");
        notice.setTargetScope("PLATFORM");
        notice.setTitle(title);
        notice.setContent("content");
        notice.setSourceType("MANUAL");
        notice.setPublishStatus("PUBLISHED");
        notice.setPublishedAt(LocalDateTime.now());
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        notice.setReadFlag(Boolean.FALSE);
        return notice;
    }
}
