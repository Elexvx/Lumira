package com.lumira.saas.modules.expert.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.expert.app.ExpertManagementAppService;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.vo.ExpertVO;
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

@RestController
@RequestMapping("/api/v2/experts")
public class ExpertV2Controller {
    private static final String VIEW = "expert:view";
    private static final String CREATE = "expert:create";
    private static final String UPDATE = "expert:update";
    private static final String DELETE = "expert:delete";

    private final ExpertManagementAppService expertManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public ExpertV2Controller(
            ExpertManagementAppService expertManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.expertManagementAppService = expertManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ApiResponse<PageResponse<ExpertVO.Expert>> experts(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "approvalStatus", required = false) String approvalStatus,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require(VIEW);
        return ApiResponse.success(
                expertManagementAppService.listExperts(securityContextFacade.getCurrentUser(), keyword, status, approvalStatus, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpertVO.Expert> expert(@PathVariable("id") Long id) {
        require(VIEW);
        return ApiResponse.success(expertManagementAppService.getExpert(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<ExpertVO.Expert> createExpert(@Valid @RequestBody ExpertDTO.ExpertUpsertRequest request) {
        require(CREATE);
        return ApiResponse.success(expertManagementAppService.createExpert(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<ExpertVO.Expert> updateExpert(@PathVariable("id") Long id, @Valid @RequestBody ExpertDTO.ExpertUpsertRequest request) {
        require(UPDATE);
        return ApiResponse.success(expertManagementAppService.updateExpert(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteExpert(@PathVariable("id") Long id) {
        require(DELETE);
        return ApiResponse.success(expertManagementAppService.deleteExpert(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
