package com.lumira.saas.modules.competition.controller;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportAppService;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.export.ExportVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/aiadc/registration-exports")
public class CompetitionRegistrationExportController {
    private final CompetitionRegistrationExportAppService exportAppService;
    private final ExportTaskService exportTaskService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedSession;

    public CompetitionRegistrationExportController(
            CompetitionRegistrationExportAppService exportAppService,
            ExportTaskService exportTaskService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(
                exportAppService,
                exportTaskService,
                securityContextFacade,
                permissionGuard,
                null,
                false
        );
    }

    @Autowired
    public CompetitionRegistrationExportController(
            CompetitionRegistrationExportAppService exportAppService,
            ExportTaskService exportTaskService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                exportAppService,
                exportTaskService,
                securityContextFacade,
                permissionGuard,
                sessionAuthenticationService,
                true
        );
    }

    private CompetitionRegistrationExportController(
            CompetitionRegistrationExportAppService exportAppService,
            ExportTaskService exportTaskService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedSession
    ) {
        this.exportAppService = exportAppService;
        this.exportTaskService = exportTaskService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedSession = enforceTrustedSession;
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<ExportVO.ExportStartVO> startExport(
            @Valid @RequestBody CompetitionRegistrationDTO.RegistrationExportRequest request
    ) {
        CurrentUser currentUser = requireExportPermission(false);
        return ApiResponse.success(
                exportAppService.startExport(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/materials-package")
    @RepeatSubmit
    public ApiResponse<ExportVO.ExportStartVO> startMaterialPackage(
            @Valid @RequestBody CompetitionRegistrationDTO.RegistrationExportRequest request
    ) {
        CurrentUser currentUser = requireExportPermission(true);
        return ApiResponse.success(
                exportAppService.startMaterialPackage(currentUser, request),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ExportVO.ExportTaskVO> task(@PathVariable("taskId") Long taskId) {
        CurrentUser currentUser = requireExportPermission(false);
        return ApiResponse.success(
                exportTaskService.getTask(
                        currentUser,
                        taskId,
                        CompetitionRegistrationExportAppService.EXPORT_PERMISSION
                ),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser requireExportPermission(boolean requireMaterialDownload) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (isTrustedCurrentUser(currentUser) && sessionAuthenticationService != null) {
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess =
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    );
            currentUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        } else if (enforceTrustedSession) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted session resolver is unavailable");
        }
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        permissionGuard.requirePermission(
                currentUser,
                CompetitionRegistrationExportAppService.EXPORT_PERMISSION
        );
        if (requireMaterialDownload) {
            permissionGuard.requirePermission(
                    currentUser,
                    CompetitionRegistrationExportAppService.MATERIAL_DOWNLOAD_PERMISSION
            );
        }
        return currentUser;
    }
}
