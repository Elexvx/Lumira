package com.lumira.alerting.controller;

import com.lumira.alerting.app.AlertingJobService;
import com.lumira.alerting.model.AlertingModels;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.InternalJobTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alerting/internal/jobs")
public class AlertingInternalJobController {
    private final AlertingJobService jobService;
    private final String jobInternalToken;

    public AlertingInternalJobController(
            AlertingJobService jobService,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobInternalToken
    ) {
        this.jobService = jobService;
        this.jobInternalToken = jobInternalToken;
    }

    @PostMapping("/run")
    public ApiResponse<AlertingModels.JobRunResult> run(
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        if (!InternalJobTokenValidator.isConfigured(jobInternalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Job internal token is not configured");
        }
        if (!InternalJobTokenValidator.isAuthorized(token, jobInternalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized alert worker access");
        }
        return ApiResponse.success(jobService.runOnce(), null);
    }
}
