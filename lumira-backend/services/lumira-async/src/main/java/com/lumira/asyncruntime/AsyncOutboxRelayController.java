package com.lumira.asyncruntime;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compatibility endpoint used by the job executor to trigger all owner relays. */
@RestController
@RequestMapping("/internal/jobs/outbox")
@ConditionalOnLumiraAsyncEnabled
public class AsyncOutboxRelayController {
    private final OutboxRelayCoordinator coordinator;
    private final String jobToken;

    public AsyncOutboxRelayController(
            OutboxRelayCoordinator coordinator,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken
    ) {
        this.coordinator = coordinator;
        this.jobToken = jobToken;
    }

    @PostMapping("/relay")
    public ApiResponse<Integer> relay(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        return ApiResponse.success(coordinator.relayNow(), null);
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(jobToken)
                || !InternalJobTokenValidator.isAuthorized(token, jobToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized internal job access");
        }
    }
}
