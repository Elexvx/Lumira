package com.lumira.saas.modules.system.workorder.controller;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.system.workorder.app.WorkOrderFeedbackService;
import com.lumira.saas.modules.system.workorder.dto.WorkOrderFeedbackDTO;
import com.lumira.saas.modules.system.workorder.vo.WorkOrderFeedbackVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/work-order-feedback")
public class WorkOrderFeedbackController {

    private final WorkOrderFeedbackService workOrderFeedbackService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public WorkOrderFeedbackController(
            WorkOrderFeedbackService workOrderFeedbackService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.workOrderFeedbackService = workOrderFeedbackService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ApiResponse<PageResponse<WorkOrderFeedbackVO.WorkOrderRecord>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "priority", required = false) String priority,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("admin".equalsIgnoreCase(scope) ? "plugin:work-order-feedback:manage" : "plugin:work-order-feedback:view");
        return ApiResponse.success(
                workOrderFeedbackService.list(securityContextFacade.getCurrentUser(), keyword, status, priority, scope, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkOrderFeedbackVO.WorkOrderRecord> detail(
            @PathVariable("id") Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        require("admin".equalsIgnoreCase(scope) ? "plugin:work-order-feedback:manage" : "plugin:work-order-feedback:view");
        return ApiResponse.success(workOrderFeedbackService.detail(securityContextFacade.getCurrentUser(), id, scope), TraceContext.getRequestId());
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<WorkOrderFeedbackVO.WorkOrderRecord> create(@Valid @RequestBody WorkOrderFeedbackDTO.CreateRequest request) {
        require("plugin:work-order-feedback:create");
        return ApiResponse.success(workOrderFeedbackService.create(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<FileObjectDTO> uploadImage(@RequestParam("file") MultipartFile file) {
        require("plugin:work-order-feedback:create");
        return ApiResponse.success(workOrderFeedbackService.uploadImage(securityContextFacade.getCurrentUser(), file), TraceContext.getRequestId());
    }

    @PatchMapping("/{id}/status")
    @RepeatSubmit
    public ApiResponse<WorkOrderFeedbackVO.WorkOrderRecord> updateStatus(
            @PathVariable("id") Long id,
            @RequestBody WorkOrderFeedbackDTO.StatusRequest request
    ) {
        require("plugin:work-order-feedback:manage");
        return ApiResponse.success(workOrderFeedbackService.updateStatus(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
