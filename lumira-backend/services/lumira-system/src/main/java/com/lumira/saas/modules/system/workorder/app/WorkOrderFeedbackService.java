package com.lumira.saas.modules.system.workorder.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.workorder.dto.WorkOrderFeedbackDTO;
import com.lumira.saas.modules.system.workorder.vo.WorkOrderFeedbackVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class WorkOrderFeedbackService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DETAIL_HTML_LENGTH = 200_000;
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String SUPPORT_FEEDBACK_BUCKET = "support_feedback";
    private static final String PERMISSION_VIEW = "plugin:work-order-feedback:view";
    private static final String PERMISSION_CREATE = "plugin:work-order-feedback:create";
    private static final String PERMISSION_MANAGE = "plugin:work-order-feedback:manage";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MyBatisQueryOperations jdbcTemplate;
    private final WorkOrderFeedbackPluginStateService pluginStateService;
    private final FileInternalApi fileInternalApi;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    @Autowired
    public WorkOrderFeedbackService(
            MyBatisQueryOperations jdbcTemplate,
            WorkOrderFeedbackPluginStateService pluginStateService,
            FileInternalApi fileInternalApi,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, pluginStateService, fileInternalApi, permissionSnapshotService, null, null);
    }

    @Autowired
    public WorkOrderFeedbackService(
            MyBatisQueryOperations jdbcTemplate,
            WorkOrderFeedbackPluginStateService pluginStateService,
            FileInternalApi fileInternalApi,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.pluginStateService = pluginStateService;
        this.fileInternalApi = fileInternalApi;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    public WorkOrderFeedbackService(
            MyBatisQueryOperations jdbcTemplate,
            WorkOrderFeedbackPluginStateService pluginStateService,
            FileInternalApi fileInternalApi,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, pluginStateService, fileInternalApi, permissionSnapshotService, null, sessionAuthenticationService);
    }

    public WorkOrderFeedbackService(
            MyBatisQueryOperations jdbcTemplate,
            WorkOrderFeedbackPluginStateService pluginStateService,
            FileInternalApi fileInternalApi
    ) {
        this(jdbcTemplate, pluginStateService, fileInternalApi, null, null, null);
    }

    public PageResponse<WorkOrderFeedbackVO.WorkOrderRecord> list(
            CurrentUser currentUser,
            String keyword,
            String status,
            String priority,
            String scope,
            long pageNo,
            long pageSize
    ) {
        boolean adminScope = isAdminScope(scope);
        Long userId;
        if (adminScope) {
            userId = requireManagePermission(currentUser);
        } else {
            userId = requireViewPermission(currentUser);
        }
        String userUuid = trustedUserUuid(currentUser);
        pluginStateService.ensureEnabled(currentUser);
        StringBuilder baseSql = new StringBuilder("""
                from sys_work_order_feedback
                where deleted = 0
                """);
        List<Object> params = new ArrayList<>();
        if (!adminScope) {
            baseSql.append(" and submitter_id = ? and submitter_uuid = ?");
            params.add(userId);
            params.add(userUuid);
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            baseSql.append(" and (title like ? or submitter_name like ?)");
            params.add(like);
            params.add(like);
        }
        if (StringUtils.hasText(status)) {
            baseSql.append(" and status = ?");
            params.add(normalizeStatus(status, false));
        }
        if (StringUtils.hasText(priority)) {
            baseSql.append(" and priority = ?");
            params.add(normalizePriority(priority));
        }
        String selectSql = """
                select id, title, detail_html as detailHtml,
                       priority, status, submitter_id as submitterId, submitter_uuid as submitterUuid, submitter_name as submitterName,
                       admin_reply as adminReply, handled_by as handledBy, handled_at as handledAt,
                       created_at as createdAt, updated_at as updatedAt
                """ + baseSql + " order by updated_at desc, id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, pageNo, pageSize, params);
    }

    public WorkOrderFeedbackVO.WorkOrderRecord detail(CurrentUser currentUser, Long id, String scope) {
        requirePositiveId(id);
        boolean adminScope = isAdminScope(scope);
        Long userId;
        if (adminScope) {
            userId = requireManagePermission(currentUser);
        } else {
            userId = requireViewPermission(currentUser);
        }
        String userUuid = trustedUserUuid(currentUser);
        pluginStateService.ensureEnabled(currentUser);
        String visibilitySql = adminScope ? "" : " and submitter_id = ? and submitter_uuid = ?";
        List<Object> params = new ArrayList<>(List.of(id));
        if (!adminScope) {
            params.add(userId);
            params.add(userUuid);
        }
        WorkOrderFeedbackVO.WorkOrderRecord record = jdbcTemplate.queryForObject("""
                select id, title, detail_html as detailHtml,
                       priority, status, submitter_id as submitterId, submitter_uuid as submitterUuid, submitter_name as submitterName,
                       admin_reply as adminReply, handled_by as handledBy, handled_at as handledAt,
                       created_at as createdAt, updated_at as updatedAt
                from sys_work_order_feedback
                where id = ?
                  and deleted = 0
                """ + visibilitySql,
                new BeanPropertyRowMapper<>(WorkOrderFeedbackVO.WorkOrderRecord.class),
                params.toArray()
        );
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "\u5de5\u5355\u4e0d\u5b58\u5728\u6216\u65e0\u6743\u67e5\u770b");
        }
        formatDateFields(record);
        return record;
    }

    public FileObjectDTO uploadImage(CurrentUser currentUser, MultipartFile file) {
        Long userId = requireCreatePermission(currentUser);
        String username = trustedUsername(currentUser);
        String userUuid = trustedUserUuid(currentUser);
        pluginStateService.ensureEnabled(currentUser);
        try {
            return fileInternalApi.uploadImageForUser(
                    file,
                    "\u5de5\u5355\u53cd\u9988",
                    "\u5de5\u5355\u53cd\u9988\u5bcc\u6587\u672c\u56fe\u7247",
                    SUPPORT_FEEDBACK_BUCKET,
                    userId,
                    userUuid,
                    username
            );
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "\u56fe\u7247\u4e0a\u4f20\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u5b58\u50a8\u7a7a\u95f4\u914d\u7f6e\u6216\u7a0d\u540e\u91cd\u8bd5");
        }
    }

    @Transactional
    public WorkOrderFeedbackVO.WorkOrderRecord create(CurrentUser currentUser, WorkOrderFeedbackDTO.CreateRequest request) {
        Long userId = requireCreatePermission(currentUser);
        String userUuid = trustedUserUuid(currentUser);
        String submitterName = trustedUsername(currentUser);
        pluginStateService.ensureEnabled(currentUser);
        String title = normalizeRequiredText(request == null ? null : request.getTitle(), 160, "\u8bf7\u586b\u5199\u5de5\u5355\u6807\u9898");
        String detailHtml = normalizeRequiredText(request == null ? null : request.getDetailHtml(), MAX_DETAIL_HTML_LENGTH, "\u8bf7\u586b\u5199\u95ee\u9898\u8be6\u60c5");
        String priority = normalizePriority(request == null ? null : request.getPriority());
        int inserted = jdbcTemplate.update("""
                insert into sys_work_order_feedback (
                    title, detail_html, priority, status, submitter_id, submitter_uuid, submitter_name,
                    created_by, created_by_uuid, created_at, updated_by, updated_by_uuid, updated_at, deleted
                ) values (?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, now(), ?, ?, now(), 0)
                """,
                title, detailHtml, priority, userId,
                userUuid, submitterName, userId, userUuid, userId, userUuid);
        if (inserted != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Work order changed, please retry");
        }
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return detail(currentUser, id, "mine");
    }

    @Transactional
    public WorkOrderFeedbackVO.WorkOrderRecord updateStatus(CurrentUser currentUser, Long id, WorkOrderFeedbackDTO.StatusRequest request) {
        requirePositiveId(id);
        Long userId = requireManagePermission(currentUser);
        pluginStateService.ensureEnabled(currentUser);
        WorkOrderFeedbackVO.WorkOrderRecord currentRecord = detail(currentUser, id, "admin");
        String status = normalizeStatus(request == null ? null : request.getStatus(), true);
        String adminReply = normalizeNullableText(request == null ? null : request.getAdminReply(), 4000);
        int updated = jdbcTemplate.update("""
                update sys_work_order_feedback
                   set status = ?,
                       admin_reply = ?,
                       handled_by = ?,
                       handled_at = case when ? in ('RESOLVED', 'CLOSED') then now() else handled_at end,
                       updated_by = ?,
                       updated_by_uuid = ?,
                       updated_at = now()
                  where id = ?
                    and status = ?
                    and submitter_id = ?
                    and submitter_uuid = ?
                    and deleted = 0
                """,
                status,
                adminReply,
                userId,
                status,
                userId,
                trustedUserUuid(currentUser),
                id,
                currentRecord.getStatus(),
                currentRecord.getSubmitterId(),
                currentRecord.getSubmitterUuid());
        if (updated == 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Work order changed, please retry");
        }
        return detail(currentUser, id, "admin");
    }

    private PageResponse<WorkOrderFeedbackVO.WorkOrderRecord> pageQuery(String selectSql, String countSql, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long offset = (safePageNo - 1L) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        List<WorkOrderFeedbackVO.WorkOrderRecord> records = jdbcTemplate.query(
                selectSql + " limit ? offset ?",
                new BeanPropertyRowMapper<>(WorkOrderFeedbackVO.WorkOrderRecord.class),
                queryParams.toArray()
        );
        records.forEach(this::formatDateFields);
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : nullToZero(jdbcTemplate.queryForObject(countSql, Long.class, params.toArray()));
        PageResponse<WorkOrderFeedbackVO.WorkOrderRecord> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private boolean isAdminScope(String scope) {
        return "admin".equalsIgnoreCase(scope);
    }

    private Long requireManagePermission(CurrentUser currentUser) {
        Long userId = currentUserId(currentUser);
        if (!hasPermission(currentUser, PERMISSION_MANAGE)) {
            throw new BizException(ErrorCode.FORBIDDEN, "\u7f3a\u5c11\u5de5\u5355\u5904\u7406\u6743\u9650");
        }
        return userId;
    }

    private Long requireViewPermission(CurrentUser currentUser) {
        Long userId = currentUserId(currentUser);
        if (!hasPermission(currentUser, PERMISSION_VIEW)) {
            throw new BizException(ErrorCode.FORBIDDEN, "\u7f3a\u5c11\u5de5\u5355\u67e5\u770b\u6743\u9650");
        }
        return userId;
    }

    private Long requireCreatePermission(CurrentUser currentUser) {
        Long userId = currentUserId(currentUser);
        if (!hasPermission(currentUser, PERMISSION_CREATE)) {
            throw new BizException(ErrorCode.FORBIDDEN, "\u7f3a\u5c11\u5de5\u5355\u521b\u5efa\u6743\u9650");
        }
        return userId;
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        return isTrustedCurrentUser(currentUser)
                && currentUser.getPermissions() != null
                && (currentUser.getPermissions().contains("*")
                || currentUser.getPermissions().contains(permissionKey));
    }

    private Long currentUserId(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser.getUserId();
    }

    private void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "\u5de5\u5355\u4e0d\u5b58\u5728");
        }
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (permissionSnapshotService == null) {
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = userSnapshot.userUuid().trim();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotService.PermissionSnapshot snapshot = currentUser.getSimulatedRoleId() != null
                ? permissionSnapshotService.loadRoleSnapshot(currentUser.getSimulatedRoleId())
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        if (authenticatedAccess == null || !AuthenticationTrustSupport.isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return authenticatedAccess.currentUser();
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(source.getSimulatedRoleId());
        target.setLoginType(source.getLoginType());
    }

    private String normalizeStatus(String status, boolean requireKnown) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized) && !requireKnown) {
            return normalized;
        }
        if (List.of("OPEN", "PROCESSING", "RESOLVED", "CLOSED").contains(normalized)) {
            return normalized;
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "\u5de5\u5355\u72b6\u6001\u65e0\u6548");
    }

    private String normalizePriority(String priority) {
        String normalized = priority == null ? "" : priority.trim().toUpperCase(Locale.ROOT);
        return List.of("LOW", "NORMAL", "HIGH", "URGENT").contains(normalized) ? normalized : "NORMAL";
    }

    private String normalizeRequiredText(String value, int maxLength, String message) {
        String normalized = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.BAD_REQUEST, "\u5185\u5bb9\u957f\u5ea6\u4e0d\u80fd\u8d85\u8fc7 " + maxLength + " \u4e2a\u5b57\u7b26");
        }
        return normalized;
    }

    private String normalizeNullableText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.BAD_REQUEST, "\u56de\u590d\u957f\u5ea6\u4e0d\u80fd\u8d85\u8fc7 " + maxLength + " \u4e2a\u5b57\u7b26");
        }
        return normalized;
    }

    private String trustedUsername(CurrentUser currentUser) {
        currentUserId(currentUser);
        return currentUser.getUsername();
    }

    private String trustedUserUuid(CurrentUser currentUser) {
        currentUserId(currentUser);
        return currentUser.getUserUuid();
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private void formatDateFields(WorkOrderFeedbackVO.WorkOrderRecord record) {
        record.setCreatedAt(formatDateText(record.getCreatedAt()));
        record.setUpdatedAt(formatDateText(record.getUpdatedAt()));
        record.setHandledAt(formatDateText(record.getHandledAt()));
    }

    private String formatDateText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return LocalDateTime.parse(value.replace(" ", "T")).format(DATE_TIME_FORMATTER);
        } catch (RuntimeException ignored) {
            return value;
        }
    }
}
