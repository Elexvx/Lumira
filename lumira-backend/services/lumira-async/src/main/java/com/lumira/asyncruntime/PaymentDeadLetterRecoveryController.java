package com.lumira.asyncruntime;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Protected inspection and replay surface for the payment Stream DLQ. */
@RestController
@RequestMapping("/internal/jobs/payment-events/dead-letter")
@ConditionalOnLumiraAsyncEnabled
@ConditionalOnProperty(prefix = "lumira.event.payment-consumer", name = "enabled", havingValue = "true")
public class PaymentDeadLetterRecoveryController {
    private static final String FENCE_OWNER = "payment-stream";
    private static final Logger log = LoggerFactory.getLogger(PaymentDeadLetterRecoveryController.class);

    private final PaymentEventStreamConsumer consumer;
    private final RecoveryFenceRegistry fences;
    private final String paymentToken;

    public PaymentDeadLetterRecoveryController(
            PaymentEventStreamConsumer consumer,
            RecoveryFenceRegistry fences,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String paymentToken
    ) {
        this.consumer = consumer;
        this.fences = fences;
        this.paymentToken = paymentToken;
    }

    @GetMapping("/stats")
    public ApiResponse<PaymentEventStreamConsumer.StreamStats> stats(
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        return ApiResponse.success(consumer.streamStats(), null);
    }

    @GetMapping
    public ApiResponse<List<PaymentEventStreamConsumer.DeadLetterRecord>> list(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        if (limit < 1 || limit > 100) throw new BizException(ErrorCode.BAD_REQUEST, "limit must be between 1 and 100");
        return ApiResponse.success(consumer.deadLetters(limit), null);
    }

    @PostMapping("/{recordId}/replay")
    public ApiResponse<PaymentEventStreamConsumer.ReplayResult> replay(
            @PathVariable("recordId") String recordId,
            @RequestHeader(name = "X-Lumira-Operation-Epoch", required = false) Long operationEpoch,
            @RequestHeader(name = "X-Lumira-Fence-Token", required = false) String fenceToken,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        try {
            fences.assertCurrent(FENCE_OWNER, operationEpoch == null ? 0L : operationEpoch, fenceToken);
            PaymentEventStreamConsumer.ReplayResult result = consumer.replayDeadLetter(recordId);
            log.info(
                    "Payment DLQ replay audit owner={} operationEpoch={} dlqRecordId={} replayedStreamId={} found={} dlqDeleted={} fenceValidated=true",
                    FENCE_OWNER,
                    operationEpoch,
                    result.dlqRecordId(),
                    result.replayedStreamId(),
                    result.found(),
                    result.dlqDeleted()
            );
            return ApiResponse.success(result, null);
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, exception.getMessage());
        } catch (RecoveryFenceRegistry.StaleRecoveryFenceException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, exception.getMessage());
        }
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(paymentToken)
                || !InternalJobTokenValidator.isAuthorized(token, paymentToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized payment Stream recovery access");
        }
    }
}
