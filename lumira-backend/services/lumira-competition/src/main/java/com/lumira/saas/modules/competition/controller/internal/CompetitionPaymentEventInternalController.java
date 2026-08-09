package com.lumira.saas.modules.competition.controller.internal;

import com.lumira.api.competition.CompetitionPaymentEventHandler;
import com.lumira.api.competition.CompetitionPaymentEventRequest;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.common.web.InternalJobTokenValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner adapter for the async payment stream worker. The handler keeps both
 * the competition write and its idempotency receipt in the control plane.
 */
@RestController
@RequestMapping("/internal/jobs/competition")
@ConditionalOnLumiraControlPlaneEnabled
public class CompetitionPaymentEventInternalController {
    private final CompetitionPaymentEventHandler handler;
    private final String jobToken;

    public CompetitionPaymentEventInternalController(
            CompetitionPaymentEventHandler handler,
            @Value("${saas.internal.job-token:${SAAS_INTERNAL_JOB_TOKEN:}}") String jobToken
    ) {
        this.handler = handler;
        this.jobToken = jobToken;
    }

    @PostMapping("/payment-order-paid")
    public ApiResponse<Boolean> handlePaymentOrderPaid(
            @RequestHeader(name = "X-Job-Token", required = false) String token,
            @RequestBody CompetitionPaymentEventRequest request
    ) {
        ensureAuthorized(token);
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Payment event request is required");
        }
        return ApiResponse.success(handler.handleOrderPaid(
                request.eventId(),
                request.orderNo(),
                request.registrationId(),
                request.ownerUserId(),
                request.ownerUserUuid()
        ), null);
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(jobToken)
                || !InternalJobTokenValidator.isAuthorized(token, jobToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Unauthorized internal job access");
        }
    }
}
