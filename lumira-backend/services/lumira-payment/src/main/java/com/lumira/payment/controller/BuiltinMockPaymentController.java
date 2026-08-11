package com.lumira.payment.controller;

import com.lumira.api.payment.BuiltinMockPaymentCheckoutDTO;
import com.lumira.api.payment.BuiltinMockPaymentSimulationRequestDTO;
import com.lumira.api.payment.BuiltinMockPaymentSimulationResultDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.web.TraceContext;
import com.lumira.payment.service.BuiltinMockPaymentService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v2/payment/builtin-mock", "/api/v1/payment/builtin-mock"})
public class BuiltinMockPaymentController {

    private final BuiltinMockPaymentService builtinMockPaymentService;

    public BuiltinMockPaymentController(BuiltinMockPaymentService builtinMockPaymentService) {
        this.builtinMockPaymentService = builtinMockPaymentService;
    }

    @GetMapping("/orders/{orderNo}/checkout")
    public ApiResponse<BuiltinMockPaymentCheckoutDTO> checkout(@PathVariable String orderNo) {
        CurrentUser currentUser = requireAuthenticatedUser();
        return ApiResponse.success(
                builtinMockPaymentService.checkout(currentUser, orderNo),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/orders/{orderNo}/simulate")
    public ApiResponse<BuiltinMockPaymentSimulationResultDTO> simulate(
            @PathVariable String orderNo,
            @Valid @RequestBody BuiltinMockPaymentSimulationRequestDTO request
    ) {
        CurrentUser currentUser = requireAuthenticatedUser();
        return ApiResponse.success(
                builtinMockPaymentService.simulate(currentUser, orderNo, request),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser requireAuthenticatedUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        CurrentUser currentUser = authentication != null && authentication.getPrincipal() instanceof CurrentUser principal
                ? principal
                : null;
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }
}
