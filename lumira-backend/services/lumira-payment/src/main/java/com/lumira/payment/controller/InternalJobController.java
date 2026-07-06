package com.lumira.payment.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraAsyncEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import com.lumira.payment.service.PaymentOutboxRelay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("paymentInternalJobController")
@RequestMapping("/payment/internal/jobs")
@ConditionalOnLumiraAsyncEnabled
public class InternalJobController {

    private final PaymentOutboxRelay paymentOutboxRelay;
    private final String paymentInternalToken;

    public InternalJobController(
            PaymentOutboxRelay paymentOutboxRelay,
            @Value("${saas.internal.payment-token:${SAAS_INTERNAL_PAYMENT_TOKEN:}}") String paymentInternalToken
    ) {
        this.paymentOutboxRelay = paymentOutboxRelay;
        this.paymentInternalToken = paymentInternalToken;
    }

    @PostMapping("/outbox/relay")
    public ApiResponse<Integer> relayOutbox(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        return ApiResponse.success(paymentOutboxRelay.dispatchPendingEvents(), null);
    }

    @PostMapping("/outbox/{id}/replay")
    public ApiResponse<Boolean> replayOutbox(
            @PathVariable("id") Long id,
            @RequestHeader(name = "X-Job-Token", required = false) String token
    ) {
        ensureAuthorized(token);
        requirePositiveId(id);
        return ApiResponse.success(paymentOutboxRelay.replay(id), null);
    }

    private void ensureAuthorized(String token) {
        String requiredToken = paymentInternalToken;
        if (!InternalJobTokenValidator.isConfigured(requiredToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Internal job token is not configured");
        }
        if (!InternalJobTokenValidator.isAuthorized(token, requiredToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized internal job access");
        }
    }

    private void requirePositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Valid outbox event id is required");
        }
    }
}
