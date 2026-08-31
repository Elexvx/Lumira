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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fenced recovery surface. Normal relay execution is owned exclusively by this runtime's scheduler. */
@RestController
@RequestMapping("/internal/jobs/outbox")
@ConditionalOnLumiraAsyncEnabled
public class AsyncOutboxRelayController {
    private final OutboxRelayCoordinator coordinator;
    private final RecoveryFenceRegistry recoveryFences;
    private final String jobToken;

    public AsyncOutboxRelayController(
            OutboxRelayCoordinator coordinator,
            RecoveryFenceRegistry recoveryFences,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken
    ) {
        this.coordinator = coordinator;
        this.recoveryFences = recoveryFences;
        this.jobToken = jobToken;
    }

    @PostMapping("/recovery/{mode}/{owner}")
    public ApiResponse<Integer> recover(
            @PathVariable("mode") String mode,
            @PathVariable("owner") String owner,
            @RequestParam(name = "eventId", required = false) Long eventId,
            @RequestHeader(name = "X-Lumira-Operation-Epoch", required = false) Long operationEpoch,
            @RequestHeader(name = "X-Lumira-Fence-Token", required = false) String fenceToken,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        try {
            recoveryFences.assertCurrent(owner, operationEpoch == null ? 0L : operationEpoch, fenceToken);
            return switch (mode) {
                case "specified-replay" -> {
                    if (eventId == null || eventId <= 0L) {
                        throw new IllegalArgumentException("eventId is required for specified replay");
                    }
                    yield ApiResponse.success(coordinator.replay(owner, eventId) ? 1 : 0, null);
                }
                case "stale", "manual", "takeover" -> ApiResponse.success(coordinator.recoverOwner(owner), null);
                default -> throw new IllegalArgumentException("Unsupported recovery mode: " + mode);
            };
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, exception.getMessage());
        } catch (RecoveryFenceRegistry.StaleRecoveryFenceException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, exception.getMessage());
        }
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(jobToken)
                || !InternalJobTokenValidator.isAuthorized(token, jobToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized internal job access");
        }
    }
}
