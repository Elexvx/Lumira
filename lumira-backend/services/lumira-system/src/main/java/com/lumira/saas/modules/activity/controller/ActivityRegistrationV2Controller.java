package com.lumira.saas.modules.activity.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
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
    public ActivityRegistrationV2Controller(ActivityRegistrationAppService service, SecurityContextFacade securityContext, PermissionGuard permissionGuard) {
        this.service = service; this.securityContext = securityContext; this.permissionGuard = permissionGuard;
    }
    @GetMapping
    public ApiResponse<List<ActivityRegistrationVO>> list() {
        return ApiResponse.success(service.list(securityContext.getCurrentUser()), TraceContext.getRequestId());
    }
    @PostMapping
    @RepeatSubmit
    public ApiResponse<ActivityRegistrationVO> create(@Valid @RequestBody ActivityRegistrationDTO.CreateRequest request) {
        CurrentUser user = securityContext.getCurrentUser();
        permissionGuard.requirePermission(user, CREATE);
        return ApiResponse.success(service.create(user, request), TraceContext.getRequestId());
    }
}
