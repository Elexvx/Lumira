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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_LENGTH = 512;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserExportAppService userExportAppService;
    private final ExportTaskService exportTaskService;

    public UserExportTaskWorkerService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            UserExportAppService userExportAppService,
            ExportTaskService exportTaskService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.userExportAppService = userExportAppService;
        this.exportTaskService = exportTaskService;
    }

    public int processPendingTasks(int limit) {
        int processed = 0;
        for (ClaimedTask task : claimPendingTasks(limit)) {
            try {
                processClaimedTask(task);
                processed++;
            } catch (RuntimeException exception) {
                markFailed(task, exception);
            }
        }
        return processed;
    }

    List<ClaimedTask> claimPendingTasks(int limit) {
        int normalizedLimit = requireClaimLimit(limit);
        LocalDateTime now = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime claimExpiresAt = now.plusMinutes(15);
        jdbcTemplate.update(
                """
                        update sys_export_task t
                        join (
                            select t.id
                            from sys_export_task t
                            where t.deleted = 0
                              and t.module_key = ?
                              and t.created_by is not null
                              and t.created_by > 0
                              and t.created_by_uuid is not null
                              and t.created_by_uuid <> ''
                              and (
                                    t.status = ?
                                    or (t.status = ? and t.claim_expires_at is not null and t.claim_expires_at <= ?)
                              )
                            order by t.created_at asc, t.id asc
                            limit ?
                        ) picked on picked.id = t.id
                        set t.status = ?,
                            t.started_at = coalesce(t.started_at, ?),
                            t.finished_at = null,
                            t.error_message = null,
                            t.claimed_by = ?,
                            t.claim_token = ?,
                            t.claim_expires_at = ?
                        where t.deleted = 0
                        """,
                MODULE_KEY,
                STATUS_PENDING,
                STATUS_RUNNING,
                now,
                normalizedLimit,
                STATUS_RUNNING,
                now,
                workerId(),
                claimToken,
                claimExpiresAt
        );
        return jdbcTemplate.query(
                """
                        select id,
                               module_key as moduleKey,
                               status,
                               request_payload as requestPayload,
                               created_by as createdBy,
                               created_by_uuid as createdByUuid,
                               claim_token as claimToken
                        from sys_export_task
                        where deleted = 0
                          and module_key = ?
                          and claim_token = ?
                        order by created_at asc, id asc
                        """,
                (rs, rowNum) -> new ClaimedTask(
                        rs.getLong("id"),
                        rs.getString("moduleKey"),
                        rs.getString("status"),
                        rs.getString("requestPayload"),
                        rs.getLong("createdBy"),
                        rs.getString("createdByUuid"),
                        rs.getString("claimToken")
                ),
                MODULE_KEY,
                claimToken
        );
    }

    private void processClaimedTask(ClaimedTask task) {
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

    private void markSucceeded(ClaimedTask task, FileObjectDTO uploaded, String fileName) {
        Long fileId = uploaded == null ? null : uploaded.id();
        if (fileId == null || fileId <= 0) {
            throw new IllegalStateException("Export file upload did not return a trusted file id");
        }
        int updated = jdbcTemplate.update(
                """
                        update sys_export_task
                        set status = ?, file_id = ?, file_name = ?, error_message = null, finished_at = ?,
                            claimed_by = null, claim_token = null, claim_expires_at = null
                        where id = ? and module_key = ? and created_by = ? and created_by_uuid = ?
                          and claim_token = ? and deleted = 0 and status = ?
                        """,
                STATUS_SUCCESS,
                fileId,
                fileName,
                LocalDateTime.now(),
                task.id(),
                task.moduleKey(),
                task.createdBy(),
                task.createdByUuid(),
                task.claimToken(),
                STATUS_RUNNING
        );
        recordClaimMismatchIfNeeded(updated, task, "markSucceeded");
    }

    private void markFailed(ClaimedTask task, RuntimeException exception) {
        if (task == null || task.id() == null || task.id() <= 0 || !StringUtils.hasText(task.claimToken())) {
            return;
        }
        int updated = jdbcTemplate.update(
                """
                        update sys_export_task
                        set status = ?, error_message = ?, finished_at = ?,
                            claimed_by = null, claim_token = null, claim_expires_at = null
                        where id = ? and module_key = ? and created_by = ? and created_by_uuid = ?
                          and claim_token = ? and deleted = 0 and status = ?
                        """,
                STATUS_FAILED,
                truncate(resolveErrorMessage(exception)),
                LocalDateTime.now(),
                task.id(),
                task.moduleKey(),
                task.createdBy(),
                task.createdByUuid(),
                task.claimToken(),
                STATUS_RUNNING
        );
        if (updated <= 0) {
            log.warn("user export task claim mismatch operation=markFailed taskId={}", task.id());
            return;
        }
        log.warn("user export task failed taskId={} message={}", task.id(), resolveErrorMessage(exception));
    }

    private void requireClaimedTask(ClaimedTask task) {
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

    private void recordClaimMismatchIfNeeded(int updated, ClaimedTask task, String operation) {
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

    record ClaimedTask(
            Long id,
            String moduleKey,
            String status,
            String requestPayload,
            Long createdBy,
            String createdByUuid,
            String claimToken
    ) {
    }
}
