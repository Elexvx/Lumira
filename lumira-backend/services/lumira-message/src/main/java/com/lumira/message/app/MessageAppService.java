package com.lumira.message.app;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserEmailRecipientDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.SystemUserWechatRecipientDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.message.config.MessageProperties;
import com.lumira.message.dto.MessageDTO;
import com.lumira.message.dto.MessageQueryModels.DeliveryLogQuery;
import com.lumira.message.dto.MessageQueryModels.NoticeArchiveQuery;
import com.lumira.message.domain.model.MessageDomainModels.NoticeAggregate;
import com.lumira.message.entity.MessageDeliveryLogEntity;
import com.lumira.message.entity.MessageNoticeEntity;
import com.lumira.message.infrastructure.redis.CacheTemplate;
import com.lumira.message.mapper.MessageDeliveryLogMapper;
import com.lumira.message.mapper.MessageNoticeMapper;
import com.lumira.message.service.MessagePushService;
import com.lumira.message.service.SmtpNotificationMailService;
import com.lumira.message.service.WechatOfficialAccountNotificationService;
import com.lumira.message.vo.MessageVO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

@Service
public class MessageAppService {

    private static final String TYPE_MESSAGE = "MESSAGE";
    private static final String TARGET_SCOPE_PLATFORM = "PLATFORM";
    private static final String TARGET_SCOPE_USER = "USER";
    private static final String TARGET_SCOPE_ROLE = "ROLE";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_RETRACTED = "RETRACTED";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String CHANNEL_INBOX = "INBOX";
    private static final String CHANNEL_EMAIL = "EMAIL";
    private static final String CHANNEL_WECHAT_OFFICIAL = "WECHAT_OFFICIAL";
    private static final String DELIVERY_SUCCESS = "SUCCESS";
    private static final String DELIVERY_FAILED = "FAILED";
    private static final String DELIVERY_SKIPPED = "SKIPPED";
    private static final String MESSAGE_LIST_TIMER = "message.list";
    private static final String MESSAGE_UNREAD_COUNT_TIMER = "message.unread_count";
    private static final String MESSAGE_READ_ALL_TIMER = "message.read_all";
    private static final int MAX_SESSION_ROLE_IDS = 200;
    private static final long UNREAD_COUNT_CAP = 100L;
    private static final long ARCHIVE_TOTAL_COUNT_CAP = 1000L;
    private static final long DELIVERY_LOG_TOTAL_COUNT_CAP = 1000L;
    private static final String READ_MODEL_CONTEXT_MESSAGE = "message";
    private static final String READ_MODEL_SCOPE_MESSAGE_UNREAD = "unread";
    private static final String READ_MODEL_EVENT_MESSAGE_UNREAD = "message.unread";
    private static final String IAM_PERMISSION_READ_MODEL_CONTEXT = "IAM";
    private static final String IAM_PERMISSION_READ_MODEL_SCOPE = "permission-snapshot";
    private static final long READ_MODEL_VERSION_CACHE_TTL_MILLIS = 3000L;
    private static final String UNREAD_COUNT_CACHE_PREFIX = "message:unread-count";
    private static final String GLOBAL_VERSION_CACHE_KEY = "global";
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);
    private static final String NOTICE_TARGET_USER_NAME_CACHE_PREFIX = "message:notice-target:user-name";
    private static final String NOTICE_TARGET_ROLE_NAME_CACHE_PREFIX = "message:notice-target:role-name";
    private static final String ARCHIVE_COUNT_CACHE_PREFIX = "message:archive-count";
    private static final String DELIVERY_LOG_COUNT_CACHE_PREFIX = "message:delivery-log-count";
    private static final String CACHED_NAME_MISS_MARKER = "__lumira_cache_miss__";
    private static final Duration NOTICE_TARGET_NAME_CACHE_TTL = Duration.ofSeconds(60);
    private static final Duration ROLE_VISIBILITY_CACHE_TTL = Duration.ofSeconds(10);
    private static final Duration LOCAL_UNREAD_COUNT_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration LOCAL_MESSAGE_LIST_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration ARCHIVE_COUNT_CACHE_TTL = Duration.ofSeconds(5);
    private static final Duration DELIVERY_LOG_COUNT_CACHE_TTL = Duration.ofSeconds(5);

    private final MessageNoticeMapper messageNoticeMapper;
    private final MessageDeliveryLogMapper messageDeliveryLogMapper;
    private final OperationAuditService operationAuditService;
    private final MessagePushService messagePushService;
    private final SmtpNotificationMailService smtpNotificationMailService;
    private final WechatOfficialAccountNotificationService wechatOfficialAccountNotificationService;
    private final SystemInternalApi systemInternalApi;
    private final CacheTemplate cacheTemplate;
    private final MessageProperties messageProperties;
    private final Timer messageListTimer;
    private final Timer messageUnreadCountTimer;
    private final Timer messageReadAllTimer;
    private final LongAdder unreadCountCacheHits = new LongAdder();
    private final LongAdder unreadCountCacheMisses = new LongAdder();
    private final LongAdder readModelVersionCacheHits = new LongAdder();
    private final LongAdder readModelVersionCacheMisses = new LongAdder();
    private final Map<String, CachedRoleVisibility> roleVisibilityCache = new ConcurrentHashMap<>();
    private final Map<String, CachedReadModelVersion> readModelVersionCache = new ConcurrentHashMap<>();
    private final Map<String, CachedPermissionSnapshotVersion> permissionSnapshotVersionCache = new ConcurrentHashMap<>();
    private final Map<String, CachedLong> localUnreadCountCache = new ConcurrentHashMap<>();
    private final Map<String, CachedNoticePage> localMessageListCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<RoleVisibility>> roleVisibilityLoadInFlight = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Long>> permissionSnapshotVersionLoadInFlight = new ConcurrentHashMap<>();
    private final LongAdder archiveTotalCappedQueryCount = new LongAdder();
    private final LongAdder deliveryLogCappedQueryCount = new LongAdder();
    private final LongAdder archiveCountCacheHits = new LongAdder();
    private final LongAdder archiveCountCacheMisses = new LongAdder();
    private final LongAdder deliveryLogCountCacheHits = new LongAdder();
    private final LongAdder deliveryLogCountCacheMisses = new LongAdder();
    private final AtomicLong roleVisibilityCacheHits = new AtomicLong();
    private final AtomicLong roleVisibilityCacheMisses = new AtomicLong();

    public MessageAppService(
            MessageNoticeMapper messageNoticeMapper,
            MessageDeliveryLogMapper messageDeliveryLogMapper,
            OperationAuditService operationAuditService,
            MessagePushService messagePushService,
            SmtpNotificationMailService smtpNotificationMailService,
            WechatOfficialAccountNotificationService wechatOfficialAccountNotificationService,
            SystemInternalApi systemInternalApi,
            CacheTemplate cacheTemplate,
            MessageProperties messageProperties
    ) {
        this(
                messageNoticeMapper,
                messageDeliveryLogMapper,
                operationAuditService,
                messagePushService,
                smtpNotificationMailService,
                wechatOfficialAccountNotificationService,
                systemInternalApi,
                cacheTemplate,
                messageProperties,
                (MeterRegistry) null
        );
    }

    @Autowired
    public MessageAppService(
            MessageNoticeMapper messageNoticeMapper,
            MessageDeliveryLogMapper messageDeliveryLogMapper,
            OperationAuditService operationAuditService,
            MessagePushService messagePushService,
            SmtpNotificationMailService smtpNotificationMailService,
            WechatOfficialAccountNotificationService wechatOfficialAccountNotificationService,
            SystemInternalApi systemInternalApi,
            CacheTemplate cacheTemplate,
            MessageProperties messageProperties,
            ObjectProvider<MeterRegistry> meterRegistry
    ) {
        this(
                messageNoticeMapper,
                messageDeliveryLogMapper,
                operationAuditService,
                messagePushService,
                smtpNotificationMailService,
                wechatOfficialAccountNotificationService,
                systemInternalApi,
                cacheTemplate,
                messageProperties,
                meterRegistry.getIfAvailable()
        );
    }

    MessageAppService(
            MessageNoticeMapper messageNoticeMapper,
            MessageDeliveryLogMapper messageDeliveryLogMapper,
            OperationAuditService operationAuditService,
            MessagePushService messagePushService,
            SmtpNotificationMailService smtpNotificationMailService,
            WechatOfficialAccountNotificationService wechatOfficialAccountNotificationService,
            SystemInternalApi systemInternalApi,
            CacheTemplate cacheTemplate,
            MessageProperties messageProperties,
            MeterRegistry meterRegistry
    ) {
        this.messageNoticeMapper = messageNoticeMapper;
        this.messageDeliveryLogMapper = messageDeliveryLogMapper;
        this.operationAuditService = operationAuditService;
        this.messagePushService = messagePushService;
        this.smtpNotificationMailService = smtpNotificationMailService;
        this.wechatOfficialAccountNotificationService = wechatOfficialAccountNotificationService;
        this.systemInternalApi = systemInternalApi;
        this.cacheTemplate = cacheTemplate;
        this.messageProperties = messageProperties;
        this.messageListTimer = createTimerIfAvailable(meterRegistry, MESSAGE_LIST_TIMER);
        this.messageUnreadCountTimer = createTimerIfAvailable(meterRegistry, MESSAGE_UNREAD_COUNT_TIMER);
        this.messageReadAllTimer = createTimerIfAvailable(meterRegistry, MESSAGE_READ_ALL_TIMER);
    }

    public MessageVO.NoticePageResponse listMessages(CurrentUser currentUser, long pageNo, long pageSize) {
        long startedNanos = System.nanoTime();
        try {
            if (AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
                requireAnyPermission(currentUser, "message:message:view", "system:notification:view");
            }
            return listNotices(currentUser, pageNo, pageSize);
        } finally {
            recordDuration(messageListTimer, startedNanos);
        }
    }

    public MessageVO.NoticeArchivePageResponse listArchive(CurrentUser currentUser, MessageDTO.MessageArchiveQueryRequest request) {
        long normalizedPageNo = Math.max(request.getPageNo() == null ? 1L : request.getPageNo(), 1L);
        long normalizedPageSize = Math.max(1L, Math.min(request.getPageSize() == null ? 20L : request.getPageSize(), 100L));
        if (!isAuthenticatedUser(currentUser)) {
            return emptyArchivePage(normalizedPageNo, normalizedPageSize);
        }
        requireAnyPermission(currentUser, "message:message:view", "system:notification:view");
        long offset = (normalizedPageNo - 1) * normalizedPageSize;
        RoleVisibility roleVisibility = visibleRoleVisibility(currentUser);
        NoticeArchiveQuery query = buildNoticeArchiveQuery(currentUser, request, roleVisibility, normalizedPageSize, offset);
        Long total = readCachedArchiveTotal(query);
        if (total == null) {
            total = messageNoticeMapper.countArchive(query);
            cacheArchiveTotal(query, total);
        }
        long normalizedTotal = normalizeArchiveTotal(total, query);
        boolean totalCapped = isArchiveTotalCapped(total, query);
        if (totalCapped) {
            archiveTotalCappedQueryCount.increment();
        }

        MessageVO.NoticeArchivePageResponse response = new MessageVO.NoticeArchivePageResponse();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(normalizedTotal);
        response.setHasMore(totalCapped);
        response.setTotalCapped(totalCapped);
        List<MessageVO.NoticeVO> records = messageNoticeMapper.listArchive(query);
        enrichNoticeTargets(records);
        response.setRecords(records);
        return response;
    }

    private MessageVO.NoticeArchivePageResponse emptyArchivePage(long pageNo, long pageSize) {
        MessageVO.NoticeArchivePageResponse response = new MessageVO.NoticeArchivePageResponse();
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setTotal(0);
        response.setHasMore(false);
        response.setTotalCapped(Boolean.FALSE);
        response.setRecords(List.of());
        return response;
    }

    private boolean isArchiveTotalCapped(Long total, NoticeArchiveQuery query) {
        if (query == null) {
            return false;
        }
        long queryLimit = query.getCountLimit();
        if (queryLimit <= 0L) {
            return false;
        }
        return total != null && total >= queryLimit;
    }

    private long normalizeArchiveTotal(Long total, NoticeArchiveQuery query) {
        if (total == null || total <= 0L) {
            return 0L;
        }
        if (query == null || query.getCountLimit() <= 0L) {
            return total;
        }
        return Math.min(total, query.getCountLimit());
    }

    private boolean canManageArchive(CurrentUser currentUser) {
        if (!isAuthenticatedUser(currentUser)) {
            return false;
        }
        Set<String> permissions = trustedPermissions(currentUser);
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return permissions.contains("*")
                || permissions.contains("message:message:write")
                || permissions.contains("message:message:retract")
                || permissions.contains("system:notification:write");
    }

    public Long countUnread(CurrentUser currentUser) {
        long startedNanos = System.nanoTime();
        try {
            if (!isAuthenticatedUser(currentUser)) {
                return 0L;
            }
            requireAnyPermission(currentUser, "message:message:view", "system:notification:view");
            UnreadContext unreadContext = resolveUnreadContext(currentUser);
            RoleVisibility roleVisibility = unreadContext.roleVisibility();
            long readModelVersion = unreadContext.readModelVersion();
            Long actorUserId = requireAuthenticatedUserId(currentUser);
            String actorUserUuid = currentUser.getUserUuid();
            Long cached = readCachedUnreadCount(actorUserId, actorUserUuid, roleVisibility.version(), readModelVersion);
            if (cached != null) {
                return cached;
            }
            Long count = countUnreadFromDb(actorUserId, actorUserUuid, roleVisibility.roleIds(), roleVisibility.version(), readModelVersion);
            return count == null ? 0L : count;
        } finally {
            recordDuration(messageUnreadCountTimer, startedNanos);
        }
    }

    @Transactional
    public MessageVO.NoticeVO createMessage(CurrentUser currentUser, MessageDTO.MessageCreateRequest request) {
        requireAnyPermission(currentUser, "message:message:write", "system:notification:write");
        Long actorUserId = requireAuthenticatedUserId(currentUser);
        String actorUserUuid = currentUser.getUserUuid();
        requireMessageCreateRequest(request);
        Set<String> channels = normalizeChannels(request.getChannels());
        if (channels.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请至少选择一个通知渠道");
        }
        request.setTargetScope(validateTarget(request.getTargetScope(), request.getTargetUserId(), request.getTargetRoleId()));
        requirePlatformBroadcastPermission(currentUser, request.getTargetScope());
        MessageVO.NoticeVO notice = null;
        if (channels.contains(CHANNEL_INBOX)) {
            notice = insertInboxNotice(
                    actorUserId,
                    actorUserUuid,
                    request.getTargetScope(),
                    request.getTargetUserId(),
                    request.getTargetRoleId(),
                    request.getTitle(),
                    request.getContent()
            );
            insertDeliveryLog(notice.getId(), CHANNEL_INBOX, request.getTargetScope(), null, null, null, null, request.getTitle(), request.getContent(), DELIVERY_SUCCESS, null, actorUserId, actorUserUuid);
            messagePushService.publishCreated(notice);
        }
        if (channels.contains(CHANNEL_EMAIL)) {
            sendEmailNotifications(actorUserId, actorUserUuid, request, notice == null ? null : notice.getId());
        }
        if (channels.contains(CHANNEL_WECHAT_OFFICIAL)) {
            sendWechatOfficialNotifications(actorUserId, actorUserUuid, request, notice == null ? null : notice.getId());
        }
        operationAuditService.log(
                actorUserId,
                currentUser.getUserUuid(),
                trustedUsername(currentUser),
                "message",
                "send-message",
                "CREATE",
                "SUCCESS",
                "发送通知: " + request.getTitle()
        );
        if (notice != null) {
            bumpUnreadReadModelVersion();
        }
        return notice;
    }

    private void requirePlatformBroadcastPermission(CurrentUser currentUser, String targetScope) {
        if (!TARGET_SCOPE_PLATFORM.equals(targetScope)) {
            return;
        }
        if (hasPermission(currentUser, "system:notification:write")) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "平台级通知需要系统通知写入权限");
    }

    private void requireMessageCreateRequest(MessageDTO.MessageCreateRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "message request is required");
        }
    }

    private String validateTarget(String targetScope, Long targetUserId, Long targetRoleId) {
        String normalizedTargetScope = normalizeTargetScope(targetScope);
        if (!StringUtils.hasText(normalizedTargetScope)
                || (!TARGET_SCOPE_PLATFORM.equals(normalizedTargetScope)
                && !TARGET_SCOPE_USER.equals(normalizedTargetScope)
                && !TARGET_SCOPE_ROLE.equals(normalizedTargetScope))) {
            throw new BizException(ErrorCode.BAD_REQUEST, "targetScope is invalid");
        }
        if (TARGET_SCOPE_USER.equals(normalizedTargetScope) && (targetUserId == null || targetUserId <= 0)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "targetUserId must be positive");
        }
        if (TARGET_SCOPE_ROLE.equals(normalizedTargetScope) && (targetRoleId == null || targetRoleId <= 0)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "targetRoleId must be positive");
        }
        return normalizedTargetScope;
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        if (!isAuthenticatedUser(currentUser)) {
            return false;
        }
        Set<String> permissions = trustedPermissions(currentUser);
        return permissions.contains("*") || permissions.contains(permissionKey);
    }

    public MessageVO.DeliveryLogPageResponse listDeliveryLogs(CurrentUser currentUser, MessageDTO.MessageArchiveQueryRequest request) {
        long normalizedPageNo = Math.max(request.getPageNo() == null ? 1L : request.getPageNo(), 1L);
        long normalizedPageSize = Math.max(1L, Math.min(request.getPageSize() == null ? 20L : request.getPageSize(), 100L));
        if (!isAuthenticatedUser(currentUser)) {
            return emptyDeliveryLogPage(normalizedPageNo, normalizedPageSize);
        }
        requireAnyPermission(currentUser, "message:message:view", "system:notification:view");
        long offset = (normalizedPageNo - 1) * normalizedPageSize;
        DeliveryLogQuery query = buildDeliveryLogQuery(currentUser, request, normalizedPageSize, offset);
        Long total = readCachedDeliveryLogTotal(query);
        if (total == null) {
            total = messageDeliveryLogMapper.countDeliveryLogs(query);
            cacheDeliveryLogTotal(query, total);
        }
        long normalizedTotal = normalizeDeliveryLogTotal(total, query);
        boolean totalCapped = isDeliveryLogTotalCapped(total, query);
        if (totalCapped) {
            deliveryLogCappedQueryCount.increment();
        }

        MessageVO.DeliveryLogPageResponse response = new MessageVO.DeliveryLogPageResponse();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(normalizedTotal);
        response.setHasMore(totalCapped);
        response.setTotalCapped(totalCapped);
        response.setRecords(messageDeliveryLogMapper.listDeliveryLogs(query));
        return response;
    }

    private MessageVO.DeliveryLogPageResponse emptyDeliveryLogPage(long pageNo, long pageSize) {
        MessageVO.DeliveryLogPageResponse response = new MessageVO.DeliveryLogPageResponse();
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setTotal(0);
        response.setHasMore(false);
        response.setTotalCapped(Boolean.FALSE);
        response.setRecords(List.of());
        return response;
    }

    @Transactional
    public MessageVO.NoticeVO retractMessage(CurrentUser currentUser, Long noticeId) {
        requireAnyPermission(currentUser, "message:message:retract", "system:notification:write");
        Long actorUserId = requireAuthenticatedUserId(currentUser);
        MessageVO.NoticeVO notice = retractNotice(currentUser, noticeId);
        messagePushService.publishRetracted(notice);
        operationAuditService.log(
                actorUserId,
                currentUser.getUserUuid(),
                trustedUsername(currentUser),
                "message",
                "retract-message",
                "RETRACT",
                "SUCCESS",
                "撤回站内信: " + notice.getTitle()
        );
        bumpUnreadReadModelVersion();
        return notice;
    }

    @Transactional
    public MessageVO.NoticeVO markMessageRead(CurrentUser currentUser, Long noticeId) {
        requireAnyPermission(currentUser, "message:message:read", "system:notification:view");
        Long actorUserId = requireAuthenticatedUserId(currentUser);
        UnreadContext unreadContext = resolveUnreadContext(currentUser);
        RoleVisibility roleVisibility = unreadContext.roleVisibility();
        MessageVO.NoticeVO notice = markRead(currentUser, noticeId, roleVisibility.roleIds());
        long readModelVersion = unreadContext.readModelVersion();
        Long unreadCount = countUnreadFromDb(actorUserId, currentUser.getUserUuid(), roleVisibility.roleIds(), roleVisibility.version(), readModelVersion);
        messagePushService.publishRead(actorUserId, currentUser.getUserUuid(), notice, unreadCount.intValue());
        bumpUnreadReadModelVersion();
        return notice;
    }

    @Transactional
    public MessageVO.UnreadCountVO markAllRead(CurrentUser currentUser) {
        requireAnyPermission(currentUser, "message:message:read", "system:notification:view");
        Long userId = requireAuthenticatedUserId(currentUser);
        long startedNanos = System.nanoTime();
        try {
            LocalDateTime now = LocalDateTime.now();
            UnreadContext unreadContext = resolveUnreadContext(currentUser);
            RoleVisibility roleVisibility = unreadContext.roleVisibility();
            List<Long> roleIds = roleVisibility.roleIds();
            messageNoticeMapper.markAllRead(userId, currentUser.getUserUuid(), roleIds, now);

            long readModelVersion = unreadContext.readModelVersion();
            Long unreadCount = countUnreadFromDb(userId, currentUser.getUserUuid(), roleIds, roleVisibility.version(), readModelVersion);
            messagePushService.publishUnreadCount(
                    userId,
                    currentUser.getUserUuid(),
                    unreadCount.intValue()
            );
            bumpUnreadReadModelVersion();

            MessageVO.UnreadCountVO unreadCountVO = new MessageVO.UnreadCountVO();
            unreadCountVO.setUnreadCount(unreadCount);
            return unreadCountVO;
        } finally {
            recordDuration(messageReadAllTimer, startedNanos);
        }
    }

    private MessageVO.NoticePageResponse listNotices(CurrentUser currentUser, long pageNo, long pageSize) {
        long normalizedPageNo = Math.max(pageNo, 1L);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, 100L));
        if (!isAuthenticatedUser(currentUser)) {
            return emptyNoticePage(normalizedPageNo, normalizedPageSize);
        }
        Long userId = requireAuthenticatedUserId(currentUser);
        long offset = (normalizedPageNo - 1) * normalizedPageSize;
        RoleVisibility roleVisibility = visibleRoleVisibility(currentUser);
        List<Long> roleIds = roleVisibility.roleIds();
        long readModelVersion = readModelVersion();
        String localCacheKey = buildMessageListCacheKey(userId, currentUser.getUserUuid(), roleVisibility.version(), readModelVersion, roleIds, normalizedPageNo, normalizedPageSize);
        CachedNoticePage cached = localMessageListCache.get(localCacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expireAt().isAfter(now)) {
            return cached.response();
        }
        if (cached != null) {
            localMessageListCache.remove(localCacheKey);
        }

        List<MessageVO.NoticeVO> records = messageNoticeMapper.listVisiblePublished(userId, currentUser.getUserUuid(), roleIds, normalizedPageSize + 1, offset);
        boolean hasMore = records.size() > normalizedPageSize;
        if (hasMore) {
            records = new ArrayList<>(records.subList(0, (int) normalizedPageSize));
        }
        enrichNoticeTargets(records);

        MessageVO.NoticePageResponse response = new MessageVO.NoticePageResponse();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(-1);
        response.setHasMore(hasMore);
        response.setTotalCapped(Boolean.TRUE);
        response.setRecords(records);
        localMessageListCache.put(localCacheKey, new CachedNoticePage(response, now.plus(LOCAL_MESSAGE_LIST_CACHE_TTL)));
        return response;
    }

    private MessageVO.NoticePageResponse emptyNoticePage(long pageNo, long pageSize) {
        MessageVO.NoticePageResponse response = new MessageVO.NoticePageResponse();
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setTotal(0);
        response.setHasMore(false);
        response.setTotalCapped(Boolean.FALSE);
        response.setRecords(List.of());
        return response;
    }

    private MessageVO.NoticeVO insertInboxNotice(
            Long operatorId,
            String operatorUserUuid,
            String targetScope,
            Long targetUserId,
            Long targetRoleId,
            String title,
            String content
    ) {
        String normalizedTargetScope = validateTarget(targetScope, targetUserId, targetRoleId);
        if (!StringUtils.hasText(targetScope)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "targetScope不能为空");
        }
        if (TARGET_SCOPE_USER.equals(targetScope) && targetUserId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "targetUserId不能为空");
        }
        if (TARGET_SCOPE_ROLE.equals(targetScope) && targetRoleId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "targetRoleId不能为空");
        }
        return insertNotice(
                operatorId,
                operatorUserUuid,
                normalizedTargetScope,
                targetUserId,
                targetRoleId,
                title,
                content
        );
    }

    private MessageVO.NoticeVO insertNotice(
            Long operatorId,
            String operatorUserUuid,
            String targetScope,
            Long targetUserId,
            Long targetRoleId,
            String title,
            String content
    ) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "标题和内容不能为空");
        }

        String targetUserUuid = TARGET_SCOPE_USER.equals(targetScope) ? requireTargetUserUuid(targetUserId) : null;

        MessageNoticeEntity entity = new MessageNoticeEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setNoticeType(TYPE_MESSAGE);
        entity.setTargetScope(targetScope);
        entity.setTargetUserId(targetUserId);
        entity.setTargetUserUuid(targetUserUuid);
        entity.setTargetRoleId(targetRoleId);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setSourceType(SOURCE_MANUAL);
        entity.setPublishStatus(STATUS_PUBLISHED);
        entity.setPublishedAt(now);
        entity.setCreatedBy(operatorId);
        entity.setCreatedByUuid(operatorUserUuid);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedByUuid(operatorUserUuid);
        entity.setDeleted(0);
        int updated = messageNoticeMapper.insert(entity);
        if (updated <= 0 || entity.getId() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息写入失败");
        }
        Long noticeId = entity.getId();
        MessageVO.NoticeVO notice = findNoticeById(noticeId, operatorId, operatorUserUuid);
        if (notice == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息写入后读取失败");
        }
        return notice;
    }

    private String requireTargetUserUuid(Long targetUserId) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "targetUserId must be positive");
        }
        String userUuid = systemInternalApi.findTargetUserUuidById(targetUserId);
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.NOT_FOUND, "Target user not found");
        }
        return userUuid.trim();
    }

    private MessageVO.NoticeVO retractNotice(CurrentUser currentUser, Long noticeId) {
        Long actorUserId = requireAuthenticatedUserId(currentUser);
        String actorUserUuid = currentUser.getUserUuid();
        if (noticeId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "通知ID不能为空");
        }
        MessageVO.NoticeVO notice = findNoticeById(noticeId, actorUserId, actorUserUuid);
        if (notice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或无权访问");
        }
        NoticeAggregate noticeAggregate = new NoticeAggregate(noticeId, notice.getPublishStatus());
        noticeAggregate.retract();
        int updated = messageNoticeMapper.update(null, new UpdateWrapper<MessageNoticeEntity>()
                .set("publish_status", STATUS_RETRACTED)
                .set("updated_by", actorUserId)
                .set("updated_by_uuid", actorUserUuid)
                .set("updated_at", LocalDateTime.now())
                .eq("id", noticeId)
                .eq("publish_status", notice.getPublishStatus())
                .eq("deleted", 0));
        if (updated <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或无权访问");
        }
        MessageVO.NoticeVO retractedNotice = findNoticeById(noticeId, actorUserId, actorUserUuid);
        if (retractedNotice == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "通知撤回后读取失败");
        }
        return retractedNotice;
    }

    private MessageVO.NoticeVO markRead(CurrentUser currentUser, Long noticeId, List<Long> roleIds) {
        Long actorUserId = requireAuthenticatedUserId(currentUser);
        MessageVO.NoticeVO notice = findVisibleNoticeById(noticeId, actorUserId, currentUser.getUserUuid(), roleIds);
        if (notice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或无权访问");
        }
        NoticeAggregate noticeAggregate = new NoticeAggregate(noticeId, notice.getPublishStatus());
        noticeAggregate.markRead(actorUserId, currentUser.getUserUuid());

        LocalDateTime now = LocalDateTime.now();
        int updated = messageNoticeMapper.upsertRead(noticeId, actorUserId, currentUser.getUserUuid(), roleIds, now);
        if (updated <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "閫氱煡涓嶅瓨鍦ㄦ垨鏃犳潈璁块棶");
        }
        notice.setReadFlag(Boolean.TRUE);
        notice.setReadAt(now);
        return notice;
    }

    private MessageVO.NoticeVO findVisibleNoticeById(Long noticeId, Long userId, String userUuid, List<Long> roleIds) {
        MessageVO.NoticeVO notice = messageNoticeMapper.findVisibleNoticeById(noticeId, userId, userUuid, roleIds);
        enrichNoticeTarget(notice);
        return notice;
    }

    private Long readCachedUnreadCount(Long userId, String userUuid, String version, long readModelVersion) {
        String cacheKey = buildUnreadCountCacheKey(userId, userUuid, version, readModelVersion);
        Long localCached = readLocalUnreadCount(cacheKey);
        if (localCached != null) {
            unreadCountCacheHits.increment();
            return localCached;
        }
        return readCachedCount(cacheKey, unreadCountCacheHits, unreadCountCacheMisses);
    }

    private Long readCachedArchiveTotal(NoticeArchiveQuery query) {
        return readCachedCount(buildArchiveCountCacheKey(query), archiveCountCacheHits, archiveCountCacheMisses);
    }

    private Long readCachedDeliveryLogTotal(DeliveryLogQuery query) {
        return readCachedCount(buildDeliveryLogCountCacheKey(query), deliveryLogCountCacheHits, deliveryLogCountCacheMisses);
    }

    private Long countUnreadFromDb(Long userId, String userUuid, List<Long> roleIds, String version, long readModelVersion) {
        Long count = messageNoticeMapper.countUnread(userId, userUuid, roleIds, UNREAD_COUNT_CAP);
        Long normalizedCount = normalizeUnreadCount(count);
        if (shouldCacheUnreadCount()) {
            cacheLocalUnreadCount(buildUnreadCountCacheKey(userId, userUuid, version, readModelVersion), normalizedCount);
            cacheTemplate.put(
                    buildUnreadCountCacheKey(userId, userUuid, version, readModelVersion),
                    String.valueOf(normalizedCount),
                    Duration.ofSeconds(messageProperties.getUnreadCountCacheTtlSeconds())
            );
        }
        return normalizedCount;
    }

    private void cacheArchiveTotal(NoticeArchiveQuery query, Long total) {
        cacheCount(buildArchiveCountCacheKey(query), total, ARCHIVE_COUNT_CACHE_TTL);
    }

    private void cacheDeliveryLogTotal(DeliveryLogQuery query, Long total) {
        cacheCount(buildDeliveryLogCountCacheKey(query), total, DELIVERY_LOG_COUNT_CACHE_TTL);
    }

    private Long readCachedCount(String cacheKey, LongAdder hitsCounter, LongAdder missesCounter) {
        if (!StringUtils.hasText(cacheKey)) {
            missesCounter.increment();
            return null;
        }
        String cached = cacheTemplate.get(cacheKey);
        if (!StringUtils.hasText(cached)) {
            missesCounter.increment();
            return null;
        }
        try {
            hitsCounter.increment();
            Long normalized = normalizeUnreadCount(Long.parseLong(cached.trim()));
            if (cacheKey.startsWith(UNREAD_COUNT_CACHE_PREFIX)) {
                cacheLocalUnreadCount(cacheKey, normalized);
            }
            return normalized;
        } catch (NumberFormatException exception) {
            missesCounter.increment();
            cacheTemplate.remove(cacheKey);
            return null;
        }
    }

    private void cacheCount(String cacheKey, Long total, Duration ttl) {
        if (!StringUtils.hasText(cacheKey) || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        cacheTemplate.put(cacheKey, String.valueOf(normalizeUnreadCount(total)), ttl);
    }

    public long unreadCountCacheHits() {
        return unreadCountCacheHits.sum();
    }

    public long unreadCountCacheMisses() {
        return unreadCountCacheMisses.sum();
    }

    public double unreadCountCacheHitRatio() {
        long hits = unreadCountCacheHits.sum();
        long total = hits + unreadCountCacheMisses.sum();
        return total <= 0 ? 0D : (double) hits / total;
    }

    public double archiveCountCacheHitRatio() {
        long hits = archiveCountCacheHits.sum();
        long total = hits + archiveCountCacheMisses.sum();
        return total <= 0 ? 0D : (double) hits / total;
    }

    public long archiveCountCacheHits() {
        return archiveCountCacheHits.sum();
    }

    public long archiveCountCacheMisses() {
        return archiveCountCacheMisses.sum();
    }

    public double deliveryLogCountCacheHitRatio() {
        long hits = deliveryLogCountCacheHits.sum();
        long total = hits + deliveryLogCountCacheMisses.sum();
        return total <= 0 ? 0D : (double) hits / total;
    }

    public long deliveryLogCountCacheHits() {
        return deliveryLogCountCacheHits.sum();
    }

    public long deliveryLogCountCacheMisses() {
        return deliveryLogCountCacheMisses.sum();
    }

    public long readModelVersionCacheHits() {
        return readModelVersionCacheHits.sum();
    }

    public long readModelVersionCacheMisses() {
        return readModelVersionCacheMisses.sum();
    }

    public double readModelVersionCacheHitRatio() {
        long hits = readModelVersionCacheHits.sum();
        long total = hits + readModelVersionCacheMisses.sum();
        return total <= 0 ? 0D : (double) hits / total;
    }

    public double listMessagesP95Millis() {
        return timerP95Millis(messageListTimer);
    }

    public double unreadCountP95Millis() {
        return timerP95Millis(messageUnreadCountTimer);
    }

    public double readAllP95Millis() {
        return timerP95Millis(messageReadAllTimer);
    }

    public MetricsSnapshot snapshotMetrics() {
        return new MetricsSnapshot(
                listMessagesP95Millis(),
                unreadCountP95Millis(),
                readAllP95Millis(),
                archiveCappedCountQueryTotal(),
                deliveryLogCappedCountQueryTotal(),
                unreadCountCacheHits(),
                unreadCountCacheMisses(),
                unreadCountCacheHitRatio(),
                archiveCountCacheHits(),
                archiveCountCacheMisses(),
                archiveCountCacheHitRatio(),
                deliveryLogCountCacheHits(),
                deliveryLogCountCacheMisses(),
                deliveryLogCountCacheHitRatio()
        );
    }

    public long roleVisibilityCacheHits() {
        return roleVisibilityCacheHits.get();
    }

    public long roleVisibilityCacheMisses() {
        return roleVisibilityCacheMisses.get();
    }

    private long readModelVersion() {
        CachedReadModelVersion cached = readModelVersionCache.get(GLOBAL_VERSION_CACHE_KEY);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMillis() > now) {
            readModelVersionCacheHits.increment();
            return cached.version();
        }

        readModelVersionCacheMisses.increment();
        long version = 0L;
        try {
            Long actualVersion = systemInternalApi.readModelVersion(
                    READ_MODEL_CONTEXT_MESSAGE,
                    READ_MODEL_SCOPE_MESSAGE_UNREAD
            );
            if (actualVersion != null) {
                version = actualVersion;
            }
        } catch (Exception ignored) {
            // Keep operation path stable even when read-model infra is unavailable.
        }
        readModelVersionCache.put(GLOBAL_VERSION_CACHE_KEY, new CachedReadModelVersion(version, now + READ_MODEL_VERSION_CACHE_TTL_MILLIS));
        return version;
    }

    private void bumpUnreadReadModelVersion() {
        invalidateReadModelVersionCache();
        try {
            systemInternalApi.bumpReadModelVersion(
                    READ_MODEL_CONTEXT_MESSAGE,
                    READ_MODEL_SCOPE_MESSAGE_UNREAD,
                    READ_MODEL_EVENT_MESSAGE_UNREAD
            );
        } catch (Exception ignored) {
            // Keep write operations non-blocking when read-model infra is temporarily unavailable.
        }
    }

    private void invalidateReadModelVersionCache() {
        readModelVersionCache.remove(GLOBAL_VERSION_CACHE_KEY);
    }

    private String buildUnreadCountCacheKey(Long userId, String userUuid, String version, long readModelVersion) {
        return String.join(":", UNREAD_COUNT_CACHE_PREFIX, String.valueOf(userId), cacheKeyPart(userUuid), normalizeVersion(version), "v" + readModelVersion);
    }

    private String buildMessageListCacheKey(Long userId, String userUuid, String version, long readModelVersion, List<Long> roleIds, long pageNo, long pageSize) {
        return String.join(":",
                "message:list",
                cacheKeyPart(userId),
                cacheKeyPart(userUuid),
                normalizeVersion(version),
                "v" + readModelVersion,
                cacheKeyPart(roleIds),
                String.valueOf(pageNo),
                String.valueOf(pageSize));
    }

    private String buildArchiveCountCacheKey(NoticeArchiveQuery query) {
        if (query == null) {
            return null;
        }
        return String.join(":",
                ARCHIVE_COUNT_CACHE_PREFIX,
                cacheKeyPart(query.getUserId()),
                cacheKeyPart(query.getUserUuid()),
                cacheKeyPart(query.isManageArchive()),
                cacheKeyPart(query.getKeyword()),
                cacheKeyPart(query.getMessageType()),
                cacheKeyPart(query.getTargetScope()),
                cacheKeyPart(query.getSourceType()),
                cacheKeyPart(query.getPublishStatus()),
                cacheKeyPart(query.getPublishedAtStart()),
                cacheKeyPart(query.getPublishedAtEnd()),
                cacheKeyPart(query.getSortField()),
                cacheKeyPart(query.getSortOrder()),
                cacheKeyPart(query.getPermissionSnapshotVersion()),
                cacheKeyPart(query.getRoleIds()),
                String.valueOf(query.getCountLimit()));
    }

    private String buildDeliveryLogCountCacheKey(DeliveryLogQuery query) {
        if (query == null) {
            return null;
        }
        return String.join(":",
                DELIVERY_LOG_COUNT_CACHE_PREFIX,
                cacheKeyPart(query.getUserId()),
                cacheKeyPart(query.getUserUuid()),
                cacheKeyPart(query.isManageDeliveryLogs()),
                cacheKeyPart(query.getKeyword()),
                cacheKeyPart(query.getChannel()),
                cacheKeyPart(query.getTargetScope()),
                cacheKeyPart(query.getSendStatus()),
                cacheKeyPart(query.getCreatedAtStart()),
                cacheKeyPart(query.getCreatedAtEnd()),
                String.valueOf(query.getCountLimit()));
    }

    private String normalizeVersion(String version) {
        return StringUtils.hasText(version) ? version : "v1";
    }

    private String cacheKeyPart(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .sorted()
                    .collect(Collectors.joining(","));
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? "-" : stringValue;
    }

    private boolean shouldCacheUnreadCount() {
        return messageProperties != null && messageProperties.getUnreadCountCacheTtlSeconds() > 0L;
    }

    private Long normalizeUnreadCount(Long count) {
        return count == null ? 0L : Math.max(0L, count);
    }

    private Long readLocalUnreadCount(String cacheKey) {
        if (!StringUtils.hasText(cacheKey)) {
            return null;
        }
        CachedLong cached = localUnreadCountCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached == null) {
            return null;
        }
        if (cached.expireAt().isAfter(now)) {
            return cached.value();
        }
        localUnreadCountCache.remove(cacheKey);
        return null;
    }

    private void cacheLocalUnreadCount(String cacheKey, Long value) {
        if (!StringUtils.hasText(cacheKey)) {
            return;
        }
        localUnreadCountCache.put(cacheKey, new CachedLong(normalizeUnreadCount(value), Instant.now().plus(LOCAL_UNREAD_COUNT_CACHE_TTL)));
    }

    private UnreadContext resolveUnreadContext(CurrentUser currentUser) {
        if (isAuthenticatedUser(currentUser)
                && isPermissionVersionAligned(currentUser)) {
            RoleVisibility roleVisibility = new RoleVisibility(currentUser.getPermissionsVersion(), normalizedRoleIds(currentUser.getRoleIds()));
            return new UnreadContext(roleVisibility, readModelVersion());
        }
        return loadUnreadContextFromSystem(currentUser);
    }

    private UnreadContext loadUnreadContextFromSystem(CurrentUser currentUser) {
        CompletableFuture<RoleVisibility> roleVisibilityFuture = CompletableFuture.supplyAsync(() -> visibleRoleVisibility(currentUser), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Long> readModelVersionFuture = CompletableFuture.supplyAsync(this::readModelVersion, BLOCKING_IO_EXECUTOR);
        return new UnreadContext(roleVisibilityFuture.join(), readModelVersionFuture.join());
    }

    private List<Long> visibleRoleIds(Long userId) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Full user identity is required");
    }

    private RoleVisibility visibleRoleVisibility(Long userId) {
        throw new BizException(ErrorCode.UNAUTHORIZED, "Full user identity is required");
    }

    private List<Long> visibleRoleIds(CurrentUser currentUser) {
        return visibleRoleVisibility(currentUser).roleIds();
    }

    private RoleVisibility visibleRoleVisibility(CurrentUser currentUser) {
        if (!isAuthenticatedUser(currentUser)) {
            return new RoleVisibility(null, List.of());
        }
        if (isPermissionVersionAligned(currentUser)) {
            return new RoleVisibility(currentUser.getPermissionsVersion(), normalizedRoleIds(currentUser.getRoleIds()));
        }
        return visibleRoleVisibilityFromSystem(requireAuthenticatedUserId(currentUser), currentUser.getUserUuid());
    }

    private RoleVisibility visibleRoleVisibilityFromSystem(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return new RoleVisibility(null, List.of());
        }
        String cacheKey = userId + ":" + userUuid.trim();
        CachedRoleVisibility cached = roleVisibilityCache.get(cacheKey);
        Instant now = Instant.now();
        if (isTrustedCachedRoleVisibility(cached, now)) {
            roleVisibilityCacheHits.incrementAndGet();
            return cached.roleVisibility();
        }
        if (cached != null) {
            roleVisibilityCache.remove(cacheKey);
        }

        try {
            CompletableFuture<RoleVisibility> inFlight = roleVisibilityLoadInFlight.computeIfAbsent(
                    cacheKey,
                    key -> CompletableFuture.completedFuture(loadRoleVisibilityFromSystem(userId, userUuid, now))
            );
            return inFlight.join();
        } catch (java.util.concurrent.CompletionException exception) {
            throw new RuntimeException("failed to load role visibility from system", exception.getCause() == null ? exception : exception.getCause());
        } finally {
            CompletableFuture<RoleVisibility> inFlight = roleVisibilityLoadInFlight.get(cacheKey);
            if (inFlight != null && inFlight.isDone()) {
                roleVisibilityLoadInFlight.remove(cacheKey, inFlight);
            }
        }
    }

    private RoleVisibility loadRoleVisibilityFromSystem(Long userId, String userUuid, Instant now) {
        roleVisibilityCacheMisses.incrementAndGet();
        PermissionSnapshotDTO snapshot = systemInternalApi.permissionRoleSnapshot(userId, userUuid);
        if (snapshot == null || snapshot.roleIds() == null || snapshot.roleIds().isEmpty()) {
            RoleVisibility roleVisibility = new RoleVisibility(snapshot == null ? null : snapshot.version(), List.of());
            roleVisibilityCache.put(userId + ":" + userUuid.trim(), new CachedRoleVisibility(roleVisibility, now.plus(ROLE_VISIBILITY_CACHE_TTL)));
            return roleVisibility;
        }
        List<Long> roleIds = normalizedRoleIds(snapshot.roleIds());
        RoleVisibility roleVisibility = new RoleVisibility(snapshot.version(), roleIds);
        roleVisibilityCache.put(userId + ":" + userUuid.trim(), new CachedRoleVisibility(roleVisibility, now.plus(ROLE_VISIBILITY_CACHE_TTL)));
        return roleVisibility;
    }

    private boolean isTrustedCachedRoleVisibility(CachedRoleVisibility cached, Instant now) {
        if (cached == null || !cached.expireAt().isAfter(now)) {
            return false;
        }
        long currentVersion = permissionSnapshotVersion();
        if (currentVersion <= 0L) {
            return true;
        }
        Long cachedVersion = parsePermissionSnapshotVersion(cached.roleVisibility().version());
        return cachedVersion != null && cachedVersion == currentVersion;
    }

    private boolean isPermissionVersionAligned(CurrentUser currentUser) {
        if (!isAuthenticatedUser(currentUser) || !StringUtils.hasText(currentUser.getPermissionsVersion())) {
            return false;
        }
        Long sessionVersion = parsePermissionSnapshotVersion(currentUser.getPermissionsVersion());
        if (sessionVersion == null) {
            return false;
        }
        long currentVersion = permissionSnapshotVersion();
        if (currentVersion <= 0L) {
            return true;
        }
        return sessionVersion == currentVersion;
    }

    private boolean isAuthenticatedUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private Long requireAuthenticatedUserId(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    private String trustedUsername(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUsername();
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
    }

    private void requireAnyPermission(CurrentUser currentUser, String... permissionKeys) {
        Set<String> permissions = trustedPermissions(currentUser);
        if (permissions.contains("*")) {
            return;
        }
        for (String permissionKey : permissionKeys) {
            if (permissions.contains(permissionKey)) {
                return;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + String.join(" or ", permissionKeys));
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemUserSnapshotDTO snapshot = systemInternalApi.findUserIdentityById(userId);
        if (snapshot == null || snapshot.userId() == null || !snapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(snapshot.userUuid()) || !snapshot.userUuid().trim().equals(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!STATUS_ENABLED.equalsIgnoreCase(snapshot.status())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotDTO permissionSnapshot = systemInternalApi.permissionSnapshot(userId, normalizedUserUuid);
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(snapshot.userId());
        currentUser.setUserUuid(snapshot.userUuid().trim());
        currentUser.setUsername(snapshot.username());
        currentUser.setPermissions(permissionSnapshot.permissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.permissions()));
        currentUser.setRoleIds(permissionSnapshot.roleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.roleIds()));
        currentUser.setPrimaryDeptId(permissionSnapshot.primaryDeptId());
        currentUser.setDeptIds(permissionSnapshot.deptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.deptIds()));
        currentUser.setDescendantDeptIds(permissionSnapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.descendantDeptIds()));
        currentUser.setDataScopes(permissionSnapshot.dataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.dataScopes()));
        currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
        currentUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
    }

    private long permissionSnapshotVersion() {
        CachedPermissionSnapshotVersion cached = permissionSnapshotVersionCache.get(GLOBAL_VERSION_CACHE_KEY);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMillis() > now) {
            return cached.version();
        }

        long version;
        CompletableFuture<Long> inFlight;
        try {
            inFlight = permissionSnapshotVersionLoadInFlight.computeIfAbsent(
                    GLOBAL_VERSION_CACHE_KEY,
                    key -> CompletableFuture.completedFuture(loadPermissionSnapshotVersionFromSystem(key))
            );
            version = inFlight.join();
        } catch (java.util.concurrent.CompletionException exception) {
            return 0L;
        } finally {
            CompletableFuture<Long> removeCandidate = permissionSnapshotVersionLoadInFlight.get(GLOBAL_VERSION_CACHE_KEY);
            if (removeCandidate != null && removeCandidate.isDone()) {
                permissionSnapshotVersionLoadInFlight.remove(GLOBAL_VERSION_CACHE_KEY, removeCandidate);
            }
        }
        permissionSnapshotVersionCache.put(GLOBAL_VERSION_CACHE_KEY, new CachedPermissionSnapshotVersion(version, now + READ_MODEL_VERSION_CACHE_TTL_MILLIS));
        return version;
    }

    private long loadPermissionSnapshotVersionFromSystem(String cacheKey) {
        readModelVersionCacheMisses.increment();
        try {
            Long actualVersion = systemInternalApi.readModelVersion(
                    IAM_PERMISSION_READ_MODEL_CONTEXT,
                    IAM_PERMISSION_READ_MODEL_SCOPE
            );
            if (actualVersion != null && actualVersion > 0L) {
                return actualVersion;
            }
        } catch (Exception ignored) {
            // Keep write operations non-blocking when read-model infra is temporarily unavailable.
        }
        return 0L;
    }

    private Long parsePermissionSnapshotVersion(String permissionVersion) {
        String normalized = permissionVersion == null ? "" : permissionVersion.trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        int start = normalized.startsWith("v") ? 1 : 0;
        int colonIndex = normalized.indexOf(':', start);
        String numericPart = colonIndex >= start ? normalized.substring(start, colonIndex == -1 ? normalized.length() : colonIndex) : normalized.substring(start);
        if (!StringUtils.hasText(numericPart)) {
            return null;
        }
        try {
            return Long.parseLong(numericPart);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<Long> normalizedRoleIds(java.util.Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(MAX_SESSION_ROLE_IDS)
                .toList();
    }

    private MessageVO.NoticeVO findNoticeById(Long noticeId, Long userId, String userUuid) {
        MessageVO.NoticeVO notice = messageNoticeMapper.findNoticeById(noticeId, userId, userUuid);
        enrichNoticeTarget(notice);
        return notice;
    }

    private void enrichNoticeTarget(MessageVO.NoticeVO notice) {
        if (notice == null) {
            return;
        }
        enrichNoticeTargets(List.of(notice));
    }

    private void enrichNoticeTargets(List<MessageVO.NoticeVO> notices) {
        if (notices == null || notices.isEmpty()) {
            return;
        }
        Set<UserIdentityKey> userKeys = new LinkedHashSet<>();
        Set<Long> roleIds = new LinkedHashSet<>();
        for (MessageVO.NoticeVO notice : notices) {
            if (notice == null) {
                continue;
            }
            if (notice.getTargetUserId() != null) {
                userKeys.add(new UserIdentityKey(notice.getTargetUserId(), notice.getTargetUserUuid()));
            }
            if (notice.getTargetRoleId() != null) {
                roleIds.add(notice.getTargetRoleId());
            }
        }
        Map<UserIdentityKey, String> userNames = loadUserNames(userKeys);
        Map<Long, String> roleNames = loadRoleNames(roleIds);
        for (MessageVO.NoticeVO notice : notices) {
            if (notice == null) {
                continue;
            }
            if (notice.getTargetUserId() != null) {
                notice.setTargetUserName(userNames.get(new UserIdentityKey(notice.getTargetUserId(), notice.getTargetUserUuid())));
            }
            if (notice.getTargetRoleId() != null) {
                notice.setTargetRoleName(roleNames.get(notice.getTargetRoleId()));
            }
        }
    }

    private Map<UserIdentityKey, String> loadUserNames(Set<UserIdentityKey> userKeys) {
        if (userKeys.isEmpty()) {
            return Map.of();
        }
        Map<UserIdentityKey, String> names = new LinkedHashMap<>();
        List<UserIdentityKey> missingUserKeys = new ArrayList<>();
        for (UserIdentityKey userKey : userKeys) {
            String cacheKey = userNameCacheKey(userKey);
            String cachedName = cacheTemplate.get(cacheKey);
            if (!StringUtils.hasText(cachedName)) {
                missingUserKeys.add(userKey);
                continue;
            }
            if (isCachedMiss(cachedName)) {
                continue;
            }
            names.put(userKey, cachedName);
        }

        if (missingUserKeys.isEmpty()) {
            return names.isEmpty() ? Collections.emptyMap() : names;
        }

        List<Long> missingUserIds = missingUserKeys.stream()
                .map(UserIdentityKey::userId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<SystemUserSnapshotDTO> users = systemInternalApi.userIdentitiesByIds(missingUserIds);
        if (users == null || users.isEmpty()) {
            cacheUserNameMisses(missingUserKeys, Set.of());
            return Map.of();
        }
        Map<Long, SystemUserSnapshotDTO> usersById = new LinkedHashMap<>();
        for (SystemUserSnapshotDTO user : users) {
            if (user != null && user.userId() != null) {
                usersById.put(user.userId(), user);
            }
        }

        Set<UserIdentityKey> resolvedKeys = new LinkedHashSet<>();
        for (UserIdentityKey userKey : missingUserKeys) {
            SystemUserSnapshotDTO user = usersById.get(userKey.userId());
            if (user == null
                    || !userKey.matches(user)
                    || !isEnabledUser(user)
                    || !StringUtils.hasText(user.username())) {
                continue;
            }
            names.put(userKey, user.username());
            cacheName(userNameCacheKey(userKey), user.username(), NOTICE_TARGET_NAME_CACHE_TTL);
            resolvedKeys.add(userKey);
        }

        cacheUserNameMisses(missingUserKeys, resolvedKeys);
        return names;
    }

    private boolean isEnabledUser(SystemUserSnapshotDTO user) {
        return StringUtils.hasText(user.status())
                && STATUS_ENABLED.equalsIgnoreCase(user.status().trim());
    }

    private Map<Long, String> loadRoleNames(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        List<Long> missingRoleIds = new ArrayList<>();
        for (Long roleId : roleIds) {
            String cacheKey = roleNameCacheKey(roleId);
            String cachedName = cacheTemplate.get(cacheKey);
            if (!StringUtils.hasText(cachedName)) {
                missingRoleIds.add(roleId);
                continue;
            }
            if (isCachedMiss(cachedName)) {
                continue;
            }
            names.put(roleId, cachedName);
        }

        if (missingRoleIds.isEmpty()) {
            return names.isEmpty() ? Collections.emptyMap() : names;
        }

        List<SystemRoleSnapshotDTO> roles = systemInternalApi.roleNamesByIds(missingRoleIds);
        if (roles == null || roles.isEmpty()) {
            cacheMissRoleNames(missingRoleIds);
            return Map.of();
        }
        Set<Long> resolvedIds = new LinkedHashSet<>();
        for (SystemRoleSnapshotDTO role : roles) {
            if (role != null && role.roleId() != null) {
                String name = role.roleName();
                if (StringUtils.hasText(name)) {
                    names.put(role.roleId(), name);
                    cacheName(roleNameCacheKey(role.roleId()), name, NOTICE_TARGET_NAME_CACHE_TTL);
                    resolvedIds.add(role.roleId());
                }
            }
        }

        cacheRoleNameMisses(missingRoleIds, resolvedIds);
        return names;
    }

    private void cacheUserNameMisses(List<UserIdentityKey> requestedUserKeys, Set<UserIdentityKey> resolvedUserKeys) {
        for (UserIdentityKey userKey : requestedUserKeys) {
            if (resolvedUserKeys.contains(userKey)) {
                continue;
            }
            cacheName(userNameCacheKey(userKey), CACHED_NAME_MISS_MARKER, NOTICE_TARGET_NAME_CACHE_TTL);
        }
    }

    private void cacheMissRoleNames(List<Long> requestedRoleIds) {
        for (Long roleId : requestedRoleIds) {
            cacheName(roleNameCacheKey(roleId), CACHED_NAME_MISS_MARKER, NOTICE_TARGET_NAME_CACHE_TTL);
        }
    }

    private void cacheRoleNameMisses(List<Long> requestedRoleIds, Set<Long> resolvedRoleIds) {
        for (Long roleId : requestedRoleIds) {
            if (resolvedRoleIds.contains(roleId)) {
                continue;
            }
            cacheName(roleNameCacheKey(roleId), CACHED_NAME_MISS_MARKER, NOTICE_TARGET_NAME_CACHE_TTL);
        }
    }

    private void cacheName(String key, String value, Duration ttl) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        cacheTemplate.put(key, value, ttl);
    }

    private String userNameCacheKey(UserIdentityKey userKey) {
        return NOTICE_TARGET_USER_NAME_CACHE_PREFIX + ":" + cacheKeyPart(userKey.userId()) + ":" + cacheKeyPart(userKey.userUuid());
    }

    private static Timer createTimerIfAvailable(MeterRegistry meterRegistry, String timerName) {
        if (meterRegistry == null) {
            return null;
        }
        return Timer.builder(timerName)
                .publishPercentiles(0.95)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    private void recordDuration(Timer timer, long startedNanos) {
        if (timer == null) {
            return;
        }
        timer.record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
    }

    private double timerP95Millis(Timer timer) {
        if (timer == null || timer.count() == 0) {
            return 0.0;
        }
        for (var percentile : timer.takeSnapshot().percentileValues()) {
            if (Double.compare(percentile.percentile(), 0.95) == 0) {
                return percentile.value(TimeUnit.MILLISECONDS);
            }
        }
        return timer.max(TimeUnit.MILLISECONDS);
    }

    private String roleNameCacheKey(Long roleId) {
        return NOTICE_TARGET_ROLE_NAME_CACHE_PREFIX + ":" + String.valueOf(roleId);
    }

    private boolean isCachedMiss(String cachedValue) {
        return CACHED_NAME_MISS_MARKER.equals(cachedValue);
    }

    private Set<String> normalizeChannels(List<String> channels) {
        List<String> source = channels == null || channels.isEmpty() ? List.of(CHANNEL_INBOX) : channels;
        Set<String> normalized = new LinkedHashSet<>();
        for (String channel : source) {
            if (!StringUtils.hasText(channel)) {
                continue;
            }
            String value = channel.trim().toUpperCase();
            if (!CHANNEL_INBOX.equals(value) && !CHANNEL_EMAIL.equals(value) && !CHANNEL_WECHAT_OFFICIAL.equals(value)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "不支持的通知渠道: " + channel);
            }
            normalized.add(value);
        }
        return normalized;
    }

    private void sendEmailNotifications(Long actorUserId, String actorUserUuid, MessageDTO.MessageCreateRequest request, Long noticeId) {
        if (!smtpNotificationMailService.isConfigured()) {
            insertDeliveryLog(noticeId, CHANNEL_EMAIL, request.getTargetScope(), null, null, null, null, request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "SMTP 未配置或配置不完整", actorUserId, actorUserUuid);
            return;
        }
        List<EmailRecipient> recipients = resolveEmailRecipients(request.getTargetScope(), request.getTargetUserId(), request.getTargetRoleId());
        if (recipients.isEmpty()) {
            insertDeliveryLog(noticeId, CHANNEL_EMAIL, request.getTargetScope(), null, null, null, null, request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "未找到可接收邮箱通知的用户", actorUserId, actorUserUuid);
            return;
        }
        for (EmailRecipient recipient : recipients) {
            if (!StringUtils.hasText(recipient.email())) {
                insertDeliveryLog(noticeId, CHANNEL_EMAIL, request.getTargetScope(), recipient.userId(), recipient.userUuid(), recipient.username(), recipient.email(), request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "用户未绑定邮箱", actorUserId, actorUserUuid);
                continue;
            }
            try {
                smtpNotificationMailService.send(recipient.email(), request.getTitle(), request.getContent());
                insertDeliveryLog(noticeId, CHANNEL_EMAIL, request.getTargetScope(), recipient.userId(), recipient.userUuid(), recipient.username(), recipient.email(), request.getTitle(), request.getContent(), DELIVERY_SUCCESS, null, actorUserId, actorUserUuid);
            } catch (Exception exception) {
                insertDeliveryLog(noticeId, CHANNEL_EMAIL, request.getTargetScope(), recipient.userId(), recipient.userUuid(), recipient.username(), recipient.email(), request.getTitle(), request.getContent(), DELIVERY_FAILED, abbreviate(exception.getMessage(), 1000), actorUserId, actorUserUuid);
            }
        }
    }

    private void sendWechatOfficialNotifications(Long actorUserId, String actorUserUuid, MessageDTO.MessageCreateRequest request, Long noticeId) {
        if (!wechatOfficialAccountNotificationService.isConfigured()) {
            insertDeliveryLog(noticeId, CHANNEL_WECHAT_OFFICIAL, request.getTargetScope(), null, null, null, null, request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "微信公众号通知未启用或配置不完整", actorUserId, actorUserUuid);
            return;
        }
        List<WechatRecipient> recipients = resolveWechatRecipients(request.getTargetScope(), request.getTargetUserId(), request.getTargetRoleId());
        if (recipients.isEmpty()) {
            insertDeliveryLog(noticeId, CHANNEL_WECHAT_OFFICIAL, request.getTargetScope(), null, null, null, null, request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "未找到可接收微信公众号通知的用户", actorUserId, actorUserUuid);
            return;
        }
        for (WechatRecipient recipient : recipients) {
            if (!StringUtils.hasText(recipient.wechatOpenid())) {
                insertDeliveryLog(noticeId, CHANNEL_WECHAT_OFFICIAL, request.getTargetScope(), recipient.userId(), recipient.userUuid(), recipient.username(), null, request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "用户未绑定微信 OpenID", actorUserId, actorUserUuid);
                continue;
            }
            try {
                wechatOfficialAccountNotificationService.send(recipient.wechatOpenid(), request.getTitle(), request.getContent());
                insertDeliveryLog(noticeId, CHANNEL_WECHAT_OFFICIAL, request.getTargetScope(), recipient.userId(), recipient.userUuid(), recipient.username(), recipient.wechatOpenid(), request.getTitle(), request.getContent(), DELIVERY_SUCCESS, null, actorUserId, actorUserUuid);
            } catch (Exception exception) {
                insertDeliveryLog(noticeId, CHANNEL_WECHAT_OFFICIAL, request.getTargetScope(), recipient.userId(), recipient.userUuid(), recipient.username(), recipient.wechatOpenid(), request.getTitle(), request.getContent(), DELIVERY_FAILED, abbreviate(exception.getMessage(), 1000), actorUserId, actorUserUuid);
            }
        }
    }

    private List<EmailRecipient> resolveEmailRecipients(String targetScope, Long targetUserId, Long targetRoleId) {
        List<SystemUserEmailRecipientDTO> recipients;
        String normalizedTargetScope = normalizeTargetScope(targetScope);
        if (TARGET_SCOPE_USER.equals(normalizedTargetScope)) {
            recipients = targetUserId == null ? List.of() : systemInternalApi.userEmailRecipientsByIds(List.of(targetUserId));
            return toEmailRecipients(recipients);
        }
        if (TARGET_SCOPE_ROLE.equals(normalizedTargetScope)) {
            recipients = targetRoleId == null ? List.of() : systemInternalApi.userEmailRecipientsByRole(targetRoleId);
            return toEmailRecipients(recipients);
        }
        recipients = systemInternalApi.platformUserEmailRecipients();
        return toEmailRecipients(recipients);
    }

    private List<WechatRecipient> resolveWechatRecipients(String targetScope, Long targetUserId, Long targetRoleId) {
        List<SystemUserWechatRecipientDTO> recipients;
        String normalizedTargetScope = normalizeTargetScope(targetScope);
        if (TARGET_SCOPE_USER.equals(normalizedTargetScope)) {
            recipients = targetUserId == null ? List.of() : systemInternalApi.userWechatRecipientsByIds(List.of(targetUserId));
            return toWechatRecipients(recipients);
        }
        if (TARGET_SCOPE_ROLE.equals(normalizedTargetScope)) {
            recipients = targetRoleId == null ? List.of() : systemInternalApi.userWechatRecipientsByRole(targetRoleId);
            return toWechatRecipients(recipients);
        }
        recipients = systemInternalApi.platformUserWechatRecipients();
        return toWechatRecipients(recipients);
    }

    private String normalizeTargetScope(String targetScope) {
        if (!StringUtils.hasText(targetScope)) {
            return targetScope;
        }
        return targetScope.trim().toUpperCase();
    }

    private List<EmailRecipient> toEmailRecipients(List<SystemUserEmailRecipientDTO> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return List.of();
        }
        return recipients.stream()
                .filter(java.util.Objects::nonNull)
                .map(this::toEmailRecipient)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<WechatRecipient> toWechatRecipients(List<SystemUserWechatRecipientDTO> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return List.of();
        }
        return recipients.stream()
                .filter(java.util.Objects::nonNull)
                .map(this::toWechatRecipient)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private EmailRecipient toEmailRecipient(SystemUserEmailRecipientDTO recipient) {
        if (recipient.userId() == null || !StringUtils.hasText(recipient.userUuid())) {
            return null;
        }
        return new EmailRecipient(recipient.userId(), recipient.userUuid().trim(), recipient.username(), recipient.email());
    }

    private WechatRecipient toWechatRecipient(SystemUserWechatRecipientDTO recipient) {
        if (recipient.userId() == null || !StringUtils.hasText(recipient.userUuid())) {
            return null;
        }
        return new WechatRecipient(recipient.userId(), recipient.userUuid().trim(), recipient.username(), recipient.wechatOpenid());
    }

    private void insertDeliveryLog(
            Long noticeId,
            String channel,
            String targetScope,
            Long targetUserId,
            String targetUserUuid,
            String targetUserName,
            String targetEmail,
            String title,
            String content,
            String sendStatus,
            String errorMessage,
            Long operatorId,
            String operatorUserUuid
    ) {
        MessageDeliveryLogEntity entity = new MessageDeliveryLogEntity();
        entity.setNoticeId(noticeId);
        entity.setChannel(channel);
        entity.setTargetScope(targetScope);
        entity.setTargetUserId(targetUserId);
        entity.setTargetUserUuid(targetUserUuid);
        entity.setTargetUserName(targetUserName);
        entity.setTargetEmail(maskContactForDeliveryLog(channel, targetEmail));
        entity.setTitle(title);
        entity.setContent(content);
        entity.setSendStatus(sendStatus);
        entity.setErrorMessage(errorMessage);
        entity.setSentAt(DELIVERY_SUCCESS.equals(sendStatus) ? LocalDateTime.now() : null);
        entity.setCreatedBy(operatorId);
        entity.setCreatedByUuid(operatorUserUuid);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedByUuid(operatorUserUuid);
        entity.setDeleted(0);
        messageDeliveryLogMapper.insert(entity);
    }

    private String maskContactForDeliveryLog(String channel, String contact) {
        if (!StringUtils.hasText(contact)) {
            return contact;
        }
        String normalized = contact.trim();
        if (CHANNEL_EMAIL.equals(channel)) {
            return maskEmail(normalized);
        }
        if (CHANNEL_WECHAT_OFFICIAL.equals(channel)) {
            return maskToken(normalized);
        }
        return normalized;
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return maskToken(email);
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***" + domain;
        }
        return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + domain;
    }

    private String maskToken(String value) {
        if (value.length() <= 6) {
            return value.charAt(0) + "***" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }

    private NoticeArchiveQuery buildNoticeArchiveQuery(
            CurrentUser currentUser,
            MessageDTO.MessageArchiveQueryRequest request,
            RoleVisibility roleVisibility,
            long limit,
            long offset
    ) {
        NoticeArchiveQuery query = new NoticeArchiveQuery();
        query.setUserId(requireAuthenticatedUserId(currentUser));
        query.setUserUuid(currentUser.getUserUuid());
        query.setManageArchive(canManageArchive(currentUser));
        query.setKeyword(normalizeText(request.getKeyword()));
        query.setMessageType(normalizeText(request.getMessageType()));
        query.setTargetScope(normalizeText(request.getTargetScope()));
        query.setSourceType(normalizeText(request.getSourceType()));
        query.setPublishStatus(normalizeText(request.getPublishStatus()));
        query.setPublishedAtStart(request.getPublishedAtStart());
        query.setPublishedAtEnd(request.getPublishedAtEnd());
        query.setSortField(normalizeSortField(request.getSortField()));
        query.setSortOrder("ASC".equalsIgnoreCase(request.getSortOrder()) ? "asc" : "desc");
        query.setPermissionSnapshotVersion(roleVisibility.version());
        query.setRoleIds(roleVisibility.roleIds());
        query.setLimit(limit);
        query.setOffset(offset);
        query.setCountLimit(calculateArchiveCountLimit(limit, offset));
        return query;
    }

    private long calculateArchiveCountLimit(long pageSize, long offset) {
        long safePageSize = Math.max(1L, pageSize);
        long safeOffset = Math.max(0L, offset);
        long dynamicLimit = safeOffset + safePageSize + 1L;
        return Math.min(dynamicLimit, ARCHIVE_TOTAL_COUNT_CAP);
    }

    private boolean isDeliveryLogTotalCapped(Long total, DeliveryLogQuery query) {
        if (query == null) {
            return false;
        }
        long queryLimit = query.getCountLimit();
        if (queryLimit <= 0L) {
            return false;
        }
        return total != null && total >= queryLimit;
    }

    private long normalizeDeliveryLogTotal(Long total, DeliveryLogQuery query) {
        if (total == null || total <= 0L) {
            return 0L;
        }
        if (query == null || query.getCountLimit() <= 0L) {
            return total;
        }
        return Math.min(total, query.getCountLimit());
    }

    public long archiveCappedCountQueryTotal() {
        return archiveTotalCappedQueryCount.sum();
    }

    public long deliveryLogCappedCountQueryTotal() {
        return deliveryLogCappedQueryCount.sum();
    }

    private DeliveryLogQuery buildDeliveryLogQuery(
            CurrentUser currentUser,
            MessageDTO.MessageArchiveQueryRequest request,
            long limit,
            long offset
    ) {
        DeliveryLogQuery query = new DeliveryLogQuery();
        query.setUserId(requireAuthenticatedUserId(currentUser));
        query.setUserUuid(currentUser.getUserUuid());
        query.setManageDeliveryLogs(canManageArchive(currentUser));
        query.setKeyword(normalizeText(request.getKeyword()));
        query.setChannel(normalizeText(request.getChannel()));
        query.setTargetScope(normalizeText(request.getTargetScope()));
        query.setSendStatus(normalizeText(request.getSendStatus()));
        query.setCreatedAtStart(request.getPublishedAtStart());
        query.setCreatedAtEnd(request.getPublishedAtEnd());
        query.setLimit(limit);
        query.setOffset(offset);
        query.setCountLimit(calculateDeliveryLogCountLimit(limit, offset));
        return query;
    }

    private long calculateDeliveryLogCountLimit(long pageSize, long offset) {
        long safePageSize = Math.max(1L, pageSize);
        long safeOffset = Math.max(0L, offset);
        long dynamicLimit = safeOffset + safePageSize + 1L;
        return Math.min(dynamicLimit, DELIVERY_LOG_TOTAL_COUNT_CAP);
    }

    private String normalizeSortField(String sortField) {
        return switch (sortField == null ? "" : sortField) {
            case "publishedAt", "createdAt", "title", "sourceType", "publishStatus", "targetScope", "messageType", "readFlag" -> sortField;
            default -> null;
        };
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record RoleVisibility(String version, List<Long> roleIds) {
    }

    private record UserIdentityKey(Long userId, String userUuid) {

        private UserIdentityKey {
            userUuid = StringUtils.hasText(userUuid) ? userUuid.trim() : null;
        }

        private boolean matches(SystemUserSnapshotDTO user) {
            if (user == null || !java.util.Objects.equals(userId, user.userId())) {
                return false;
            }
            if (!StringUtils.hasText(userUuid)) {
                return !StringUtils.hasText(user.userUuid());
            }
            return StringUtils.hasText(user.userUuid()) && userUuid.equals(user.userUuid().trim());
        }
    }

    private record CachedReadModelVersion(long version, long expiresAtEpochMillis) {
    }

    private record CachedRoleVisibility(RoleVisibility roleVisibility, Instant expireAt) {
    }

    private record CachedPermissionSnapshotVersion(long version, long expiresAtEpochMillis) {
    }

    private record CachedLong(Long value, Instant expireAt) {
    }

    private record CachedNoticePage(MessageVO.NoticePageResponse response, Instant expireAt) {
    }

    private record UnreadContext(RoleVisibility roleVisibility, long readModelVersion) {
    }

    public record MetricsSnapshot(
            double listMessagesP95Millis,
            double unreadCountP95Millis,
            double readAllP95Millis,
            long archiveCappedCountQueryTotal,
            long deliveryLogCappedCountQueryTotal,
            long unreadCountCacheHits,
            long unreadCountCacheMisses,
            double unreadCountCacheHitRatio,
            long archiveCountCacheHits,
            long archiveCountCacheMisses,
            double archiveCountCacheHitRatio,
            long deliveryLogCountCacheHits,
            long deliveryLogCountCacheMisses,
            double deliveryLogCountCacheHitRatio
    ) {
    }

    private record EmailRecipient(Long userId, String userUuid, String username, String email) {
    }

    private record WechatRecipient(Long userId, String userUuid, String username, String wechatOpenid) {
    }
}
