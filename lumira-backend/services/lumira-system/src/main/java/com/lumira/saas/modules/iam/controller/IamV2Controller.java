package com.lumira.saas.modules.iam.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.export.ExportDTO;
import com.lumira.api.export.ExportFieldVO;
import com.lumira.api.export.ExportTaskPort;
import com.lumira.api.export.ExportVO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemManagementAppService;
import com.lumira.saas.modules.system.department.app.SystemDepartmentAppService;
import com.lumira.saas.modules.system.department.dto.DepartmentUpsertRequest;
import com.lumira.saas.modules.system.department.vo.DepartmentVO;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.user.app.UserExportAppService;
import com.lumira.saas.modules.system.vo.SystemVO;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
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

import static com.lumira.common.security.AuthenticationTrustSupport.isTrustedCurrentUser;

@RestController
@RequestMapping("/api/v2/iam")
public class IamV2Controller {
    private static final String STATUS_ENABLED = "ENABLED";

    private final SystemManagementAppService systemManagementAppService;
    private final SystemDepartmentAppService departmentAppService;
    private final UserExportAppService userExportAppService;
    private final ExportTaskPort exportTaskService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public IamV2Controller(
            SystemManagementAppService systemManagementAppService,
            SystemDepartmentAppService departmentAppService,
            UserExportAppService userExportAppService,
            ExportTaskPort exportTaskService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(
                systemManagementAppService,
                departmentAppService,
                userExportAppService,
                exportTaskService,
                securityContextFacade,
                permissionGuard,
                null,
                null,
                null,
                false
        );
    }

    public IamV2Controller(
            SystemManagementAppService systemManagementAppService,
            SystemDepartmentAppService departmentAppService,
            UserExportAppService userExportAppService,
            ExportTaskPort exportTaskService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(
                systemManagementAppService,
                departmentAppService,
                userExportAppService,
                exportTaskService,
                securityContextFacade,
                permissionGuard,
                permissionSnapshotService,
                null,
                null,
                false
        );
    }

    public IamV2Controller(
            SystemManagementAppService systemManagementAppService,
            SystemDepartmentAppService departmentAppService,
            UserExportAppService userExportAppService,
            ExportTaskPort exportTaskService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(
                systemManagementAppService,
                departmentAppService,
                userExportAppService,
                exportTaskService,
                securityContextFacade,
                permissionGuard,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                false
        );
    }

    @Autowired
    public IamV2Controller(
            SystemManagementAppService systemManagementAppService,
            SystemDepartmentAppService departmentAppService,
            UserExportAppService userExportAppService,
            ExportTaskPort exportTaskService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(systemManagementAppService, departmentAppService, userExportAppService, exportTaskService, securityContextFacade, permissionGuard, permissionSnapshotService, systemInternalApi, sessionAuthenticationService, true);
    }

    private IamV2Controller(
            SystemManagementAppService systemManagementAppService,
            SystemDepartmentAppService departmentAppService,
            UserExportAppService userExportAppService,
            ExportTaskPort exportTaskService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.systemManagementAppService = systemManagementAppService;
        this.departmentAppService = departmentAppService;
        this.userExportAppService = userExportAppService;
        this.exportTaskService = exportTaskService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping("/permissions")
    public ApiResponse<List<SystemVO.PermissionVO>> permissions() {
        CurrentUser currentUser = require("system:role:view");
        return ApiResponse.success(systemManagementAppService.listPermissions(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/permissions/tree")
    public ApiResponse<List<SystemVO.PermissionTreeVO>> permissionTree() {
        CurrentUser currentUser = require("system:role:view");
        return ApiResponse.success(systemManagementAppService.listPermissionTree(currentUser), TraceContext.getRequestId());
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
        CurrentUser currentUser = require("system:user:view");
        return ApiResponse.success(
                systemManagementAppService.listUsers(
                        currentUser,
                        userId,
                        null,
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
        CurrentUser currentUser = require("system:user:view");
        return ApiResponse.success(systemManagementAppService.getUser(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/users")
    @RepeatSubmit
    public ApiResponse<SystemVO.UserDetailVO> createUser(@Valid @RequestBody SystemDTO.UserUpsertRequest request) {
        CurrentUser currentUser = require("system:user:create");
        return ApiResponse.success(systemManagementAppService.createUser(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/users/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.UserDetailVO> updateUser(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.UserUpsertRequest request) {
        CurrentUser currentUser = require("system:user:update");
        return ApiResponse.success(systemManagementAppService.updateUser(currentUser, id, request), TraceContext.getRequestId());
    }

    @PatchMapping("/users/{id}/status")
    @RepeatSubmit
    public ApiResponse<Boolean> changeUserStatus(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.UserStatusRequest request) {
        CurrentUser currentUser = require("system:user:status");
        return ApiResponse.success(systemManagementAppService.updateUserStatus(currentUser, id, request.getStatus()), TraceContext.getRequestId());
    }

    @DeleteMapping("/users/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteUser(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:user:delete");
        return ApiResponse.success(systemManagementAppService.deleteUser(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/users/{id}/roles")
    public ApiResponse<List<SystemVO.RoleVO>> userRoles(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:user:view");
        return ApiResponse.success(systemManagementAppService.listUserRoles(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/users/export-fields")
    public ApiResponse<List<ExportFieldVO>> userExportFields() {
        CurrentUser currentUser = require("system:user:export");
        return ApiResponse.success(userExportAppService.listUserExportFields(currentUser), TraceContext.getRequestId());
    }

    @PostMapping("/users/export")
    @RepeatSubmit
    public ApiResponse<ExportVO.ExportStartVO> exportUsers(@Valid @RequestBody ExportDTO.UserExportRequest request) {
        CurrentUser currentUser = require("system:user:export");
        return ApiResponse.success(userExportAppService.exportUsers(currentUser, request), TraceContext.getRequestId());
    }

    @GetMapping("/export-tasks/{taskId}")
    public ApiResponse<ExportVO.ExportTaskVO> exportTask(@PathVariable("taskId") Long taskId) {
        CurrentUser currentUser = require("system:user:export");
        return ApiResponse.success(
                exportTaskService.getTaskVo(currentUser, taskId, "system:user:export"),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/roles")
    public ApiResponse<PageResponse<SystemVO.RoleVO>> roles(
            @RequestParam(name = "roleCode", required = false) String roleCode,
            @RequestParam(name = "roleName", required = false) String roleName,
            @RequestParam(name = "roleType", required = false) String roleType,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = require("system:role:view");
        return ApiResponse.success(
                systemManagementAppService.listRoles(currentUser, roleCode, roleName, roleType, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/roles/{id}")
    public ApiResponse<SystemVO.RoleDetailVO> role(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:role:view");
        return ApiResponse.success(systemManagementAppService.getRole(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/roles/default-registration-role")
    public ApiResponse<SystemVO.DefaultRegistrationRoleVO> defaultRegistrationRole() {
        CurrentUser currentUser = require("system:role:view");
        return ApiResponse.success(systemManagementAppService.getDefaultRegistrationRole(currentUser), TraceContext.getRequestId());
    }

    @PutMapping("/roles/default-registration-role")
    @RepeatSubmit
    public ApiResponse<SystemVO.DefaultRegistrationRoleVO> updateDefaultRegistrationRole(@Valid @RequestBody SystemDTO.DefaultRegistrationRoleRequest request) {
        CurrentUser currentUser = require("system:role:update");
        return ApiResponse.success(systemManagementAppService.updateDefaultRegistrationRole(currentUser, request.getRoleId()), TraceContext.getRequestId());
    }

    @PostMapping("/roles")
    @RepeatSubmit
    public ApiResponse<SystemVO.RoleDetailVO> createRole(@Valid @RequestBody SystemDTO.RoleUpsertRequest request) {
        CurrentUser currentUser = require("system:role:create");
        return ApiResponse.success(systemManagementAppService.createRole(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/roles/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.RoleDetailVO> updateRole(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.RoleUpsertRequest request) {
        CurrentUser currentUser = require("system:role:update");
        return ApiResponse.success(systemManagementAppService.updateRole(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/roles/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteRole(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:role:delete");
        return ApiResponse.success(systemManagementAppService.deleteRole(currentUser, id), TraceContext.getRequestId());
    }

    @PutMapping("/roles/{id}/permissions")
    @RepeatSubmit
    public ApiResponse<Boolean> updateRolePermissions(@PathVariable("id") Long id, @RequestBody SystemDTO.RolePermissionRequest request) {
        CurrentUser currentUser = require("system:role:grant");
        return ApiResponse.success(systemManagementAppService.updateRolePermissions(currentUser, id, request.getPermissionKeys()), TraceContext.getRequestId());
    }

    @GetMapping("/menus")
    public ApiResponse<List<SystemVO.MenuVO>> menus() {
        CurrentUser currentUser = require("system:menu:view");
        return ApiResponse.success(systemManagementAppService.listMenus(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/menus/{id}")
    public ApiResponse<SystemVO.MenuVO> menu(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:menu:view");
        return ApiResponse.success(systemManagementAppService.getMenu(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/menus")
    @RepeatSubmit
    public ApiResponse<SystemVO.MenuVO> createMenu(@Valid @RequestBody SystemDTO.MenuUpsertRequest request) {
        CurrentUser currentUser = require("system:menu:create");
        return ApiResponse.success(systemManagementAppService.createMenu(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/menus/{id}")
    @RepeatSubmit
    public ApiResponse<SystemVO.MenuVO> updateMenu(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.MenuUpsertRequest request) {
        CurrentUser currentUser = require("system:menu:update");
        return ApiResponse.success(systemManagementAppService.updateMenu(currentUser, id, request), TraceContext.getRequestId());
    }

    @PutMapping("/menus/reorder")
    @RepeatSubmit
    public ApiResponse<Boolean> reorderMenus(@Valid @RequestBody SystemDTO.MenuReorderRequest request) {
        CurrentUser currentUser = require("system:menu:update");
        return ApiResponse.success(systemManagementAppService.reorderMenus(currentUser, request), TraceContext.getRequestId());
    }

    @PatchMapping("/menus/{id}/status")
    @RepeatSubmit
    public ApiResponse<Boolean> updateMenuStatus(@PathVariable("id") Long id, @Valid @RequestBody SystemDTO.MenuStatusRequest request) {
        CurrentUser currentUser = require("system:menu:status");
        return ApiResponse.success(systemManagementAppService.updateMenuStatus(currentUser, id, request.getStatus()), TraceContext.getRequestId());
    }

    @DeleteMapping("/menus/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteMenu(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:menu:delete");
        return ApiResponse.success(systemManagementAppService.deleteMenu(currentUser, id), TraceContext.getRequestId());
    }

    @GetMapping("/departments")
    public ApiResponse<List<DepartmentVO>> departments() {
        CurrentUser currentUser = require("system:department:view");
        return ApiResponse.success(departmentAppService.listDepartments(currentUser), TraceContext.getRequestId());
    }

    @GetMapping("/departments/{id}")
    public ApiResponse<DepartmentVO> department(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:department:view");
        return ApiResponse.success(departmentAppService.getDepartment(currentUser, id), TraceContext.getRequestId());
    }

    @PostMapping("/departments")
    @RepeatSubmit
    public ApiResponse<DepartmentVO> createDepartment(@Valid @RequestBody DepartmentUpsertRequest request) {
        CurrentUser currentUser = require("system:department:create");
        return ApiResponse.success(departmentAppService.createDepartment(currentUser, request), TraceContext.getRequestId());
    }

    @PutMapping("/departments/{id}")
    @RepeatSubmit
    public ApiResponse<DepartmentVO> updateDepartment(@PathVariable("id") Long id, @Valid @RequestBody DepartmentUpsertRequest request) {
        CurrentUser currentUser = require("system:department:update");
        return ApiResponse.success(departmentAppService.updateDepartment(currentUser, id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/departments/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteDepartment(@PathVariable("id") Long id) {
        CurrentUser currentUser = require("system:department:delete");
        return ApiResponse.success(departmentAppService.deleteDepartment(currentUser, id), TraceContext.getRequestId());
    }

    private CurrentUser require(String permissionKey) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        currentUser = requireTrustedUser(currentUser);
        permissionGuard.requirePermission(currentUser, permissionKey);
        return currentUser;
    }

    private CurrentUser requireTrustedUser(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser;
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            String currentUserUuid = userSnapshot == null || !StringUtils.hasText(userSnapshot.userUuid())
                    ? null
                    : userSnapshot.userUuid().trim();
            String currentUsername = userSnapshot == null || !StringUtils.hasText(userSnapshot.username())
                    ? null
                    : userSnapshot.username().trim();
            if (userSnapshot == null
                    || userSnapshot.userId() == null
                    || !userId.equals(userSnapshot.userId())
                    || !StringUtils.hasText(currentUserUuid)
                    || !normalizedUserUuid.equals(currentUserUuid)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            if (!StringUtils.hasText(currentUsername)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(currentUserUuid);
            currentUser.setUsername(currentUsername);
            normalizedUserUuid = currentUserUuid;
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(SessionAuthenticationService.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return refreshedUser;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
    }
}
