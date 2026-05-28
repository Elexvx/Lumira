package com.legendary.invention.message.app;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.constant.PlatformConstants;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.vo.PageResponse;
import com.legendary.invention.message.dto.MessageDTO;
import com.legendary.invention.message.dto.MessageQueryModels.DeliveryLogQuery;
import com.legendary.invention.message.dto.MessageQueryModels.NoticeArchiveQuery;
import com.legendary.invention.message.dto.MessageQueryModels.RecipientRow;
import com.legendary.invention.message.entity.MessageDeliveryLogEntity;
import com.legendary.invention.message.entity.MessageNoticeEntity;
import com.legendary.invention.message.mapper.MessageDeliveryLogMapper;
import com.legendary.invention.message.mapper.MessageNoticeMapper;
import com.legendary.invention.message.service.MessagePushService;
import com.legendary.invention.message.service.SmtpNotificationMailService;
import com.legendary.invention.message.service.WechatOfficialAccountNotificationService;
import com.legendary.invention.message.vo.MessageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    private final MessageNoticeMapper messageNoticeMapper;
    private final MessageDeliveryLogMapper messageDeliveryLogMapper;
    private final OperationAuditService operationAuditService;
    private final MessagePushService messagePushService;
    private final SmtpNotificationMailService smtpNotificationMailService;
    private final WechatOfficialAccountNotificationService wechatOfficialAccountNotificationService;

    public MessageAppService(
            MessageNoticeMapper messageNoticeMapper,
            MessageDeliveryLogMapper messageDeliveryLogMapper,
            OperationAuditService operationAuditService,
            MessagePushService messagePushService,
            SmtpNotificationMailService smtpNotificationMailService,
            WechatOfficialAccountNotificationService wechatOfficialAccountNotificationService
    ) {
        this.messageNoticeMapper = messageNoticeMapper;
        this.messageDeliveryLogMapper = messageDeliveryLogMapper;
        this.operationAuditService = operationAuditService;
        this.messagePushService = messagePushService;
        this.smtpNotificationMailService = smtpNotificationMailService;
        this.wechatOfficialAccountNotificationService = wechatOfficialAccountNotificationService;
    }

    public PageResponse<MessageVO.NoticeVO> listMessages(CurrentUser currentUser, long pageNo, long pageSize) {
        return listNotices(tenantId(currentUser), currentUser.getUserId(), pageNo, pageSize);
    }

    public PageResponse<MessageVO.NoticeVO> listArchive(CurrentUser currentUser, MessageDTO.MessageArchiveQueryRequest request) {
        Long tenantId = tenantId(currentUser);
        long normalizedPageNo = Math.max(request.getPageNo() == null ? 1L : request.getPageNo(), 1L);
        long normalizedPageSize = Math.max(1L, Math.min(request.getPageSize() == null ? 20L : request.getPageSize(), 100L));
        long offset = (normalizedPageNo - 1) * normalizedPageSize;
        NoticeArchiveQuery query = buildNoticeArchiveQuery(currentUser, request, tenantId, normalizedPageSize, offset);
        Long total = messageNoticeMapper.countArchive(query);

        PageResponse<MessageVO.NoticeVO> response = new PageResponse<>();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(total == null ? 0 : total);
        response.setRecords(messageNoticeMapper.listArchive(query));
        return response;
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
        Long count = messageNoticeMapper.countUnread(tenantId(currentUser), currentUser.getUserId());
        return count == null ? 0L : count;
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
        return notice;
    }

    public PageResponse<MessageVO.DeliveryLogVO> listDeliveryLogs(CurrentUser currentUser, MessageDTO.MessageArchiveQueryRequest request) {
        Long tenantId = tenantId(currentUser);
        long normalizedPageNo = Math.max(request.getPageNo() == null ? 1L : request.getPageNo(), 1L);
        long normalizedPageSize = Math.max(1L, Math.min(request.getPageSize() == null ? 20L : request.getPageSize(), 100L));
        long offset = (normalizedPageNo - 1) * normalizedPageSize;
        DeliveryLogQuery query = buildDeliveryLogQuery(request, tenantId, normalizedPageSize, offset);
        Long total = messageDeliveryLogMapper.countDeliveryLogs(query);
        PageResponse<MessageVO.DeliveryLogVO> response = new PageResponse<>();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(total == null ? 0 : total);
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
        return notice;
    }

    @Transactional
    public MessageVO.NoticeVO markMessageRead(CurrentUser currentUser, Long noticeId) {
        MessageVO.NoticeVO notice = markRead(currentUser, noticeId);
        messagePushService.publishRead(tenantId(currentUser), currentUser.getUserId(), notice, countUnread(currentUser).intValue());
        return notice;
    }

    @Transactional
    public MessageVO.UnreadCountVO markAllRead(CurrentUser currentUser) {
        LocalDateTime now = LocalDateTime.now();
        messageNoticeMapper.markAllRead(tenantId(currentUser), currentUser.getUserId(), now);

        Long unreadCount = countUnread(currentUser);
        messagePushService.publishUnreadCount(
                tenantId(currentUser),
                currentUser.getUserId(),
                unreadCount.intValue()
        );

        MessageVO.UnreadCountVO unreadCountVO = new MessageVO.UnreadCountVO();
        unreadCountVO.setUnreadCount(unreadCount);
        return unreadCountVO;
    }

    private PageResponse<MessageVO.NoticeVO> listNotices(Long tenantId, Long userId, long pageNo, long pageSize) {
        long normalizedPageNo = Math.max(pageNo, 1L);
        long normalizedPageSize = Math.max(1L, Math.min(pageSize, 100L));
        long offset = (normalizedPageNo - 1) * normalizedPageSize;

        Long total = messageNoticeMapper.countVisiblePublished(tenantId, userId);

        PageResponse<MessageVO.NoticeVO> response = new PageResponse<>();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(total == null ? 0 : total);
        response.setRecords(messageNoticeMapper.listVisiblePublished(tenantId, userId, normalizedPageSize, offset));
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

    private MessageVO.NoticeVO markRead(CurrentUser currentUser, Long noticeId) {
        MessageVO.NoticeVO notice = findVisibleNoticeById(tenantId(currentUser), noticeId, currentUser.getUserId());
        if (notice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或无权访问");
        }

        LocalDateTime now = LocalDateTime.now();
        messageNoticeMapper.upsertRead(tenantId(currentUser), noticeId, currentUser.getUserId(), now);
        notice.setReadFlag(Boolean.TRUE);
        notice.setReadAt(now);
        return notice;
    }

    private MessageVO.NoticeVO findVisibleNoticeById(Long tenantId, Long noticeId, Long userId) {
        return messageNoticeMapper.findVisibleNoticeById(tenantId, noticeId, userId);
    }

    private MessageVO.NoticeVO findNoticeById(Long tenantId, Long noticeId, Long userId) {
        return messageNoticeMapper.findNoticeById(tenantId, noticeId, userId);
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
        List<RecipientRow> rows;
        if (TARGET_SCOPE_USER.equals(targetScope)) {
            rows = messageNoticeMapper.listUserRecipient(tenantId, targetUserId);
            return rows.stream().map(row -> new Recipient(row.getUserId(), row.getUsername(), row.getEmail(), row.getWechatOpenid())).toList();
        }
        if (TARGET_SCOPE_ROLE.equals(targetScope)) {
            rows = messageNoticeMapper.listRoleRecipients(tenantId, targetRoleId);
            return rows.stream().map(row -> new Recipient(row.getUserId(), row.getUsername(), row.getEmail(), row.getWechatOpenid())).toList();
        }
        rows = messageNoticeMapper.listTenantRecipients(tenantId);
        return rows.stream().map(row -> new Recipient(row.getUserId(), row.getUsername(), row.getEmail(), row.getWechatOpenid())).toList();
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
        query.setLimit(limit);
        query.setOffset(offset);
        return query;
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
        return query;
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

    private record Recipient(Long userId, String username, String email, String wechatOpenid) {
    }
}
