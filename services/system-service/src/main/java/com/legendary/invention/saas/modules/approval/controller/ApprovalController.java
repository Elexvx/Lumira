package com.legendary.invention.saas.modules.approval.controller;

import com.legendary.invention.saas.common.annotation.RepeatSubmit;
import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.approval.app.ApprovalAppService;
import com.legendary.invention.saas.modules.approval.dto.ApprovalDTO;
import com.legendary.invention.saas.modules.approval.vo.ApprovalVO;
import com.legendary.invention.saas.modules.iam.service.PermissionGuard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalAppService approvalAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public ApprovalController(ApprovalAppService approvalAppService, SecurityContextFacade securityContextFacade, PermissionGuard permissionGuard) {
        this.approvalAppService = approvalAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/templates")
    public ApiResponse<PageResponse<ApprovalVO.TemplateVO>> templates(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        require("approval:view");
        return ApiResponse.success(approvalAppService.listTemplates(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/templates")
    @RepeatSubmit
    public ApiResponse<ApprovalVO.TemplateVO> createTemplate(@Valid @RequestBody ApprovalDTO.TemplateRequest request) {
        require("approval:template:manage");
        return ApiResponse.success(approvalAppService.createTemplate(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/templates/{id}")
    @RepeatSubmit
    public ApiResponse<ApprovalVO.TemplateVO> updateTemplate(@PathVariable Long id, @Valid @RequestBody ApprovalDTO.TemplateRequest request) {
        require("approval:template:manage");
        return ApiResponse.success(approvalAppService.updateTemplate(currentUser(), id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/templates/{id}/enabled")
    @RepeatSubmit
    public ApiResponse<Boolean> updateTemplateEnabled(@PathVariable Long id, @RequestBody ApprovalDTO.EnabledRequest request) {
        require("approval:template:manage");
        return ApiResponse.success(approvalAppService.updateTemplateEnabled(currentUser(), id, request.isEnabled()), TraceContext.getRequestId());
    }

    @PostMapping("/instances")
    @RepeatSubmit
    public ApiResponse<ApprovalVO.InstanceVO> createInstance(@Valid @RequestBody ApprovalDTO.InstanceCreateRequest request) {
        require("approval:submit");
        return ApiResponse.success(approvalAppService.start(currentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/instances")
    public ApiResponse<PageResponse<ApprovalVO.InstanceVO>> instances(@RequestParam(required = false) String scope, @RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        require("approval:view");
        return ApiResponse.success(approvalAppService.listInstances(currentUser(), scope, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/instances/{id}")
    public ApiResponse<ApprovalVO.InstanceVO> instance(@PathVariable Long id) {
        require("approval:view");
        return ApiResponse.success(approvalAppService.getInstance(currentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/instances/{id}/cancel")
    @RepeatSubmit
    public ApiResponse<ApprovalVO.InstanceVO> cancel(@PathVariable Long id) {
        require("approval:submit");
        return ApiResponse.success(approvalAppService.cancel(currentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/tasks/my-pending")
    public ApiResponse<PageResponse<ApprovalVO.TaskVO>> myPending(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        require("approval:approve");
        return ApiResponse.success(approvalAppService.myPendingTasks(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/tasks/{taskId}/approve")
    @RepeatSubmit
    public ApiResponse<ApprovalVO.InstanceVO> approve(@PathVariable Long taskId, @RequestBody ApprovalDTO.HandleTaskRequest request) {
        require("approval:approve");
        return ApiResponse.success(approvalAppService.approve(currentUser(), taskId, request.getComment()), TraceContext.getRequestId());
    }

    @PostMapping("/tasks/{taskId}/reject")
    @RepeatSubmit
    public ApiResponse<ApprovalVO.InstanceVO> reject(@PathVariable Long taskId, @RequestBody ApprovalDTO.HandleTaskRequest request) {
        require("approval:approve");
        return ApiResponse.success(approvalAppService.reject(currentUser(), taskId, request.getComment()), TraceContext.getRequestId());
    }

    private CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(currentUser(), permissionKey);
    }
}
