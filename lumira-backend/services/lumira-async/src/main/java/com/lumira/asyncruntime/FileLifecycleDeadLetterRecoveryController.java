package com.lumira.asyncruntime;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Protected inspection and fenced replay surface for File lifecycle events. */
@RestController
@RequestMapping("/internal/jobs/file-lifecycle/dead-letter")
@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "lumira.event.file-consumer", name = "enabled", havingValue = "true")
public class FileLifecycleDeadLetterRecoveryController {

    private static final String FENCE_OWNER = "file-lifecycle-stream";

    private final FileLifecycleConsumer consumer;
    private final RecoveryFenceRegistry fences;
    private final String jobToken;

    public FileLifecycleDeadLetterRecoveryController(
            FileLifecycleConsumer consumer,
            RecoveryFenceRegistry fences,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken
    ) {
        this.consumer = consumer;
        this.fences = fences;
        this.jobToken = jobToken;
    }

    @GetMapping("/stats")
    public ApiResponse<FileLifecycleConsumer.StreamStats> stats(
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        return ApiResponse.success(consumer.streamStats(), null);
    }

    @GetMapping
    public ApiResponse<List<FileLifecycleConsumer.DeadLetterRecord>> list(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        if (limit < 1 || limit > 100) {
            throw new BizException(ErrorCode.BAD_REQUEST, "limit must be between 1 and 100");
        }
        return ApiResponse.success(consumer.deadLetters(limit), null);
    }

    @PostMapping("/{recordId}/replay")
    public ApiResponse<FileLifecycleConsumer.ReplayResult> replay(
            @PathVariable("recordId") String recordId,
            @RequestHeader(name = "X-Lumira-Operation-Epoch", required = false) Long operationEpoch,
            @RequestHeader(name = "X-Lumira-Fence-Token", required = false) String fenceToken,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        try {
            fences.assertCurrent(FENCE_OWNER, operationEpoch == null ? 0L : operationEpoch, fenceToken);
            return ApiResponse.success(consumer.replayDeadLetter(recordId), null);
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, exception.getMessage());
        } catch (RecoveryFenceRegistry.StaleRecoveryFenceException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, exception.getMessage());
        }
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(jobToken)
                || !InternalJobTokenValidator.isAuthorized(token, jobToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized File lifecycle recovery access");
        }
    }
}
