package com.legendary.invention.payment.controller;

import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.web.InternalJobTokenValidator;
import com.legendary.invention.payment.service.PaymentOutboxRelay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("paymentInternalJobController")
@RequestMapping("/internal/jobs")
public class InternalJobController {

    private final PaymentOutboxRelay paymentOutboxRelay;
    private final String internalToken;

    public InternalJobController(
            PaymentOutboxRelay paymentOutboxRelay,
            @Value("${saas.job.internal-token:${SAAS_JOB_INTERNAL_TOKEN:}}") String internalToken
    ) {
        this.paymentOutboxRelay = paymentOutboxRelay;
        this.internalToken = internalToken;
    }

    @PostMapping("/outbox/relay")
    public ApiResponse<Integer> relayOutbox(@RequestHeader(name = "X-Job-Token", required = false) String token) {
        ensureAuthorized(token);
        return ApiResponse.success(paymentOutboxRelay.dispatchPendingEvents(), null);
    }

    private void ensureAuthorized(String token) {
        if (!InternalJobTokenValidator.isConfigured(internalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "内部任务令牌未配置");
        }
        if (!InternalJobTokenValidator.isAuthorized(token, internalToken)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问内部任务接口");
        }
    }
}
