package com.lumira.saas.modules.competition.controller;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

import com.lumira.api.export.ExportTaskPort;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.competition.app.CompetitionAuthenticationTrust;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportAppService;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.export.ExportVO;
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
    private final ExportTaskPort exportTaskPort;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedSession;

    public CompetitionRegistrationExportController(
            CompetitionRegistrationExportAppService exportAppService,
            ExportTaskPort exportTaskPort,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(
                exportAppService,
                exportTaskPort,
                securityContextFacade,
                permissionGuard,
                null,
                false
        );
    }

    @Autowired
    public CompetitionRegistrationExportController(
            CompetitionRegistrationExportAppService exportAppService,
            ExportTaskPort exportTaskPort,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(
                exportAppService,
                exportTaskPort,
                securityContextFacade,
                permissionGuard,
                trustedCurrentUserResolver,
                true
        );
    }

    private CompetitionRegistrationExportController(
            CompetitionRegistrationExportAppService exportAppService,
            ExportTaskPort exportTaskPort,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedSession
    ) {
        this.exportAppService = exportAppService;
        this.exportTaskPort = exportTaskPort;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
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
                toExportTaskVo(exportTaskPort.getTask(
                        currentUser,
                        taskId,
                        CompetitionRegistrationExportAppService.EXPORT_PERMISSION
                )),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser requireExportPermission(boolean requireMaterialDownload) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        CompetitionAuthenticationTrust.refresh(currentUser, trustedCurrentUserResolver, enforceTrustedSession);
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

    private ExportVO.ExportTaskVO toExportTaskVo(ExportTaskPort.ExportTaskView task) {
        ExportVO.ExportTaskVO response = new ExportVO.ExportTaskVO();
        response.setId(task.id());
        response.setModuleKey(task.moduleKey());
        response.setStatus(task.status());
        response.setTotalCount(task.totalCount());
        response.setFileId(task.fileId());
        response.setFileName(task.fileName());
        response.setDownloadUrl(task.downloadUrl());
        response.setErrorMessage(task.errorMessage());
        response.setCreatedAt(task.createdAt());
        response.setStartedAt(task.startedAt());
        response.setFinishedAt(task.finishedAt());
        return response;
    }
}
