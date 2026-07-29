package com.lumira.saas.infrastructure.job;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportTaskWorkerService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs/registration-export")
@ConditionalOnLumiraControlPlaneEnabled
public class InternalRegistrationExportJobController {
    private final ObjectProvider<CompetitionRegistrationExportTaskWorkerService> workerProvider;
    private final String jobInternalToken;

    public InternalRegistrationExportJobController(
            ObjectProvider<CompetitionRegistrationExportTaskWorkerService> workerProvider,
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
        if (limit < 1 || limit > CompetitionRegistrationExportTaskWorkerService.MAX_CLAIM_LIMIT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid registration export task limit");
        }
        CompetitionRegistrationExportTaskWorkerService worker = workerProvider.getIfAvailable();
        if (worker == null) {
            throw new BizException(
                    ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Registration export task worker is unavailable"
            );
        }
        return ApiResponse.success(worker.processPendingTasks(limit), null);
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(jobInternalToken)
                || !InternalJobTokenValidator.isAuthorized(token, jobInternalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized internal job access");
        }
    }
}
