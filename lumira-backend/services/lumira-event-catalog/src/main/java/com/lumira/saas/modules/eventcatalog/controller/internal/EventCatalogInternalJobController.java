package com.lumira.saas.modules.eventcatalog.controller.internal;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.saas.modules.eventcatalog.app.EventCatalogAppService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal, idempotent source rebuild entry point for job-executor operations. */
@RestController
@RequestMapping("/internal/jobs/event-catalog")
public class EventCatalogInternalJobController {

    private final EventCatalogAppService eventCatalogAppService;
    private final String jobToken;

    public EventCatalogInternalJobController(
            EventCatalogAppService eventCatalogAppService,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken
    ) {
        this.eventCatalogAppService = eventCatalogAppService;
        this.jobToken = jobToken;
    }

    @PostMapping("/rebuild/{sourceType}")
    public ApiResponse<Integer> rebuild(
            @PathVariable("sourceType") String sourceType,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        return ApiResponse.success(eventCatalogAppService.rebuildSource(sourceType), null);
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(jobToken)
                || !InternalJobTokenValidator.isAuthorized(token, jobToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized internal job access");
        }
    }
}
