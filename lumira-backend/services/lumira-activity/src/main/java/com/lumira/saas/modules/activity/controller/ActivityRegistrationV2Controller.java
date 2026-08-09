package com.lumira.saas.modules.activity.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.activity.app.ActivityRegistrationAppService;
import com.lumira.saas.modules.activity.dto.ActivityRegistrationDTO;
import com.lumira.saas.modules.activity.vo.ActivityRegistrationVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/aiadc/activity-registrations")
public class ActivityRegistrationV2Controller {
    private static final String CREATE = "aiadc:activity:create";
    private final ActivityRegistrationAppService service;
    private final SecurityContextFacade securityContext;
    private final PermissionGuard permissionGuard;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedUserResolution;

    public ActivityRegistrationV2Controller(
            ActivityRegistrationAppService service,
            SecurityContextFacade securityContext,
            PermissionGuard permissionGuard
    ) {
        this(service, securityContext, permissionGuard, null, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ActivityRegistrationV2Controller(
            ActivityRegistrationAppService service,
            SecurityContextFacade securityContext,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(service, securityContext, permissionGuard, trustedCurrentUserResolver, true);
    }

    public ActivityRegistrationV2Controller(
            ActivityRegistrationAppService service,
            SecurityContextFacade securityContext,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.service = service;
        this.securityContext = securityContext;
        this.permissionGuard = permissionGuard;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }
    @GetMapping
    public ApiResponse<List<ActivityRegistrationVO>> list() {
        return ApiResponse.success(service.list(currentUser()), TraceContext.getRequestId());
    }
    @PostMapping
    @RepeatSubmit
    public ApiResponse<ActivityRegistrationVO> create(@Valid @RequestBody ActivityRegistrationDTO.CreateRequest request) {
        CurrentUser user = currentUser();
        permissionGuard.requirePermission(user, CREATE);
        return ApiResponse.success(service.create(user, request), TraceContext.getRequestId());
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = securityContext.getCurrentUser();
        if (!com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new com.lumira.common.exception.BizException(com.lumira.common.enums.ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (trustedCurrentUserResolver == null) {
            if (enforceTrustedUserResolution) {
                throw new com.lumira.common.exception.BizException(
                        com.lumira.common.enums.ErrorCode.UNAUTHORIZED,
                        "Trusted user resolver is unavailable"
                );
            }
            return currentUser;
        }
        CurrentUser resolvedCurrentUser = trustedCurrentUserResolver.resolve(currentUser);
        if (!com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser(resolvedCurrentUser)) {
            throw new com.lumira.common.exception.BizException(com.lumira.common.enums.ErrorCode.UNAUTHORIZED, "Login required");
        }
        return resolvedCurrentUser;
    }
}
