package com.lumira.saas.modules.competition.controller;

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.export.ExportTaskPort;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.competition.app.CompetitionAuthenticationTrust;
import com.lumira.saas.modules.competition.app.CompetitionCapability;
import com.lumira.saas.modules.competition.app.CompetitionRef;
import com.lumira.saas.modules.competition.app.CompetitionWorkspaceAccessPolicy;
import com.lumira.saas.modules.competition.app.CompetitionRegistrationExportAppService;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.export.ExportVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UUID-scoped export endpoints with task-to-competition revalidation. */
@RestController
@RequestMapping("/api/v2/aiadc/competitions/{competitionUuid}/registration-exports")
public class CompetitionWorkspaceExportController {
    private final CompetitionRegistrationExportAppService exportAppService;
    private final ExportTaskPort exportTaskPort;
    private final CompetitionWorkspaceAccessPolicy accessPolicy;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final ObjectMapper objectMapper;

    @Autowired
    public CompetitionWorkspaceExportController(
            CompetitionRegistrationExportAppService exportAppService,
            ExportTaskPort exportTaskPort,
            CompetitionWorkspaceAccessPolicy accessPolicy,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            ObjectMapper objectMapper
    ) {
        this.exportAppService = exportAppService;
        this.exportTaskPort = exportTaskPort;
        this.accessPolicy = accessPolicy;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ApiResponse<ExportVO.ExportStartVO> startExport(
            @PathVariable String competitionUuid,
            @Valid @RequestBody WorkspaceExportRequest request
    ) {
        CurrentUser currentUser = requireExportUser(competitionUuid, false);
        return ApiResponse.success(exportAppService.startExport(currentUser, toExportRequest(currentUser, competitionUuid, request)), TraceContext.getRequestId());
    }

    @PostMapping("/materials-package")
    public ApiResponse<ExportVO.ExportStartVO> startMaterialPackage(
            @PathVariable String competitionUuid,
            @Valid @RequestBody WorkspaceExportRequest request
    ) {
        CurrentUser currentUser = requireExportUser(competitionUuid, true);
        return ApiResponse.success(exportAppService.startMaterialPackage(currentUser, toExportRequest(currentUser, competitionUuid, request)), TraceContext.getRequestId());
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ExportVO.ExportTaskVO> task(
            @PathVariable String competitionUuid,
            @PathVariable Long taskId
    ) {
        CurrentUser currentUser = requireExportUser(competitionUuid, false);
        CompetitionRef competition = accessPolicy.requireAccessibleCompetition(currentUser, competitionUuid, CompetitionCapability.REGISTRATION_READ).competition();
        String payload = exportTaskPort.getTaskRequestPayload(
                currentUser,
                taskId,
                CompetitionRegistrationExportAppService.EXPORT_PERMISSION
        );
        if (!belongsToCompetition(payload, competition.id())) {
            throw new BizException(ErrorCode.NOT_FOUND, "Export task does not exist");
        }
        return ApiResponse.success(
                toExportTaskVo(exportTaskPort.getTask(
                        currentUser,
                        taskId,
                        CompetitionRegistrationExportAppService.EXPORT_PERMISSION
                )),
                TraceContext.getRequestId()
        );
    }

    private CurrentUser requireExportUser(String competitionUuid, boolean materialPackage) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        CompetitionAuthenticationTrust.refresh(currentUser, trustedCurrentUserResolver, true);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        accessPolicy.requireAccessibleCompetition(currentUser, competitionUuid, CompetitionCapability.REGISTRATION_READ);
        permissionGuard.requirePermission(currentUser, CompetitionRegistrationExportAppService.EXPORT_PERMISSION);
        if (materialPackage) {
            permissionGuard.requirePermission(currentUser, CompetitionRegistrationExportAppService.MATERIAL_DOWNLOAD_PERMISSION);
        }
        return currentUser;
    }

    private CompetitionRegistrationDTO.RegistrationExportRequest toExportRequest(
            CurrentUser currentUser,
            String competitionUuid,
            WorkspaceExportRequest request
    ) {
        CompetitionRef competition = accessPolicy.requireAccessibleCompetition(currentUser, competitionUuid, CompetitionCapability.REGISTRATION_READ).competition();
        CompetitionRegistrationDTO.RegistrationExportRequest exportRequest = new CompetitionRegistrationDTO.RegistrationExportRequest();
        exportRequest.setCompetitionId(competition.id());
        exportRequest.setStatus(request.status);
        exportRequest.setKeyword(request.keyword);
        exportRequest.setRegistrationIds(request.registrationIds);
        return exportRequest;
    }

    private boolean belongsToCompetition(String payload, Long competitionId) {
        if (payload == null || competitionId == null) return false;
        try {
            JsonNode root = objectMapper.readTree(payload);
            return root.path("competitionId").asLong(-1L) == competitionId;
        } catch (Exception ignored) {
            return false;
        }
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

    public static class WorkspaceExportRequest {
        @Size(max = 32)
        private String status;
        @Size(max = 128)
        private String keyword;
        @Size(max = 500)
        private List<Long> registrationIds;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public List<Long> getRegistrationIds() { return registrationIds; }
        public void setRegistrationIds(List<Long> registrationIds) { this.registrationIds = registrationIds; }
    }
}
