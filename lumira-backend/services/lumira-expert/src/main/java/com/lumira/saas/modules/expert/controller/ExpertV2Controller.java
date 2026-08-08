package com.lumira.saas.modules.expert.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.expert.app.ExpertAuthenticationTrust;
import com.lumira.saas.modules.expert.app.ExpertManagementAppService;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/v2/experts")
public class ExpertV2Controller {
    private static final String VIEW = "expert:view";
    private static final String UPDATE = "expert:update";
    private static final String DELETE = "expert:delete";
    private final ExpertManagementAppService expertManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedUserResolution;

    public ExpertV2Controller(
            ExpertManagementAppService expertManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(expertManagementAppService, securityContextFacade, permissionGuard, null, false);
    }

    @Autowired
    public ExpertV2Controller(
            ExpertManagementAppService expertManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(expertManagementAppService, securityContextFacade, permissionGuard, trustedCurrentUserResolver, true);
    }

    private ExpertV2Controller(
            ExpertManagementAppService expertManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.expertManagementAppService = expertManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping
    public ApiResponse<PageResponse<ExpertVO.Expert>> experts(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "approvalStatus", required = false) String approvalStatus,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require(VIEW);
        return ApiResponse.success(
                expertManagementAppService.listExperts(currentUser, keyword, status, approvalStatus, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpertVO.Expert> expert(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(VIEW);
        return ApiResponse.success(expertManagementAppService.getExpert(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<ExpertVO.Expert> createExpert(@Valid @RequestBody ExpertDTO.ExpertUpsertRequest request) {
        CurrentUser currentUser = requireTrustedUser(securityContextFacade.getCurrentUser());
        return ApiResponse.success(expertManagementAppService.createExpert(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<ExpertVO.Expert> updateExpert(@PathVariable("id") Long id, @Valid @RequestBody ExpertDTO.ExpertUpsertRequest request) {
        CurrentUser currentUser = require(UPDATE);
        return ApiResponse.success(expertManagementAppService.updateExpert(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteExpert(@PathVariable("id") Long id) {
        CurrentUser currentUser = require(DELETE);
        return ApiResponse.success(expertManagementAppService.deleteExpert(currentUser, id), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = requireTrustedUser(currentUser);
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        ExpertAuthenticationTrust.refresh(
                currentUser,
                trustedCurrentUserResolver,
                enforceTrustedUserResolution
        );
    }
}
