package com.lumira.saas.modules.competition.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.competition.app.CompetitionPaymentConsistencyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator diagnostics and owner-safe recovery for payment/registration consistency. */
@RestController
@RequestMapping("/api/v2/aiadc/payment-consistency")
public class CompetitionPaymentConsistencyController {
    private static final String PAYMENT_VIEW = "payment:order:view";
    private static final String REGISTRATION_VIEW = "aiadc:registration:view";
    private static final String PAYMENT_REPLAY = "payment:webhook:retry";

    private final CompetitionPaymentConsistencyService consistencyService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public CompetitionPaymentConsistencyController(
            CompetitionPaymentConsistencyService consistencyService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.consistencyService = consistencyService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ApiResponse<CompetitionPaymentConsistencyService.Snapshot> snapshot() {
        CurrentUser currentUser = requireTrustedUser();
        permissionGuard.requirePermission(currentUser, PAYMENT_VIEW);
        permissionGuard.requirePermission(currentUser, REGISTRATION_VIEW);
        return ApiResponse.success(consistencyService.refresh(), TraceContext.getRequestId());
    }

    @PostMapping("/{orderNo}/replay")
    @RepeatSubmit
    public ApiResponse<CompetitionPaymentConsistencyService.ReplayResult> replay(
            @PathVariable("orderNo") String orderNo
    ) {
        CurrentUser currentUser = requireTrustedUser();
        permissionGuard.requirePermission(currentUser, PAYMENT_VIEW);
        permissionGuard.requirePermission(currentUser, REGISTRATION_VIEW);
        permissionGuard.requirePermission(currentUser, PAYMENT_REPLAY);
        return ApiResponse.success(
                consistencyService.replayPaidRegistrationEvent(orderNo),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser requireTrustedUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }
}
