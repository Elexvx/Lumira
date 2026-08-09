package com.lumira.saas.modules.export.controller.internal;

import com.lumira.api.export.UserExportTaskWorkerPort;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Export-owned internal endpoint; user rendering remains behind a shared port. */
@RestController
@RequestMapping({"/internal/jobs/user-export", "/internal/jobs/export"})
@ConditionalOnLumiraControlPlaneEnabled
public class InternalUserExportJobController {

    private final ObjectProvider<UserExportTaskWorkerPort> workerProvider;
    private final String jobInternalToken;

    public InternalUserExportJobController(
            ObjectProvider<UserExportTaskWorkerPort> workerProvider,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobInternalToken
    ) {
        this.workerProvider = workerProvider;
        this.jobInternalToken = jobInternalToken;
    }

    @PostMapping("/run")
    public ApiResponse<Integer> processExportTasks(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        if (limit < 1 || limit > UserExportTaskWorkerPort.MAX_CLAIM_LIMIT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid export task limit");
        }
        UserExportTaskWorkerPort worker = workerProvider.getIfAvailable();
        if (worker == null) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "User export task worker is unavailable");
        }
        return ApiResponse.success(worker.processPendingTasks(limit), null);
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(jobInternalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Internal job token is not configured");
        }
        if (!InternalJobTokenValidator.isAuthorized(token, jobInternalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized internal job access");
        }
    }
}
