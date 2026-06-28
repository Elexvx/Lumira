package com.lumira.saas.modules.workflow.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.workflow.app.WorkflowAppService;
import com.lumira.saas.modules.workflow.dto.WorkflowDTO;
import com.lumira.saas.modules.workflow.vo.WorkflowVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/workflows")
public class WorkflowV2Controller {
    private static final String VIEW = "workflow:view";
    private static final String CONFIG = "workflow:config";
    private static final String APPROVE = "workflow:approve";

    private final WorkflowAppService workflowAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public WorkflowV2Controller(
            WorkflowAppService workflowAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.workflowAppService = workflowAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/definitions/{businessType}")
    public ApiResponse<WorkflowVO.Definition> definition(@PathVariable("businessType") String businessType) {
        require(VIEW);
        return ApiResponse.success(workflowAppService.getDefinition(securityContextFacade.getCurrentUser(), businessType), TraceContext.getRequestId());
    }

    @PutMapping("/definitions/{businessType}/draft")
    @RepeatSubmit
    public ApiResponse<WorkflowVO.Definition> saveDraft(
            @PathVariable("businessType") String businessType,
            @Valid @RequestBody WorkflowDTO.DefinitionSaveRequest request
    ) {
        require(CONFIG);
        return ApiResponse.success(workflowAppService.saveDraft(securityContextFacade.getCurrentUser(), businessType, request), TraceContext.getRequestId());
    }

    @PostMapping("/definitions/{businessType}/publish")
    @RepeatSubmit
    public ApiResponse<WorkflowVO.Definition> publish(@PathVariable("businessType") String businessType) {
        require(CONFIG);
        return ApiResponse.success(workflowAppService.publish(securityContextFacade.getCurrentUser(), businessType), TraceContext.getRequestId());
    }

    @GetMapping("/tasks/my")
    public ApiResponse<PageResponse<WorkflowVO.Task>> myTasks(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require(APPROVE);
        return ApiResponse.success(workflowAppService.listMyTasks(securityContextFacade.getCurrentUser(), status, pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/tasks/{taskId}/approve")
    @RepeatSubmit
    public ApiResponse<Boolean> approve(@PathVariable("taskId") Long taskId, @RequestBody(required = false) WorkflowDTO.WorkflowActionRequest request) {
        require(APPROVE);
        return ApiResponse.success(
                workflowAppService.approveTask(securityContextFacade.getCurrentUser(), taskId, request == null ? null : request.getComment()),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/tasks/{taskId}/reject")
    @RepeatSubmit
    public ApiResponse<Boolean> reject(@PathVariable("taskId") Long taskId, @RequestBody(required = false) WorkflowDTO.WorkflowActionRequest request) {
        require(APPROVE);
        return ApiResponse.success(
                workflowAppService.rejectTask(securityContextFacade.getCurrentUser(), taskId, request == null ? null : request.getComment()),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/instances/{instanceId}/logs")
    public ApiResponse<List<WorkflowVO.ActionLog>> logs(@PathVariable("instanceId") Long instanceId) {
        require(VIEW);
        return ApiResponse.success(workflowAppService.listLogs(securityContextFacade.getCurrentUser(), instanceId), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
