package com.lumira.saas.modules.system.config.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.system.config.app.SystemConfigVersioningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/platform/config-versions")
public class SystemConfigVersionController {

    private final SystemConfigVersioningService versioningService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final OperationAuditService operationAuditService;

    public SystemConfigVersionController(
            SystemConfigVersioningService versioningService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            OperationAuditService operationAuditService
    ) {
        this.versioningService = versioningService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.operationAuditService = operationAuditService;
    }

    @GetMapping
    public ApiResponse<PageResponse<SystemConfigVersioningService.VersionSummary>> history(
            @RequestParam(name = "groupCode", defaultValue = "SYSTEM_CONFIG") String groupCode,
            @RequestParam(name = "domainCode", defaultValue = "PLATFORM") String domainCode,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize
    ) {
        require("system:config:view");
        List<SystemConfigVersioningService.VersionSummary> records = versioningService.history(groupCode, domainCode, pageNo, pageSize);
        PageResponse<SystemConfigVersioningService.VersionSummary> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(versioningService.historyTotal(groupCode, domainCode));
        response.setPageNo(Math.max(1, pageNo));
        response.setPageSize(Math.max(1, Math.min(100, pageSize)));
        return ApiResponse.success(response, TraceContext.getRequestId());
    }

    @GetMapping("/status")
    public ApiResponse<SystemConfigVersioningService.ConfigStatus> status(
            @RequestParam(name = "groupCode", defaultValue = "SYSTEM_CONFIG") String groupCode,
            @RequestParam(name = "domainCode", defaultValue = "PLATFORM") String domainCode
    ) {
        require("system:config:view");
        return ApiResponse.success(versioningService.status(groupCode, domainCode), TraceContext.getRequestId());
    }

    @GetMapping("/{versionNo}")
    public ApiResponse<SystemConfigVersioningService.VersionDetail> detail(
            @PathVariable long versionNo,
            @RequestParam(name = "groupCode", defaultValue = "SYSTEM_CONFIG") String groupCode,
            @RequestParam(name = "domainCode", defaultValue = "PLATFORM") String domainCode
    ) {
        require("system:config:view");
        return ApiResponse.success(versioningService.detail(groupCode, domainCode, versionNo), TraceContext.getRequestId());
    }

    @PostMapping("/{versionNo}/rollback")
    public ApiResponse<SystemConfigVersioningService.VersionDetail> rollback(
            @PathVariable long versionNo,
            @RequestParam(name = "groupCode", defaultValue = "SYSTEM_CONFIG") String groupCode,
            @RequestParam(name = "domainCode", defaultValue = "PLATFORM") String domainCode,
            @Valid @RequestBody RollbackRequest request
    ) {
        CurrentUser currentUser = require("system:config:update");
        if (request == null || request.getExpectedConfigVersion() == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "expectedConfigVersion is required for rollback");
        }
        SystemConfigVersioningService.ChangeRequest change = new SystemConfigVersioningService.ChangeRequest(
                groupCode,
                domainCode,
                request.getExpectedConfigVersion(),
                request.getChangeReason(),
                currentUser,
                SystemConfigVersioningService.CHANGE_ROLLBACK,
                versionNo
        );
        SystemConfigVersioningService.VersionDetail result = versioningService.rollback(
                change,
                versionNo,
                request.getExpectedConfigVersion()
        );
        operationAuditService.log(
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                currentUser.getUsername(),
                "config-version",
                "rollback",
                "ROLLBACK",
                "SUCCESS",
                "Rollback " + groupCode + "/" + domainCode + " to version " + versionNo
        );
        return ApiResponse.success(result, TraceContext.getRequestId());
    }

    private CurrentUser require(String permission) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        permissionGuard.requirePermission(currentUser, permission);
        return currentUser;
    }

    public static class RollbackRequest {
        private Long expectedConfigVersion;
        private String changeReason;

        public Long getExpectedConfigVersion() {
            return expectedConfigVersion;
        }

        public void setExpectedConfigVersion(Long expectedConfigVersion) {
            this.expectedConfigVersion = expectedConfigVersion;
        }

        public String getChangeReason() {
            return changeReason;
        }

        public void setChangeReason(String changeReason) {
            this.changeReason = changeReason;
        }
    }
}
