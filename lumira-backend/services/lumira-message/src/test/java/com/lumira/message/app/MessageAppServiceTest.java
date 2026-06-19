package com.lumira.message.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.message.config.MessageProperties;
import com.lumira.common.security.CurrentUser;
import com.lumira.message.dto.MessageDTO;
import com.lumira.message.infrastructure.redis.CacheTemplate;
import com.lumira.message.dto.MessageQueryModels.NoticeArchiveQuery;
import com.lumira.message.dto.MessageQueryModels.DeliveryLogQuery;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
        MessageVO.NoticeVO notice = notice(1001L, "欢迎公告");
        notice.setTargetScope("USER");
        notice.setTargetUserId(2001L);
        MessageVO.NoticeVO extra = notice(1002L, "更多公告");
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.listVisiblePublished(1001L, 1001L, List.of(3001L), 2L, 0L)).thenReturn(List.of(notice, extra));
        when(systemInternalApi.usersByIds(1001L, List.of(2001L))).thenReturn(List.of(
                new SystemUserSnapshotDTO(2001L, "bob", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        ));

        CurrentUser currentUser = currentUser();
        MessageVO.NoticePageResponse response = messageAppService.listMessages(currentUser, 1, 1);

        assertThat(response.getPageNo()).isEqualTo(1L);
        assertThat(response.getPageSize()).isEqualTo(1L);
        assertThat(response.getTotal()).isEqualTo(-1L);
        assertThat(response.getHasMore()).isTrue();
        assertThat(response.getTotalCapped()).isTrue();
        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getRecords().get(0).getTitle()).isEqualTo("欢迎公告");
        assertThat(response.getRecords().get(0).getTargetUserName()).isEqualTo("bob");
    }

    @Test
    void listMessages_shouldUseCurrentUserPermissionSnapshotWhenAligned() {
        MessageVO.NoticeVO notice = notice(1001L, "会话快照公告");
        notice.setTargetScope("TENANT");
        when(systemInternalApi.readModelVersion(1001L, "IAM", "permission-snapshot")).thenReturn(9L);
        when(messageNoticeMapper.listVisiblePublished(eq(1001L), eq(1001L), argThat(list ->
                        list != null && list.containsAll(List.of(3001L, 3002L)) && list.size() == 2
                ), eq(2L), eq(0L)))
                .thenReturn(List.of(notice));

        MessageVO.NoticePageResponse response = messageAppService.listMessages(currentUserWithSnapshot(), 1, 1);

        assertThat(response.getRecords()).hasSize(1);
        verify(systemInternalApi, never()).permissionSnapshot(anyLong(), anyLong());
    }

    @Test
    void listMessages_shouldFallbackToSystemPermissionSnapshotWhenVersionMisaligned() {
        MessageVO.NoticeVO notice = notice(1001L, "回源快照公告");
        notice.setTargetScope("TENANT");
        CurrentUser currentUser = currentUserWithSnapshot();
        currentUser.setPermissionsVersion("v10");
        when(systemInternalApi.readModelVersion(1001L, "IAM", "permission-snapshot")).thenReturn(11L);
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(List.of(7001L, 8001L), "v11"));
        when(messageNoticeMapper.listVisiblePublished(eq(1001L), eq(1001L), eq(List.of(7001L, 8001L)), eq(2L), eq(0L)))
                .thenReturn(List.of(notice));

        messageAppService.listMessages(currentUser, 1, 1);

        verify(systemInternalApi).permissionSnapshot(1001L, 1001L);
    }

    @Test
    void listMessages_shouldUseSessionPermissionSnapshotWhenIamReadModelUnavailable() {
        MessageVO.NoticeVO notice = notice(1001L, "会话快照公告");
        notice.setTargetScope("TENANT");

        CurrentUser currentUser = currentUserWithSnapshot();
        when(systemInternalApi.readModelVersion(1001L, "IAM", "permission-snapshot")).thenReturn(null);
        when(messageNoticeMapper.listVisiblePublished(
                eq(1001L),
                eq(1001L),
                argThat(list ->
                        list != null && list.containsAll(List.of(3001L, 3002L)) && list.size() == 2
                ),
                eq(2L),
                eq(0L)
        )).thenReturn(List.of(notice));

        MessageVO.NoticePageResponse response = messageAppService.listMessages(currentUser, 1, 1);

        assertThat(response.getRecords()).hasSize(1);
        verify(systemInternalApi, never()).permissionSnapshot(1001L, 1001L);
    }

    @Test
    void countUnread_shouldUseCurrentUserPermissionSnapshotWhenAligned() {
        CurrentUser currentUser = currentUserWithSnapshot();
        when(systemInternalApi.readModelVersion(1001L, "IAM", "permission-snapshot")).thenReturn(9L);
        when(systemInternalApi.readModelVersion(1001L, "message", "unread")).thenReturn(READ_MODEL_VERSION);
        when(messageNoticeMapper.countUnread(eq(1001L), eq(1001L), argThat(list ->
                list != null && list.containsAll(List.of(3001L, 3002L)) && list.size() == 2
        ), eq(100L))).thenReturn(7L);
        when(cacheTemplate.get("message:unread-count:1001:1001:v9:v10")).thenReturn(null);

        Long unreadCount = messageAppService.countUnread(currentUser);

        assertThat(unreadCount).isEqualTo(7L);
        verify(systemInternalApi, never()).permissionSnapshot(anyLong(), anyLong());
        verify(messageNoticeMapper).countUnread(eq(1001L), eq(1001L), argThat(list ->
                list != null && list.containsAll(List.of(3001L, 3002L)) && list.size() == 2
        ), eq(100L));
    }

    @Test
    void listMessages_shouldReuseRoleVisibilityCacheOnRepeatedCalls() {
        when(systemInternalApi.permissionSnapshot(1001L, 1001L))
                .thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.listVisiblePublished(eq(1001L), eq(1001L), eq(List.of(3001L)), anyLong(), anyLong()))
                .thenReturn(List.of());

        CurrentUser currentUser = currentUser();
        messageAppService.listMessages(currentUser, 1, 1);
        messageAppService.listMessages(currentUser, 1, 1);

        verify(systemInternalApi, times(1)).permissionSnapshot(1001L, 1001L);
        assertThat(messageAppService.roleVisibilityCacheHits()).isEqualTo(1L);
        assertThat(messageAppService.roleVisibilityCacheMisses()).isEqualTo(1L);
    }

    @Test
    void listMessages_shouldCacheTargetUserNameOnSecondListCall() {
        var cacheStore = new java.util.HashMap<String, String>();
        when(cacheTemplate.get(anyString())).thenAnswer(invocation -> cacheStore.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            cacheStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(cacheTemplate).put(anyString(), anyString(), any());

        MessageVO.NoticeVO notice = notice(1001L, "用户公告");
        notice.setTargetScope("USER");
        notice.setTargetUserId(2001L);
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(List.of()));
        when(messageNoticeMapper.listVisiblePublished(1001L, 1001L, List.of(), 2L, 0L))
                .thenReturn(List.of(notice))
                .thenReturn(List.of(notice));
        when(systemInternalApi.usersByIds(1001L, List.of(2001L)))
                .thenReturn(List.of(new SystemUserSnapshotDTO(2001L, "bob", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)));

        CurrentUser currentUser = currentUser();
        messageAppService.listMessages(currentUser, 1, 1);
        messageAppService.listMessages(currentUser, 1, 1);

        verify(systemInternalApi).usersByIds(eq(1001L), eq(List.of(2001L)));
        verify(systemInternalApi, times(1)).permissionSnapshot(1001L, 1001L);
    }

    @Test
    void listMessages_shouldCacheTargetRoleNameOnSecondListCall() {
        var cacheStore = new java.util.HashMap<String, String>();
        when(cacheTemplate.get(anyString())).thenAnswer(invocation -> cacheStore.get(invocation.getArgument(0)));
        org.mockito.Mockito.doAnswer(invocation -> {
            cacheStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(cacheTemplate).put(anyString(), anyString(), any());

        MessageVO.NoticeVO notice = notice(1001L, "角色公告");
        notice.setTargetScope("ROLE");
        notice.setTargetRoleId(3001L);
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.listVisiblePublished(1001L, 1001L, List.of(3001L), 2L, 0L))
                .thenReturn(List.of(notice));
        when(systemInternalApi.rolesByIds(1001L, List.of(3001L)))
                .thenReturn(List.of(new SystemRoleSnapshotDTO(3001L, "管理员", "管理员")));

        CurrentUser currentUser = currentUser();
        messageAppService.listMessages(currentUser, 1, 1);
        messageAppService.listMessages(currentUser, 1, 1);

        verify(systemInternalApi).rolesByIds(eq(1001L), eq(List.of(3001L)));
        verify(messageNoticeMapper, times(1))
                .listVisiblePublished(1001L, 1001L, List.of(3001L), 2L, 0L);
    }

    @Test
    void countUnread_shouldNormalizeNullCountToZero() {
        when(systemInternalApi.readModelVersion(1001L, "message", "unread")).thenReturn(READ_MODEL_VERSION);
        when(cacheTemplate.get("message:unread-count:1001:1001:v1:v10")).thenReturn(null);
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(Arrays.asList(3001L, 3001L, null)));
        when(messageNoticeMapper.countUnread(1001L, 1001L, List.of(3001L), 100L)).thenReturn(null);

        Long unreadCount = messageAppService.countUnread(currentUser());

        assertThat(unreadCount).isZero();
        verify(cacheTemplate).put(eq("message:unread-count:1001:1001:v1:v10"), eq("0"), any(Duration.class));
    }

    @Test
    void countUnread_shouldUseCachedValueWhenAvailable() {
        when(systemInternalApi.readModelVersion(1001L, "message", "unread")).thenReturn(READ_MODEL_VERSION);
        when(cacheTemplate.get("message:unread-count:1001:1001:v1:v10")).thenReturn("42");

        Long unreadCount = messageAppService.countUnread(currentUser());

        assertThat(unreadCount).isEqualTo(42L);
        verify(messageNoticeMapper, times(0)).countUnread(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void countUnread_shouldIgnoreInvalidCacheAndFallbackToDatabase() {
        when(systemInternalApi.readModelVersion(1001L, "message", "unread")).thenReturn(READ_MODEL_VERSION);
        when(cacheTemplate.get("message:unread-count:1001:1001:v1:v10")).thenReturn("invalid");
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(Arrays.asList(3001L)));
        when(messageNoticeMapper.countUnread(1001L, 1001L, List.of(3001L), 100L)).thenReturn(1L);

        Long unreadCount = messageAppService.countUnread(currentUser());

        assertThat(unreadCount).isEqualTo(1L);
        verify(messageNoticeMapper).countUnread(anyLong(), anyLong(), eq(List.of(3001L)), anyLong());
        verify(cacheTemplate).put(eq("message:unread-count:1001:1001:v1:v10"), eq("1"), any(Duration.class));
    }

    @Test
    void listArchive_shouldScopeRegularUsersToOwnedOrVisibleMessages() {
        MessageVO.NoticeVO roleNotice = notice(1003L, "角色公告");
        roleNotice.setTargetScope("ROLE");
        roleNotice.setTargetRoleId(3001L);
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.countArchive(any(NoticeArchiveQuery.class))).thenReturn(0L);
        when(messageNoticeMapper.listArchive(any(NoticeArchiveQuery.class))).thenReturn(List.of(roleNotice));
        when(systemInternalApi.rolesByIds(1001L, List.of(3001L))).thenReturn(List.of(
                new SystemRoleSnapshotDTO(3001L, "ADMIN", "管理员")
        ));

        MessageVO.NoticeArchivePageResponse response = messageAppService.listArchive(currentUser(), new MessageDTO.MessageArchiveQueryRequest());

        ArgumentCaptor<NoticeArchiveQuery> queryCaptor = ArgumentCaptor.forClass(NoticeArchiveQuery.class);
        verify(messageNoticeMapper).countArchive(queryCaptor.capture());
        assertThat(queryCaptor.getValue().isManageArchive()).isFalse();
        assertThat(queryCaptor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(queryCaptor.getValue().getRoleIds()).containsExactly(3001L);
        assertThat(queryCaptor.getValue().getPermissionSnapshotVersion()).isEqualTo("v1");
        assertThat(queryCaptor.getValue().getCountLimit()).isEqualTo(21L);
        assertThat(response.getRecords().get(0).getTargetRoleName()).isEqualTo("管理员");
    }

    @Test
    void listArchive_shouldExposeHasMoreWhenArchiveTotalIsCapped() {
        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        request.setPageNo(2L);
        request.setPageSize(10L);
        MessageVO.NoticeVO notice = notice(1001L, "归档页结果");
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(List.of(3001L)));
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
        MessageVO.NoticeVO notice = notice(1001L, "归档页结果");
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(List.of(3001L)));
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

        MessageVO.NoticeVO notice = notice(1001L, "归档页结果");
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(List.of(3001L)));
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
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.countUnread(eq(1001L), eq(1001L), eq(List.of(3001L)), eq(100L))).thenReturn(0L);
        when(systemInternalApi.readModelVersion(1001L, "message", "unread")).thenReturn(READ_MODEL_VERSION);

        messageAppService.markAllRead(currentUser());

        verify(messageNoticeMapper).markAllRead(eq(1001L), eq(1001L), eq(List.of(3001L)), any(LocalDateTime.class));
        verify(systemInternalApi, times(1)).permissionSnapshot(1001L, 1001L);
        verify(systemInternalApi).bumpReadModelVersion(1001L, "message", "unread", "message.unread");
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
        when(systemInternalApi.permissionSnapshot(1001L, 1001L)).thenReturn(permissionSnapshot(List.of(3001L)));
        when(messageNoticeMapper.listVisiblePublished(eq(1001L), eq(1001L), eq(List.of(3001L)), anyLong(), anyLong()))
                .thenReturn(List.of());

        service.listMessages(currentUser(), 1, 1);
        service.countUnread(currentUser());
        when(messageNoticeMapper.countUnread(eq(1001L), eq(1001L), eq(List.of(3001L)), eq(100L))).thenReturn(0L);
        when(messageNoticeMapper.markAllRead(eq(1001L), eq(1001L), eq(List.of(3001L)), any(LocalDateTime.class))).thenReturn(1);

        service.markAllRead(currentUser());

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

    private CurrentUser currentUser() {
        return new CurrentUser(1001L, "alice", 1001L, "session-1", 3, true, Set.of("message:message:view"));
    }

    private PermissionSnapshotDTO permissionSnapshot(List<Long> roleIds) {
        return permissionSnapshot(roleIds, "v1");
    }

    private PermissionSnapshotDTO permissionSnapshot(List<Long> roleIds, String version) {
        return new PermissionSnapshotDTO(version, List.of("message:message:view"), roleIds, null, List.of(), List.of(), List.of(), "/");
    }

    private CurrentUser currentUserWithSnapshot() {
        CurrentUser currentUser = currentUser();
        currentUser.setPermissionsVersion("v9");
        LinkedHashSet<Long> roleIds = new LinkedHashSet<>();
        roleIds.add(3001L);
        roleIds.add(3002L);
        currentUser.setRoleIds(roleIds);
        return currentUser;
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
