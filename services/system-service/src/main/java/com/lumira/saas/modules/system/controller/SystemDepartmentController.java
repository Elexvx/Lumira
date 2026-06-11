package com.lumira.saas.modules.system.controller;

import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.modules.system.department.app.SystemDepartmentAppService;
import com.lumira.saas.modules.system.department.dto.DepartmentUpsertRequest;
import com.lumira.saas.modules.system.department.vo.DepartmentVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/departments")
public class SystemDepartmentController {

    private final SystemDepartmentAppService departmentAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public SystemDepartmentController(
            SystemDepartmentAppService departmentAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.departmentAppService = departmentAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ApiResponse<List<DepartmentVO>> list() {
        require("system:department:view");
        return ApiResponse.success(departmentAppService.listDepartments(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/{id}")
    public ApiResponse<DepartmentVO> detail(@PathVariable("id") Long id) {
        require("system:department:view");
        return ApiResponse.success(departmentAppService.getDepartment(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping
    @RepeatSubmit
    public ApiResponse<DepartmentVO> create(@Valid @RequestBody DepartmentUpsertRequest request) {
        require("system:department:create");
        return ApiResponse.success(departmentAppService.createDepartment(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<DepartmentVO> update(@PathVariable("id") Long id, @Valid @RequestBody DepartmentUpsertRequest request) {
        require("system:department:update");
        return ApiResponse.success(departmentAppService.updateDepartment(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> delete(@PathVariable("id") Long id) {
        require("system:department:delete");
        return ApiResponse.success(departmentAppService.deleteDepartment(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
