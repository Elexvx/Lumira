package com.legendary.invention.message.app;

import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.constant.PlatformConstants;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.vo.PageResponse;
import com.legendary.invention.message.dto.MessageDTO;
import com.legendary.invention.message.service.MessagePushService;
import com.legendary.invention.message.service.SmtpNotificationMailService;
import com.legendary.invention.message.vo.MessageVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private static final String DELIVERY_SUCCESS = "SUCCESS";
    private static final String DELIVERY_FAILED = "FAILED";
    private static final String DELIVERY_SKIPPED = "SKIPPED";

    private final JdbcTemplate jdbcTemplate;
    private final OperationAuditService operationAuditService;
    private final MessagePushService messagePushService;
    private final SmtpNotificationMailService smtpNotificationMailService;

    public MessageAppService(
            JdbcTemplate jdbcTemplate,
            OperationAuditService operationAuditService,
            MessagePushService messagePushService,
            SmtpNotificationMailService smtpNotificationMailService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationAuditService = operationAuditService;
        this.messagePushService = messagePushService;
        this.smtpNotificationMailService = smtpNotificationMailService;
    }

    public PageResponse<MessageVO.NoticeVO> listMessages(CurrentUser currentUser, long pageNo, long pageSize) {
        return listNotices(tenantId(currentUser), currentUser.getUserId(), pageNo, pageSize);
    }

    public PageResponse<MessageVO.NoticeVO> listArchive(CurrentUser currentUser, MessageDTO.MessageArchiveQueryRequest request) {
        Long tenantId = tenantId(currentUser);
        long normalizedPageNo = Math.max(request.getPageNo() == null ? 1L : request.getPageNo(), 1L);
        long normalizedPageSize = Math.max(1L, Math.min(request.getPageSize() == null ? 20L : request.getPageSize(), 100L));
        long offset = (normalizedPageNo - 1) * normalizedPageSize;

        StringBuilder whereClause = new StringBuilder("""
                where n.tenant_id = ?
                  and n.deleted = 0
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (!canManageArchive(currentUser)) {
            whereClause.append("""
                      and (
                            n.created_by = ?
                         or %s
                      )
                    """.formatted(buildVisibleNoticePredicate("n")));
            params.add(currentUser.getUserId());
            addVisibleNoticeParams(params, currentUser.getUserId());
        }

        if (StringUtils.hasText(request.getKeyword())) {
            whereClause.append("""
                      and (
                            n.title like ?
                         or n.content like ?
                      )
                    """);
            String keywordLike = "%" + request.getKeyword().trim() + "%";
            params.add(keywordLike);
            params.add(keywordLike);
        }
        if (StringUtils.hasText(request.getMessageType())) {
            whereClause.append(" and n.notice_type = ?");
            params.add(request.getMessageType());
        }
        if (StringUtils.hasText(request.getTargetScope())) {
            whereClause.append(" and n.target_scope = ?");
            params.add(request.getTargetScope());
        }
        if (StringUtils.hasText(request.getSourceType())) {
            whereClause.append(" and n.source_type = ?");
            params.add(request.getSourceType());
        }
        if (StringUtils.hasText(request.getPublishStatus())) {
            whereClause.append(" and n.publish_status = ?");
            params.add(request.getPublishStatus());
        }
        if (request.getPublishedAtStart() != null) {
            whereClause.append(" and n.published_at >= ?");
            params.add(Timestamp.valueOf(request.getPublishedAtStart()));
        }
        if (request.getPublishedAtEnd() != null) {
            whereClause.append(" and n.published_at <= ?");
            params.add(Timestamp.valueOf(request.getPublishedAtEnd()));
        }

        Long total = jdbcTemplate.queryForObject("select count(*) from msg_notice n " + whereClause, Long.class, params.toArray());

        List<Object> listParams = new ArrayList<>();
        listParams.add(currentUser.getUserId());
        listParams.addAll(params);
        listParams.add(normalizedPageSize);
        listParams.add(offset);

        String sortColumn = switch (request.getSortField() == null ? "" : request.getSortField()) {
            case "publishedAt" -> "n.published_at";
            case "createdAt" -> "n.created_at";
            case "title" -> "n.title";
            case "sourceType" -> "n.source_type";
            case "publishStatus" -> "n.publish_status";
            case "targetScope" -> "n.target_scope";
            case "messageType" -> "n.notice_type";
            case "readFlag" -> "case when r.id is null then 0 else 1 end";
            default -> null;
        };
        String sortOrder = "ASC".equalsIgnoreCase(request.getSortOrder()) ? "asc" : "desc";
        String orderBy = sortColumn == null
                ? """
                order by n.published_at desc, n.id desc
                """
                : " order by " + sortColumn + " " + sortOrder + ", n.id desc";

        String listSql = """
                select n.id,
                       n.tenant_id as tenantId,
                       n.notice_type as messageType,
                       n.target_scope as targetScope,
                       n.target_user_id as targetUserId,
                       n.target_role_id as targetRoleId,
                       u.username as targetUserName,
                       role.role_name as targetRoleName,
                       n.title,
                       n.content,
                       n.source_type as sourceType,
                       n.publish_status as publishStatus,
                       n.published_at as publishedAt,
                       n.created_by as createdBy,
                       n.updated_by as updatedBy,
                       n.created_at as createdAt,
                       n.updated_at as updatedAt,
                       case when r.id is null then 0 else 1 end as readFlag,
                       r.read_at as readAt
                from msg_notice n
                left join msg_notice_read r
                  on r.tenant_id = n.tenant_id
                 and r.notice_id = n.id
                 and r.user_id = ?
                 and r.deleted = 0
                left join sys_user_tenant ut
                  on ut.tenant_id = n.tenant_id
                 and ut.user_id = n.target_user_id
                 and ut.deleted = 0
                left join sys_user u
                  on u.id = ut.user_id
                 and u.deleted = 0
                left join sys_role role
                  on role.tenant_id = n.tenant_id
                 and role.id = n.target_role_id
                 and role.deleted = 0
                """
                + whereClause
                + orderBy
                + """
                limit ? offset ?
                """;

        PageResponse<MessageVO.NoticeVO> response = new PageResponse<>();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(total == null ? 0 : total);
        response.setRecords(jdbcTemplate.query(listSql, this::mapNoticeRow, listParams.toArray()));
        return response;
    }

    private String buildVisibleNoticePredicate(String noticeAlias) {
        return """
                (%s.target_scope = ?
                 or (%s.target_scope = ? and %s.target_user_id = ?)
                 or (%s.target_scope = ? and exists (
                        select 1
                        from sys_user_role ur
                        where ur.tenant_id = %s.tenant_id
                          and ur.user_id = ?
                          and ur.role_id = %s.target_role_id
                          and ur.deleted = 0
                 )))
                """.formatted(noticeAlias, noticeAlias, noticeAlias, noticeAlias, noticeAlias, noticeAlias);
    }

    private void addVisibleNoticeParams(List<Object> params, Long userId) {
        params.add(TARGET_SCOPE_TENANT);
        params.add(TARGET_SCOPE_USER);
        params.add(userId);
        params.add(TARGET_SCOPE_ROLE);
        params.add(userId);
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
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from msg_notice n
                        where n.tenant_id = ?
                          and n.deleted = 0
                          and n.publish_status = ?
                          and %s
                          and not exists (
                                select 1
                                from msg_notice_read r
                                where r.notice_id = n.id
                                  and r.tenant_id = n.tenant_id
                                  and r.user_id = ?
                                  and r.deleted = 0
                          )
                """.formatted(buildVisibleNoticePredicate("n")),
                Long.class,
                tenantId(currentUser),
                STATUS_PUBLISHED,
                TARGET_SCOPE_TENANT,
                TARGET_SCOPE_USER,
                currentUser.getUserId(),
                TARGET_SCOPE_ROLE,
                currentUser.getUserId(),
                currentUser.getUserId()
        );
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

        StringBuilder whereClause = new StringBuilder("""
                where l.tenant_id = ?
                  and l.deleted = 0
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (StringUtils.hasText(request.getKeyword())) {
            whereClause.append("""
                      and (
                            l.title like ?
                         or l.content like ?
                         or l.target_user_name like ?
                         or l.target_email like ?
                         or l.error_message like ?
                      )
                    """);
            String keywordLike = "%" + request.getKeyword().trim() + "%";
            params.add(keywordLike);
            params.add(keywordLike);
            params.add(keywordLike);
            params.add(keywordLike);
            params.add(keywordLike);
        }
        if (StringUtils.hasText(request.getChannel())) {
            whereClause.append(" and l.channel = ?");
            params.add(request.getChannel());
        }
        if (StringUtils.hasText(request.getTargetScope())) {
            whereClause.append(" and l.target_scope = ?");
            params.add(request.getTargetScope());
        }
        if (StringUtils.hasText(request.getSendStatus())) {
            whereClause.append(" and l.send_status = ?");
            params.add(request.getSendStatus());
        }
        if (request.getPublishedAtStart() != null) {
            whereClause.append(" and l.created_at >= ?");
            params.add(Timestamp.valueOf(request.getPublishedAtStart()));
        }
        if (request.getPublishedAtEnd() != null) {
            whereClause.append(" and l.created_at <= ?");
            params.add(Timestamp.valueOf(request.getPublishedAtEnd()));
        }

        Long total = jdbcTemplate.queryForObject("select count(*) from msg_delivery_log l " + whereClause, Long.class, params.toArray());
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(normalizedPageSize);
        listParams.add(offset);
        String listSql = """
                select l.id,
                       l.tenant_id as tenantId,
                       l.notice_id as noticeId,
                       l.channel,
                       l.target_scope as targetScope,
                       l.target_user_id as targetUserId,
                       l.target_user_name as targetUserName,
                       l.target_email as targetEmail,
                       l.title,
                       l.content,
                       l.send_status as sendStatus,
                       l.error_message as errorMessage,
                       l.sent_at as sentAt,
                       l.created_by as createdBy,
                       l.created_at as createdAt
                from msg_delivery_log l
                """
                + whereClause
                + """
                order by l.created_at desc, l.id desc
                limit ? offset ?
                """;
        PageResponse<MessageVO.DeliveryLogVO> response = new PageResponse<>();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(total == null ? 0 : total);
        response.setRecords(jdbcTemplate.query(listSql, this::mapDeliveryLogRow, listParams.toArray()));
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
        jdbcTemplate.update(
                """
                        insert into msg_notice_read (
                            tenant_id, notice_id, user_id, read_at, created_by, updated_by, deleted
                        )
                        select n.tenant_id,
                               n.id,
                               ?,
                               ?,
                               ?,
                               ?,
                               0
                        from msg_notice n
                        where n.tenant_id = ?
                          and n.deleted = 0
                          and n.publish_status = ?
                          and %s
                          and not exists (
                                select 1
                                from msg_notice_read r
                                where r.notice_id = n.id
                                  and r.tenant_id = n.tenant_id
                                  and r.user_id = ?
                                  and r.deleted = 0
                          )
                        """.formatted(buildVisibleNoticePredicate("n")),
                currentUser.getUserId(),
                now,
                currentUser.getUserId(),
                currentUser.getUserId(),
                tenantId(currentUser),
                STATUS_PUBLISHED,
                TARGET_SCOPE_TENANT,
                TARGET_SCOPE_USER,
                currentUser.getUserId(),
                TARGET_SCOPE_ROLE,
                currentUser.getUserId(),
                currentUser.getUserId()
        );

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

        String countSql = """
                select count(*)
                from msg_notice n
                where n.tenant_id = ?
                  and n.deleted = 0
                  and n.publish_status = ?
                  and %s
                """.formatted(buildVisibleNoticePredicate("n"));
        List<Object> countParams = new ArrayList<>();
        countParams.add(tenantId);
        countParams.add(STATUS_PUBLISHED);
        addVisibleNoticeParams(countParams, userId);

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countParams.toArray());
        String listSql = """
                select n.id,
                       n.tenant_id as tenantId,
                       n.notice_type as messageType,
                       n.target_scope as targetScope,
                       n.target_user_id as targetUserId,
                       n.target_role_id as targetRoleId,
                       u.username as targetUserName,
                       role.role_name as targetRoleName,
                       n.title,
                       n.content,
                       n.source_type as sourceType,
                       n.publish_status as publishStatus,
                       n.published_at as publishedAt,
                       n.created_by as createdBy,
                       n.updated_by as updatedBy,
                       n.created_at as createdAt,
                       n.updated_at as updatedAt,
                       case when r.id is null then 0 else 1 end as readFlag,
                       r.read_at as readAt
                from msg_notice n
                left join msg_notice_read r
                  on r.tenant_id = n.tenant_id
                 and r.notice_id = n.id
                 and r.user_id = ?
                 and r.deleted = 0
                left join sys_user_tenant ut
                  on ut.tenant_id = n.tenant_id
                 and ut.user_id = n.target_user_id
                 and ut.deleted = 0
                left join sys_user u
                  on u.id = ut.user_id
                 and u.deleted = 0
                left join sys_role role
                  on role.tenant_id = n.tenant_id
                 and role.id = n.target_role_id
                 and role.deleted = 0
                where n.tenant_id = ?
                  and n.deleted = 0
                  and n.publish_status = ?
                  and %s
                order by n.id desc
                limit ? offset ?
                """.formatted(buildVisibleNoticePredicate("n"));
        List<Object> listParams = new ArrayList<>();
        listParams.add(userId);
        listParams.add(tenantId);
        listParams.add(STATUS_PUBLISHED);
        addVisibleNoticeParams(listParams, userId);
        listParams.add(normalizedPageSize);
        listParams.add(offset);

        PageResponse<MessageVO.NoticeVO> response = new PageResponse<>();
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        response.setTotal(total == null ? 0 : total);
        response.setRecords(jdbcTemplate.query(listSql, this::mapNoticeRow, listParams.toArray()));
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

        KeyHolder keyHolder = new GeneratedKeyHolder();
        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            insert into msg_notice (
                                tenant_id, notice_type, target_scope, target_user_id, target_role_id, title, content, source_type,
                                publish_status, published_at, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setObject(1, tenantId);
            statement.setString(2, TYPE_MESSAGE);
            statement.setString(3, targetScope);
            statement.setObject(4, targetUserId);
            statement.setObject(5, targetRoleId);
            statement.setString(6, title);
            statement.setString(7, content);
            statement.setString(8, SOURCE_MANUAL);
            statement.setString(9, STATUS_PUBLISHED);
            statement.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            statement.setObject(11, operatorId);
            statement.setObject(12, operatorId);
            return statement;
        }, keyHolder);

        if (updated <= 0 || keyHolder.getKey() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "消息写入失败");
        }
        Long noticeId = keyHolder.getKey().longValue();
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
        int updated = jdbcTemplate.update(
                """
                        update msg_notice
                        set publish_status = ?, updated_by = ?, updated_at = ?
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                STATUS_RETRACTED,
                currentUser.getUserId(),
                LocalDateTime.now(),
                noticeId,
                tenantId(currentUser)
        );
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

        jdbcTemplate.update(
                """
                        insert into msg_notice_read (tenant_id, notice_id, user_id, read_at, created_by, updated_by, deleted)
                        values (?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update read_at = values(read_at), updated_at = values(updated_at), deleted = 0
                        """,
                tenantId(currentUser),
                noticeId,
                currentUser.getUserId(),
                LocalDateTime.now(),
                currentUser.getUserId(),
                currentUser.getUserId()
        );
        notice.setReadFlag(Boolean.TRUE);
        notice.setReadAt(LocalDateTime.now());
        return notice;
    }

    private MessageVO.NoticeVO findVisibleNoticeById(Long tenantId, Long noticeId, Long userId) {
        List<MessageVO.NoticeVO> results = jdbcTemplate.query(
                """
                        select n.id,
                               n.tenant_id as tenantId,
                               n.notice_type as messageType,
                               n.target_scope as targetScope,
                               n.target_user_id as targetUserId,
                               n.target_role_id as targetRoleId,
                               u.username as targetUserName,
                               role.role_name as targetRoleName,
                               n.title,
                               n.content,
                               n.source_type as sourceType,
                               n.publish_status as publishStatus,
                               n.published_at as publishedAt,
                               n.created_by as createdBy,
                               n.updated_by as updatedBy,
                               n.created_at as createdAt,
                               n.updated_at as updatedAt,
                               case when r.id is null then 0 else 1 end as readFlag,
                               r.read_at as readAt
                        from msg_notice n
                        left join msg_notice_read r
                          on r.tenant_id = n.tenant_id
                         and r.notice_id = n.id
                         and r.user_id = ?
                         and r.deleted = 0
                        left join sys_user_tenant ut
                          on ut.tenant_id = n.tenant_id
                         and ut.user_id = n.target_user_id
                         and ut.deleted = 0
                        left join sys_user u
                          on u.id = ut.user_id
                         and u.deleted = 0
                        left join sys_role role
                          on role.tenant_id = n.tenant_id
                         and role.id = n.target_role_id
                         and role.deleted = 0
                        where n.tenant_id = ?
                          and n.id = ?
                          and n.deleted = 0
                          and %s
                        limit 1
                        """.formatted(buildVisibleNoticePredicate("n")),
                this::mapNoticeRow,
                userId,
                tenantId,
                noticeId,
                TARGET_SCOPE_TENANT,
                TARGET_SCOPE_USER,
                userId,
                TARGET_SCOPE_ROLE,
                userId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    private MessageVO.NoticeVO findNoticeById(Long tenantId, Long noticeId, Long userId) {
        List<MessageVO.NoticeVO> results = jdbcTemplate.query(
                """
                        select n.id,
                               n.tenant_id as tenantId,
                               n.notice_type as messageType,
                               n.target_scope as targetScope,
                               n.target_user_id as targetUserId,
                               n.target_role_id as targetRoleId,
                               u.username as targetUserName,
                               role.role_name as targetRoleName,
                               n.title,
                               n.content,
                               n.source_type as sourceType,
                               n.publish_status as publishStatus,
                               n.published_at as publishedAt,
                               n.created_by as createdBy,
                               n.updated_by as updatedBy,
                               n.created_at as createdAt,
                               n.updated_at as updatedAt,
                               case when r.id is null then 0 else 1 end as readFlag,
                               r.read_at as readAt
                        from msg_notice n
                        left join msg_notice_read r
                          on r.tenant_id = n.tenant_id
                         and r.notice_id = n.id
                         and r.user_id = ?
                         and r.deleted = 0
                        left join sys_user_tenant ut
                          on ut.tenant_id = n.tenant_id
                         and ut.user_id = n.target_user_id
                         and ut.deleted = 0
                        left join sys_user u
                          on u.id = ut.user_id
                         and u.deleted = 0
                        left join sys_role role
                          on role.tenant_id = n.tenant_id
                         and role.id = n.target_role_id
                         and role.deleted = 0
                        where n.tenant_id = ?
                          and n.id = ?
                          and n.deleted = 0
                        limit 1
                        """,
                this::mapNoticeRow,
                userId,
                tenantId,
                noticeId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    private Set<String> normalizeChannels(List<String> channels) {
        List<String> source = channels == null || channels.isEmpty() ? List.of(CHANNEL_INBOX) : channels;
        Set<String> normalized = new LinkedHashSet<>();
        for (String channel : source) {
            if (!StringUtils.hasText(channel)) {
                continue;
            }
            String value = channel.trim().toUpperCase();
            if (!CHANNEL_INBOX.equals(value) && !CHANNEL_EMAIL.equals(value)) {
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

    private List<Recipient> resolveEmailRecipients(Long tenantId, String targetScope, Long targetUserId, Long targetRoleId) {
        if (TARGET_SCOPE_USER.equals(targetScope)) {
            return jdbcTemplate.query(
                    """
                            select u.id as userId, u.username, u.email
                            from sys_user u
                            join sys_user_tenant ut
                              on ut.user_id = u.id
                             and ut.tenant_id = ?
                             and ut.deleted = 0
                            where u.id = ?
                              and u.deleted = 0
                              and u.status = 'ENABLED'
                            limit 1
                            """,
                    (rs, rowNum) -> new Recipient(rs.getLong("userId"), rs.getString("username"), rs.getString("email")),
                    tenantId,
                    targetUserId
            );
        }
        if (TARGET_SCOPE_ROLE.equals(targetScope)) {
            return jdbcTemplate.query(
                    """
                            select distinct u.id as userId, u.username, u.email
                            from sys_user u
                            join sys_user_role ur
                              on ur.user_id = u.id
                             and ur.tenant_id = ?
                             and ur.role_id = ?
                             and ur.deleted = 0
                            where u.deleted = 0
                              and u.status = 'ENABLED'
                            order by u.id asc
                            """,
                    (rs, rowNum) -> new Recipient(rs.getLong("userId"), rs.getString("username"), rs.getString("email")),
                    tenantId,
                    targetRoleId
            );
        }
        return jdbcTemplate.query(
                """
                        select distinct u.id as userId, u.username, u.email
                        from sys_user u
                        join sys_user_tenant ut
                          on ut.user_id = u.id
                         and ut.tenant_id = ?
                         and ut.deleted = 0
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                        order by u.id asc
                        """,
                (rs, rowNum) -> new Recipient(rs.getLong("userId"), rs.getString("username"), rs.getString("email")),
                tenantId
        );
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
        jdbcTemplate.update(
                """
                        insert into msg_delivery_log (
                            tenant_id, notice_id, channel, target_scope, target_user_id, target_user_name, target_email,
                            title, content, send_status, error_message, sent_at, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantId,
                noticeId,
                channel,
                targetScope,
                targetUserId,
                targetUserName,
                targetEmail,
                title,
                content,
                sendStatus,
                errorMessage,
                DELIVERY_SUCCESS.equals(sendStatus) ? LocalDateTime.now() : null,
                operatorId,
                operatorId
        );
    }

    private MessageVO.NoticeVO mapNoticeRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        MessageVO.NoticeVO notice = new MessageVO.NoticeVO();
        notice.setId(rs.getLong("id"));
        notice.setTenantId(rs.getLong("tenantId"));
        notice.setMessageType(rs.getString("messageType"));
        notice.setTargetScope(rs.getString("targetScope"));
        long targetUserId = rs.getLong("targetUserId");
        notice.setTargetUserId(rs.wasNull() ? null : targetUserId);
        long targetRoleId = rs.getLong("targetRoleId");
        notice.setTargetRoleId(rs.wasNull() ? null : targetRoleId);
        notice.setTargetUserName(rs.getString("targetUserName"));
        notice.setTargetRoleName(rs.getString("targetRoleName"));
        notice.setTitle(rs.getString("title"));
        notice.setContent(rs.getString("content"));
        notice.setSourceType(rs.getString("sourceType"));
        notice.setPublishStatus(rs.getString("publishStatus"));
        notice.setPublishedAt(toLocalDateTime(rs.getTimestamp("publishedAt")));
        long createdBy = rs.getLong("createdBy");
        notice.setCreatedBy(rs.wasNull() ? null : createdBy);
        long updatedBy = rs.getLong("updatedBy");
        notice.setUpdatedBy(rs.wasNull() ? null : updatedBy);
        notice.setCreatedAt(toLocalDateTime(rs.getTimestamp("createdAt")));
        notice.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updatedAt")));
        notice.setReadFlag(rs.getInt("readFlag") == 1);
        notice.setReadAt(toLocalDateTime(rs.getTimestamp("readAt")));
        return notice;
    }

    private MessageVO.DeliveryLogVO mapDeliveryLogRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        MessageVO.DeliveryLogVO log = new MessageVO.DeliveryLogVO();
        log.setId(rs.getLong("id"));
        log.setTenantId(rs.getLong("tenantId"));
        long noticeId = rs.getLong("noticeId");
        log.setNoticeId(rs.wasNull() ? null : noticeId);
        log.setChannel(rs.getString("channel"));
        log.setTargetScope(rs.getString("targetScope"));
        long targetUserId = rs.getLong("targetUserId");
        log.setTargetUserId(rs.wasNull() ? null : targetUserId);
        log.setTargetUserName(rs.getString("targetUserName"));
        log.setTargetEmail(rs.getString("targetEmail"));
        log.setTitle(rs.getString("title"));
        log.setContent(rs.getString("content"));
        log.setSendStatus(rs.getString("sendStatus"));
        log.setErrorMessage(rs.getString("errorMessage"));
        log.setSentAt(toLocalDateTime(rs.getTimestamp("sentAt")));
        long createdBy = rs.getLong("createdBy");
        log.setCreatedBy(rs.wasNull() ? null : createdBy);
        log.setCreatedAt(toLocalDateTime(rs.getTimestamp("createdAt")));
        return log;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long tenantId(CurrentUser currentUser) {
        return PlatformConstants.PLATFORM_TENANT_ID;
    }

    private record Recipient(Long userId, String username, String email) {
    }
}
