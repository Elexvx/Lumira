package com.lumira.api.export;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Export-owned task and trusted-upload capability exposed to bounded contexts
 * without exposing an owner service's implementation classes.
 */
public interface ExportTaskPort {

    /** Compatibility overload for the existing System user-export route. */
    default ExportTask createTask(
            CurrentUser currentUser,
            String moduleKey,
            Object request,
            List<String> selectedFields,
            long totalCount
    ) {
        return createTask(currentUser, moduleKey, request, selectedFields, totalCount, "system:user:export");
    }

    ExportTask createTask(
            CurrentUser currentUser,
            String moduleKey,
            Object request,
            List<String> selectedFields,
            long totalCount,
            String requiredPermission
    );

    FileObjectDTO uploadExportFileFromTrustedSnapshot(
            CurrentUser currentUser,
            byte[] content,
            String fileName,
            String category,
            String tags,
            String remark,
            String requiredPermission
    );

    /** Compatibility overload for the existing System user-export worker. */
    default FileObjectDTO uploadExportFileFromTrustedSnapshot(
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
                "system:user:export"
        );
    }

    void markRunningFromTrustedSnapshot(
            CurrentUser currentUser,
            Long taskId,
            String requiredPermission
    );

    default void markRunningFromTrustedSnapshot(CurrentUser currentUser, Long taskId) {
        markRunningFromTrustedSnapshot(currentUser, taskId, "system:user:export");
    }

    void markSuccessFromTrustedSnapshot(
            CurrentUser currentUser,
            Long taskId,
            FileObjectDTO file,
            String fileName,
            String requiredPermission
    );

    default void markSuccessFromTrustedSnapshot(
            CurrentUser currentUser,
            Long taskId,
            FileObjectDTO file,
            String fileName
    ) {
        markSuccessFromTrustedSnapshot(currentUser, taskId, file, fileName, "system:user:export");
    }

    void markFailedFromTrustedSnapshot(
            CurrentUser currentUser,
            Long taskId,
            Exception exception,
            String requiredPermission
    );

    default void markFailedFromTrustedSnapshot(CurrentUser currentUser, Long taskId, Exception exception) {
        markFailedFromTrustedSnapshot(currentUser, taskId, exception, "system:user:export");
    }

    ExportTaskView getTask(CurrentUser currentUser, Long taskId, String requiredPermission);

    /**
     * Returns the persisted request payload for a task owned by the caller.
     * Bounded contexts use this only to re-check their own resource scope;
     * implementations may return {@code null} when the backing store does not
     * expose task payloads.
     */
    default String getTaskRequestPayload(CurrentUser currentUser, Long taskId, String requiredPermission) {
        return null;
    }

    default ExportVO.ExportTaskVO getTaskVo(CurrentUser currentUser, Long taskId, String requiredPermission) {
        ExportTaskView task = getTask(currentUser, taskId, requiredPermission);
        ExportVO.ExportTaskVO response = new ExportVO.ExportTaskVO();
        response.setId(task.id());
        response.setModuleKey(task.moduleKey());
        response.setStatus(task.status());
        response.setTotalCount(task.totalCount());
        response.setFileId(task.fileId());
        response.setFileName(task.fileName());
        response.setDownloadUrl(task.downloadUrl());
        response.setErrorMessage(task.errorMessage());
        response.setCreatedAt(task.createdAt());
        response.setStartedAt(task.startedAt());
        response.setFinishedAt(task.finishedAt());
        return response;
    }

    record ExportTask(Long id) {
    }

    record ExportTaskView(
            Long id,
            String moduleKey,
            String status,
            Long totalCount,
            Long fileId,
            String fileName,
            String downloadUrl,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }
}
