package com.yourcompany.saas.modules.message.app;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.common.vo.PageResponse;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.modules.audit.app.OperationAuditService;
import com.yourcompany.saas.modules.message.dto.MessageDTO;
import com.yourcompany.saas.modules.message.service.MessagePushService;
import com.yourcompany.saas.modules.message.vo.MessageVO;
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
import java.util.List;

@Service
public class MessageAppService {

    private static final String TYPE_MESSAGE = "MESSAGE";
    private static final String TARGET_SCOPE_TENANT = "TENANT";
    private static final String TARGET_SCOPE_USER = "USER";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_RETRACTED = "RETRACTED";
    private static final String SOURCE_MANUAL = "MANUAL";

    private final JdbcTemplate jdbcTemplate;
    private final OperationAuditService operationAuditService;
    private final MessagePushService messagePushService;

    public MessageAppService(
            JdbcTemplate jdbcTemplate,
            OperationAuditService operationAuditService,
            MessagePushService messagePushService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationAuditService = operationAuditService;
        this.messagePushService = messagePushService;
    }

    public PageResponse<MessageVO.NoticeVO> listAnnouncements(CurrentUser currentUser, long pageNo, long pageSize) {
        return listMessages(currentUser, pageNo, pageSize);
    }

    public PageResponse<MessageVO.NoticeVO> listMessages(CurrentUser currentUser, long pageNo, long pageSize) {
        return listNotices(currentUser.getCurrentTenantId(), currentUser.getUserId(), pageNo, pageSize);
    }

    public Long countUnread(CurrentUser currentUser) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from msg_notice n
                        where n.tenant_id = ?
                          and n.deleted = 0
                          and n.publish_status = ?
                          and (n.target_scope = ? or (n.target_scope = ? and n.target_user_id = ?))
                          and not exists (
                                select 1
                                from msg_notice_read r
                                where r.notice_id = n.id
                                  and r.tenant_id = n.tenant_id
                                  and r.user_id = ?
                                  and r.deleted = 0
                          )
                        """,
                Long.class,
                currentUser.getCurrentTenantId(),
                STATUS_PUBLISHED,
                TARGET_SCOPE_TENANT,
                TARGET_SCOPE_USER,
                TARGET_SCOPE_TENANT,
                currentUser.getUserId(),
                currentUser.getUserId()
        );
        return count == null ? 0L : count;
    }

    @Transactional
    public MessageVO.NoticeVO createAnnouncement(CurrentUser currentUser, MessageDTO.AnnouncementCreateRequest request) {
        MessageVO.NoticeVO notice = insertInboxNotice(
                currentUser.getCurrentTenantId(),
                currentUser.getUserId(),
                TARGET_SCOPE_TENANT,
                null,
                request.getTitle(),
                request.getContent(),
                SOURCE_MANUAL
        );
        messagePushService.publishCreated(notice);
        operationAuditService.log(
                currentUser.getCurrentTenantId(),
                currentUser.getUserId(),
                currentUser.getUsername(),
                "message",
                "publish-message",
                "CREATE",
                "SUCCESS",
                "发布站内信: " + notice.getTitle()
        );
        return notice;
    }

    @Transactional
    public MessageVO.NoticeVO createAnnouncement(Long tenantId, Long operatorId, String operatorName, MessageDTO.AnnouncementCreateRequest request, String sourceType) {
        MessageVO.NoticeVO notice = insertInboxNotice(
                tenantId,
                operatorId,
                TARGET_SCOPE_TENANT,
                null,
                request.getTitle(),
                request.getContent(),
                sourceType
        );
        messagePushService.publishCreated(notice);
        operationAuditService.log(
                tenantId,
                operatorId,
                operatorName,
                "message-openapi",
                "publish-message",
                "OPENAPI",
                "SUCCESS",
                "开放接口发布站内信: " + notice.getTitle()
        );
        return notice;
    }

    @Transactional
    public MessageVO.NoticeVO createMessage(CurrentUser currentUser, MessageDTO.MessageCreateRequest request) {
        MessageVO.NoticeVO notice = insertInboxNotice(
                currentUser.getCurrentTenantId(),
                currentUser.getUserId(),
                request.getTargetScope(),
                request.getTargetUserId(),
                request.getTitle(),
                request.getContent(),
                SOURCE_MANUAL
        );
        messagePushService.publishCreated(notice);
        operationAuditService.log(
                currentUser.getCurrentTenantId(),
                currentUser.getUserId(),
                currentUser.getUsername(),
                "message",
                "send-message",
                "CREATE",
                "SUCCESS",
                "发送站内信: " + notice.getTitle()
        );
        return notice;
    }

    @Transactional
    public MessageVO.NoticeVO createMessage(Long tenantId, Long operatorId, String operatorName, MessageDTO.MessageCreateRequest request, String sourceType) {
        MessageVO.NoticeVO notice = insertInboxNotice(
                tenantId,
                operatorId,
                request.getTargetScope(),
                request.getTargetUserId(),
                request.getTitle(),
                request.getContent(),
                sourceType
        );
        messagePushService.publishCreated(notice);
        operationAuditService.log(
                tenantId,
                operatorId,
                operatorName,
                "message-openapi",
                "send-message",
                "OPENAPI",
                "SUCCESS",
                "开放接口发送站内信: " + notice.getTitle()
        );
        return notice;
    }

    @Transactional
    public MessageVO.NoticeVO retractAnnouncement(CurrentUser currentUser, Long noticeId) {
        MessageVO.NoticeVO notice = retractNotice(currentUser, noticeId);
        messagePushService.publishRetracted(notice);
        operationAuditService.log(
                currentUser.getCurrentTenantId(),
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
    public MessageVO.NoticeVO retractMessage(CurrentUser currentUser, Long noticeId) {
        MessageVO.NoticeVO notice = retractNotice(currentUser, noticeId);
        messagePushService.publishRetracted(notice);
        operationAuditService.log(
                currentUser.getCurrentTenantId(),
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
    public MessageVO.NoticeVO markAnnouncementRead(CurrentUser currentUser, Long noticeId) {
        MessageVO.NoticeVO notice = markRead(currentUser, noticeId);
        messagePushService.publishRead(currentUser.getCurrentTenantId(), currentUser.getUserId(), notice, countUnread(currentUser).intValue());
        return notice;
    }

    @Transactional
    public MessageVO.NoticeVO markMessageRead(CurrentUser currentUser, Long noticeId) {
        MessageVO.NoticeVO notice = markRead(currentUser, noticeId);
        messagePushService.publishRead(currentUser.getCurrentTenantId(), currentUser.getUserId(), notice, countUnread(currentUser).intValue());
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
                          and (n.target_scope = ? or (n.target_scope = ? and n.target_user_id = ?))
                          and not exists (
                                select 1
                                from msg_notice_read r
                                where r.notice_id = n.id
                                  and r.tenant_id = n.tenant_id
                                  and r.user_id = ?
                                  and r.deleted = 0
                          )
                        """,
                currentUser.getUserId(),
                now,
                currentUser.getUserId(),
                currentUser.getUserId(),
                currentUser.getCurrentTenantId(),
                STATUS_PUBLISHED,
                TARGET_SCOPE_TENANT,
                TARGET_SCOPE_USER,
                TARGET_SCOPE_TENANT,
                currentUser.getUserId(),
                currentUser.getUserId()
        );

        Long unreadCount = countUnread(currentUser);
        messagePushService.publishUnreadCount(
                currentUser.getCurrentTenantId(),
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

        String countPredicate = "n.target_scope = ? or (n.target_scope = ? and n.target_user_id = ?)";
        String countSql = """
                select count(*)
                from msg_notice n
                where n.tenant_id = ?
                  and n.deleted = 0
                  and n.publish_status = ?
                  and %s
                """.formatted(countPredicate);
        List<Object> countParams = new ArrayList<>();
        countParams.add(tenantId);
        countParams.add(STATUS_PUBLISHED);
        countParams.add(TARGET_SCOPE_TENANT);
        countParams.add(TARGET_SCOPE_USER);
        countParams.add(userId);

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countParams.toArray());
        String listSql = """
                select n.id,
                       n.tenant_id as tenantId,
                       n.notice_type as messageType,
                       n.target_scope as targetScope,
                       n.target_user_id as targetUserId,
                       n.title,
                       n.content,
                       n.source_type as sourceType,
                       n.publish_status as publishStatus,
                       n.published_at as publishedAt,
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
                where n.tenant_id = ?
                  and n.deleted = 0
                  and n.publish_status = ?
                  and %s
                order by n.id desc
                limit ? offset ?
                """.formatted(countPredicate);
        List<Object> listParams = new ArrayList<>();
        listParams.add(userId);
        listParams.add(tenantId);
        listParams.add(STATUS_PUBLISHED);
        listParams.add(TARGET_SCOPE_TENANT);
        listParams.add(TARGET_SCOPE_USER);
        listParams.add(userId);
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
            String title,
            String content,
            String sourceType
    ) {
        if (!StringUtils.hasText(targetScope)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "targetScope不能为空");
        }
        if (TARGET_SCOPE_USER.equals(targetScope) && targetUserId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "targetUserId不能为空");
        }
        return insertNotice(
                tenantId,
                operatorId,
                targetScope,
                targetUserId,
                title,
                content,
                sourceType
        );
    }

    private MessageVO.NoticeVO insertNotice(
            Long tenantId,
            Long operatorId,
            String targetScope,
            Long targetUserId,
            String title,
            String content,
            String sourceType
    ) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "标题和内容不能为空");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            insert into msg_notice (
                                tenant_id, notice_type, target_scope, target_user_id, title, content, source_type,
                                publish_status, published_at, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setObject(1, tenantId);
            statement.setString(2, TYPE_MESSAGE);
            statement.setString(3, targetScope);
            statement.setObject(4, targetUserId);
            statement.setString(5, title);
            statement.setString(6, content);
            statement.setString(7, sourceType);
            statement.setString(8, STATUS_PUBLISHED);
            statement.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            statement.setObject(10, operatorId);
            statement.setObject(11, operatorId);
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
        MessageVO.NoticeVO notice = findNoticeById(currentUser.getCurrentTenantId(), noticeId, currentUser.getUserId());
        if (notice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或不属于当前租户");
        }
        jdbcTemplate.update(
                """
                        update msg_notice
                        set publish_status = ?, updated_by = ?, updated_at = ?
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                STATUS_RETRACTED,
                currentUser.getUserId(),
                LocalDateTime.now(),
                noticeId,
                currentUser.getCurrentTenantId()
        );
        notice.setPublishStatus(STATUS_RETRACTED);
        return notice;
    }

    private MessageVO.NoticeVO markRead(CurrentUser currentUser, Long noticeId) {
        MessageVO.NoticeVO notice = findNoticeById(currentUser.getCurrentTenantId(), noticeId, currentUser.getUserId());
        if (notice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或不属于当前租户");
        }
        if (TARGET_SCOPE_USER.equalsIgnoreCase(notice.getTargetScope()) && notice.getTargetUserId() != null && !notice.getTargetUserId().equals(currentUser.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权读取该通知");
        }

        jdbcTemplate.update(
                """
                        insert into msg_notice_read (tenant_id, notice_id, user_id, read_at, created_by, updated_by, deleted)
                        values (?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update read_at = values(read_at), updated_at = values(updated_at), deleted = 0
                        """,
                currentUser.getCurrentTenantId(),
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

    private MessageVO.NoticeVO findNoticeById(Long tenantId, Long noticeId, Long userId) {
        List<MessageVO.NoticeVO> results = jdbcTemplate.query(
                """
                        select n.id,
                               n.tenant_id as tenantId,
                               n.notice_type as messageType,
                               n.target_scope as targetScope,
                               n.target_user_id as targetUserId,
                               n.title,
                               n.content,
                               n.source_type as sourceType,
                               n.publish_status as publishStatus,
                               n.published_at as publishedAt,
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

    private MessageVO.NoticeVO mapNoticeRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        MessageVO.NoticeVO notice = new MessageVO.NoticeVO();
        notice.setId(rs.getLong("id"));
        notice.setTenantId(rs.getLong("tenantId"));
        notice.setMessageType(rs.getString("messageType"));
        notice.setTargetScope(rs.getString("targetScope"));
        long targetUserId = rs.getLong("targetUserId");
        notice.setTargetUserId(rs.wasNull() ? null : targetUserId);
        notice.setTitle(rs.getString("title"));
        notice.setContent(rs.getString("content"));
        notice.setSourceType(rs.getString("sourceType"));
        notice.setPublishStatus(rs.getString("publishStatus"));
        notice.setPublishedAt(toLocalDateTime(rs.getTimestamp("publishedAt")));
        notice.setCreatedAt(toLocalDateTime(rs.getTimestamp("createdAt")));
        notice.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updatedAt")));
        notice.setReadFlag(rs.getInt("readFlag") == 1);
        notice.setReadAt(toLocalDateTime(rs.getTimestamp("readAt")));
        return notice;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
