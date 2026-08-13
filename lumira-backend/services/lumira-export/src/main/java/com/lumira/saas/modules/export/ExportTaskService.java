package com.lumira.saas.modules.export;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.FileInternalApi;
import com.lumira.api.export.ExportTaskPort;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.security.TrustedUserSnapshotResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Export-task owner.  Cross-context callers only see {@link ExportTaskPort};
 * session and IAM state are resolved through shared security ports.
 */
@Service
@ConditionalOnLumiraControlPlaneEnabled
public class ExportTaskService implements ExportTaskPort {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String PERMISSION_USER_EXPORT = "system:user:export";

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
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final TrustedUserSnapshotResolver trustedUserSnapshotResolver;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public ExportTaskService(
            ExportTaskMapper exportTaskMapper,
            FileInternalApi fileInternalApi,
            ObjectMapper objectMapper,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            TrustedUserSnapshotResolver trustedUserSnapshotResolver
    ) {
        this(
                exportTaskMapper,
                fileInternalApi,
                objectMapper,
                trustedCurrentUserResolver,
                trustedUserSnapshotResolver,
                true
        );
    }

    /** Convenience constructor for focused unit tests without a runtime resolver. */
    public ExportTaskService(ExportTaskMapper exportTaskMapper, FileInternalApi fileInternalApi, ObjectMapper objectMapper) {
        this(exportTaskMapper, fileInternalApi, objectMapper, null, null, false);
    }

    public ExportTaskService(
            ExportTaskMapper exportTaskMapper,
            FileInternalApi fileInternalApi,
            ObjectMapper objectMapper,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            TrustedUserSnapshotResolver trustedUserSnapshotResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.exportTaskMapper = exportTaskMapper;
        this.fileInternalApi = fileInternalApi;
        this.objectMapper = objectMapper;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.trustedUserSnapshotResolver = trustedUserSnapshotResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public ExportTask createTask(
            CurrentUser currentUser,
            String moduleKey,
            Object request,
            List<String> selectedFields,
            long totalCount
    ) {
        return createTask(currentUser, moduleKey, request, selectedFields, totalCount, PERMISSION_USER_EXPORT);
    }

    @Override
    public ExportTask createTask(
            CurrentUser currentUser,
            String moduleKey,
            Object request,
            List<String> selectedFields,
            long totalCount,
            String requiredPermission
    ) {
        CurrentUser trustedUser = resolveInteractiveUser(currentUser, requiredPermission);
        String normalizedModuleKey = requireSafeToken(moduleKey, "Export module key", MAX_MODULE_KEY_LENGTH, false);
        requireRequest(request, "Export request");
        List<String> normalizedSelectedFields = requireSelectedFields(selectedFields);
        if (totalCount < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export total count must not be negative");
        }
        ExportTaskEntity entity = new ExportTaskEntity();
        entity.setModuleKey(normalizedModuleKey);
        entity.setStatus(STATUS_PENDING);
        entity.setRequestPayload(writeJson(request));
        entity.setSelectedFields(writeJson(normalizedSelectedFields));
        entity.setTotalCount(totalCount);
        entity.setCreatedBy(trustedUser.getUserId());
        entity.setCreatedByUuid(trustedUser.getUserUuid());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        int inserted = exportTaskMapper.insert(entity);
        if (inserted != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Export task changed, please retry");
        }
        return new ExportTask(entity.getId());
    }

    public void markRunning(CurrentUser currentUser, Long taskId) {
        CurrentUser trustedUser = resolveInteractiveUser(currentUser, PERMISSION_USER_EXPORT);
        doMarkRunning(trustedUser, taskId);
    }

    @Override
    public void markRunningFromTrustedSnapshot(CurrentUser currentUser, Long taskId, String requiredPermission) {
        doMarkRunning(resolveSnapshotUser(currentUser, requiredPermission), taskId);
    }

    public void markRunningFromTrustedSnapshot(CurrentUser currentUser, Long taskId) {
        markRunningFromTrustedSnapshot(currentUser, taskId, PERMISSION_USER_EXPORT);
    }

    private void doMarkRunning(CurrentUser trustedUser, Long taskId) {
        requirePositiveId(taskId, "Export task id");
        ExportTaskEntity update = new ExportTaskEntity();
        update.setStatus(STATUS_RUNNING);
        update.setStartedAt(LocalDateTime.now());
        int updated = exportTaskMapper.update(update, ownerScopedUpdate(trustedUser, taskId)
                .eq(ExportTaskEntity::getStatus, STATUS_PENDING));
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Export task changed, please retry");
        }
    }

    public void markSuccess(CurrentUser currentUser, Long taskId, FileObjectDTO file, String fileName) {
        doMarkSuccess(resolveInteractiveUser(currentUser, PERMISSION_USER_EXPORT), taskId, file, fileName);
    }

    @Override
    public void markSuccessFromTrustedSnapshot(
            CurrentUser currentUser,
            Long taskId,
            FileObjectDTO file,
            String fileName,
            String requiredPermission
    ) {
        doMarkSuccess(resolveSnapshotUser(currentUser, requiredPermission), taskId, file, fileName);
    }

    public void markSuccessFromTrustedSnapshot(CurrentUser currentUser, Long taskId, FileObjectDTO file, String fileName) {
        markSuccessFromTrustedSnapshot(currentUser, taskId, file, fileName, PERMISSION_USER_EXPORT);
    }

    private void doMarkSuccess(CurrentUser trustedUser, Long taskId, FileObjectDTO file, String fileName) {
        requirePositiveId(taskId, "Export task id");
        String normalizedFileName = requireSafeXlsxFileName(fileName);
        Long fileId = requireTrustedUploadedFile(file, trustedUser.getUserId(), trustedUser.getUserUuid());
        ExportTaskEntity update = new ExportTaskEntity();
        update.setStatus(STATUS_SUCCESS);
        update.setFileId(fileId);
        update.setFileName(normalizedFileName);
        update.setFinishedAt(LocalDateTime.now());
        int updated = exportTaskMapper.update(update, ownerScopedUpdate(trustedUser, taskId)
                .eq(ExportTaskEntity::getStatus, STATUS_RUNNING));
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Export task changed, please retry");
        }
    }

    public void markFailed(CurrentUser currentUser, Long taskId, Exception exception) {
        doMarkFailed(resolveInteractiveUser(currentUser, PERMISSION_USER_EXPORT), taskId, exception);
    }

    @Override
    public void markFailedFromTrustedSnapshot(
            CurrentUser currentUser,
            Long taskId,
            Exception exception,
            String requiredPermission
    ) {
        doMarkFailed(resolveSnapshotUser(currentUser, requiredPermission), taskId, exception);
    }

    public void markFailedFromTrustedSnapshot(CurrentUser currentUser, Long taskId, Exception exception) {
        markFailedFromTrustedSnapshot(currentUser, taskId, exception, PERMISSION_USER_EXPORT);
    }

    private void doMarkFailed(CurrentUser trustedUser, Long taskId, Exception exception) {
        requirePositiveId(taskId, "Export task id");
        ExportTaskEntity update = new ExportTaskEntity();
        update.setStatus(STATUS_FAILED);
        update.setErrorMessage(resolveErrorMessage(exception));
        update.setFinishedAt(LocalDateTime.now());
        int updated = exportTaskMapper.update(update, ownerScopedUpdate(trustedUser, taskId)
                .in(ExportTaskEntity::getStatus, STATUS_PENDING, STATUS_RUNNING));
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Export task changed, please retry");
        }
    }

    public FileObjectDTO uploadExportFile(
            CurrentUser currentUser,
            byte[] content,
            String fileName,
            String category,
            String tags,
            String remark
    ) {
        return doUploadExportFile(resolveInteractiveUser(currentUser, PERMISSION_USER_EXPORT), content, fileName, category, tags, remark);
    }

    @Override
    public FileObjectDTO uploadExportFileFromTrustedSnapshot(
            CurrentUser currentUser,
            byte[] content,
            String fileName,
            String category,
            String tags,
            String remark,
            String requiredPermission
    ) {
        return doUploadExportFile(resolveSnapshotUser(currentUser, requiredPermission), content, fileName, category, tags, remark);
    }

    public FileObjectDTO uploadExportFileFromTrustedSnapshot(
            CurrentUser currentUser,
            byte[] content,
            String fileName,
            String category,
            String tags,
            String remark
    ) {
        return uploadExportFileFromTrustedSnapshot(
                currentUser,
                content,
                fileName,
                category,
                tags,
                remark,
                PERMISSION_USER_EXPORT
        );
    }

    private FileObjectDTO doUploadExportFile(
            CurrentUser trustedUser,
            byte[] content,
            String fileName,
            String category,
            String tags,
            String remark
    ) {
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
                trustedUser.getUserId(),
                trustedUser.getUserUuid(),
                trustedUser.getUsername(),
                trustedUser.getSimulatedRoleId()
        );
    }

    public ExportTaskView getTask(CurrentUser currentUser, Long taskId) {
        return getTask(currentUser, taskId, PERMISSION_USER_EXPORT);
    }

    @Override
    public ExportTaskView getTask(CurrentUser currentUser, Long taskId, String requiredPermission) {
        CurrentUser trustedUser = resolveInteractiveUser(currentUser, requiredPermission);
        requirePositiveId(taskId, "Export task id");
        ExportTaskEntity entity = exportTaskMapper.selectOne(new LambdaQueryWrapper<ExportTaskEntity>()
                .eq(ExportTaskEntity::getId, taskId)
                .eq(ExportTaskEntity::getCreatedBy, trustedUser.getUserId())
                .eq(ExportTaskEntity::getCreatedByUuid, trustedUser.getUserUuid())
                .eq(ExportTaskEntity::getDeleted, 0));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Export task does not exist");
        }
        return new ExportTaskView(
                entity.getId(),
                entity.getModuleKey(),
                entity.getStatus(),
                entity.getTotalCount(),
                entity.getFileId(),
                entity.getFileName(),
                entity.getFileId() == null ? null : "/api/v1/files/" + entity.getFileId() + "/download",
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt()
        );
    }

    @Override
    public String getTaskRequestPayload(CurrentUser currentUser, Long taskId, String requiredPermission) {
        CurrentUser trustedUser = resolveInteractiveUser(currentUser, requiredPermission);
        requirePositiveId(taskId, "Export task id");
        ExportTaskEntity entity = exportTaskMapper.selectOne(new LambdaQueryWrapper<ExportTaskEntity>()
                .select(ExportTaskEntity::getRequestPayload)
                .eq(ExportTaskEntity::getId, taskId)
                .eq(ExportTaskEntity::getCreatedBy, trustedUser.getUserId())
                .eq(ExportTaskEntity::getCreatedByUuid, trustedUser.getUserUuid())
                .eq(ExportTaskEntity::getDeleted, 0));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Export task does not exist");
        }
        return entity.getRequestPayload();
    }

    private LambdaUpdateWrapper<ExportTaskEntity> ownerScopedUpdate(CurrentUser trustedUser, Long taskId) {
        return new LambdaUpdateWrapper<ExportTaskEntity>()
                .eq(ExportTaskEntity::getId, taskId)
                .eq(ExportTaskEntity::getCreatedBy, trustedUser.getUserId())
                .eq(ExportTaskEntity::getCreatedByUuid, trustedUser.getUserUuid())
                .eq(ExportTaskEntity::getDeleted, 0);
    }

    private CurrentUser resolveInteractiveUser(CurrentUser currentUser, String requiredPermission) {
        requireTrustedInput(currentUser);
        if (trustedCurrentUserResolver == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return requirePermission(currentUser, requiredPermission);
        }
        CurrentUser trustedUser = trustedCurrentUserResolver.resolve(currentUser);
        requireTrustedInput(trustedUser);
        return requirePermission(trustedUser, requiredPermission);
    }

    private CurrentUser resolveSnapshotUser(CurrentUser currentUser, String requiredPermission) {
        requireTrustedInput(currentUser);
        String normalizedPermission = requireSafeToken(requiredPermission, "Export permission", MAX_SELECTED_FIELD_LENGTH, false);
        if (trustedUserSnapshotResolver == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return requirePermission(currentUser, normalizedPermission);
        }
        CurrentUser trustedUser = trustedUserSnapshotResolver.resolve(
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                normalizeSimulatedRoleId(currentUser.getSimulatedRoleId()),
                currentUser.getSessionId(),
                normalizedPermission
        );
        requireTrustedInput(trustedUser);
        return requirePermission(trustedUser, normalizedPermission);
    }

    private CurrentUser requirePermission(CurrentUser currentUser, String requiredPermission) {
        String normalizedPermission = requireSafeToken(requiredPermission, "Export permission", MAX_SELECTED_FIELD_LENGTH, false);
        Set<String> permissions = currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
        if (!permissions.contains("*") && !permissions.contains(normalizedPermission)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + normalizedPermission);
        }
        return currentUser;
    }

    private void requireTrustedInput(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
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
        for (int index = 0; index < normalized.length(); index += 1) {
            char character = normalized.charAt(index);
            boolean allowed = Character.isLetterOrDigit(character)
                    || character == '_'
                    || character == '-'
                    || character == ':'
                    || character == '.'
                    || (allowComma && character == ',');
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
        for (int index = 0; index < normalized.length(); index += 1) {
            if (Character.isISOControl(normalized.charAt(index))) {
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
                || normalized.contains("\\\\")
                || normalized.contains("..")
                || !normalized.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export file name is invalid");
        }
        for (int index = 0; index < normalized.length(); index += 1) {
            char character = normalized.charAt(index);
            if (Character.isISOControl(character)
                    || character == ':'
                    || character == '*'
                    || character == '?'
                    || character == '"'
                    || character == '<'
                    || character == '>'
                    || character == '|') {
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

    private List<String> requireSelectedFields(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export selected fields is required");
        }
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
        if (file.uploadedBy() == null || file.uploadedBy() <= 0 || !file.uploadedBy().equals(userId)) {
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
            throw new BizException(ErrorCode.BIZ_ERROR, "Export task parameter serialization failed");
        }
    }

    private String resolveErrorMessage(Exception exception) {
        if (exception instanceof BizException bizException && StringUtils.hasText(bizException.getMessage())) {
            return bizException.getMessage();
        }
        return exception == null || !StringUtils.hasText(exception.getMessage())
                ? "Export task execution failed"
                : exception.getMessage();
    }
}
