package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.export.ExportTaskQueuePort;
import com.lumira.api.export.ExportTaskQueuePort.ExportTaskClaim;
import com.lumira.api.export.ExportTaskPort;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class CompetitionRegistrationExportTaskWorkerService {
    public static final int MAX_CLAIM_LIMIT = 100;

    private static final Logger log =
            LoggerFactory.getLogger(CompetitionRegistrationExportTaskWorkerService.class);
    private static final String STATUS_RUNNING = "RUNNING";
    private static final int MAX_ERROR_LENGTH = 512;

    private final ExportTaskQueuePort taskQueue;
    private final ObjectMapper objectMapper;
    private final CompetitionRegistrationExportAppService exportAppService;
    private final ExportTaskPort exportTaskPort;

    public CompetitionRegistrationExportTaskWorkerService(
            ExportTaskQueuePort taskQueue,
            ObjectMapper objectMapper,
            CompetitionRegistrationExportAppService exportAppService,
            ExportTaskPort exportTaskPort
    ) {
        this.taskQueue = taskQueue;
        this.objectMapper = objectMapper;
        this.exportAppService = exportAppService;
        this.exportTaskPort = exportTaskPort;
    }

    public int processPendingTasks(int limit) {
        int processed = 0;
        for (ExportTaskClaim task : claimPendingTasks(limit)) {
            try {
                processClaimedTask(task);
                processed++;
            } catch (RuntimeException exception) {
                markFailed(task, exception);
            }
        }
        return processed;
    }

    List<ExportTaskClaim> claimPendingTasks(int limit) {
        if (limit < 1 || limit > MAX_CLAIM_LIMIT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid registration export task limit");
        }
        LocalDateTime now = LocalDateTime.now();
        return taskQueue.claim(
                CompetitionRegistrationExportAppService.MODULE_KEY,
                limit,
                workerId(),
                UUID.randomUUID().toString(),
                now,
                now.plusMinutes(15)
        );
    }

    private void processClaimedTask(ExportTaskClaim task) {
        requireClaimedTask(task);
        CompetitionRegistrationExportAppService.AsyncTaskPayload payload = parsePayload(task.requestPayload());
        if (payload.getRequest() == null || !StringUtils.hasText(payload.getFileName())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Registration export task payload is incomplete");
        }
        CurrentUser currentUser = exportAppService.buildQueuedAsyncUser(
                task.createdBy(),
                task.createdByUuid(),
                payload.getSimulatedRoleId(),
                task.id()
        );
        boolean materialPackage = CompetitionRegistrationExportAppService.EXPORT_TYPE_MATERIAL_ZIP.equals(
                payload.getExportType()
        );
        byte[] content = materialPackage
                ? exportAppService.exportMaterialPackageFromTrustedSnapshot(
                        currentUser,
                        payload.getRequest(),
                        task.id()
                )
                : exportAppService.exportFromTrustedSnapshot(
                        currentUser,
                        payload.getRequest(),
                        task.id()
                );
        FileObjectDTO uploaded = exportTaskPort.uploadExportFileFromTrustedSnapshot(
                currentUser,
                content,
                payload.getFileName().trim(),
                materialPackage ? "competition-registration-materials" : "competition-registration-export",
                materialPackage
                        ? "export,competition,registration,materials"
                        : "export,competition,registration",
                materialPackage
                        ? "competition registration material package"
                        : "competition registration dataset export",
                materialPackage
                        ? CompetitionRegistrationExportAppService.MATERIAL_DOWNLOAD_PERMISSION
                        : CompetitionRegistrationExportAppService.EXPORT_PERMISSION
        );
        Long fileId = uploaded == null ? null : uploaded.id();
        if (fileId == null || fileId <= 0) {
            throw new IllegalStateException("Registration export file upload returned no trusted file id");
        }
        int updated = taskQueue.markSucceeded(
                task,
                fileId,
                payload.getFileName().trim(),
                LocalDateTime.now()
        );
        if (updated <= 0) {
            log.warn("registration export task claim mismatch operation=markSucceeded taskId={}", task.id());
        }
    }

    private CompetitionRegistrationExportAppService.AsyncTaskPayload parsePayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Registration export task payload is unavailable");
        }
        try {
            return objectMapper.readValue(
                    payload,
                    CompetitionRegistrationExportAppService.AsyncTaskPayload.class
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Registration export task payload is invalid", exception);
        }
    }

    private void markFailed(ExportTaskClaim task, RuntimeException exception) {
        if (task == null || task.id() == null || !StringUtils.hasText(task.claimToken())) {
            return;
        }
        String message = errorMessage(exception);
        int updated = taskQueue.markFailed(task, message, LocalDateTime.now());
        if (updated <= 0) {
            log.warn("registration export task claim mismatch operation=markFailed taskId={}", task.id());
            return;
        }
        log.warn("registration export task failed taskId={} message={}", task.id(), message);
    }

    private void requireClaimedTask(ExportTaskClaim task) {
        if (task == null
                || task.id() == null
                || task.id() <= 0
                || !CompetitionRegistrationExportAppService.MODULE_KEY.equals(task.moduleKey())
                || !STATUS_RUNNING.equals(task.status())
                || task.createdBy() == null
                || task.createdBy() <= 0
                || !StringUtils.hasText(task.createdByUuid())
                || !StringUtils.hasText(task.claimToken())) {
            throw new IllegalStateException("Registration export task row is invalid");
        }
    }

    private String errorMessage(RuntimeException exception) {
        String message = null;
        Throwable current = exception;
        while (current != null) {
            if (current instanceof BizException && StringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
                break;
            }
            if (!StringUtils.hasText(message) && StringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        if (!StringUtils.hasText(message)) {
            message = exception == null ? "Registration export failed" : exception.getClass().getSimpleName();
        }
        message = message.trim();
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private String workerId() {
        return System.getProperty(
                "lumira.worker.id",
                java.net.InetAddress.getLoopbackAddress().getHostName()
        );
    }
}
