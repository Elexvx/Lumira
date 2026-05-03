package com.legendary.invention.saas.modules.message.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.message.dto.MessageDTO;
import com.legendary.invention.saas.modules.message.service.MessagePushService;
import com.legendary.invention.saas.modules.message.vo.MessageVO;
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
    private static final String TARGET_SCOPE_ROLE = "ROLE";
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

    public PageResponse<MessageVO.NoticeVO> listMessages(CurrentUser currentUser, long pageNo, long pageSize) {
        return listNotices(currentUser.getCurrentTenantId(), currentUser.getUserId(), pageNo, pageSize);
    }

    public PageResponse<MessageVO.NoticeVO> listArchive(CurrentUser currentUser, MessageDTO.MessageArchiveQueryRequest request) {
        Long tenantId = currentUser.getCurrentTenantId();
        long normalizedPageNo = Math.max(request.getPageNo() == null ? 1L : request.getPageNo(), 1L);
        long normalizedPageSize = Math.max(1L, Math.min(request.getPageSize() == null ? 20L : request.getPageSize(), 100L));
        long offset = (normalizedPageNo - 1) * normalizedPageSize;

        StringBuilder whereClause = new StringBuilder("""
                where n.tenant_id = ?
                  and n.deleted = 0
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);

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
                currentUser.getCurrentTenantId(),
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
        MessageVO.NoticeVO notice = insertInboxNotice(
                currentUser.getCurrentTenantId(),
                currentUser.getUserId(),
                request.getTargetScope(),
                request.getTargetUserId(),
                request.getTargetRoleId(),
                request.getTitle(),
                request.getContent()
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
                currentUser.getCurrentTenantId(),
                STATUS_PUBLISHED,
                TARGET_SCOPE_TENANT,
                TARGET_SCOPE_USER,
                currentUser.getUserId(),
                TARGET_SCOPE_ROLE,
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
        MessageVO.NoticeVO notice = findNoticeById(currentUser.getCurrentTenantId(), noticeId, currentUser.getUserId());
        if (notice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或不属于当前租户");
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
                currentUser.getCurrentTenantId()
        );
        if (updated <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或不属于当前租户");
        }
        MessageVO.NoticeVO retractedNotice = findNoticeById(currentUser.getCurrentTenantId(), noticeId, currentUser.getUserId());
        if (retractedNotice == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "通知撤回后读取失败");
        }
        return retractedNotice;
    }

    private MessageVO.NoticeVO markRead(CurrentUser currentUser, Long noticeId) {
        MessageVO.NoticeVO notice = findVisibleNoticeById(currentUser.getCurrentTenantId(), noticeId, currentUser.getUserId());
        if (notice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或不属于当前租户");
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

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
