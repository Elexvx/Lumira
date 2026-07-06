package com.lumira.saas.modules.activity.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.activity.app.ActivityManagementAppService;
import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v2/aiadc/activities")
public class ActivityV2Controller {
    private static final String VIEW = "aiadc:activity:view";
    private static final String CREATE = "aiadc:activity:create";
    private static final String UPDATE = "aiadc:activity:update";
    private static final String DELETE = "aiadc:activity:delete";

    private final ActivityManagementAppService activityManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public ActivityV2Controller(
            ActivityManagementAppService activityManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.activityManagementAppService = activityManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ApiResponse<PageResponse<ActivityVO.Activity>> activities(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "locale", required = false) String locale,
            @RequestParam(name = "featured", required = false) Boolean featured,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require(VIEW);
        return ApiResponse.success(
                activityManagementAppService.listActivities(currentUser, keyword, status, locale, featured, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityVO.Activity> activity(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(VIEW);
        return ApiResponse.success(activityManagementAppService.getActivity(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<ActivityVO.Activity> createActivity(@Valid @RequestBody ActivityDTO.ActivityUpsertRequest request) {
        CurrentUser currentUser = require(CREATE);
        return ApiResponse.success(activityManagementAppService.createActivity(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<ActivityVO.Activity> updateActivity(@PathVariable("id") Long id, @Valid @RequestBody ActivityDTO.ActivityUpsertRequest request) {
        CurrentUser currentUser = require(UPDATE);
        return ApiResponse.success(activityManagementAppService.updateActivity(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteActivity(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(DELETE);
        return ApiResponse.success(activityManagementAppService.deleteActivity(currentUser, id), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        permissionGuard.requirePermission(currentUser, permissionKey);
        return requireTrustedUser(currentUser);
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }
}
