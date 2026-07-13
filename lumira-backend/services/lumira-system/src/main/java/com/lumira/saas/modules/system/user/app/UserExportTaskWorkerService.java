package com.lumira.saas.modules.system.user.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.system.export.ExportDTO;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.user.repository.UserExportTaskWorkerRepository;
import com.lumira.saas.modules.system.user.repository.UserExportTaskWorkerRepository.TaskClaim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnLumiraAsyncEnabled
@ConditionalOnLumiraControlPlaneEnabled
public class UserExportTaskWorkerService {
    private static final Logger log = LoggerFactory.getLogger(UserExportTaskWorkerService.class);

    public static final int MAX_CLAIM_LIMIT = 100;
    private static final String MODULE_KEY = "system:user";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final int MAX_ERROR_LENGTH = 512;

    private final UserExportTaskWorkerRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final UserExportAppService userExportAppService;
    private final ExportTaskService exportTaskService;

    public UserExportTaskWorkerService(
            UserExportTaskWorkerRepository taskRepository,
            ObjectMapper objectMapper,
            UserExportAppService userExportAppService,
            ExportTaskService exportTaskService
    ) {
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
        this.userExportAppService = userExportAppService;
        this.exportTaskService = exportTaskService;
    }

    public int processPendingTasks(int limit) {
        int processed = 0;
        for (TaskClaim task : claimPendingTasks(limit)) {
            try {
                processClaimedTask(task);
                processed++;
            } catch (RuntimeException exception) {
                markFailed(task, exception);
            }
        }
        return processed;
    }

    List<TaskClaim> claimPendingTasks(int limit) {
        int normalizedLimit = requireClaimLimit(limit);
        LocalDateTime now = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime claimExpiresAt = now.plusMinutes(15);
        return taskRepository.claim(normalizedLimit, workerId(), claimToken, now, claimExpiresAt);
    }

    private void processClaimedTask(TaskClaim task) {
        requireClaimedTask(task);
        UserExportAppService.AsyncTaskPayload payload = parsePayload(task.requestPayload());
        ExportDTO.UserExportRequest request = payload.getRequest();
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export task request payload is unavailable");
        }
        if (!StringUtils.hasText(payload.getFileName())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export task file name is unavailable");
        }
        CurrentUser currentUser = userExportAppService.buildQueuedAsyncUser(
                task.createdBy(),
                task.createdByUuid(),
                payload.getSimulatedRoleId(),
                task.id()
        );
        byte[] content = userExportAppService.exportUsersFromTrustedSnapshot(currentUser, request);
        FileObjectDTO uploaded = exportTaskService.uploadExportFileFromTrustedSnapshot(
                currentUser,
                content,
                payload.getFileName().trim(),
                "user-export",
                "export,user",
                "system user export"
        );
        markSucceeded(task, uploaded, payload.getFileName().trim());
    }

    private UserExportAppService.AsyncTaskPayload parsePayload(String requestPayload) {
        if (!StringUtils.hasText(requestPayload)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Export task request payload is unavailable");
        }
        try {
            return objectMapper.readValue(requestPayload, UserExportAppService.AsyncTaskPayload.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Export task request payload is invalid", exception);
        }
    }

    private void markSucceeded(TaskClaim task, FileObjectDTO uploaded, String fileName) {
        Long fileId = uploaded == null ? null : uploaded.id();
        if (fileId == null || fileId <= 0) {
            throw new IllegalStateException("Export file upload did not return a trusted file id");
        }
        int updated = taskRepository.markSucceeded(task, fileId, fileName, LocalDateTime.now());
        recordClaimMismatchIfNeeded(updated, task, "markSucceeded");
    }

    private void markFailed(TaskClaim task, RuntimeException exception) {
        if (task == null || task.id() == null || task.id() <= 0 || !StringUtils.hasText(task.claimToken())) {
            return;
        }
        int updated = taskRepository.markFailed(task, truncate(resolveErrorMessage(exception)), LocalDateTime.now());
        if (updated <= 0) {
            log.warn("user export task claim mismatch operation=markFailed taskId={}", task.id());
            return;
        }
        log.warn("user export task failed taskId={} message={}", task.id(), resolveErrorMessage(exception));
    }

    private void requireClaimedTask(TaskClaim task) {
        if (task == null
                || task.id() == null
                || task.id() <= 0
                || !MODULE_KEY.equals(task.moduleKey())
                || !STATUS_RUNNING.equals(task.status())
                || task.createdBy() == null
                || task.createdBy() <= 0
                || !StringUtils.hasText(task.createdByUuid())
                || !StringUtils.hasText(task.claimToken())) {
            throw new IllegalStateException("Export task row is invalid");
        }
    }

    private int requireClaimLimit(int limit) {
        if (limit < 1 || limit > MAX_CLAIM_LIMIT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid export task limit");
        }
        return limit;
    }

    private void recordClaimMismatchIfNeeded(int updated, TaskClaim task, String operation) {
        if (updated > 0) {
            return;
        }
        log.warn("user export task claim mismatch operation={} taskId={}", operation, task.id());
    }

    private String resolveErrorMessage(RuntimeException exception) {
        if (exception == null) {
            return "Export task failed";
        }
        String message = exception.getMessage();
        if (StringUtils.hasText(message)) {
            return message.trim();
        }
        return exception.getClass().getSimpleName();
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private String workerId() {
        return System.getProperty("lumira.worker.id", java.net.InetAddress.getLoopbackAddress().getHostName());
    }

}
