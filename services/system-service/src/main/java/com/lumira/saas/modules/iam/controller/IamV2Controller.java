package com.lumira.saas.modules.iam.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.iam.app.IamTenantCommandService;
import com.lumira.saas.modules.iam.app.IamTenantQueryService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.department.app.SystemDepartmentAppService;
import com.lumira.saas.modules.system.department.dto.DepartmentUpsertRequest;
import com.lumira.saas.modules.system.department.vo.DepartmentVO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.export.ExportDTO;
import com.lumira.saas.modules.system.export.ExportFieldVO;
import com.lumira.saas.modules.system.export.ExportTaskService;
import com.lumira.saas.modules.system.export.ExportVO;
import com.lumira.saas.modules.system.user.app.UserExportAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v2/iam")
public class IamV2Controller {

    private final SystemManagementAppService systemManagementAppService;
    private final SystemDepartmentAppService departmentAppService;
    private final IamTenantCommandService tenantCommandService;
    private final IamTenantQueryService tenantQueryService;
    private final UserExportAppService userExportAppService;
    private final ExportTaskService exportTaskService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public IamV2Controller(
            SystemManagementAppService systemManagementAppService,
            SystemDepartmentAppService departmentAppService,
            IamTenantCommandService tenantCommandService,
            IamTenantQueryService tenantQueryService,
            UserExportAppService userExportAppService,
            ExportTaskService exportTaskService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.departmentAppService = departmentAppService;
        this.tenantCommandService = tenantCommandService;
        this.tenantQueryService = tenantQueryService;
        this.userExportAppService = userExportAppService;
        this.exportTaskService = exportTaskService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/permissions")
    public ApiResponse<List<SystemVO.PermissionVO>> permissions() {
        require("system:role:view");
        return ApiResponse.success(systemManagementAppService.listPermissions(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/permissions/tree")
    public ApiResponse<List<SystemVO.PermissionTreeVO>> permissionTree() {
        require("system:role:view");
        return ApiResponse.success(systemManagementAppService.listPermissionTree(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/tenants/current")
    public ApiResponse<IamTenantQueryService.TenantSnapshot> currentTenant() {
        return ApiResponse.success(tenantQueryService.currentTenant(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/tenants/mine")
    public ApiResponse<List<IamTenantQueryService.TenantSnapshot>> myTenants() {
        return ApiResponse.success(tenantQueryService.listCurrentUserTenants(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PostMapping("/tenants")
    @RepeatSubmit
    public ApiResponse<IamTenantQueryService.TenantSnapshot> createTenant(@Valid @RequestBody IamTenantCommandService.TenantUpsertRequest request) {
        require("system:tenant:create");
        return ApiResponse.success(tenantCommandService.createTenant(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/tenants/{id}")
    @RepeatSubmit
    public ApiResponse<IamTenantQueryService.TenantSnapshot> updateTenant(
            @PathVariable("id") Long id,
            @Valid @RequestBody IamTenantCommandService.TenantUpsertRequest request
    ) {
        require("system:tenant:update");
        return ApiResponse.success(tenantCommandService.updateTenant(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/tenants/{id}/status")
    @RepeatSubmit
    public ApiResponse<IamTenantQueryService.TenantSnapshot> changeTenantStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody IamTenantCommandService.TenantStatusRequest request
    ) {
        require("system:tenant:update");
        return ApiResponse.success(tenantCommandService.changeTenantStatus(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/tenants/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> archiveTenant(@PathVariable("id") Long id) {
        require("system:tenant:delete");
        return ApiResponse.success(tenantCommandService.archiveTenant(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PutMapping("/tenants/{tenantId}/members/{userId}")
    @RepeatSubmit
    public ApiResponse<IamTenantQueryService.TenantSnapshot> upsertTenantMember(
            @PathVariable("tenantId") Long tenantId,
            @PathVariable("userId") Long userId,
            @Valid @RequestBody IamTenantCommandService.TenantMemberRequest request
    ) {
        require("system:tenant:member");
        return ApiResponse.success(tenantCommandService.upsertTenantMember(securityContextFacade.getCurrentUser(), tenantId, userId, request), TraceContext.getRequestId());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<SystemVO.UserVO>> users(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "mobile", required = false) String mobile,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "deptId", required = false) Long deptId,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "registeredStart", required = false) String registeredStart,
            @RequestParam(name = "registeredEnd", required = false) String registeredEnd,
            @RequestParam(name = "lastLoginStart", required = false) String lastLoginStart,
            @RequestParam(name = "lastLoginEnd", required = false) String lastLoginEnd,
            @RequestParam(name = "cursorId", required = false) Long cursorId,
            @RequestParam(name = "cursorCreatedAt", required = false) String cursorCreatedAt,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("system:user:view");
        return ApiResponse.success(
                systemManagementAppService.listUsers(
                        securityContextFacade.getCurrentUser(),
                        userId,
                        username,
                        mobile,
                        email,
                        deptId,
                        status,
                        source,
                        registeredStart,
                        registeredEnd,
                        lastLoginStart,
                        lastLoginEnd,
                        cursorId,
                        cursorCreatedAt,
                        pageNo,
                        pageSize
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/users/{id}")
    public ApiResponse<SystemVO.UserDetailVO> user(@PathVariable("id") Long id) {
        require("system:user:view");
        return ApiResponse.success(systemManagementAppService.getUser(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/users")
    @RepeatSubmit
    public ApiResponse<SystemVO.UserDetailVO> createUser(@Valid @RequestBody SystemDTO.UserUpsertRequest request) {
        require("system:user:create");
        return ApiResponse.success(systemManagementAppService.createUser(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/users/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.UserDetailVO> updateUser(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.UserUpsertRequest request) {
        require("system:user:update");
        return ApiResponse.success(systemManagementAppService.updateUser(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/users/{id}/status")
    @RepeatSubmit
    public ApiResponse<Boolean> changeUserStatus(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.UserStatusRequest request) {
        require("system:user:status");
        return ApiResponse.success(systemManagementAppService.updateUserStatus(securityContextFacade.getCurrentUser(), id, request.getStatus()), TraceContext.getRequestId());
    }

    @DeleteMapping("/users/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteUser(@PathVariable("id") Long id) {
        require("system:user:delete");
        return ApiResponse.success(systemManagementAppService.deleteUser(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/users/{id}/roles")
    public ApiResponse<List<SystemVO.RoleVO>> userRoles(@PathVariable("id") Long id) {
        require("system:user:view");
        return ApiResponse.success(systemManagementAppService.listUserRoles(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/users/export-fields")
    public ApiResponse<List<ExportFieldVO>> userExportFields() {
        require("system:user:export");
        return ApiResponse.success(userExportAppService.listUserExportFields(), TraceContext.getRequestId());
    }

    @PostMapping("/users/export")
    @RepeatSubmit
    public ApiResponse<ExportVO.ExportStartVO> exportUsers(@Valid @RequestBody ExportDTO.UserExportRequest request) {
        require("system:user:export");
        return ApiResponse.success(userExportAppService.exportUsers(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/export-tasks/{taskId}")
    public ApiResponse<ExportVO.ExportTaskVO> exportTask(@PathVariable("taskId") Long taskId) {
        require("system:user:export");
        return ApiResponse.success(exportTaskService.getTask(securityContextFacade.getCurrentUser(), taskId), TraceContext.getRequestId());
    }

    @GetMapping("/roles")
    public ApiResponse<PageResponse<SystemVO.RoleVO>> roles(
            @RequestParam(name = "roleCode", required = false) String roleCode,
            @RequestParam(name = "roleName", required = false) String roleName,
            @RequestParam(name = "roleType", required = false) String roleType,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        require("system:role:view");
        return ApiResponse.success(
                systemManagementAppService.listRoles(securityContextFacade.getCurrentUser(), roleCode, roleName, roleType, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/roles/{id}")
    public ApiResponse<SystemVO.RoleDetailVO> role(@PathVariable("id") Long id) {
        require("system:role:view");
        return ApiResponse.success(systemManagementAppService.getRole(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/roles/default-registration-role")
    public ApiResponse<SystemVO.DefaultRegistrationRoleVO> defaultRegistrationRole() {
        require("system:role:view");
        return ApiResponse.success(systemManagementAppService.getDefaultRegistrationRole(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PutMapping("/roles/default-registration-role")
    @RepeatSubmit
    public ApiResponse<SystemVO.DefaultRegistrationRoleVO> updateDefaultRegistrationRole(@Valid @RequestBody SystemDTO.DefaultRegistrationRoleRequest request) {
        require("system:role:update");
        return ApiResponse.success(systemManagementAppService.updateDefaultRegistrationRole(securityContextFacade.getCurrentUser(), request.getRoleId()), TraceContext.getRequestId());
    }

    @PostMapping("/roles")
    @RepeatSubmit
    public ApiResponse<SystemVO.RoleDetailVO> createRole(@Valid @RequestBody SystemDTO.RoleUpsertRequest request) {
        require("system:role:create");
        return ApiResponse.success(systemManagementAppService.createRole(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/roles/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.RoleDetailVO> updateRole(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.RoleUpsertRequest request) {
        require("system:role:update");
        return ApiResponse.success(systemManagementAppService.updateRole(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/roles/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteRole(@PathVariable("id") Long id) {
        require("system:role:delete");
        return ApiResponse.success(systemManagementAppService.deleteRole(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PutMapping("/roles/{id}/permissions")
    @RepeatSubmit
    public ApiResponse<Boolean> updateRolePermissions(@PathVariable("id") Long id, @RequestBody SystemDTO.RolePermissionRequest request) {
        require("system:role:permissions");
        return ApiResponse.success(systemManagementAppService.updateRolePermissions(securityContextFacade.getCurrentUser(), id, request.getPermissionKeys()), TraceContext.getRequestId());
    }

    @GetMapping("/menus")
    public ApiResponse<List<SystemVO.MenuVO>> menus() {
        require("system:menu:view");
        return ApiResponse.success(systemManagementAppService.listMenus(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/menus/{id}")
    public ApiResponse<SystemVO.MenuVO> menu(@PathVariable("id") Long id) {
        require("system:menu:view");
        return ApiResponse.success(systemManagementAppService.getMenu(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/menus")
    @RepeatSubmit
    public ApiResponse<SystemVO.MenuVO> createMenu(@Valid @RequestBody SystemDTO.MenuUpsertRequest request) {
        require("system:menu:create");
        return ApiResponse.success(systemManagementAppService.createMenu(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/menus/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.MenuVO> updateMenu(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.MenuUpsertRequest request) {
        require("system:menu:update");
        return ApiResponse.success(systemManagementAppService.updateMenu(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @PutMapping("/menus/reorder")
    @RepeatSubmit
    public ApiResponse<Boolean> reorderMenus(@Valid @RequestBody SystemDTO.MenuReorderRequest request) {
        require("system:menu:update");
        return ApiResponse.success(systemManagementAppService.reorderMenus(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PatchMapping("/menus/{id}/status")
    @RepeatSubmit
    public ApiResponse<Boolean> updateMenuStatus(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.MenuStatusRequest request) {
        require("system:menu:status");
        return ApiResponse.success(systemManagementAppService.updateMenuStatus(securityContextFacade.getCurrentUser(), id, request.getStatus()), TraceContext.getRequestId());
    }

    @DeleteMapping("/menus/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteMenu(@PathVariable("id") Long id) {
        require("system:menu:delete");
        return ApiResponse.success(systemManagementAppService.deleteMenu(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/departments")
    public ApiResponse<List<DepartmentVO>> departments() {
        require("system:department:view");
        return ApiResponse.success(departmentAppService.listDepartments(securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/departments/{id}")
    public ApiResponse<DepartmentVO> department(@PathVariable("id") Long id) {
        require("system:department:view");
        return ApiResponse.success(departmentAppService.getDepartment(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/departments")
    @RepeatSubmit
    public ApiResponse<DepartmentVO> createDepartment(@Valid @RequestBody DepartmentUpsertRequest request) {
        require("system:department:create");
        return ApiResponse.success(departmentAppService.createDepartment(securityContextFacade.getCurrentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/departments/{id}")
    @RepeatSubmit
    public ApiResponse<DepartmentVO> updateDepartment(@PathVariable("id") Long id, @Valid @RequestBody DepartmentUpsertRequest request) {
        require("system:department:update");
        return ApiResponse.success(departmentAppService.updateDepartment(securityContextFacade.getCurrentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/departments/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteDepartment(@PathVariable("id") Long id) {
        require("system:department:delete");
        return ApiResponse.success(departmentAppService.deleteDepartment(securityContextFacade.getCurrentUser(), id), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
