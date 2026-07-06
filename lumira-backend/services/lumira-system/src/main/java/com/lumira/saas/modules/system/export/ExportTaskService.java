package com.lumira.saas.modules.system.export;

import com.lumira.api.client.SystemInternalApi;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class ExportTaskService {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_ENABLED = "ENABLED";

    private static final String PERMISSION_USER_EXPORT = "system:user:export";
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int MAX_MODULE_KEY_LENGTH = 64;
    private static final int MAX_SELECTED_FIELDS = 100;
    private static final int MAX_SELECTED_FIELD_LENGTH = 64;
    private static final int MAX_FILE_NAME_LENGTH = 128;
    private static final int MAX_CATEGORY_LENGTH = 64;
    private static final int MAX_TAGS_LENGTH = 256;
    private static final int MAX_REMARK_LENGTH = 512;

    private final ExportTaskMapper exportTaskMapper;
    private final FileInternalApi fileInternalApi;
    private final ObjectMapper objectMapper;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;

    @Autowired
    public ExportTaskService(
            ExportTaskMapper exportTaskMapper,
            FileInternalApi fileInternalApi,
            ObjectMapper objectMapper,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this.exportTaskMapper = exportTaskMapper;
        this.fileInternalApi = fileInternalApi;
        this.objectMapper = objectMapper;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    public ExportTaskService(
            ExportTaskMapper exportTaskMapper,
            FileInternalApi fileInternalApi,
            ObjectMapper objectMapper,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(exportTaskMapper, fileInternalApi, objectMapper, permissionSnapshotService, null, sessionAuthenticationService);
    }

    public ExportTaskService(ExportTaskMapper exportTaskMapper, FileInternalApi fileInternalApi, ObjectMapper objectMapper) {
        this(exportTaskMapper, fileInternalApi, objectMapper, null, null, null);
    }

    public ExportTaskService(
            ExportTaskMapper exportTaskMapper,
            FileInternalApi fileInternalApi,
            ObjectMapper objectMapper,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(exportTaskMapper, fileInternalApi, objectMapper, permissionSnapshotService, null, null);
    }

    public ExportTaskEntity createTask(CurrentUser currentUser, String moduleKey, Object request, List<String> selectedFields, long totalCount) {
        Long userId = requireUserExportPermission(currentUser);
        String normalizedModuleKey = requireSafeToken(moduleKey, "Export module key", MAX_MODULE_KEY_LENGTH, false);
        requireRequest(request, "Export request");
        List<String> normalizedSelectedFields = requireSelectedFields(selectedFields);
        if (totalCount < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export total count must not be negative");
        }
        LocalDateTime now = LocalDateTime.now();
        ExportTaskEntity entity = new ExportTaskEntity();
        entity.setModuleKey(normalizedModuleKey);
        entity.setStatus(STATUS_PENDING);
        entity.setRequestPayload(writeJson(request));
        entity.setSelectedFields(writeJson(normalizedSelectedFields));
        entity.setTotalCount(totalCount);
        entity.setCreatedBy(userId);
        entity.setCreatedByUuid(trustedUserUuid(currentUser));
        entity.setCreatedAt(now);
        entity.setDeleted(0);
        int inserted = exportTaskMapper.insert(entity);
        if (inserted != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Export task changed, please retry");
        }
        return entity;
    }

    public void markRunning(CurrentUser currentUser, Long taskId) {
        requireUserExportPermission(currentUser);
        requirePositiveId(taskId, "Export task id");
        ExportTaskEntity update = new ExportTaskEntity();
        update.setStatus(STATUS_RUNNING);
        update.setStartedAt(LocalDateTime.now());
        int updated = exportTaskMapper.update(update, ownerScopedUpdate(currentUser, taskId)
                .eq(ExportTaskEntity::getStatus, STATUS_PENDING));
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Export task changed, please retry");
        }
    }

    public void markSuccess(CurrentUser currentUser, Long taskId, FileObjectDTO file, String fileName) {
        Long userId = requireUserExportPermission(currentUser);
        requirePositiveId(taskId, "Export task id");
        String normalizedFileName = requireSafeXlsxFileName(fileName);
        Long fileId = requireTrustedUploadedFile(file, userId, trustedUserUuid(currentUser));
        ExportTaskEntity update = new ExportTaskEntity();
        update.setStatus(STATUS_SUCCESS);
        update.setFileId(fileId);
        update.setFileName(normalizedFileName);
        update.setFinishedAt(LocalDateTime.now());
        int updated = exportTaskMapper.update(update, ownerScopedUpdate(currentUser, taskId)
                .eq(ExportTaskEntity::getStatus, STATUS_RUNNING));
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Export task changed, please retry");
        }
    }

    public void markFailed(CurrentUser currentUser, Long taskId, Exception exception) {
        requireUserExportPermission(currentUser);
        requirePositiveId(taskId, "Export task id");
        ExportTaskEntity update = new ExportTaskEntity();
        update.setStatus(STATUS_FAILED);
        update.setErrorMessage(resolveErrorMessage(exception));
        update.setFinishedAt(LocalDateTime.now());
        int updated = exportTaskMapper.update(update, ownerScopedUpdate(currentUser, taskId)
                .in(ExportTaskEntity::getStatus, STATUS_PENDING, STATUS_RUNNING));
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Export task changed, please retry");
        }
    }

    private LambdaUpdateWrapper<ExportTaskEntity> ownerScopedUpdate(CurrentUser currentUser, Long taskId) {
        return new LambdaUpdateWrapper<ExportTaskEntity>()
                .eq(ExportTaskEntity::getId, taskId)
                .eq(ExportTaskEntity::getCreatedBy, currentUserId(currentUser))
                .eq(ExportTaskEntity::getCreatedByUuid, trustedUserUuid(currentUser))
                .eq(ExportTaskEntity::getDeleted, 0);
    }

    public FileObjectDTO uploadExportFile(CurrentUser currentUser, byte[] content, String fileName, String category, String tags, String remark) {
        Long userId = requireUserExportPermission(currentUser);
        if (content == null || content.length == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export file content is required");
        }
        String normalizedFileName = requireSafeXlsxFileName(fileName);
        String normalizedCategory = requireOptionalText(category, "Export file category", MAX_CATEGORY_LENGTH);
        String normalizedTags = requireOptionalText(tags, "Export file tags", MAX_TAGS_LENGTH);
        String normalizedRemark = requireOptionalText(remark, "Export file remark", MAX_REMARK_LENGTH);
        ByteArrayMultipartFile file = new ByteArrayMultipartFile(content, "file", normalizedFileName, XLSX_CONTENT_TYPE);
        return fileInternalApi.uploadDocumentForUser(
                file,
                normalizedCategory,
                normalizedTags,
                normalizedRemark,
                null,
                userId,
                trustedUserUuid(currentUser),
                trustedUsername(currentUser)
        );
    }

    public ExportVO.ExportTaskVO getTask(CurrentUser currentUser, Long taskId) {
        Long userId = requireUserExportPermission(currentUser);
        requirePositiveId(taskId, "Export task id");
        ExportTaskEntity entity = exportTaskMapper.selectOne(new LambdaQueryWrapper<ExportTaskEntity>()
                .eq(ExportTaskEntity::getId, taskId)
                .eq(ExportTaskEntity::getCreatedBy, userId)
                .eq(ExportTaskEntity::getCreatedByUuid, trustedUserUuid(currentUser))
                .eq(ExportTaskEntity::getDeleted, 0));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导出任务不存在");
        }
        ExportVO.ExportTaskVO vo = new ExportVO.ExportTaskVO();
        vo.setId(entity.getId());
        vo.setModuleKey(entity.getModuleKey());
        vo.setStatus(entity.getStatus());
        vo.setTotalCount(entity.getTotalCount());
        vo.setFileId(entity.getFileId());
        vo.setFileName(entity.getFileName());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setStartedAt(entity.getStartedAt());
        vo.setFinishedAt(entity.getFinishedAt());
        if (entity.getFileId() != null) {
            vo.setDownloadUrl("/api/v1/files/" + entity.getFileId() + "/download");
        }
        return vo;
    }

    private Long currentUserId(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser.getUserId();
    }

    private Long requireUserExportPermission(CurrentUser currentUser) {
        Long userId = currentUserId(currentUser);
        Set<String> permissions = trustedPermissions(currentUser);
        if (!permissions.contains("*") && !permissions.contains(PERMISSION_USER_EXPORT)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + PERMISSION_USER_EXPORT);
        }
        return userId;
    }

    private String trustedUsername(CurrentUser currentUser) {
        currentUserId(currentUser);
        return currentUser.getUsername();
    }

    private String trustedUserUuid(CurrentUser currentUser) {
        currentUserId(currentUser);
        return currentUser.getUserUuid();
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        currentUserId(currentUser);
        return currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
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
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
            }
            if (!StringUtils.hasText(userSnapshot.userUuid())
                    || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
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
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        return refreshedUser;
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
    }

    private void requireRequest(Object request, String name) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is required");
        }
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is required");
        }
    }

    private String requireSafeToken(String value, String name, int maxLength, boolean allowComma) {
        requireText(value, name);
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is too long");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            boolean allowed = Character.isLetterOrDigit(ch)
                    || ch == '_'
                    || ch == '-'
                    || ch == ':'
                    || ch == '.'
                    || (allowComma && ch == ',');
            if (!allowed) {
                throw new BizException(ErrorCode.BAD_REQUEST, name + " is invalid");
            }
        }
        return normalized;
    }

    private String requireOptionalText(String value, String name, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is too long");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new BizException(ErrorCode.BAD_REQUEST, name + " is invalid");
            }
        }
        return normalized;
    }

    private String requireSafeXlsxFileName(String fileName) {
        requireText(fileName, "Export file name");
        String normalized = fileName.trim();
        if (normalized.length() > MAX_FILE_NAME_LENGTH
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.contains("..")
                || !normalized.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export file name is invalid");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isISOControl(ch) || ch == ':' || ch == '*' || ch == '?' || ch == '"' || ch == '<' || ch == '>' || ch == '|') {
                throw new BizException(ErrorCode.BAD_REQUEST, "Export file name is invalid");
            }
        }
        return normalized;
    }

    private void requirePositiveId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " must be a positive number");
        }
    }

    private void requireNonEmptyList(List<?> values, String name) {
        if (values == null || values.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, name + " is required");
        }
    }

    private List<String> requireSelectedFields(List<String> values) {
        requireNonEmptyList(values, "Export selected fields");
        if (values.size() > MAX_SELECTED_FIELDS) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export selected fields are too many");
        }
        return values.stream()
                .map(value -> requireSafeToken(value, "Export selected field", MAX_SELECTED_FIELD_LENGTH, false))
                .distinct()
                .toList();
    }

    private Long requireTrustedUploadedFile(FileObjectDTO file, Long userId, String userUuid) {
        if (file == null || file.id() == null || file.id() <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export uploaded file is invalid");
        }
        if (file.uploadedBy() == null || file.uploadedBy() <= 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "Export uploaded file owner is required");
        }
        if (!file.uploadedBy().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Export uploaded file owner mismatch");
        }
        if (!StringUtils.hasText(file.uploadedByUuid()) || !file.uploadedByUuid().trim().equals(userUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Export uploaded file owner uuid mismatch");
        }
        return file.id();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "导出任务参数序列化失败");
        }
    }

    private String resolveErrorMessage(Exception exception) {
        if (exception instanceof BizException bizException && StringUtils.hasText(bizException.getMessage())) {
            return bizException.getMessage();
        }
        return exception == null || !StringUtils.hasText(exception.getMessage()) ? "导出任务执行失败" : exception.getMessage();
    }

}
