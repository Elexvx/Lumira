package com.lumira.payment.controller;

import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.payment.service.PaymentInternalApiService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payment")
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false", matchIfMissing = true)
public class InternalPaymentController {

    private final PaymentInternalApiService paymentInternalApiService;

    public InternalPaymentController(PaymentInternalApiService paymentInternalApiService) {
        this.paymentInternalApiService = paymentInternalApiService;
    }

    @PostMapping("/orders")
    public PaymentOrderDTO createOrder(
            @RequestParam("operatorId") Long operatorId,
            @RequestParam("operatorUuid") String operatorUuid,
            @RequestBody PaymentCreateOrderRequestDTO request
    ) {
        requireInternalServicePrincipal();
        return paymentInternalApiService.createOrder(operatorId, operatorUuid, request);
    }

    @GetMapping("/orders/{orderNo}")
    public PaymentOrderDTO getOrder(
            @RequestParam("operatorId") Long operatorId,
            @RequestParam("operatorUuid") String operatorUuid,
            @PathVariable String orderNo
    ) {
        requireInternalServicePrincipal();
        return paymentInternalApiService.getOrder(operatorId, operatorUuid, orderNo);
    }

    private void requireInternalServicePrincipal() {
        if (!AuthenticationTrustSupport.isInternalServiceAuthentication(SecurityContextHolder.getContext().getAuthentication())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Internal service token is required");
        }
    }
}
