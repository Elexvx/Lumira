package com.lumira.message.app;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserContactSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.constant.PlatformConstants;
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
    private static final String TARGET_SCOPE_TENANT = "TENANT";
    private static final String TARGET_SCOPE_USER = "USER";
    private static final String TARGET_SCOPE_ROLE = "ROLE";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_RETRACTED = "RETRACTED";
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
    private final Map<Long, CachedReadModelVersion> readModelVersionCache = new ConcurrentHashMap<>();
    private final Map<Long, CachedPermissionSnapshotVersion> permissionSnapshotVersionCache = new ConcurrentHashMap<>();
    private final Map<String, CachedLong> localUnreadCountCache = new ConcurrentHashMap<>();
    private final Map<String, CachedNoticePage> localMessageListCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<RoleVisibility>> roleVisibilityLoadInFlight = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Long>> permissionSnapshotVersionLoadInFlight = new ConcurrentHashMap<>();
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
            return listNotices(currentUser, pageNo, pageSize);
        } finally {
            recordDuration(messageListTimer, startedNanos);
        }
    }

    public MessageVO.NoticeArchivePageResponse listArchive(CurrentUser currentUser, MessageDTO.MessageArchiveQueryRequest request) {
        Long tenantId = tenantId(currentUser);
        long normalizedPageNo = Math.max(request.getPageNo() == null ? 1L : request.getPageNo(), 1L);
        long normalizedPageSize = Math.max(1L, Math.min(request.getPageSize() == null ? 20L : request.getPageSize(), 100L));
        long offset = (normalizedPageNo - 1) * normalizedPageSize;
        RoleVisibility roleVisibility = visibleRoleVisibility(currentUser, tenantId);
        NoticeArchiveQuery query = buildNoticeArchiveQuery(currentUser, request, tenantId, roleVisibility, normalizedPageSize, offset);
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
        enrichNoticeTargets(tenantId, records);
        response.setRecords(records);
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
        Set<String> permissions = currentUser.getPermissions();
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
            Long tenantId = tenantId(currentUser);
            if (currentUser == null || currentUser.getUserId() == null) {
                return 0L;
            }
            UnreadContext unreadContext = resolveUnreadContext(currentUser, tenantId);
            RoleVisibility roleVisibility = unreadContext.roleVisibility();
            long readModelVersion = unreadContext.readModelVersion();
            Long cached = readCachedUnreadCount(tenantId, currentUser.getUserId(), roleVisibility.version(), readModelVersion);
            if (cached != null) {
                return cached;
            }
            Long count = countUnreadFromDb(tenantId, currentUser.getUserId(), roleVisibility.roleIds(), roleVisibility.version(), readModelVersion);
            return count == null ? 0L : count;
        } finally {
            recordDuration(messageUnreadCountTimer, startedNanos);
        }
    }

    @Transactional
    public MessageVO.NoticeVO createMessage(CurrentUser currentUser, MessageDTO.MessageCreateRequest request) {
        Set<String> channels = normalizeChannels(request.getChannels());
        if (channels.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请至少选择一个通知渠道");
        }
        MessageVO.NoticeVO notice = null;
        if (channels.contains(CHANNEL_INBOX)) {
            notice = insertInboxNotice(
                    tenantId(currentUser),
                    currentUser.getUserId(),
                    request.getTargetScope(),
                    request.getTargetUserId(),
                    request.getTargetRoleId(),
                    request.getTitle(),
                    request.getContent()
            );
            insertDeliveryLog(tenantId(currentUser), notice.getId(), CHANNEL_INBOX, request.getTargetScope(), null, null, null, request.getTitle(), request.getContent(), DELIVERY_SUCCESS, null, currentUser.getUserId());
            messagePushService.publishCreated(notice);
        }
        if (channels.contains(CHANNEL_EMAIL)) {
            sendEmailNotifications(currentUser, request, notice == null ? null : notice.getId());
        }
        if (channels.contains(CHANNEL_WECHAT_OFFICIAL)) {
            sendWechatOfficialNotifications(currentUser, request, notice == null ? null : notice.getId());
        }
        operationAuditService.log(
                tenantId(currentUser),
                currentUser.getUserId(),
                currentUser.getUsername(),
                "message",
                "send-message",
                "CREATE",
                "SUCCESS",
                "发送通知: " + request.getTitle()
        );
        if (notice != null) {
            bumpUnreadReadModelVersion(tenantId(currentUser));
        }
        return notice;
    }

    public MessageVO.DeliveryLogPageResponse listDeliveryLogs(CurrentUser currentUser, MessageDTO.MessageArchiveQueryRequest request) {
        Long tenantId = tenantId(currentUser);
        long normalizedPageNo = Math.max(request.getPageNo() == null ? 1L : request.getPageNo(), 1L);
        long normalizedPageSize = Math.max(1L, Math.min(request.getPageSize() == null ? 20L : request.getPageSize(), 100L));
        long offset = (normalizedPageNo - 1) * normalizedPageSize;
        DeliveryLogQuery query = buildDeliveryLogQuery(request, tenantId, normalizedPageSize, offset);
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

    @Transactional
    public MessageVO.NoticeVO retractMessage(CurrentUser currentUser, Long noticeId) {
        MessageVO.NoticeVO notice = retractNotice(currentUser, noticeId);
        messagePushService.publishRetracted(notice);
        operationAuditService.log(
                tenantId(currentUser),
                currentUser.getUserId(),
                currentUser.getUsername(),
                "message",
                "retract-message",
                "RETRACT",
                "SUCCESS",
                "撤回站内信: " + notice.getTitle()
        );
        bumpUnreadReadModelVersion(tenantId(currentUser));
        return notice;
    }

    @Transactional
    public MessageVO.NoticeVO markMessageRead(CurrentUser currentUser, Long noticeId) {
        Long tenantId = tenantId(currentUser);
        UnreadContext unreadContext = resolveUnreadContext(currentUser, tenantId);
        RoleVisibility roleVisibility = unreadContext.roleVisibility();
        MessageVO.NoticeVO notice = markRead(currentUser, noticeId, roleVisibility.roleIds());
        long readModelVersion = unreadContext.readModelVersion();
        Long unreadCount = countUnreadFromDb(tenantId, currentUser.getUserId(), roleVisibility.roleIds(), roleVisibility.version(), readModelVersion);
        messagePushService.publishRead(tenantId, currentUser.getUserId(), notice, unreadCount.intValue());
        bumpUnreadReadModelVersion(tenantId);
        return notice;
    }

    @Transactional
    public MessageVO.UnreadCountVO markAllRead(CurrentUser currentUser) {
        long startedNanos = System.nanoTime();
        try {
            LocalDateTime now = LocalDateTime.now();
            Long tenantId = tenantId(currentUser);
            UnreadContext unreadContext = resolveUnreadContext(currentUser, tenantId);
            RoleVisibility roleVisibility = unreadContext.roleVisibility();
            List<Long> roleIds = roleVisibility.roleIds();
            messageNoticeMapper.markAllRead(tenantId, currentUser.getUserId(), roleIds, now);

            long readModelVersion = unreadContext.readModelVersion();
            Long unreadCount = countUnreadFromDb(tenantId, currentUser.getUserId(), roleIds, roleVisibility.version(), readModelVersion);
            messagePushService.publishUnreadCount(
                    tenantId,
                    currentUser.getUserId(),
                    unreadCount.intValue()
            );
            bumpUnreadReadModelVersion(tenantId);

            MessageVO.UnreadCountVO unreadCountVO = new MessageVO.UnreadCountVO();
            unreadCountVO.setUnreadCount(unreadCount);
            return unreadCountVO;
        } finally {
            recordDuration(messageReadAllTimer, startedNanos);
        }
    }

    private MessageVO.NoticePageResponse listNotices(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = tenantId(currentUser);
        long normalizedPageNo = Math.max(pageNo, 1L);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, 100L));
        long offset = (normalizedPageNo - 1) * normalizedPageSize;
        RoleVisibility roleVisibility = visibleRoleVisibility(currentUser, tenantId);
        List<Long> roleIds = roleVisibility.roleIds();
        Long userId = currentUser == null ? null : currentUser.getUserId();
        String localCacheKey = buildMessageListCacheKey(tenantId, userId, roleVisibility.version(), roleIds, normalizedPageNo, normalizedPageSize);
        CachedNoticePage cached = localMessageListCache.get(localCacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expireAt().isAfter(now)) {
            return cached.response();
        }
        if (cached != null) {
            localMessageListCache.remove(localCacheKey);
        }

        List<MessageVO.NoticeVO> records = messageNoticeMapper.listVisiblePublished(tenantId, userId, roleIds, normalizedPageSize + 1, offset);
        boolean hasMore = records.size() > normalizedPageSize;
        if (hasMore) {
            records = new ArrayList<>(records.subList(0, (int) normalizedPageSize));
        }
        enrichNoticeTargets(tenantId, records);

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

    private MessageVO.NoticeVO insertInboxNotice(
            Long tenantId,
            Long operatorId,
            String targetScope,
            Long targetUserId,
            Long targetRoleId,
            String title,
            String content
    ) {
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
                tenantId,
                operatorId,
                targetScope,
                targetUserId,
                targetRoleId,
                title,
                content
        );
    }

    private MessageVO.NoticeVO insertNotice(
            Long tenantId,
            Long operatorId,
            String targetScope,
            Long targetUserId,
            Long targetRoleId,
            String title,
            String content
    ) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "标题和内容不能为空");
        }

        MessageNoticeEntity entity = new MessageNoticeEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setTenantId(tenantId);
        entity.setNoticeType(TYPE_MESSAGE);
        entity.setTargetScope(targetScope);
        entity.setTargetUserId(targetUserId);
        entity.setTargetRoleId(targetRoleId);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setSourceType(SOURCE_MANUAL);
        entity.setPublishStatus(STATUS_PUBLISHED);
        entity.setPublishedAt(now);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setDeleted(0);
        int updated = messageNoticeMapper.insert(entity);
        if (updated <= 0 || entity.getId() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息写入失败");
        }
        Long noticeId = entity.getId();
        MessageVO.NoticeVO notice = findNoticeById(tenantId, noticeId, operatorId);
        if (notice == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息写入后读取失败");
        }
        return notice;
    }

    private MessageVO.NoticeVO retractNotice(CurrentUser currentUser, Long noticeId) {
        if (noticeId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "通知ID不能为空");
        }
        MessageVO.NoticeVO notice = findNoticeById(tenantId(currentUser), noticeId, currentUser.getUserId());
        if (notice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或无权访问");
        }
        NoticeAggregate noticeAggregate = new NoticeAggregate(noticeId, tenantId(currentUser), notice.getPublishStatus());
        noticeAggregate.retract();
        int updated = messageNoticeMapper.update(null, new UpdateWrapper<MessageNoticeEntity>()
                .set("publish_status", STATUS_RETRACTED)
                .set("updated_by", currentUser.getUserId())
                .set("updated_at", LocalDateTime.now())
                .eq("id", noticeId)
                .eq("tenant_id", tenantId(currentUser))
                .eq("deleted", 0));
        if (updated <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或无权访问");
        }
        MessageVO.NoticeVO retractedNotice = findNoticeById(tenantId(currentUser), noticeId, currentUser.getUserId());
        if (retractedNotice == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "通知撤回后读取失败");
        }
        return retractedNotice;
    }

    private MessageVO.NoticeVO markRead(CurrentUser currentUser, Long noticeId, List<Long> roleIds) {
        Long tenantId = tenantId(currentUser);
        MessageVO.NoticeVO notice = findVisibleNoticeById(tenantId, noticeId, currentUser.getUserId(), roleIds);
        if (notice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或无权访问");
        }
        NoticeAggregate noticeAggregate = new NoticeAggregate(noticeId, tenantId, notice.getPublishStatus());
        noticeAggregate.markRead(currentUser.getUserId());

        LocalDateTime now = LocalDateTime.now();
        messageNoticeMapper.upsertRead(tenantId, noticeId, currentUser.getUserId(), now);
        notice.setReadFlag(Boolean.TRUE);
        notice.setReadAt(now);
        return notice;
    }

    private MessageVO.NoticeVO findVisibleNoticeById(Long tenantId, Long noticeId, Long userId, List<Long> roleIds) {
        MessageVO.NoticeVO notice = messageNoticeMapper.findVisibleNoticeById(tenantId, noticeId, userId, roleIds);
        enrichNoticeTarget(tenantId, notice);
        return notice;
    }

    private Long readCachedUnreadCount(Long tenantId, Long userId, String version, long readModelVersion) {
        String cacheKey = buildUnreadCountCacheKey(tenantId, userId, version, readModelVersion);
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

    private Long countUnreadFromDb(Long tenantId, Long userId, List<Long> roleIds, String version, long readModelVersion) {
        Long count = messageNoticeMapper.countUnread(tenantId, userId, roleIds, UNREAD_COUNT_CAP);
        Long normalizedCount = normalizeUnreadCount(count);
        if (shouldCacheUnreadCount()) {
            cacheLocalUnreadCount(buildUnreadCountCacheKey(tenantId, userId, version, readModelVersion), normalizedCount);
            cacheTemplate.put(
                    buildUnreadCountCacheKey(tenantId, userId, version, readModelVersion),
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

    private long readModelVersionForTenant(Long tenantId) {
        Long effectiveTenantId = tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        CachedReadModelVersion cached = readModelVersionCache.get(effectiveTenantId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMillis() > now) {
            readModelVersionCacheHits.increment();
            return cached.version();
        }

        readModelVersionCacheMisses.increment();
        long version = 0L;
        try {
            Long actualVersion = systemInternalApi.readModelVersion(
                    effectiveTenantId,
                    READ_MODEL_CONTEXT_MESSAGE,
                    READ_MODEL_SCOPE_MESSAGE_UNREAD
            );
            if (actualVersion != null) {
                version = actualVersion;
            }
        } catch (Exception ignored) {
            // Keep operation path stable even when read-model infra is unavailable.
        }
        readModelVersionCache.put(effectiveTenantId, new CachedReadModelVersion(version, now + READ_MODEL_VERSION_CACHE_TTL_MILLIS));
        return version;
    }

    private void bumpUnreadReadModelVersion(Long tenantId) {
        Long effectiveTenantId = tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        invalidateReadModelVersionCache(effectiveTenantId);
        try {
            systemInternalApi.bumpReadModelVersion(
                    effectiveTenantId,
                    READ_MODEL_CONTEXT_MESSAGE,
                    READ_MODEL_SCOPE_MESSAGE_UNREAD,
                    READ_MODEL_EVENT_MESSAGE_UNREAD
            );
        } catch (Exception ignored) {
            // Keep write operations non-blocking when read-model infra is temporarily unavailable.
        }
    }

    private void invalidateReadModelVersionCache(Long tenantId) {
        if (tenantId == null) {
            readModelVersionCache.remove(PlatformConstants.PLATFORM_TENANT_ID);
            return;
        }
        readModelVersionCache.remove(tenantId);
    }

    private String buildUnreadCountCacheKey(Long tenantId, Long userId, String version, long readModelVersion) {
        return String.join(":", UNREAD_COUNT_CACHE_PREFIX, String.valueOf(tenantId), String.valueOf(userId), normalizeVersion(version), "v" + readModelVersion);
    }

    private String buildMessageListCacheKey(Long tenantId, Long userId, String version, List<Long> roleIds, long pageNo, long pageSize) {
        return String.join(":",
                "message:list",
                cacheKeyPart(tenantId),
                cacheKeyPart(userId),
                normalizeVersion(version),
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
                cacheKeyPart(query.getTenantId()),
                cacheKeyPart(query.getUserId()),
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
                cacheKeyPart(query.getTenantId()),
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

    private UnreadContext resolveUnreadContext(CurrentUser currentUser, Long tenantId) {
        if (currentUser != null
                && currentUser.getUserId() != null
                && isPermissionVersionAligned(currentUser, tenantId)) {
            RoleVisibility roleVisibility = new RoleVisibility(currentUser.getPermissionsVersion(), normalizedRoleIds(currentUser.getRoleIds()));
            return new UnreadContext(roleVisibility, readModelVersionForTenant(tenantId));
        }
        return loadUnreadContextFromSystem(currentUser, tenantId);
    }

    private UnreadContext loadUnreadContextFromSystem(CurrentUser currentUser, Long tenantId) {
        CompletableFuture<RoleVisibility> roleVisibilityFuture = CompletableFuture.supplyAsync(() -> visibleRoleVisibility(currentUser, tenantId), BLOCKING_IO_EXECUTOR);
        CompletableFuture<Long> readModelVersionFuture = CompletableFuture.supplyAsync(() -> readModelVersionForTenant(tenantId), BLOCKING_IO_EXECUTOR);
        return new UnreadContext(roleVisibilityFuture.join(), readModelVersionFuture.join());
    }

    private List<Long> visibleRoleIds(Long tenantId, Long userId) {
        return visibleRoleVisibilityFromSystem(tenantId, userId).roleIds();
    }

    private RoleVisibility visibleRoleVisibility(Long tenantId, Long userId) {
        return visibleRoleVisibilityFromSystem(tenantId, userId);
    }

    private List<Long> visibleRoleIds(CurrentUser currentUser, Long tenantId) {
        return visibleRoleVisibility(currentUser, tenantId).roleIds();
    }

    private RoleVisibility visibleRoleVisibility(CurrentUser currentUser, Long tenantId) {
        if (tenantId == null || currentUser == null || currentUser.getUserId() == null) {
            return new RoleVisibility(null, List.of());
        }
        if (isPermissionVersionAligned(currentUser, tenantId)) {
            return new RoleVisibility(currentUser.getPermissionsVersion(), normalizedRoleIds(currentUser.getRoleIds()));
        }
        return visibleRoleVisibilityFromSystem(tenantId, currentUser.getUserId());
    }

    private RoleVisibility visibleRoleVisibilityFromSystem(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return new RoleVisibility(null, List.of());
        }
        String cacheKey = tenantId + ":" + userId;
        CachedRoleVisibility cached = roleVisibilityCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expireAt().isAfter(now)) {
            roleVisibilityCacheHits.incrementAndGet();
            return cached.roleVisibility();
        }
        if (cached != null && cached.expireAt().isBefore(now)) {
            roleVisibilityCache.remove(cacheKey);
        }

        try {
            CompletableFuture<RoleVisibility> inFlight = roleVisibilityLoadInFlight.computeIfAbsent(
                    cacheKey,
                    key -> CompletableFuture.completedFuture(loadRoleVisibilityFromSystem(tenantId, userId, now))
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

    private RoleVisibility loadRoleVisibilityFromSystem(Long tenantId, Long userId, Instant now) {
        roleVisibilityCacheMisses.incrementAndGet();
        PermissionSnapshotDTO snapshot = systemInternalApi.permissionSnapshot(tenantId, userId);
        if (snapshot == null || snapshot.roleIds() == null || snapshot.roleIds().isEmpty()) {
            RoleVisibility roleVisibility = new RoleVisibility(snapshot == null ? null : snapshot.version(), List.of());
            roleVisibilityCache.put(tenantId + ":" + userId, new CachedRoleVisibility(roleVisibility, now.plus(ROLE_VISIBILITY_CACHE_TTL)));
            return roleVisibility;
        }
        List<Long> roleIds = normalizedRoleIds(snapshot.roleIds());
        RoleVisibility roleVisibility = new RoleVisibility(snapshot.version(), roleIds);
        roleVisibilityCache.put(tenantId + ":" + userId, new CachedRoleVisibility(roleVisibility, now.plus(ROLE_VISIBILITY_CACHE_TTL)));
        return roleVisibility;
    }

    private boolean isPermissionVersionAligned(CurrentUser currentUser, Long tenantId) {
        if (currentUser == null || !StringUtils.hasText(currentUser.getPermissionsVersion()) || tenantId == null || currentUser.getUserId() == null) {
            return false;
        }
        Long sessionVersion = parsePermissionSnapshotVersion(currentUser.getPermissionsVersion());
        if (sessionVersion == null) {
            return false;
        }
        long currentVersion = permissionSnapshotVersionForTenant(tenantId);
        if (currentVersion <= 0L) {
            return true;
        }
        return sessionVersion == currentVersion;
    }

    private long permissionSnapshotVersionForTenant(Long tenantId) {
        Long effectiveTenantId = tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId;
        CachedPermissionSnapshotVersion cached = permissionSnapshotVersionCache.get(effectiveTenantId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMillis() > now) {
            return cached.version();
        }

        long version;
        CompletableFuture<Long> inFlight;
        try {
            inFlight = permissionSnapshotVersionLoadInFlight.computeIfAbsent(
                    effectiveTenantId,
                    key -> CompletableFuture.completedFuture(loadPermissionSnapshotVersionFromSystem(key))
            );
            version = inFlight.join();
        } catch (java.util.concurrent.CompletionException exception) {
            return 0L;
        } finally {
            CompletableFuture<Long> removeCandidate = permissionSnapshotVersionLoadInFlight.get(effectiveTenantId);
            if (removeCandidate != null && removeCandidate.isDone()) {
                permissionSnapshotVersionLoadInFlight.remove(effectiveTenantId, removeCandidate);
            }
        }
        permissionSnapshotVersionCache.put(effectiveTenantId, new CachedPermissionSnapshotVersion(version, now + READ_MODEL_VERSION_CACHE_TTL_MILLIS));
        return version;
    }

    private long loadPermissionSnapshotVersionFromSystem(Long tenantId) {
        readModelVersionCacheMisses.increment();
        try {
            Long actualVersion = systemInternalApi.readModelVersion(
                    tenantId,
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

    private MessageVO.NoticeVO findNoticeById(Long tenantId, Long noticeId, Long userId) {
        MessageVO.NoticeVO notice = messageNoticeMapper.findNoticeById(tenantId, noticeId, userId);
        enrichNoticeTarget(tenantId, notice);
        return notice;
    }

    private void enrichNoticeTarget(Long tenantId, MessageVO.NoticeVO notice) {
        if (notice == null) {
            return;
        }
        enrichNoticeTargets(tenantId, List.of(notice));
    }

    private void enrichNoticeTargets(Long tenantId, List<MessageVO.NoticeVO> notices) {
        if (notices == null || notices.isEmpty()) {
            return;
        }
        Set<Long> userIds = new LinkedHashSet<>();
        Set<Long> roleIds = new LinkedHashSet<>();
        for (MessageVO.NoticeVO notice : notices) {
            if (notice == null) {
                continue;
            }
            if (notice.getTargetUserId() != null) {
                userIds.add(notice.getTargetUserId());
            }
            if (notice.getTargetRoleId() != null) {
                roleIds.add(notice.getTargetRoleId());
            }
        }
        Map<Long, String> userNames = loadUserNames(tenantId, userIds);
        Map<Long, String> roleNames = loadRoleNames(tenantId, roleIds);
        for (MessageVO.NoticeVO notice : notices) {
            if (notice == null) {
                continue;
            }
            if (notice.getTargetUserId() != null) {
                notice.setTargetUserName(userNames.get(notice.getTargetUserId()));
            }
            if (notice.getTargetRoleId() != null) {
                notice.setTargetRoleName(roleNames.get(notice.getTargetRoleId()));
            }
        }
    }

    private Map<Long, String> loadUserNames(Long tenantId, Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        List<Long> missingUserIds = new ArrayList<>();
        for (Long userId : userIds) {
            String cacheKey = userNameCacheKey(tenantId, userId);
            String cachedName = cacheTemplate.get(cacheKey);
            if (!StringUtils.hasText(cachedName)) {
                missingUserIds.add(userId);
                continue;
            }
            if (isCachedMiss(cachedName)) {
                continue;
            }
            names.put(userId, cachedName);
        }

        if (missingUserIds.isEmpty()) {
            return names.isEmpty() ? Collections.emptyMap() : names;
        }

        List<SystemUserSnapshotDTO> users = systemInternalApi.usersByIds(tenantId, missingUserIds);
        if (users == null || users.isEmpty()) {
            cacheUserNameMisses(tenantId, missingUserIds, Set.of());
            return Map.of();
        }
        Set<Long> resolvedIds = new LinkedHashSet<>();
        for (SystemUserSnapshotDTO user : users) {
            if (user != null && user.userId() != null) {
                String name = user.username();
                if (StringUtils.hasText(name)) {
                    names.put(user.userId(), name);
                    cacheName(userNameCacheKey(tenantId, user.userId()), name, NOTICE_TARGET_NAME_CACHE_TTL);
                    resolvedIds.add(user.userId());
                }
            }
        }

        cacheUserNameMisses(tenantId, missingUserIds, resolvedIds);
        return names;
    }

    private Map<Long, String> loadRoleNames(Long tenantId, Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        List<Long> missingRoleIds = new ArrayList<>();
        for (Long roleId : roleIds) {
            String cacheKey = roleNameCacheKey(tenantId, roleId);
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

        List<SystemRoleSnapshotDTO> roles = systemInternalApi.rolesByIds(tenantId, missingRoleIds);
        if (roles == null || roles.isEmpty()) {
            cacheMissRoleNames(tenantId, missingRoleIds);
            return Map.of();
        }
        Set<Long> resolvedIds = new LinkedHashSet<>();
        for (SystemRoleSnapshotDTO role : roles) {
            if (role != null && role.roleId() != null) {
                String name = role.roleName();
                if (StringUtils.hasText(name)) {
                    names.put(role.roleId(), name);
                    cacheName(roleNameCacheKey(tenantId, role.roleId()), name, NOTICE_TARGET_NAME_CACHE_TTL);
                    resolvedIds.add(role.roleId());
                }
            }
        }

        cacheRoleNameMisses(tenantId, missingRoleIds, resolvedIds);
        return names;
    }

    private void cacheUserNameMisses(Long tenantId, List<Long> requestedUserIds, Set<Long> resolvedUserIds) {
        for (Long userId : requestedUserIds) {
            if (resolvedUserIds.contains(userId)) {
                continue;
            }
            cacheName(userNameCacheKey(tenantId, userId), CACHED_NAME_MISS_MARKER, NOTICE_TARGET_NAME_CACHE_TTL);
        }
    }

    private void cacheMissRoleNames(Long tenantId, List<Long> requestedRoleIds) {
        for (Long roleId : requestedRoleIds) {
            cacheName(roleNameCacheKey(tenantId, roleId), CACHED_NAME_MISS_MARKER, NOTICE_TARGET_NAME_CACHE_TTL);
        }
    }

    private void cacheRoleNameMisses(Long tenantId, List<Long> requestedRoleIds, Set<Long> resolvedRoleIds) {
        for (Long roleId : requestedRoleIds) {
            if (resolvedRoleIds.contains(roleId)) {
                continue;
            }
            cacheName(roleNameCacheKey(tenantId, roleId), CACHED_NAME_MISS_MARKER, NOTICE_TARGET_NAME_CACHE_TTL);
        }
    }

    private void cacheName(String key, String value, Duration ttl) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        cacheTemplate.put(key, value, ttl);
    }

    private String userNameCacheKey(Long tenantId, Long userId) {
        return NOTICE_TARGET_USER_NAME_CACHE_PREFIX + ":" + String.valueOf(tenantId) + ":" + String.valueOf(userId);
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

    private String roleNameCacheKey(Long tenantId, Long roleId) {
        return NOTICE_TARGET_ROLE_NAME_CACHE_PREFIX + ":" + String.valueOf(tenantId) + ":" + String.valueOf(roleId);
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

    private void sendEmailNotifications(CurrentUser currentUser, MessageDTO.MessageCreateRequest request, Long noticeId) {
        Long tenantId = tenantId(currentUser);
        if (!smtpNotificationMailService.isConfigured(tenantId)) {
            insertDeliveryLog(tenantId, noticeId, CHANNEL_EMAIL, request.getTargetScope(), null, null, null, request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "SMTP 未配置或配置不完整", currentUser.getUserId());
            return;
        }
        List<Recipient> recipients = resolveEmailRecipients(tenantId, request.getTargetScope(), request.getTargetUserId(), request.getTargetRoleId());
        if (recipients.isEmpty()) {
            insertDeliveryLog(tenantId, noticeId, CHANNEL_EMAIL, request.getTargetScope(), null, null, null, request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "未找到可接收邮箱通知的用户", currentUser.getUserId());
            return;
        }
        for (Recipient recipient : recipients) {
            if (!StringUtils.hasText(recipient.email())) {
                insertDeliveryLog(tenantId, noticeId, CHANNEL_EMAIL, request.getTargetScope(), recipient.userId(), recipient.username(), recipient.email(), request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "用户未绑定邮箱", currentUser.getUserId());
                continue;
            }
            try {
                smtpNotificationMailService.send(tenantId, recipient.email(), request.getTitle(), request.getContent());
                insertDeliveryLog(tenantId, noticeId, CHANNEL_EMAIL, request.getTargetScope(), recipient.userId(), recipient.username(), recipient.email(), request.getTitle(), request.getContent(), DELIVERY_SUCCESS, null, currentUser.getUserId());
            } catch (Exception exception) {
                insertDeliveryLog(tenantId, noticeId, CHANNEL_EMAIL, request.getTargetScope(), recipient.userId(), recipient.username(), recipient.email(), request.getTitle(), request.getContent(), DELIVERY_FAILED, abbreviate(exception.getMessage(), 1000), currentUser.getUserId());
            }
        }
    }

    private void sendWechatOfficialNotifications(CurrentUser currentUser, MessageDTO.MessageCreateRequest request, Long noticeId) {
        Long tenantId = tenantId(currentUser);
        if (!wechatOfficialAccountNotificationService.isConfigured(tenantId)) {
            insertDeliveryLog(tenantId, noticeId, CHANNEL_WECHAT_OFFICIAL, request.getTargetScope(), null, null, null, request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "微信公众号通知未启用或配置不完整", currentUser.getUserId());
            return;
        }
        List<Recipient> recipients = resolveRecipients(tenantId, request.getTargetScope(), request.getTargetUserId(), request.getTargetRoleId());
        if (recipients.isEmpty()) {
            insertDeliveryLog(tenantId, noticeId, CHANNEL_WECHAT_OFFICIAL, request.getTargetScope(), null, null, null, request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "未找到可接收微信公众号通知的用户", currentUser.getUserId());
            return;
        }
        for (Recipient recipient : recipients) {
            if (!StringUtils.hasText(recipient.wechatOpenid())) {
                insertDeliveryLog(tenantId, noticeId, CHANNEL_WECHAT_OFFICIAL, request.getTargetScope(), recipient.userId(), recipient.username(), null, request.getTitle(), request.getContent(), DELIVERY_SKIPPED, "用户未绑定微信 OpenID", currentUser.getUserId());
                continue;
            }
            try {
                wechatOfficialAccountNotificationService.send(tenantId, recipient.wechatOpenid(), request.getTitle(), request.getContent());
                insertDeliveryLog(tenantId, noticeId, CHANNEL_WECHAT_OFFICIAL, request.getTargetScope(), recipient.userId(), recipient.username(), recipient.wechatOpenid(), request.getTitle(), request.getContent(), DELIVERY_SUCCESS, null, currentUser.getUserId());
            } catch (Exception exception) {
                insertDeliveryLog(tenantId, noticeId, CHANNEL_WECHAT_OFFICIAL, request.getTargetScope(), recipient.userId(), recipient.username(), recipient.wechatOpenid(), request.getTitle(), request.getContent(), DELIVERY_FAILED, abbreviate(exception.getMessage(), 1000), currentUser.getUserId());
            }
        }
    }

    private List<Recipient> resolveEmailRecipients(Long tenantId, String targetScope, Long targetUserId, Long targetRoleId) {
        return resolveRecipients(tenantId, targetScope, targetUserId, targetRoleId);
    }

    private List<Recipient> resolveRecipients(Long tenantId, String targetScope, Long targetUserId, Long targetRoleId) {
        List<SystemUserContactSnapshotDTO> contacts;
        if (TARGET_SCOPE_USER.equals(targetScope)) {
            contacts = targetUserId == null ? List.of() : systemInternalApi.userContactsByIds(tenantId, List.of(targetUserId));
            return toRecipients(contacts);
        }
        if (TARGET_SCOPE_ROLE.equals(targetScope)) {
            contacts = targetRoleId == null ? List.of() : systemInternalApi.userContactsByRole(tenantId, targetRoleId);
            return toRecipients(contacts);
        }
        contacts = systemInternalApi.tenantUserContacts(tenantId);
        return toRecipients(contacts);
    }

    private List<Recipient> toRecipients(List<SystemUserContactSnapshotDTO> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return List.of();
        }
        return contacts.stream()
                .filter(java.util.Objects::nonNull)
                .map(this::toRecipient)
                .toList();
    }

    private Recipient toRecipient(SystemUserContactSnapshotDTO contact) {
        return new Recipient(contact.userId(), contact.username(), contact.email(), contact.wechatOpenid());
    }

    private void insertDeliveryLog(
            Long tenantId,
            Long noticeId,
            String channel,
            String targetScope,
            Long targetUserId,
            String targetUserName,
            String targetEmail,
            String title,
            String content,
            String sendStatus,
            String errorMessage,
            Long operatorId
    ) {
        MessageDeliveryLogEntity entity = new MessageDeliveryLogEntity();
        entity.setTenantId(tenantId);
        entity.setNoticeId(noticeId);
        entity.setChannel(channel);
        entity.setTargetScope(targetScope);
        entity.setTargetUserId(targetUserId);
        entity.setTargetUserName(targetUserName);
        entity.setTargetEmail(targetEmail);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setSendStatus(sendStatus);
        entity.setErrorMessage(errorMessage);
        entity.setSentAt(DELIVERY_SUCCESS.equals(sendStatus) ? LocalDateTime.now() : null);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        entity.setDeleted(0);
        messageDeliveryLogMapper.insert(entity);
    }

    private NoticeArchiveQuery buildNoticeArchiveQuery(
            CurrentUser currentUser,
            MessageDTO.MessageArchiveQueryRequest request,
            Long tenantId,
            RoleVisibility roleVisibility,
            long limit,
            long offset
    ) {
        NoticeArchiveQuery query = new NoticeArchiveQuery();
        query.setTenantId(tenantId);
        query.setUserId(currentUser.getUserId());
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
            MessageDTO.MessageArchiveQueryRequest request,
            Long tenantId,
            long limit,
            long offset
    ) {
        DeliveryLogQuery query = new DeliveryLogQuery();
        query.setTenantId(tenantId);
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

    private Long tenantId(CurrentUser currentUser) {
        return currentUser == null || currentUser.getCurrentTenantId() == null
                ? PlatformConstants.PLATFORM_TENANT_ID
                : currentUser.getCurrentTenantId();
    }

    private record RoleVisibility(String version, List<Long> roleIds) {
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

    private record Recipient(Long userId, String username, String email, String wechatOpenid) {
    }
}
